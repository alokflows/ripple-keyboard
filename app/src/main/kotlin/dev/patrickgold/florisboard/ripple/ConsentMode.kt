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

/**
 * How incoming Ripple text is allowed onto the device — the user's consent for
 * text arriving from a paired device. Chosen in the Ripple settings screen and
 * enforced by [RippleManager] at ingest time (so [OFF] truly never surfaces a
 * message) plus the IME layer (which performs the [AUTO] cursor commit).
 *
 * The semantics are pure data, so they can be unit-tested without Android — see
 * `RippleConsentModeTest`.
 */
enum class ConsentMode {
    /**
     * Incoming text is committed at the cursor immediately whenever the keyboard
     * is visible. It is also kept in the panel history (as a chip), so a message
     * that arrives while the keyboard is hidden is not lost — it can still be
     * inserted by tapping it once the panel is opened.
     */
    AUTO,

    /**
     * The default. Incoming text lands in the panel as tap-to-insert chips and is
     * never committed without an explicit tap.
     */
    ASK,

    /**
     * The panel receives nothing: incoming text (and received history) is dropped
     * at ingest and never enters the shared state. The connection and outgoing
     * send still work; the panel simply shows that receiving is off.
     */
    OFF;

    /** Whether an incoming/received message is allowed into the panel at all. */
    val receivesIntoPanel: Boolean
        get() = this != OFF

    /** Whether an incoming message should be auto-committed at the cursor. */
    val autoCommits: Boolean
        get() = this == AUTO
}
