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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the Ripple consent-mode contract that [RippleManager] and the IME rely on:
 *
 * - AUTO  → text enters the panel AND is auto-committed at the cursor.
 * - ASK   → text enters the panel, but is never auto-committed (tap to insert).
 * - OFF   → text never enters the panel and is never committed.
 *
 * These two booleans drive the ingest branch in `RippleManager.onEvent`, so a
 * regression here silently changes what lands on the user's device.
 */
class RippleConsentModeTest {
    @Test fun auto_receivesAndAutoCommits() {
        assertTrue(ConsentMode.AUTO.receivesIntoPanel)
        assertTrue(ConsentMode.AUTO.autoCommits)
    }

    @Test fun ask_receivesButDoesNotAutoCommit() {
        assertTrue(ConsentMode.ASK.receivesIntoPanel)
        assertFalse(ConsentMode.ASK.autoCommits)
    }

    @Test fun off_receivesNothingAndNeverCommits() {
        assertFalse(ConsentMode.OFF.receivesIntoPanel)
        assertFalse(ConsentMode.OFF.autoCommits)
    }

    @Test fun askIsTheDefault() {
        // The Ripple preference group defaults to ASK — the least surprising mode.
        assertEquals(ConsentMode.ASK, ConsentMode.valueOf("ASK"))
    }

    @Test fun onlyAutoAutoCommits() {
        // Exactly one mode may auto-commit; guards against a future mode slipping
        // through with autoCommits accidentally true.
        assertEquals(listOf(ConsentMode.AUTO), ConsentMode.entries.filter { it.autoCommits })
    }
}
