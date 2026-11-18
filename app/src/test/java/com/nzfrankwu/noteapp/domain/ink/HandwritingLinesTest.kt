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

    /**
     * A real page: lines 24 units apart (lined paper), small letters 10 tall, ascenders and
     * descenders 16 tall reaching into the gap, a t-bar, an i-dot. Must stay three lines (the old
     * grouping merged them all into one, so nothing could be read).
     */
    @Test
    fun aDensePage_withAscendersAndDescenders_staysOneLinePerLine() {
        val strokes = ArrayList<InkStroke>()
        for ((n, base) in listOf(100f, 124f, 148f).withIndex()) {
            var x = 50f
            fun add(id: String, top: Float, bottom: Float) {
                strokes += testStroke("$n-$id", x to top, x + 4f to bottom, x + 8f to top)
                x += 12f
            }
            add("a", base - 10f, base)          // x-height letter
            add("h", base - 16f, base)          // ascender
            add("g", base - 10f, base + 6f)     // descender
            add("e", base - 10f, base)
            strokes += testStroke("$n-tbar", x to base - 12f, x + 8f to base - 12f) // flat t-bar
            add("t", base - 15f, base)
            strokes += testStroke("$n-dot", x + 2f to base - 15f, x + 3f to base - 15f) // i-dot
            add("i", base - 10f, base)
            add("y", base - 10f, base + 6f)
        }
        val lines = HandwritingLines.group(strokes)
        assertEquals(3, lines.size)
        for ((n, line) in lines.withIndex()) {
            assertTrue("line $n has only its own strokes", line.strokes.all { it.id.startsWith("$n-") })
            assertEquals(9, line.strokes.size)
        }
    }

    @Test
    fun nothingToRead_givesNoLines() {
        assertEquals(emptyList<HandwritingLines.Line>(), HandwritingLines.group(emptyList()))
    }
}
