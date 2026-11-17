package com.nzfrankwu.noteapp.domain.ink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** PHASE-7.md 7.10: handwriting is split into lines before it's recognised. */
class HandwritingLinesTest {
    /** A small letter-sized squiggle at (x, y), about 20 units tall. */
    private fun letter(id: String, x: Float, y: Float, tool: InkTool = InkTool.PEN) =
        testStroke(id, x to y, x + 6f to y + 20f, x + 12f to y, tool = tool)

    @Test
    fun twoLinesOfWriting_becomeTwoLines_topToBottom_inWritingOrder() {
        val strokes = listOf(
            letter("b1", 100f, 200f), letter("b2", 120f, 202f), // second line, written first
            letter("a1", 100f, 100f), letter("a2", 120f, 98f), letter("a3", 140f, 101f),
        )
        val lines = HandwritingLines.group(strokes)
        assertEquals(listOf(listOf("a1", "a2", "a3"), listOf("b1", "b2")), lines.map { l -> l.strokes.map { it.id } })
        assertTrue(lines[0].top < lines[1].top)
        assertEquals(98f, lines[0].top)
        assertEquals(152f, lines[0].right)
    }

    @Test
    fun highlighters_dots_andBigDrawings_areLeftOut() {
        val strokes = listOf(
            letter("a", 100f, 100f), letter("b", 120f, 100f), letter("c", 140f, 100f),
            letter("hl", 100f, 100f, tool = InkTool.HIGHLIGHTER),
            testStroke("dot", 160f to 110f),
            testStroke("tall", 300f to 0f, 310f to 400f), // a diagram's line
        )
        assertEquals(listOf("a", "b", "c"), HandwritingLines.group(strokes).single().strokes.map { it.id })
    }

    @Test
    fun aTallLetter_staysInItsLine() {
        // "l" reaches above the others but its middle is still in the line's band.
        val strokes = listOf(letter("h", 100f, 100f), testStroke("l", 120f to 88f, 121f to 120f), letter("o", 130f, 100f))
        assertEquals(1, HandwritingLines.group(strokes).size)
    }

    @Test
    fun flatStrokes_likeATsBar_stayInTheirLine() {
        // "TO": the T's bar is flat (no height), its stem and the O are letter-sized.
        val strokes = listOf(
            testStroke("bar", 100f to 100f, 130f to 100f),
            testStroke("stem", 115f to 100f, 115f to 130f),
            testStroke("o", 150f to 100f, 165f to 115f, 150f to 130f, 135f to 115f, 150f to 100f),
            testStroke("dash", 200f to 115f, 220f to 115f),
        )
        assertEquals(listOf("bar", "stem", "o", "dash"), HandwritingLines.group(strokes).single().strokes.map { it.id })
    }

    @Test
    fun nothingToRead_givesNoLines() {
        assertEquals(emptyList<HandwritingLines.Line>(), HandwritingLines.group(emptyList()))
    }
}
