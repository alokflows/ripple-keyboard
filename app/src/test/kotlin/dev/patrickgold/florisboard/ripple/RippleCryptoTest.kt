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
import kotlin.test.assertNull

/**
 * Asserts the SAME cross-language vectors as the Ripple JS (`crypto.test.mjs`) and
 * Rust (`core-rs`) suites — proving this keyboard interoperates byte-for-byte with
 * the web + desktop clients. If any of these fail, the pairing/crypto contract has
 * drifted and the fork must NOT ship (see HANDOFF §3).
 */
class RippleCryptoTest {
    @Test fun roomVector() {
        assertEquals(
            "m5y7nOTrj9TE1Pbh9LSBNGFqitACsWIlLsKk8cfTqjg",
            RippleCrypto.roomFromCode("K7QF9P"),
        )
    }

    @Test fun sealVectorZeroIv() {
        val key = RippleCrypto.keyFromCode("K7QF9P")
        assertEquals(
            "AAAAAAAAAAAAAAAAA8gVxDfIR9jOqUCwRBdsU7oecTFk-DiEAtrwkOY",
            RippleCrypto.sealRaw(key, "hello, cursor", ByteArray(12)),
        )
    }

    @Test fun roundTripWithEmoji() {
        val key = RippleCrypto.keyFromCode("HELLO9")
        val sealed = RippleCrypto.seal(key, "type at the cursor 🎯")
        assertEquals("type at the cursor 🎯", RippleCrypto.unseal(key, sealed))
    }

    @Test fun wrongKeyReturnsNull() {
        val sealed = RippleCrypto.seal(RippleCrypto.keyFromCode("K7QF9P"), "secret")
        assertNull(RippleCrypto.unseal(RippleCrypto.keyFromCode("WRONG1"), sealed))
    }

    @Test fun normalizeIsCaseAndPunctuationInsensitive() {
        assertEquals("K7QF9P", RippleCrypto.normalizeCode("k7-qf 9p!"))
    }
}
