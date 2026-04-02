package com.dgp.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation tests for ConfigCrypto — must run on device/emulator because
 * `android.util.Base64` is not available in the JVM unit test environment.
 *
 * Run via:  ./gradlew :app:connectedAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class ConfigCryptoInstrumentedTest {

    // ── Happy-path round-trips ─────────────────────────────────────────────────

    @Test
    fun encrypt_decrypt_roundTrip() {
        val plaintext = "hello world"
        val seed = "my-seed"
        val encrypted = ConfigCrypto.encrypt(plaintext, seed)
        val decrypted = ConfigCrypto.decrypt(encrypted, seed)
        assertEquals(plaintext, decrypted)
    }

    @Test
    fun encrypt_decrypt_emptyString() {
        val encrypted = ConfigCrypto.encrypt("", "seed")
        val decrypted = ConfigCrypto.decrypt(encrypted, "seed")
        assertEquals("", decrypted)
    }

    @Test
    fun encrypt_decrypt_largePayload() {
        val large = "x".repeat(100_000)
        val encrypted = ConfigCrypto.encrypt(large, "seed")
        val decrypted = ConfigCrypto.decrypt(encrypted, "seed")
        assertEquals(large, decrypted)
    }

    @Test
    fun encrypt_decrypt_unicodeAndSpecialChars() {
        val special = """{"key":"value","unicode":"日本語","emoji":"🔑","quotes":"He said \"hi\""}"""
        val encrypted = ConfigCrypto.encrypt(special, "seed")
        val decrypted = ConfigCrypto.decrypt(encrypted, "seed")
        assertEquals(special, decrypted)
    }

    @Test
    fun encrypt_decrypt_longSeed() {
        val longSeed = "A".repeat(500)
        val encrypted = ConfigCrypto.encrypt("payload", longSeed)
        val decrypted = ConfigCrypto.decrypt(encrypted, longSeed)
        assertEquals("payload", decrypted)
    }

    // ── IV randomness ─────────────────────────────────────────────────────────

    @Test
    fun twoEncryptionsOfSamePlaintext_produceDifferentCiphertexts() {
        // A fixed IV would be a security vulnerability; this guards against it.
        val c1 = ConfigCrypto.encrypt("same plaintext", "seed")
        val c2 = ConfigCrypto.encrypt("same plaintext", "seed")
        assertNotEquals(
            "Two encryptions of identical plaintext must produce distinct ciphertexts (random IV)",
            c1, c2
        )
    }

    @Test
    fun bothCiphertexts_decryptToSamePlaintext() {
        // Even though the ciphertexts differ (different IVs), both must decrypt correctly.
        val plaintext = "same plaintext"
        val c1 = ConfigCrypto.encrypt(plaintext, "seed")
        val c2 = ConfigCrypto.encrypt(plaintext, "seed")
        assertEquals(plaintext, ConfigCrypto.decrypt(c1, "seed"))
        assertEquals(plaintext, ConfigCrypto.decrypt(c2, "seed"))
    }

    // ── Failure modes ─────────────────────────────────────────────────────────

    @Test
    fun decrypt_wrongSeed_returnsNull() {
        val encrypted = ConfigCrypto.encrypt("secret data", "correct-seed")
        val result = ConfigCrypto.decrypt(encrypted, "wrong-seed")
        assertNull("Wrong seed must fail authentication and return null", result)
    }

    @Test
    fun decrypt_tamperedCiphertext_returnsNull() {
        // AES-GCM is authenticated; flipping any byte in the ciphertext invalidates the tag.
        val encrypted = ConfigCrypto.encrypt("secret", "seed")
        // Replace the last 4 chars of the Base64 to corrupt the GCM auth tag
        val tampered = encrypted.dropLast(4) + "AAAA"
        val result = ConfigCrypto.decrypt(tampered, "seed")
        assertNull("Tampered ciphertext must fail GCM authentication and return null", result)
    }

    @Test
    fun decrypt_truncatedToFewerThan12Bytes_returnsNull() {
        // The IV alone is 12 bytes; anything shorter is structurally invalid.
        // Base64("test") = "dGVzdA==" which decodes to 4 bytes — below the 12-byte minimum.
        val result = ConfigCrypto.decrypt("dGVzdA==", "seed")
        assertNull("Payload shorter than IV length must return null", result)
    }

    @Test
    fun decrypt_emptyString_returnsNull() {
        val result = ConfigCrypto.decrypt("", "seed")
        assertNull(result)
    }

    @Test
    fun decrypt_invalidBase64_returnsNull() {
        val result = ConfigCrypto.decrypt("not-valid-base64!!!", "seed")
        assertNull(result)
    }

    @Test
    fun decrypt_truncatedValidCiphertext_returnsNull() {
        // Encrypt, then chop bytes from the end — ciphertext is too short to verify GCM tag.
        val encrypted = ConfigCrypto.encrypt("some data", "seed")
        val truncated = encrypted.take(encrypted.length / 2)
        val result = ConfigCrypto.decrypt(truncated, "seed")
        assertNull(result)
    }

    // ── Key derivation consistency ────────────────────────────────────────────

    @Test
    fun differentSeeds_produceIndependentEncryption() {
        // Data encrypted with seed-A must not be decryptable with seed-B.
        val encrypted = ConfigCrypto.encrypt("payload", "seed-A")
        assertNull(ConfigCrypto.decrypt(encrypted, "seed-B"))
    }

    @Test
    fun sameSeed_alwaysDecryptsCorrectly() {
        // Key derivation from the same seed must be deterministic (no randomness in key).
        val seed = "stable-seed"
        val encrypted = ConfigCrypto.encrypt("data", seed)
        // Decrypt three times; each must succeed
        repeat(3) {
            assertEquals("data", ConfigCrypto.decrypt(encrypted, seed))
        }
    }
}
