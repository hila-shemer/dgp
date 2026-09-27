package com.dgp.engine

import com.dgp.DgpService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChromeImportTest {

    // Fake "derivation": the secret of a non-vault entry is "gen:<name>".
    // Fake vault encryption: "enc:<name>:<password>".
    private fun secretOf(s: DgpService): String? =
        if (s.type == "vault") s.encryptedSecret?.split(":", limit = 3)?.get(2) else "gen:${s.name}"
    private fun encrypt(name: String, pw: String) = "enc:$name:$pw"

    private val csv = "name,url,username,password,note\r\n" +
        "github.com,https://github.com/session,me@x,gen:gh,\r\n" +
        "example.com,https://www.example.com/login,alice,\"p,w\"\"1\",\"multi\nline\"\r\n" +
        "example.com,https://example.com/,bob,pw2,\r\n" +
        "app,android://AbC==@com.Some.App/,carol,pw3,\r\n" +
        "empty,https://empty.org/,dave,,\r\n"

    @Test
    fun parseCsv_readsColumnsByName_andAndroidRows() {
        val rows = ChromeImport.parseCsv("﻿" + csv)
        assertEquals(5, rows.size)
        assertEquals(ChromeImport.Row("example.com", false, "alice", "p,w\"1"), rows[1])
        assertEquals(ChromeImport.Row("com.some.app", true, "carol", "pw3"), rows[3])
    }

    @Test(expected = IllegalArgumentException::class)
    fun parseCsv_rejectsFileWithoutUrlColumn() {
        ChromeImport.parseCsv("a,b\n1,2\n")
    }

    @Test
    fun plan_generatedMatchAddsSite_othersBecomeVaults() {
        val existing = listOf(
            DgpService(id = "gh", name = "gh", sites = listOf("github.com")),
            DgpService(id = "ex", name = "example.com"),
        )
        val p = ChromeImport.plan(ChromeImport.parseCsv(csv), existing, ::secretOf, ::encrypt)
        assertEquals(1, p.generated)
        assertEquals(1, p.skipped) // the empty password
        // example.com is taken by an existing entry whose derived password differs.
        assertEquals(listOf("example.com (alice)", "example.com (bob)", "com.some.app"), p.added)
        val alice = p.services.first { it.name == "example.com (alice)" }
        assertEquals("vault", alice.type)
        assertEquals("alice", alice.comment)
        assertEquals(listOf("example.com"), alice.sites)
        assertEquals(listOf(ChromeImport.TAG), alice.tags)
        assertEquals("enc:example.com (alice):p,w\"1", alice.encryptedSecret)
    }

    @Test
    fun plan_rerunIsHarmless() {
        val rows = ChromeImport.parseCsv(csv)
        val first = ChromeImport.plan(rows, emptyList(), ::secretOf, ::encrypt)
        val second = ChromeImport.plan(rows, first.services, ::secretOf, ::encrypt)
        assertTrue(second.added.isEmpty())
        assertEquals(first.services, second.services)
    }

    @Test
    fun plan_generatedMatchByNameGetsTheSiteAdded() {
        val existing = listOf(DgpService(id = "g", name = "github"))
        val rows = listOf(ChromeImport.Row("github.com", false, "me", "gen:github"))
        val p = ChromeImport.plan(rows, existing, ::secretOf, ::encrypt)
        assertEquals(1, p.generated)
        assertEquals(listOf("github.com"), p.services.single().sites)
    }
}
