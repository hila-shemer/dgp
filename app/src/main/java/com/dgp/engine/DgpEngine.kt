package com.dgp.engine

import java.math.BigInteger
import java.security.spec.KeySpec
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Kotlin port of the DGP logic from dgp-simple.c
 */
class DgpEngine(private val wordList: List<String>) {

    private val base58Alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"

    fun generate(
        seed: String,
        name: String,
        entryType: String,
        secret: String,
        iterations: Int = 42000
    ): String {
        val seedSecret = seed + secret
        val salt = name.toByteArray()
        
        // PBKDF2-HMAC-SHA1 matches dgp-simple.c
        val keySpec: KeySpec = PBEKeySpec(seedSecret.toCharArray(), salt, iterations, 40 * 8)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        val binData = factory.generateSecret(keySpec).encoded

        return when (entryType) {
            "hex" -> binToHex(binData.sliceArray(0 until 4))
            "hexlong" -> binToHex(binData.sliceArray(0 until 8))
            "alnum" -> grabAlnum(binData, 8)
            "alnumlong" -> grabAlnum(binData, 12)
            "base58" -> getBase58(binData).take(8)
            "base58long" -> getBase58(binData).take(12)
            "xkcd" -> getXkcd(binData, 4)
            "xkcdlong" -> getXkcd(binData, 6)
            // 32-byte AES-256 key material, hex-encoded. Used by vault entries
            // to encrypt/decrypt user-supplied secrets; never shown to users.
            "aeskey" -> binToHex(binData.sliceArray(0 until 32))
            else -> "unknown type"
        }
    }

    /**
     * Derive a 32-byte AES-256 key from the same (seed + secret, name) PBKDF2
     * parameters used for password generation. Convenience wrapper around the
     * "aeskey" entry type that returns raw bytes instead of hex.
     */
    fun deriveAesKey(seed: String, name: String, secret: String, iterations: Int = 42000): ByteArray {
        val seedSecret = seed + secret
        val salt = name.toByteArray()
        val keySpec: KeySpec = PBEKeySpec(seedSecret.toCharArray(), salt, iterations, 40 * 8)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        val binData = factory.generateSecret(keySpec).encoded
        return binData.sliceArray(0 until 32)
    }

    private fun binToHex(data: ByteArray): String {
        return data.joinToString("") { "%02x".format(it) }
    }

    private fun getBase58(data: ByteArray): String {
        var intData = BigInteger(1, data)
        val base58 = BigInteger.valueOf(58)
        val sb = StringBuilder()

        while (intData > BigInteger.ZERO) {
            val (div, mod) = intData.divideAndRemainder(base58)
            sb.append(base58Alphabet[mod.toInt()])
            intData = div
        }
        return sb.toString()
    }

    private fun isAlnum(s: String): Boolean {
        return s.any { it.isDigit() } && s.any { it.isLowerCase() } && s.any { it.isUpperCase() }
    }

    private fun grabAlnum(data: ByteArray, length: Int): String {
        val raw = getBase58(data)
        for (i in 0..raw.length - length) {
            val candidate = raw.substring(i, i + length)
            if (isAlnum(candidate)) return candidate
        }
        return raw.take(length)
    }

    private fun getXkcd(data: ByteArray, count: Int): String {
        var intData = BigInteger(1, data)
        val wordBn = BigInteger.valueOf(2048)
        val sb = StringBuilder()
        var wordsRemaining = count

        while (intData > BigInteger.ZERO && wordsRemaining > 0) {
            val (div, mod) = intData.divideAndRemainder(wordBn)
            val word = wordList[mod.toInt()]
            sb.append(word.replaceFirstChar { it.uppercase() })
            intData = div
            wordsRemaining--
        }
        return sb.toString()
    }
}
