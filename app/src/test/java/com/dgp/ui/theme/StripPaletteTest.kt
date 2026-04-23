package com.dgp.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class StripPaletteTest {

    private val colors = EditorialColorsLight

    @Test
    fun knownTypes_returnNonInkMuted() {
        val knownTypes = listOf("alnum", "alnumlong", "hex", "hexlong", "base58", "base58long", "xkcd", "xkcdlong", "vault")
        for (type in knownTypes) {
            assertNotEquals("$type should not return inkMuted", colors.inkMuted, colors.stripFor(type))
        }
    }

    @Test
    fun unknownStrings_returnInkMuted() {
        assertEquals(colors.inkMuted, colors.stripFor("bogus"))
        assertEquals(colors.inkMuted, colors.stripFor(""))
    }

    @Test
    fun knownTypes_return9DistinctColors() {
        val knownTypes = listOf("alnum", "alnumlong", "hex", "hexlong", "base58", "base58long", "xkcd", "xkcdlong", "vault")
        val distinct = knownTypes.map { colors.stripFor(it) }.toSet()
        assertEquals(9, distinct.size)
    }
}
