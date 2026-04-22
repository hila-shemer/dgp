package com.dgp.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * JVM unit tests for DgpEngine — no Android runtime needed.
 *
 * The BIP-39 word list (english.txt) is included as a JVM test resource via the
 * `sourceSets { test { resources.srcDirs += ['src/main/assets'] } }` entry in
 * build.gradle, so these tests validate xkcd output against the real word list.
 */
class DgpEngineTest {

    private lateinit var engine: DgpEngine

    @Before
    fun setUp() {
        // Load the real BIP-39 word list from the assets directory, exposed as a
        // JVM resource so no Android AssetManager is required.
        val stream = javaClass.getResourceAsStream("/english.txt")
        val wordList = stream?.bufferedReader()?.readLines() ?: emptyList()
        engine = DgpEngine(wordList)
    }

    // ── Test vectors ──────────────────────────────────────────────────────────

    /**
     * Runs every hardcoded vector from TestVectors.  Any algorithm regression
     * immediately breaks this test, making it the first line of defence against
     * accidental parameter changes.
     */
    @Test
    fun allTestVectors_produce_expectedOutput() {
        val failures = mutableListOf<String>()
        for (v in TestVectors.vectors) {
            val actual = engine.generate(v.seed, v.name, v.type, v.account)
            if (actual != v.expected) {
                failures += "FAIL [${v.seed.take(8)}:${v.account}:${v.name}:${v.type}]" +
                        " expected=${v.expected} actual=$actual"
            }
        }
        assertTrue("Test vector failures:\n${failures.joinToString("\n")}", failures.isEmpty())
    }

    // ── Output format constraints ─────────────────────────────────────────────

    @Test
    fun hex_returns8HexCharacters() {
        val result = engine.generate("seed", "service", "hex", "")
        assertEquals(8, result.length)
        assertTrue("hex must be lowercase hex", result.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun hexlong_returns16HexCharacters() {
        val result = engine.generate("seed", "service", "hexlong", "")
        assertEquals(16, result.length)
        assertTrue("hexlong must be lowercase hex", result.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun base58_returns8Base58Characters() {
        val base58Chars = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        val result = engine.generate("seed", "service", "base58", "")
        assertEquals(8, result.length)
        assertTrue("base58 must only contain base58 alphabet chars",
            result.all { it in base58Chars })
    }

    @Test
    fun base58long_returns12Base58Characters() {
        val base58Chars = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        val result = engine.generate("seed", "service", "base58long", "")
        assertEquals(12, result.length)
        assertTrue(result.all { it in base58Chars })
    }

    @Test
    fun alnum_returns8CharactersMeetingComplexityRules() {
        val result = engine.generate("seed", "service", "alnum", "")
        assertEquals(8, result.length)
        assertTrue("alnum must contain at least one digit", result.any { it.isDigit() })
        assertTrue("alnum must contain at least one lowercase letter", result.any { it.isLowerCase() })
        assertTrue("alnum must contain at least one uppercase letter", result.any { it.isUpperCase() })
    }

    @Test
    fun alnumlong_returns12CharactersMeetingComplexityRules() {
        val result = engine.generate("seed", "service", "alnumlong", "")
        assertEquals(12, result.length)
        assertTrue(result.any { it.isDigit() })
        assertTrue(result.any { it.isLowerCase() })
        assertTrue(result.any { it.isUpperCase() })
    }

    @Test
    fun xkcd_produces4CapitalisedWords() {
        // With the real BIP-39 word list, words are all lowercase; DgpEngine capitalises
        // the first letter of each word, so 4 words → 4 uppercase-starting segments.
        val result = engine.generate("seed", "service", "xkcd", "")
        assertTrue("xkcd result must not be empty", result.isNotEmpty())
        assertTrue("xkcd result must start with an uppercase letter", result[0].isUpperCase())
        // Count capital letters as a proxy for word count
        val capitalCount = result.count { it.isUpperCase() }
        assertTrue("xkcd should produce ~4 capitalised words", capitalCount in 4..8)
    }

    @Test
    fun xkcdlong_produces6CapitalisedWords() {
        val result = engine.generate("seed", "service", "xkcdlong", "")
        assertTrue(result.isNotEmpty())
        assertTrue(result[0].isUpperCase())
        val capitalCount = result.count { it.isUpperCase() }
        assertTrue("xkcdlong should produce ~6 capitalised words", capitalCount in 6..12)
    }

    @Test
    fun unknownType_returnsErrorString() {
        val result = engine.generate("seed", "service", "bogus", "")
        assertEquals("unknown type", result)
    }

    // ── Determinism ───────────────────────────────────────────────────────────

    @Test
    fun generate_isDeterministic() {
        val a = engine.generate("myseed", "github.com", "alnum", "myaccount")
        val b = engine.generate("myseed", "github.com", "alnum", "myaccount")
        assertEquals("Same inputs must always produce the same password", a, b)
    }

    @Test
    fun differentSeeds_produceDifferentPasswords() {
        val a = engine.generate("seed1", "github.com", "alnum", "")
        val b = engine.generate("seed2", "github.com", "alnum", "")
        assertNotEquals("Different seeds must produce different passwords", a, b)
    }

    @Test
    fun differentServiceNames_produceDifferentPasswords() {
        val a = engine.generate("seed", "github.com", "alnum", "")
        val b = engine.generate("seed", "gmail.com", "alnum", "")
        assertNotEquals(a, b)
    }

    @Test
    fun differentAccounts_produceDifferentPasswords() {
        val noAccount = engine.generate("seed", "github.com", "alnum", "")
        val withAccount = engine.generate("seed", "github.com", "alnum", "myaccount")
        assertNotEquals(noAccount, withAccount)
    }

    // ── Edge cases ────────────────────────────────────────────────────────────

    @Test
    fun emptySeed_doesNotThrow() {
        val result = engine.generate("", "service", "alnum", "")
        assertEquals(8, result.length)
    }

    @Test
    fun emptyAccount_doesNotThrow() {
        val result = engine.generate("seed", "service", "alnum", "")
        assertEquals(8, result.length)
    }

    @Test
    fun seed64Chars_doesNotThrow() {
        // 64-char seeds trigger pre-hashing in some historic implementations;
        // the Kotlin engine should handle them identically to shorter seeds.
        val result = engine.generate("A".repeat(64), "salt", "alnum", "")
        assertEquals(8, result.length)
    }

    @Test
    fun seed65Chars_doesNotThrow() {
        val result = engine.generate("A".repeat(65), "salt", "alnum", "")
        assertEquals(8, result.length)
    }

    @Test
    fun veryLongSeed_doesNotThrow() {
        val result = engine.generate("A".repeat(1000), "service", "alnum", "")
        assertEquals(8, result.length)
    }

    @Test
    fun unicodeSeed_doesNotThrow() {
        val result = engine.generate("日本語シード🔑", "service", "alnum", "")
        assertEquals(8, result.length)
    }

    @Test
    fun unicodeServiceName_doesNotThrow() {
        val result = engine.generate("seed", "日本語サービス", "alnum", "")
        assertEquals(8, result.length)
    }

    // ── Specific known vectors (subset — belt-and-suspenders) ─────────────────

    @Test
    fun knownVector_basicAlnum() {
        assertEquals("oxToKKV2", engine.generate("a", "aa", "alnum", ""))
    }

    @Test
    fun knownVector_basicBase58() {
        assertEquals("zWNoxToK", engine.generate("a", "aa", "base58", ""))
    }

    @Test
    fun knownVector_passwordSalt_hex() {
        assertEquals("21934584",
            engine.generate("passwordPASSWORDpassword", "saltSALTsaltSALTsaltSALTsaltSALTsalt", "hex", ""))
    }

    @Test
    fun knownVector_passWordSalt_xkcd() {
        assertEquals("StemDialSureHen",
            engine.generate("pass", "salt", "xkcd", "word"))
    }

    @Test
    fun knownVector_passWordSalt_xkcdlong() {
        assertEquals("StemDialSureHenAlbumDonor",
            engine.generate("pass", "salt", "xkcdlong", "word"))
    }

    // ── aeskey entry type ─────────────────────────────────────────────────────

    @Test
    fun aeskey_returns64HexCharacters() {
        val result = engine.generate("seed", "service", "aeskey", "account")
        assertEquals(64, result.length)
        assertTrue("aeskey must be lowercase hex", result.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun aeskey_isDeterministic() {
        val a = engine.generate("seed", "service", "aeskey", "account")
        val b = engine.generate("seed", "service", "aeskey", "account")
        assertEquals(a, b)
    }

    @Test
    fun aeskey_firstBytesMatchHexlong() {
        // aeskey takes the first 32 bytes of the same PBKDF2 stream that
        // hex/hexlong take their first 4/8 bytes from. The first 16 hex chars
        // of aeskey must therefore equal the hexlong output for the same inputs.
        val aes = engine.generate("pass", "salt", "aeskey", "word")
        val hexlong = engine.generate("pass", "salt", "hexlong", "word")
        assertEquals("842b8a866ef6f789", hexlong)
        assertEquals(hexlong, aes.substring(0, 16))
    }

    @Test
    fun aeskey_differsAcrossSeedServiceAccount() {
        val base = engine.generate("seed", "service", "aeskey", "account")
        assertNotEquals(base, engine.generate("other", "service", "aeskey", "account"))
        assertNotEquals(base, engine.generate("seed", "other", "aeskey", "account"))
        assertNotEquals(base, engine.generate("seed", "service", "aeskey", "other"))
    }

    @Test
    fun deriveAesKey_returns32Bytes() {
        val bytes = engine.deriveAesKey("seed", "service", "account")
        assertEquals(32, bytes.size)
    }

    @Test
    fun deriveAesKey_matchesAeskeyHex() {
        val bytes = engine.deriveAesKey("pass", "salt", "word")
        val hex = bytes.joinToString("") { "%02x".format(it) }
        assertEquals(engine.generate("pass", "salt", "aeskey", "word"), hex)
    }
}
