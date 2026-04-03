package com.dgp

import org.junit.Assert.assertEquals
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
    fun parseServices_isSortedCaseInsensitively() {
        val json = """[
            {"id":"1","name":"zoom","type":"alnum","comment":""},
            {"id":"2","name":"Apple","type":"alnum","comment":""},
            {"id":"3","name":"microsoft","type":"alnum","comment":""}
        ]"""
        val names = parseServices(json).map { it.name }
        assertEquals(listOf("Apple", "microsoft", "zoom"), names)
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
        // parseServices sorts by name; re-sort original the same way for comparison
        val expected = original.sortedBy { it.name.lowercase() }
        assertEquals(expected.size, parsed.size)
        for (i in expected.indices) {
            assertEquals(expected[i].id, parsed[i].id)
            assertEquals(expected[i].name, parsed[i].name)
            assertEquals(expected[i].type, parsed[i].type)
            assertEquals(expected[i].comment, parsed[i].comment)
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
}
