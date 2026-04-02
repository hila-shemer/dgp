package com.dgp.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

/**
 * Instrumentation tests for BiometricHelper.
 *
 * Design note
 * ───────────
 * `getEncryptionCipher()` / `getDecryptionCipher()` rely on Android Keystore keys created
 * with `setUserAuthenticationRequired(true)`.  Calling `Cipher.init()` on such keys
 * outside of a real biometric authentication will throw `UserNotAuthenticatedException`.
 * It is not possible to automate biometric interaction in instrumentation tests without
 * mocking the entire AndroidKeyStore stack.
 *
 * Instead, these tests exercise the encrypt/decrypt *logic* of BiometricHelper using a
 * standard JCE AES/GCM key (not from the Keystore).  This validates the byte-encoding,
 * IV handling, and UTF-8 round-trips — which is the code that is actually novel to
 * BiometricHelper (the Keystore plumbing is provided by the Android platform).
 *
 * Run via:  ./gradlew :app:connectedAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class BiometricHelperInstrumentedTest {

    private val helper = BiometricHelper()

    /**
     * Creates a matched encryption + decryption cipher pair using a fresh in-memory
     * AES-256-GCM key (not from the Keystore — no biometric auth required).
     */
    private fun makeTestCipherPair(): Pair<Cipher, Cipher> {
        val keyGen = KeyGenerator.getInstance("AES").apply { init(256) }
        val key = keyGen.generateKey()

        val enc = Cipher.getInstance("AES/GCM/NoPadding").also {
            it.init(Cipher.ENCRYPT_MODE, key)
        }
        val iv = enc.iv                       // capture IV after ENCRYPT_MODE init
        val dec = Cipher.getInstance("AES/GCM/NoPadding").also {
            it.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        }
        return Pair(enc, dec)
    }

    // ── Round-trip ────────────────────────────────────────────────────────────

    @Test
    fun encrypt_decrypt_roundTrip() {
        val (enc, dec) = makeTestCipherPair()
        val plaintext = "my-secret-seed-value"
        val (ciphertext, _) = helper.encrypt(enc, plaintext)
        val decrypted = helper.decrypt(dec, ciphertext)
        assertEquals(plaintext, decrypted)
    }

    @Test
    fun encrypt_decrypt_emptyString() {
        val (enc, dec) = makeTestCipherPair()
        val (ciphertext, _) = helper.encrypt(enc, "")
        val decrypted = helper.decrypt(dec, ciphertext)
        assertEquals("", decrypted)
    }

    @Test
    fun encrypt_decrypt_longSeed() {
        val (enc, dec) = makeTestCipherPair()
        val longSeed = "A".repeat(500)
        val (ciphertext, _) = helper.encrypt(enc, longSeed)
        val decrypted = helper.decrypt(dec, ciphertext)
        assertEquals(longSeed, decrypted)
    }

    @Test
    fun encrypt_decrypt_unicodeCharacters() {
        val (enc, dec) = makeTestCipherPair()
        val seed = "日本語シード🔑"
        val (ciphertext, _) = helper.encrypt(enc, seed)
        val decrypted = helper.decrypt(dec, ciphertext)
        assertEquals(seed, decrypted)
    }

    // ── IV format ─────────────────────────────────────────────────────────────

    @Test
    fun encrypt_ivIs12Bytes() {
        // AES-GCM standard IV length; must match the GCMParameterSpec(128, iv) in
        // getDecryptionCipher() — if the size ever changes, decryption will silently fail.
        val (enc, _) = makeTestCipherPair()
        val (_, iv) = helper.encrypt(enc, "seed")
        assertEquals("GCM IV must be exactly 12 bytes", 12, iv.size)
    }

    @Test
    fun encrypt_ivMatchesCipherIv() {
        // The returned IV must equal cipher.iv — ensuring the correct bytes are stored
        // and later passed to getDecryptionCipher().
        val (enc, _) = makeTestCipherPair()
        val (_, returnedIv) = helper.encrypt(enc, "seed")
        assertArrayEquals(enc.iv, returnedIv)
    }

    // ── Ciphertext properties ─────────────────────────────────────────────────

    @Test
    fun encrypt_ciphertextIsNotEmpty() {
        val (enc, _) = makeTestCipherPair()
        val (ciphertext, _) = helper.encrypt(enc, "seed")
        assertTrue("Ciphertext must not be empty", ciphertext.isNotEmpty())
    }

    @Test
    fun encrypt_ciphertextDiffersFromPlaintext() {
        val (enc, _) = makeTestCipherPair()
        val plaintext = "my-plaintext-seed"
        val (ciphertext, _) = helper.encrypt(enc, plaintext)
        assertNotNull(ciphertext)
        // Ciphertext (bytes) decoded as UTF-8 should not equal the original string
        val ciphertextAsString = String(ciphertext)
        assertTrue(ciphertextAsString != plaintext)
    }

    @Test
    fun encrypt_longerPlaintext_producesSizableOutput() {
        // AES-GCM ciphertext length = plaintext length + 16-byte tag; verify it is
        // at least as long as the plaintext (basic sanity check).
        val (enc, _) = makeTestCipherPair()
        val plaintext = "A".repeat(100)
        val (ciphertext, _) = helper.encrypt(enc, plaintext)
        assertTrue(ciphertext.size >= plaintext.toByteArray().size)
    }
}
