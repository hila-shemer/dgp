package com.dgp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for the top-level JSON helpers in MainActivity.kt.
 *
 * These tests require the `org.json:json` dependency in testImplementation (added to
 * build.gradle) because the standard JVM does not bundle org.json — Android does.
 */
class ServiceParsingTest {

    // ── parseServices ─────────────────────────────────────────────────────────

    @Test
    fun parseServices_validJson_returnsAllFields() {
        val json = """[{"id":"abc","name":"GitHub","type":"hexlong","comment":"work"}]"""
        val services = parseServices(json)
        assertEquals(1, services.size)
        assertEquals("abc", services[0].id)
        assertEquals("GitHub", services[0].name)
        assertEquals("hexlong", services[0].type)
        assertEquals("work", services[0].comment)
    }

    @Test
    fun parseServices_multipleItems_returnsAllItems() {
        val json = """[
            {"id":"1","name":"GitHub","type":"alnum","comment":""},
            {"id":"2","name":"Gmail","type":"hex","comment":"personal"},
            {"id":"3","name":"AWS","type":"base58long","comment":""}
        ]"""
        val services = parseServices(json)
        assertEquals(3, services.size)
    }

    @Test
    fun parseServices_preservesInsertionOrder() {
        val json = """[
            {"id":"1","name":"zoom","type":"alnum","comment":""},
            {"id":"2","name":"Apple","type":"alnum","comment":""},
            {"id":"3","name":"microsoft","type":"alnum","comment":""}
        ]"""
        val names = parseServices(json).map { it.name }
        assertEquals(listOf("zoom", "Apple", "microsoft"), names)
    }

    @Test
    fun parseServices_missingTypeField_defaultsToAlnum() {
        val json = """[{"id":"1","name":"GitHub","comment":""}]"""
        val services = parseServices(json)
        assertEquals("alnum", services[0].type)
    }

    @Test
    fun parseServices_missingCommentField_defaultsToEmpty() {
        val json = """[{"id":"1","name":"GitHub","type":"alnum"}]"""
        val services = parseServices(json)
        assertEquals("", services[0].comment)
    }

    @Test
    fun parseServices_emptyArray_returnsEmptyList() {
        val services = parseServices("[]")
        assertTrue(services.isEmpty())
    }

    @Test
    fun parseServices_malformedJson_returnsEmptyList() {
        val services = parseServices("not json at all")
        assertTrue(services.isEmpty())
    }

    @Test
    fun parseServices_emptyString_returnsEmptyList() {
        val services = parseServices("")
        assertTrue(services.isEmpty())
    }

    @Test
    fun parseServices_partiallyMalformedJson_returnsEmptyList() {
        // JSONArray constructor throws on invalid JSON — entire result is empty
        val services = parseServices("""[{"id":"1","name":"ok"},BROKEN""")
        assertTrue(services.isEmpty())
    }

    @Test
    fun parseServices_singleCharName_isAccepted() {
        val json = """[{"id":"1","name":"X","type":"alnum","comment":""}]"""
        val services = parseServices(json)
        assertEquals("X", services[0].name)
    }

    // ── serializeServices ─────────────────────────────────────────────────────

    @Test
    fun serializeServices_emptyList_producesEmptyJsonArray() {
        val json = serializeServices(emptyList())
        assertEquals("[]", json)
    }

    @Test
    fun serializeServices_preservesAllFields() {
        val service = DgpService("my-id", "GitHub", "hexlong", "work account")
        val json = serializeServices(listOf(service))
        assertTrue("id should appear in JSON", json.contains("my-id"))
        assertTrue("name should appear in JSON", json.contains("GitHub"))
        assertTrue("type should appear in JSON", json.contains("hexlong"))
        assertTrue("comment should appear in JSON", json.contains("work account"))
    }

    // ── Round-trip ────────────────────────────────────────────────────────────

    @Test
    fun roundTrip_singleService_identityUnderSerializeAndParse() {
        val original = listOf(DgpService("uuid-1", "GitHub", "alnum", ""))
        val parsed = parseServices(serializeServices(original))
        assertEquals(1, parsed.size)
        assertEquals(original[0].id, parsed[0].id)
        assertEquals(original[0].name, parsed[0].name)
        assertEquals(original[0].type, parsed[0].type)
        assertEquals(original[0].comment, parsed[0].comment)
    }

    @Test
    fun roundTrip_multipleServices_allFieldsPreserved() {
        val original = listOf(
            DgpService("id-1", "GitHub", "alnum", "work"),
            DgpService("id-2", "Gmail", "hex", "personal"),
            DgpService("id-3", "AWS", "base58long", "")
        )
        val parsed = parseServices(serializeServices(original))
        assertEquals(original.size, parsed.size)
        for (i in original.indices) {
            assertEquals(original[i].id, parsed[i].id)
            assertEquals(original[i].name, parsed[i].name)
            assertEquals(original[i].type, parsed[i].type)
            assertEquals(original[i].comment, parsed[i].comment)
        }
    }

    @Test
    fun roundTrip_specialCharactersInComment_surviveSerialization() {
        val original = listOf(DgpService("1", "test", "alnum", "comment with \"quotes\" & <special>"))
        val parsed = parseServices(serializeServices(original))
        assertEquals(original[0].comment, parsed[0].comment)
    }

    @Test
    fun roundTrip_unicodeServiceName_survivesSerialization() {
        val original = listOf(DgpService("1", "日本語サービス", "alnum", ""))
        val parsed = parseServices(serializeServices(original))
        assertEquals(original[0].name, parsed[0].name)
    }

    @Test
    fun roundTrip_emptyList_returnsEmptyList() {
        val parsed = parseServices(serializeServices(emptyList()))
        assertTrue(parsed.isEmpty())
    }

    // ── Archived field ────────────────────────────────────────────────────────

    @Test
    fun parseServices_missingArchivedField_defaultsToFalse() {
        // Configs written before the archive feature don't have this field.
        val json = """[{"id":"1","name":"GitHub","type":"alnum","comment":""}]"""
        val services = parseServices(json)
        assertEquals(false, services[0].archived)
    }

    @Test
    fun parseServices_archivedTrue_isParsed() {
        val json = """[{"id":"1","name":"GitHub","type":"alnum","comment":"","archived":true}]"""
        val services = parseServices(json)
        assertEquals(true, services[0].archived)
    }

    @Test
    fun serializeServices_includesArchivedField() {
        val service = DgpService("1", "GitHub", "alnum", "", archived = true)
        val json = serializeServices(listOf(service))
        assertTrue("archived field should appear in JSON", json.contains("\"archived\""))
        assertTrue("archived value true should appear in JSON", json.contains("true"))
    }

    @Test
    fun roundTrip_archivedFlagPreserved_forBothValues() {
        val original = listOf(
            DgpService("1", "Active", "alnum", "", archived = false),
            DgpService("2", "Archived", "alnum", "", archived = true)
        )
        val parsed = parseServices(serializeServices(original))
        assertEquals(false, parsed[0].archived)
        assertEquals(true, parsed[1].archived)
    }

    // ── encryptedSecret field (vault entries) ─────────────────────────────────

    @Test
    fun parseServices_missingEncryptedSecretField_defaultsToNull() {
        val json = """[{"id":"1","name":"GitHub","type":"alnum","comment":""}]"""
        val services = parseServices(json)
        assertEquals(null, services[0].encryptedSecret)
    }

    @Test
    fun parseServices_encryptedSecretPresent_isParsed() {
        val json = """[{"id":"1","name":"x","type":"vault","comment":"","encryptedSecret":"blob123"}]"""
        val services = parseServices(json)
        assertEquals("blob123", services[0].encryptedSecret)
    }

    @Test
    fun parseServices_encryptedSecretExplicitNull_treatedAsNull() {
        val json = """[{"id":"1","name":"x","type":"alnum","comment":"","encryptedSecret":null}]"""
        val services = parseServices(json)
        assertEquals(null, services[0].encryptedSecret)
    }

    @Test
    fun serializeServices_omitsEncryptedSecretWhenNull() {
        // Non-vault entries shouldn't carry a null field in JSON.
        val service = DgpService("1", "GitHub", "alnum", "")
        val json = serializeServices(listOf(service))
        assertTrue("null encryptedSecret should not appear",
            !json.contains("encryptedSecret"))
    }

    @Test
    fun serializeServices_includesEncryptedSecretWhenPresent() {
        val service = DgpService("1", "x", "vault", "", encryptedSecret = "blob")
        val json = serializeServices(listOf(service))
        assertTrue(json.contains("\"encryptedSecret\""))
        assertTrue(json.contains("blob"))
    }

    @Test
    fun roundTrip_encryptedSecretPreserved() {
        val original = listOf(
            DgpService("1", "plain", "alnum", ""),
            DgpService("2", "vaulted", "vault", "", encryptedSecret = "ciphertext-base64")
        )
        val parsed = parseServices(serializeServices(original))
        assertEquals(null, parsed[0].encryptedSecret)
        assertEquals("ciphertext-base64", parsed[1].encryptedSecret)
    }

    // ── pinned field ──────────────────────────────────────────────────────────

    @Test
    fun parseServices_missingPinnedField_defaultsToFalse() {
        val json = """[{"id":"1","name":"GitHub","type":"alnum","comment":""}]"""
        val services = parseServices(json)
        assertFalse(services[0].pinned)
    }

    @Test
    fun parseServices_pinnedTrue_isParsed() {
        val json = """[{"id":"1","name":"GitHub","type":"alnum","comment":"","pinned":true}]"""
        val services = parseServices(json)
        assertTrue(services[0].pinned)
    }

    // ── tags field ────────────────────────────────────────────────────────────

    @Test
    fun parseServices_missingTagsField_defaultsToEmptyList() {
        val json = """[{"id":"1","name":"GitHub","type":"alnum","comment":""}]"""
        val services = parseServices(json)
        assertTrue(services[0].tags.isEmpty())
    }

    @Test
    fun parseServices_tagsArray_isParsed() {
        val json = """[{"id":"1","name":"GitHub","type":"alnum","comment":"","tags":["work","ops"]}]"""
        val services = parseServices(json)
        assertEquals(listOf("work", "ops"), services[0].tags)
    }

    @Test
    fun roundTrip_pinnedAndTagsPreserved() {
        val original = listOf(
            DgpService("1", "GitHub", "alnum", "", pinned = true, tags = listOf("work", "ops")),
            DgpService("2", "Gmail", "alnum", "", pinned = false, tags = emptyList())
        )
        val parsed = parseServices(serializeServices(original))
        assertTrue(parsed[0].pinned)
        assertEquals(listOf("work", "ops"), parsed[0].tags)
        assertFalse(parsed[1].pinned)
        assertTrue(parsed[1].tags.isEmpty())
    }

    // ── Legacy config compatibility ───────────────────────────────────────────

    @Test
    fun parseServices_legacyConfig_opensWithDefaults() {
        // Minimal legacy JSON: only id, name, type, comment — no archived, pinned, tags.
        val json = """[{"id":"legacy-1","name":"OldService","type":"hex","comment":"old"}]"""
        val services = parseServices(json)
        assertEquals(1, services.size)
        assertFalse(services[0].archived)
        assertFalse(services[0].pinned)
        assertTrue(services[0].tags.isEmpty())
    }
}
