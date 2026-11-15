package com.nzfrankwu.noteapp.domain.ink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ColorHexTest {
    @Test
    fun formats_asSixDigitHex_withoutAlpha() {
        assertEquals("#1A3A8F", ColorHex.format(0xFF1A3A8F.toInt()))
        assertEquals("#000000", ColorHex.format(0xFF000000.toInt()))
        assertEquals("#FFEB3B", ColorHex.format(0x66FFEB3B))
    }

    @Test
    fun parses_withOrWithoutHash_andTheShortForm() {
        assertEquals(0xFF1A3A8F.toInt(), ColorHex.parse("#1a3a8f"))
        assertEquals(0xFF1A3A8F.toInt(), ColorHex.parse(" 1A3A8F "))
        assertEquals(0xFFFF0000.toInt(), ColorHex.parse("#f00"))
    }

    @Test
    fun rejects_whatIsntAColour() {
        assertNull(ColorHex.parse(""))
        assertNull(ColorHex.parse("#12345"))
        assertNull(ColorHex.parse("#GGGGGG"))
        assertNull(ColorHex.parse("#1234567"))
    }

    @Test
    fun customColours_newestFirst_noRepeats_atMostEight() {
        var s = InkToolSettings()
        (1..10).forEach { s = s.withCustomColor(0xFF000000.toInt() or it) }
        assertEquals(InkPalette.MAX_CUSTOM, s.customColors.size)
        assertEquals(0xFF00000A.toInt(), s.customColors.first())
        s = s.withCustomColor(0xFF000005.toInt())
        assertEquals(0xFF000005.toInt(), s.customColors.first())
        assertEquals(1, s.customColors.count { it == 0xFF000005.toInt() })
    }
}
