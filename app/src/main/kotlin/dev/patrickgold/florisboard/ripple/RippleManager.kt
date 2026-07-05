/*
 * Copyright (C) 2026 The Ripple Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ripple

import android.content.Context
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import org.florisboard.lib.kotlin.collectIn
import java.security.SecureRandom
import java.util.Base64

/** The shared, observable connection state — one snapshot for the whole keyboard. */
data class RippleState(
    val code: String = "",
    val state: ConnState = ConnState.Idle,
    val messages: List<ChatMessage> = emptyList(),
    val members: List<Member> = emptyList(),
    val open: Boolean = true,
    val notice: String? = null,
    val terminal: String? = null,
)

/**
 * Process-wide owner of the single Ripple socket, following FlorisBoard's manager
 * pattern (lazy field on [dev.patrickgold.florisboard.FlorisApplication], accessed
 * via `Context.rippleManager()`). All keyboard surfaces observe the one [state]
 * flow, so there is exactly one WebSocket and one shared history. A foreground
 * [RippleConnectionService] keeps the process (and socket) alive while only the
 * keyboard is on screen.
 *
 * The pairing code is persisted so [resume] can silently re-pair after a process
 * restart — the user is never asked twice.
 */
class RippleManager(context: Context) {
    private val appContext = context.applicationContext
    private val prefs by FlorisPreferenceStore
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /** Stable per-install device id (host model + locked rooms key on it). */
    val did: String = stableDid(appContext)

    private var client: RippleClient? = null

    private val _state = MutableStateFlow(RippleState())
    val state: StateFlow<RippleState> = _state.asStateFlow()

    /**
     * Fires the plain text of each newly received message when the active consent
     * mode is [ConsentMode.AUTO]. The IME collects this and commits it at the
     * cursor while the keyboard is visible. Replay is 0 (a commit only makes sense
     * live) and the buffer drops the oldest so a burst can never block ingest.
     */
    private val _autoCommits = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val autoCommits: SharedFlow<String> = _autoCommits.asSharedFlow()

    val isConnected: Boolean get() = client?.isConnected == true
    val currentCode: String get() = _state.value.code

    /** The consent mode chosen in settings; read fresh at each ingest. */
    private val consentMode: ConsentMode get() = prefs.ripple.consentMode.get()

    /** The last code we paired with, remembered across launches (null if none). */
    val savedCode: String? get() = prefs().getString("code", null)?.takeIf { it.isNotBlank() }

    init {
        // Keep the foreground connection service in sync with its toggle: start or
        // stop it as the preference changes while a connection is live.
        prefs.ripple.keepConnectionAlive.asFlow().drop(1).collectIn(scope) { keepAlive ->
            val code = _state.value.code
            if (isConnected && code.isNotEmpty()) {
                if (keepAlive) RippleConnectionService.start(appContext, code)
                else RippleConnectionService.stop(appContext)
            }
        }
    }

    fun connect(code: String) {
        val normalized = code.trim()
        if (normalized.isEmpty()) return
        prefs().edit().putString("code", normalized).apply()
        client?.disconnect()
        val c = RippleClient(did = did, onEvent = ::onEvent)
        client = c
        _state.update { RippleState(code = normalized) }
        c.connect(normalized)
        if (prefs.ripple.keepConnectionAlive.get()) {
            RippleConnectionService.start(appContext, normalized)
        }
    }

    /** Re-pair with the remembered code, so the user isn't asked again. */
    fun resume() {
        if (client == null) savedCode?.let { connect(it) }
    }

    /** Seal + send; returns the optimistic bubble (also appended to [state]). */
    fun send(text: String): ChatMessage? {
        val pending = client?.send(text) ?: return null
        _state.update { it.copy(messages = it.messages + pending) }
        return pending
    }

    fun leave() {
        client?.disconnect()
        client = null
        prefs().edit().remove("code").apply()
        _state.update { RippleState() }
        RippleConnectionService.stop(appContext)
    }

    fun dismissNotice() = _state.update { it.copy(notice = null) }

    private fun prefs() = appContext.getSharedPreferences("ripple", Context.MODE_PRIVATE)

    private fun onEvent(event: RippleEvent) {
        when (event) {
            is RippleEvent.Status -> _state.update { it.copy(state = event.state) }

            is RippleEvent.Incoming -> {
                val mode = consentMode
                // OFF: the panel receives nothing — drop the message at ingest.
                if (!mode.receivesIntoPanel) return
                _state.update { it.copy(messages = it.messages + event.message) }
                // AUTO: also hand the text to the IME for an immediate cursor commit.
                if (mode.autoCommits) _autoCommits.tryEmit(event.message.text)
            }

            is RippleEvent.History -> {
                // OFF: no received history enters the panel either. Any pending
                // messages we sent ourselves are always kept.
                if (!consentMode.receivesIntoPanel) {
                    _state.update { s -> s.copy(messages = s.messages.filter { it.mine }) }
                    return
                }
                _state.update { s ->
                    val mineKept = s.messages.filter { it.mine }
                    s.copy(messages = (event.messages + mineKept).sortedBy { it.t })
                }
            }

            is RippleEvent.Acked -> _state.update { s ->
                s.copy(messages = s.messages.map {
                    if (it.pending && it.cid == event.cid) it.copy(id = event.id, t = event.t, pending = false) else it
                })
            }

            is RippleEvent.Presence -> _state.update { it.copy(members = event.members, open = event.open) }

            is RippleEvent.Notice -> _state.update { it.copy(notice = event.text) }

            RippleEvent.Cleared -> _state.update { it.copy(messages = it.messages.filter { m -> m.pending }) }

            is RippleEvent.Terminal -> _state.update { it.copy(state = ConnState.Closed, terminal = event.reason) }
        }
    }

    private fun stableDid(ctx: Context): String {
        val p = ctx.getSharedPreferences("ripple", Context.MODE_PRIVATE)
        p.getString("did", null)?.let { return it }
        val bytes = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val d = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        p.edit().putString("did", d).apply()
        return d
    }
}
