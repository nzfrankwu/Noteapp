package com.nzfrankwu.noteapp.domain.ink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** F21–F23: highlighter opacity, pen pressure levels, and editing the toolbar's colours. */
class PenOptionsTest {
    @Test
    fun eachPressureLevel_hasItsOwnPenInk_andPensAreNotHighlighters() {
        assertEquals(InkTool.PEN_STEADY, InkTool.pen(PenPressure.OFF))
        assertEquals(InkTool.PEN_PRESSURE_LOW, InkTool.pen(PenPressure.LOW))
        assertEquals(InkTool.PEN_PRESSURE_MID, InkTool.pen(PenPressure.MEDIUM))
        assertEquals(InkTool.PEN, InkTool.pen(PenPressure.FULL))
        listOf(InkTool.PEN, InkTool.PEN_STEADY, InkTool.PEN_PRESSURE_LOW, InkTool.PEN_PRESSURE_MID).forEach { assertFalse(it.isHighlighter) }
    }

    @Test
    fun aPenSlot_drawsWithItsPressureLevel_andTheDefaultIsFull() {
        val settings = InkToolSettings()
        assertEquals(InkTool.PEN, settings.inkTool(EditorTool.PEN_1))
        val steady = settings.withStyle(EditorTool.PEN_2, settings.pens[1].copy(pressure = PenPressure.OFF))
        assertEquals(InkTool.PEN_STEADY, steady.inkTool(EditorTool.PEN_2))
        assertEquals(InkTool.PEN, steady.inkTool(EditorTool.PEN_1))
    }

    @Test
    fun highlighterOpacity_livesInTheColoursAlpha_andOpaqueMeansTheDefault() {
        val yellow = InkPalette.HIGHLIGHT_YELLOW
        assertEquals(HighlightOpacity.DEFAULT, HighlightOpacity.of(yellow), 0.001f)
        val faint = HighlightOpacity.applied(yellow, 0.2f)
        assertEquals(0.2f, HighlightOpacity.of(faint), 0.01f)
        assertTrue(HighlightOpacity.sameColour(faint, yellow))
        // Out-of-range opacities are kept within the slider's range, and never become "opaque".
        assertEquals(HighlightOpacity.MAX, HighlightOpacity.of(HighlightOpacity.applied(yellow, 5f)), 0.01f)
    }

    @Test
    fun pickingAnotherHighlighterColour_keepsTheOpacity_butPensStayOpaque() {
        val faint = PenStyle(HighlightOpacity.applied(InkPalette.HIGHLIGHT_YELLOW, 0.2f), 10f)
        val green = faint.withColour(InkPalette.HIGHLIGHT_GREEN, highlighter = true)
        assertTrue(HighlightOpacity.sameColour(green.colorArgb, InkPalette.HIGHLIGHT_GREEN))
        assertEquals(0.2f, HighlightOpacity.of(green.colorArgb), 0.01f)
        val pen = PenStyle(InkPalette.BLACK, 1.5f).withColour(0x112233, highlighter = false)
        assertEquals(0xFF112233.toInt(), pen.colorArgb)
    }

    @Test
    fun theToolbarColours_canBeMovedDeletedReplacedAndAdded() {
        val list = listOf(1, 2, 3).map { it or 0xFF000000.toInt() }
        assertEquals(listOf(list[1], list[0], list[2]), PaletteEdit.move(list, 1, -1))
        assertEquals(list, PaletteEdit.move(list, 0, -1)) // already first
        assertEquals(listOf(list[0], list[2]), PaletteEdit.delete(list, 1))
        assertEquals(listOf(list[0]), PaletteEdit.delete(listOf(list[0]), 0)) // the last one stays
        assertEquals(0xFF0000FF.toInt(), PaletteEdit.replace(list, 2, 0x0000FF)[2])
        // Changing one to a colour already in the list leaves just one of it.
        assertEquals(listOf(list[1], list[2]), PaletteEdit.replace(list, 0, list[1]).let { listOf(it[0], it[1]) })
        assertEquals(2, PaletteEdit.replace(list, 0, list[1]).size)
        assertEquals(4, PaletteEdit.add(list, 0x00FF00).size)
        assertEquals(list, PaletteEdit.add(list, 2)) // not twice
    }

    @Test
    fun eachToolHasItsOwnPalette() {
        val s = InkToolSettings().withPalette(EditorTool.HIGHLIGHTER, listOf(InkPalette.HIGHLIGHT_PINK))
        assertEquals(listOf(InkPalette.HIGHLIGHT_PINK), s.palette(EditorTool.HIGHLIGHTER))
        assertEquals(InkPalette.PEN_COLORS, s.palette(EditorTool.PEN_2))
    }

    @Test
    fun aHardPressThatNeverMoves_stillCountsAsAPressurePen() {
        // F26: the OPPO pen's first sample of a hard press may already be high and never change.
        assertTrue(PressureErase.reportsPressure(first = 0.95f, now = 0.95f))
        assertTrue(PressureErase.reportsPressure(first = 1f, now = 0.97f))
        // A pen without pressure sends exactly 1.0 all along: never "hard".
        assertFalse(PressureErase.reportsPressure(first = 1f, now = 1f))
    }

    @Test
    fun aHardPressFromTheFirstSample_erasesAfterTheHold_evenWithNoMoreSamples() {
        val hold = PressHold(holdMs = 500)
        // Only the pen-down sample arrives (hard), then nothing: the watcher's quiet time counts.
        assertFalse(hold.feed(0.95f, 0.9f, true, 10f, 10f, 1_000L))
        assertFalse(hold.doneAfterQuiet(stillHard = true, quietMs = 300))
        assertTrue(hold.doneAfterQuiet(stillHard = true, quietMs = 520))
    }

    @Test
    fun colourWheelColours_canBeDeleted() {
        val s = InkToolSettings().withCustomColor(0xFF123456.toInt()).withCustomColor(0xFF654321.toInt())
        assertEquals(listOf(0xFF654321.toInt()), s.withoutCustomColor(0xFF123456.toInt()).customColors)
    }
}
