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

package dev.patrickgold.florisboard.app.settings.ripple

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.enumDisplayEntriesOf
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.ripple.ConnState
import dev.patrickgold.florisboard.ripple.ConsentMode
import dev.patrickgold.florisboard.rippleManager
import dev.patrickgold.jetpref.datastore.ui.ExperimentalJetPrefDatastoreUi
import dev.patrickgold.jetpref.datastore.ui.ListPreference
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import dev.patrickgold.jetpref.datastore.ui.SwitchPreference
import dev.patrickgold.jetpref.material.ui.JetPrefAlertDialog
import dev.patrickgold.jetpref.material.ui.JetPrefTextField
import org.florisboard.lib.compose.stringRes

@OptIn(ExperimentalJetPrefDatastoreUi::class)
@Composable
fun RippleScreen() = FlorisScreen {
    title = stringRes(R.string.settings__ripple__title)
    previewFieldVisible = false

    val context = LocalContext.current
    val rippleManager by context.rippleManager()

    content {
        val rippleState by rippleManager.state.collectAsState()

        // The active pairing code — the live one when connected, otherwise the one
        // remembered across launches. Owned by RippleManager, never duplicated here.
        val code = rippleState.code.ifEmpty { rippleManager.savedCode.orEmpty() }
        val paired = code.isNotEmpty()

        var codeRevealed by remember { mutableStateOf(false) }
        var showChangeDialog by remember { mutableStateOf(false) }

        val statusText = when {
            !paired -> stringRes(R.string.ripple__status_not_paired)
            rippleState.state == ConnState.Connected -> stringRes(R.string.ripple__status_connected)
            rippleState.state == ConnState.Connecting ||
                rippleState.state == ConnState.Reconnecting -> stringRes(R.string.ripple__status_connecting)
            else -> stringRes(R.string.ripple__status_offline)
        }

        PreferenceGroup(title = stringRes(R.string.pref__ripple__group_connection__label)) {
            Preference(
                title = stringRes(R.string.pref__ripple__status__label),
                summary = statusText,
            )
            Preference(
                title = stringRes(R.string.pref__ripple__pairing_code__label),
                summary = when {
                    !paired -> stringRes(R.string.pref__ripple__pairing_code__none)
                    codeRevealed -> code
                    else -> "•".repeat(code.length.coerceAtLeast(4))
                },
                onClick = { if (paired) codeRevealed = !codeRevealed },
                trailing = {
                    if (paired) {
                        Row {
                            IconButton(onClick = { codeRevealed = !codeRevealed }) {
                                Icon(
                                    imageVector = if (codeRevealed) {
                                        Icons.Default.VisibilityOff
                                    } else {
                                        Icons.Default.Visibility
                                    },
                                    contentDescription = null,
                                )
                            }
                            IconButton(onClick = { context.copyPairingCode(code) }) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = null,
                                )
                            }
                        }
                    }
                },
            )
            Preference(
                icon = Icons.Default.Edit,
                title = stringRes(R.string.pref__ripple__change_code__label),
                summary = stringRes(R.string.pref__ripple__change_code__summary),
                onClick = { showChangeDialog = true },
            )
            Preference(
                icon = Icons.Default.LinkOff,
                title = stringRes(R.string.pref__ripple__disconnect__label),
                summary = stringRes(R.string.pref__ripple__disconnect__summary),
                enabledIf = { paired },
                onClick = {
                    rippleManager.leave()
                    codeRevealed = false
                },
            )
        }

        PreferenceGroup(title = stringRes(R.string.pref__ripple__group_incoming__label)) {
            ListPreference(
                prefs.ripple.consentMode,
                title = stringRes(R.string.pref__ripple__consent_mode__label),
                entries = enumDisplayEntriesOf(ConsentMode::class),
            )
        }

        PreferenceGroup(title = stringRes(R.string.pref__ripple__group_service__label)) {
            SwitchPreference(
                prefs.ripple.keepConnectionAlive,
                title = stringRes(R.string.pref__ripple__keep_connection_alive__label),
                summary = stringRes(R.string.pref__ripple__keep_connection_alive__summary),
            )
        }

        if (showChangeDialog) {
            var input by remember { mutableStateOf("") }
            JetPrefAlertDialog(
                title = stringRes(R.string.pref__ripple__change_code__dialog_title),
                confirmLabel = stringRes(R.string.ripple__connect_button),
                onConfirm = {
                    val trimmed = input.trim()
                    if (trimmed.length >= 4) {
                        rippleManager.connect(trimmed)
                        codeRevealed = false
                        showChangeDialog = false
                    }
                },
                dismissLabel = stringRes(R.string.action__cancel),
                onDismiss = { showChangeDialog = false },
            ) {
                JetPrefTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholderText = stringRes(R.string.ripple__connect_hint),
                    singleLine = true,
                )
            }
        }
    }
}

private fun Context.copyPairingCode(code: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("Ripple pairing code", code))
    Toast.makeText(this, R.string.pref__ripple__pairing_code__copied, Toast.LENGTH_SHORT).show()
}
