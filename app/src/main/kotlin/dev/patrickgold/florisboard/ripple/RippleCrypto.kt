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

import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Ripple end-to-end crypto — the Kotlin mirror of the Ripple product repo's
 * `packages/core/crypto.mjs` (JS) and `packages/core-rs` (Rust). Byte-for-byte
 * compatible: same room hash, same sealed blob format, so this keyboard
 * interoperates with the web + desktop Ripple clients with no key exchange.
 * Verified against the shared cross-language vectors (see [RippleCryptoTest] and
 * the product repo's HANDOFF §3).
 *
 * Pure JVM crypto (`javax.crypto` / `java.security`) — no Android-only APIs — so
 * it runs identically in unit tests and on-device (minSdk 26 provides
 * java.util.Base64 + PBKDF2WithHmacSHA256).
 *
 * The shared secret is ONLY the pairing code: both the room id and the AES key are
 * derived from it, so the relay (which sees neither) stays a blind pipe.
 */
object RippleCrypto {
    // Fixed, app-wide KDF salt. DO NOT change — it is part of key derivation and
    // changing it breaks interop with every existing client and the test vectors.
    private val KDF_SALT = "yap.kdf.v1".toByteArray(UTF_8)
    private const val KDF_ITERATIONS = 210_000
    private const val IV_BYTES = 12   // AES-GCM standard nonce length
    private const val TAG_BITS = 128  // AES-GCM auth tag

    private val rng = SecureRandom()
    private val b64urlEnc = Base64.getUrlEncoder().withoutPadding()
    private val b64urlDec = Base64.getUrlDecoder()

    /** Upper-case and strip non-alphanumerics, so "k7qf9p" and "K7QF9P" agree. */
    fun normalizeCode(code: String): String =
        code.uppercase().replace(Regex("[^A-Z0-9]"), "")

    /** base64url(SHA-256(code)). The relay routes on this, never the raw code. */
    fun roomFromCode(code: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(normalizeCode(code).toByteArray(UTF_8))
        return b64urlEnc.encodeToString(digest)
    }

    /** Derive the AES-256-GCM key from the code (PBKDF2-HMAC-SHA256, 210k iters). */
    fun keyFromCode(code: String): SecretKey {
        val spec = PBEKeySpec(normalizeCode(code).toCharArray(), KDF_SALT, KDF_ITERATIONS, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }

    /** Encrypt plaintext → compact base64url blob of (iv(12) || ciphertext||tag). */
    fun seal(key: SecretKey, plaintext: String): String =
        sealRaw(key, plaintext, ByteArray(IV_BYTES).also(rng::nextBytes))

    /** Decrypt a [seal] blob; returns null on wrong key / tamper (never throws). */
    fun unseal(key: SecretKey, blob: String): String? = try {
        val bytes = b64urlDec.decode(blob)
        val iv = bytes.copyOfRange(0, IV_BYTES)
        val ct = bytes.copyOfRange(IV_BYTES, bytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        String(cipher.doFinal(ct), UTF_8)
    } catch (e: Exception) {
        null
    }

    /** Seal with a caller-supplied IV — internal, for deterministic vector tests. */
    internal fun sealRaw(key: SecretKey, plaintext: String, iv: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val ct = cipher.doFinal(plaintext.toByteArray(UTF_8))
        return b64urlEnc.encodeToString(iv + ct)
    }
}
