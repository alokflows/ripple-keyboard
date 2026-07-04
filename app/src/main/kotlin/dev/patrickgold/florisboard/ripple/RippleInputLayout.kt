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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.editorInstance
import dev.patrickgold.florisboard.ime.ImeUiMode
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.rippleManager
import org.florisboard.lib.compose.rippleClickable
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.SnyggButton
import org.florisboard.lib.snygg.ui.SnyggChip
import org.florisboard.lib.snygg.ui.SnyggColumn
import org.florisboard.lib.snygg.ui.SnyggIcon
import org.florisboard.lib.snygg.ui.SnyggIconButton
import org.florisboard.lib.snygg.ui.SnyggRow
import org.florisboard.lib.snygg.ui.SnyggText

/**
 * The Ripple panel — the cross-device heart of this fork, structured like
 * [dev.patrickgold.florisboard.ime.clipboard.ClipboardInputLayout]:
 *
 * - Unpaired: enter the pairing code shown on another Ripple device and connect.
 * - Paired: received messages appear as chips; tapping a chip commits the text
 *   at the cursor. A compose row seals + sends text to the paired devices
 *   (optimistic, never blocking on the network).
 *
 * Because an IME cannot type into a text field inside its own window, code entry
 * and the compose field use a small self-contained inline key grid.
 */
@Composable
fun RippleInputLayout(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val editorInstance by context.editorInstance()
    val rippleManager by context.rippleManager()

    val rippleState by rippleManager.state.collectAsState()
    LaunchedEffect(Unit) {
        // Silently re-pair with the persisted code after a keyboard restart.
        rippleManager.resume()
    }

    var codeInput by remember { mutableStateOf("") }
    var composeInput by remember { mutableStateOf("") }
    var composeExpanded by remember { mutableStateOf(false) }

    val paired = rippleState.code.isNotEmpty()

    val statusText = when {
        rippleState.terminal != null -> rippleState.terminal!!
        !paired -> stringRes(R.string.ripple__status_not_paired)
        rippleState.state == ConnState.Connected -> {
            val others = (rippleState.members.size - 1).coerceAtLeast(0)
            if (others > 0) {
                "${stringRes(R.string.ripple__status_connected)} · $others"
            } else {
                stringRes(R.string.ripple__status_waiting)
            }
        }
        rippleState.state == ConnState.Connecting ||
            rippleState.state == ConnState.Reconnecting -> stringRes(R.string.ripple__status_connecting)
        else -> stringRes(R.string.ripple__status_offline)
    }

    @Composable
    fun HeaderRow() {
        SnyggRow(FlorisImeUi.RippleHeader.elementName,
            modifier = Modifier
                .fillMaxWidth()
                .height(FlorisImeSizing.smartbarHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val sizeModifier = Modifier
                .sizeIn(maxHeight = FlorisImeSizing.smartbarHeight)
                .aspectRatio(1f)
            SnyggIconButton(
                elementName = FlorisImeUi.RippleHeaderButton.elementName,
                onClick = { keyboardManager.activeState.imeUiMode = ImeUiMode.TEXT },
                modifier = sizeModifier,
            ) {
                SnyggIcon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                )
            }
            SnyggText(
                elementName = FlorisImeUi.RippleHeaderText.elementName,
                modifier = Modifier.weight(1f),
                text = if (paired) {
                    "${stringRes(R.string.ripple__header_title)} · ${rippleState.code}"
                } else {
                    stringRes(R.string.ripple__header_title)
                },
            )
            if (paired) {
                SnyggIconButton(
                    elementName = FlorisImeUi.RippleHeaderButton.elementName,
                    onClick = {
                        rippleManager.leave()
                        codeInput = ""
                        composeInput = ""
                        composeExpanded = false
                    },
                    modifier = sizeModifier,
                ) {
                    SnyggIcon(
                        imageVector = Icons.Default.LinkOff,
                    )
                }
            }
        }
    }

    @Composable
    fun ColumnScope.ConnectView() {
        SnyggColumn(FlorisImeUi.RippleContent.elementName,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            SnyggText(
                elementName = FlorisImeUi.RippleStatusText.elementName,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                text = statusText,
            )
            SnyggBox(FlorisImeUi.RippleComposeField.elementName,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
            ) {
                SnyggText(
                    text = codeInput.ifEmpty { stringRes(R.string.ripple__connect_hint) },
                )
            }
            InlineKeyGrid(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                withSpace = false,
                onKey = { key -> if (codeInput.length < 24) codeInput += key },
                onBackspace = { codeInput = codeInput.dropLast(1) },
            )
            SnyggButton(FlorisImeUi.RippleActionButton.elementName,
                onClick = {
                    rippleManager.connect(codeInput)
                    codeInput = ""
                },
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                enabled = codeInput.length >= 4,
            ) {
                SnyggText(
                    text = stringRes(R.string.ripple__connect_button),
                )
            }
        }
    }

    @Composable
    fun ColumnScope.PairedView() {
        SnyggColumn(FlorisImeUi.RippleContent.elementName,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            SnyggText(
                elementName = FlorisImeUi.RippleStatusText.elementName,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .rippleClickable { rippleManager.dismissNotice() },
                text = rippleState.notice ?: statusText,
            )
            val received = remember(rippleState.messages) {
                rippleState.messages.filter { !it.mine }.asReversed()
            }
            if (received.isEmpty()) {
                SnyggText(
                    elementName = FlorisImeUi.RippleStatusText.elementName,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(8.dp),
                    text = stringRes(R.string.ripple__empty_message),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    items(received) { message ->
                        // Tapping a received message inserts it at the cursor —
                        // the headline feature. Always instant and local.
                        SnyggChip(
                            elementName = FlorisImeUi.RippleChip.elementName,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            onClick = { editorInstance.commitText(message.text) },
                            text = message.text,
                        )
                    }
                }
            }
            SnyggRow(FlorisImeUi.RippleComposeRow.elementName,
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SnyggBox(FlorisImeUi.RippleComposeField.elementName,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    clickAndSemanticsModifier = Modifier.rippleClickable {
                        composeExpanded = !composeExpanded
                    },
                ) {
                    SnyggText(
                        text = composeInput.ifEmpty { stringRes(R.string.ripple__compose_hint) },
                    )
                }
                SnyggIconButton(
                    elementName = FlorisImeUi.RippleActionButton.elementName,
                    onClick = {
                        // Optimistic send: seals locally and fires the frame async,
                        // never waiting on the network.
                        rippleManager.send(composeInput)
                        composeInput = ""
                        composeExpanded = false
                    },
                    modifier = Modifier
                        .sizeIn(maxHeight = FlorisImeSizing.smartbarHeight)
                        .aspectRatio(1f),
                    enabled = composeInput.isNotBlank(),
                ) {
                    SnyggIcon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                    )
                }
            }
            if (composeExpanded) {
                InlineKeyGrid(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.4f),
                    withSpace = true,
                    onKey = { key -> composeInput += key.lowercase() },
                    onBackspace = { composeInput = composeInput.dropLast(1) },
                )
            }
        }
    }

    SnyggColumn(
        modifier = modifier
            .fillMaxWidth()
            .height(FlorisImeSizing.imeUiHeight()),
    ) {
        HeaderRow()
        if (paired) {
            PairedView()
        } else {
            ConnectView()
        }
    }
}

/**
 * A compact self-contained key grid used to type into panel-local fields (the
 * pairing code, the compose-and-send text). It exists because the keyboard's own
 * key input always goes to the target app, never to views inside the IME window.
 */
@Composable
private fun InlineKeyGrid(
    modifier: Modifier = Modifier,
    withSpace: Boolean,
    onKey: (String) -> Unit,
    onBackspace: () -> Unit,
) {
    val rows = remember { listOf("1234567890", "QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM") }
    Column(modifier = modifier) {
        for ((index, row) in rows.withIndex()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                for (char in row) {
                    SnyggButton(FlorisImeUi.RippleKey.elementName,
                        onClick = { onKey(char.toString()) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) {
                        SnyggText(
                            text = char.toString(),
                        )
                    }
                }
                if (index == rows.lastIndex) {
                    if (withSpace) {
                        SnyggButton(FlorisImeUi.RippleKey.elementName,
                            onClick = { onKey(" ") },
                            modifier = Modifier
                                .weight(1.5f)
                                .fillMaxHeight(),
                        ) {
                            SnyggText(
                                text = "␣",
                            )
                        }
                    }
                    SnyggButton(FlorisImeUi.RippleKey.elementName,
                        onClick = onBackspace,
                        modifier = Modifier
                            .weight(1.5f)
                            .fillMaxHeight(),
                    ) {
                        SnyggIcon(
                            imageVector = Icons.AutoMirrored.Outlined.Backspace,
                        )
                    }
                }
            }
        }
    }
}
