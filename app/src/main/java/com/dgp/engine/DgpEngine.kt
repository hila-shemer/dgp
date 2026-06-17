package com.dgp.engine

import java.math.BigInteger
import java.security.MessageDigest
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

    companion object {
        /** Number of flags in the gallery (see FlagGallery). Load-bearing: the
         *  flag index maps into that ordered list. */
        const val FLAG_COUNT = 10
        /** Index of the reference flag — the vanity-mining target. */
        const val REFERENCE_FLAG_INDEX = 0

        // Reserved salt that domain-separates the visual fingerprint from every
        // password / aeskey derivation (those salt with the service name).
        private val FINGERPRINT_SALT = "dgp-flag-fp:v1".toByteArray()

        /** Reserved salt prefix domain-separating subaccount cap-tokens from every
         *  password derivation (which salt with the service name). */
        private const val SUBACCOUNT_SALT_PREFIX = "dgp-subaccount:v1:"

        /** Cap-token length in BIP-39 words (~264 bits — seed-grade). */
        const val SUBACCOUNT_WORDS = 24
    }

    /**
     * 32 bytes derived from seed+account under a reserved salt. This is the only
     * expensive step (PBKDF2-HMAC-SHA1, same iteration count as password gen).
     * Independent of the password path: callers display only the low-entropy
     * flag/word derived from these bytes, never the bytes themselves.
     */
    fun deriveFingerprintBytes(seed: String, account: String, iterations: Int = 42000): ByteArray {
        val keySpec: KeySpec = PBEKeySpec((seed + account).toCharArray(), FINGERPRINT_SALT, iterations, 32 * 8)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        return factory.generateSecret(keySpec).encoded
    }

    /**
     * One-way 24-word cap-token for [label] under the (seed, account) identity.
     * Domain-separated from password derivation via a reserved salt prefix; PBKDF2
     * is one-way, so a cap-token holder cannot recover seed/account. The output is
     * a seed-grade word phrase an agent can use as its own DGP seed.
     */
    fun deriveSubaccountSeed(seed: String, account: String, label: String): String {
        val salt = (SUBACCOUNT_SALT_PREFIX + label).toByteArray()
        val keySpec: KeySpec = PBEKeySpec((seed + account).toCharArray(), salt, 42000, 40 * 8)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        val raw = factory.generateSecret(keySpec).encoded
        return renderWordPhrase(raw, SUBACCOUNT_WORDS)
    }

    /** Fixed-length CamelCase phrase: exactly [count] words, low-order word first
     *  (divmod by 2048). Never stops early — matches Python `_render_word_phrase`
     *  byte-for-byte, so cap-tokens are identical across engines. */
    private fun renderWordPhrase(data: ByteArray, count: Int): String {
        var intData = BigInteger(1, data)
        val wordBn = BigInteger.valueOf(2048)
        val sb = StringBuilder()
        repeat(count) {
            val (div, mod) = intData.divideAndRemainder(wordBn)
            val word = wordList[mod.toInt()]
            sb.append(word.replaceFirstChar { it.uppercase() })
            intData = div
        }
        return sb.toString()
    }

    /** Two lowercase BIP-39 words joined by '-'. Nonce-independent, so the
     *  correct identity always shows the same word. */
    fun fingerprintWord(fpBytes: ByteArray): String {
        var intData = BigInteger(1, fpBytes)
        val wordBn = BigInteger.valueOf(2048)
        val words = mutableListOf<String>()
        repeat(2) {
            val (div, mod) = intData.divideAndRemainder(wordBn)
            words.add(wordList[mod.toInt()])
            intData = div
        }
        return words.joinToString("-")
    }

    /** Flag index = first 4 bytes of SHA-256(fpBytes ‖ nonce-LE) as an unsigned
     *  int, mod flagCount. Fast — so nonce mining is cheap. */
    fun flagIndexFor(fpBytes: ByteArray, nonce: Int, flagCount: Int = FLAG_COUNT): Int {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(fpBytes)
        md.update(byteArrayOf(
            (nonce and 0xFF).toByte(),
            ((nonce ushr 8) and 0xFF).toByte(),
            ((nonce ushr 16) and 0xFF).toByte(),
            ((nonce ushr 24) and 0xFF).toByte(),
        ))
        val h = md.digest()
        val v = ((h[0].toLong() and 0xFF) shl 24) or
            ((h[1].toLong() and 0xFF) shl 16) or
            ((h[2].toLong() and 0xFF) shl 8) or
            (h[3].toLong() and 0xFF)
        return (v % flagCount).toInt()
    }

    /** Smallest nonce >= 0 mapping fpBytes onto targetIndex (~flagCount tries on
     *  average; SHA-256 over the varying nonce is uniform, so it always finds one). */
    fun mineFlagNonce(fpBytes: ByteArray, targetIndex: Int = REFERENCE_FLAG_INDEX, flagCount: Int = FLAG_COUNT): Int {
        var nonce = 0
        while (flagIndexFor(fpBytes, nonce, flagCount) != targetIndex) nonce++
        return nonce
    }

    /** Convenience: flag index (for a stored nonce) + nonce-independent word. */
    fun fingerprintFor(fpBytes: ByteArray, nonce: Int, flagCount: Int = FLAG_COUNT): FlagFingerprint =
        FlagFingerprint(flagIndexFor(fpBytes, nonce, flagCount), fingerprintWord(fpBytes))

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

/** A rendered identity fingerprint: which flag to show + the two-word label. */
data class FlagFingerprint(val flagIndex: Int, val word: String)
