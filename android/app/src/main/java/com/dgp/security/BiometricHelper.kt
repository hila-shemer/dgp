package com.dgp.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Helper class for managing encrypted storage with biometric requirements.
 */
class BiometricHelper {

    private val KEY_ALIAS = "DGP_MASTER_SEED_KEY"
    private val ANDROID_KEYSTORE = "AndroidKeyStore"
    private val TRANSFORMATION = "${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_GCM}/${KeyProperties.ENCRYPTION_PADDING_NONE}"

    init {
        generateKeyIfMissing()
    }

    private fun generateKeyIfMissing() {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(true) // Requires Biometric/PIN
                .setInvalidatedByBiometricEnrollment(true) // Security best practice
                .build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        }
    }

    fun getEncryptionCipher(): Cipher {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val secretKey = keyStore.getKey(KEY_ALIAS, null) as SecretKey
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        return cipher
    }

    fun getDecryptionCipher(iv: ByteArray): Cipher {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val secretKey = keyStore.getKey(KEY_ALIAS, null) as SecretKey
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        return cipher
    }

    /**
     * Helper to encrypt the seed after successful biometric authentication.
     */
    fun encrypt(cipher: Cipher, plaintext: String): Pair<ByteArray, ByteArray> {
        val ciphertext = cipher.doFinal(plaintext.toByteArray())
        return Pair(ciphertext, cipher.iv)
    }

    /**
     * Helper to decrypt the seed after successful biometric authentication.
     */
    fun decrypt(cipher: Cipher, ciphertext: ByteArray): String {
        return String(cipher.doFinal(ciphertext))
    }
}
