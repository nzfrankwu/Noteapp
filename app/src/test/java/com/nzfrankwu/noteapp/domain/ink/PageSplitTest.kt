package com.nzfrankwu.noteapp.domain.ink

import com.nzfrankwu.noteapp.domain.PageSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** Strokes written across the edge between pages (feedback F36). */
class PageSplitTest {
    // Three A4 pages side by side, touching: edges at x = 595 and 1190.
    private val strip = PageStrip(List(3) { PageSize(595f, 842f) })
    private var ids = 0
    private fun split(s: InkStroke) = PageSplit.split(s, strip::pageAt, { a, b -> strip.pageLeft(maxOf(a, b)) }) { "piece-${++ids}" }

    @Test
    fun aStrokeOnOnePage_staysWhole() {
        val s = testStroke("s", 100f to 100f, 300f to 120f)
        val pieces = split(s)
        assertEquals(1, pieces.size)
        assertEquals(0, pieces[0].first)
        assertSame(s, pieces[0].second)
    }

    @Test
    fun aStrokeAcrossTheEdge_isSplitThere_andThePiecesMeet() {
        val s = testStroke("s", 500f to 100f, 700f to 200f)
        val pieces = split(s)
        assertEquals(listOf(0, 1), pieces.map { it.first })
        val (left, right) = pieces.map { it.second }
        assertEquals("s", left.id) // the first piece keeps the stroke's ID
        assertEquals("piece-1", right.id)
        // Both end/start exactly at the edge, half way along (x 595 is 95/200 of the way).
        assertEquals(595f, left.xs.last(), 0.001f)
        assertEquals(595f, right.xs.first(), 0.001f)
        assertEquals(100f + 100f * 95f / 200f, left.ys.last(), 0.01f)
        assertEquals(left.ys.last(), right.ys.first(), 0.001f)
        assertEquals(500f, left.xs.first(), 0.001f)
        assertEquals(700f, right.xs.last(), 0.001f)
        assertEquals(s.tool, right.tool)
        assertEquals(s.colorArgb, right.colorArgb)
        assertEquals(s.size, right.size, 0f)
    }

    @Test
    fun aStrokeThatComesBack_givesOnePiecePerVisit() {
        val s = testStroke("s", 550f to 100f, 650f to 110f, 640f to 120f, 560f to 130f)
        assertEquals(listOf(0, 1, 0), split(s).map { it.first })
    }

    @Test
    fun aFastStrokeSkippingAPage_stillLandsOnBothEnds() {
        // From page 0 straight to page 2 between two samples.
        val s = testStroke("s", 500f to 100f, 1300f to 100f)
        val pieces = split(s)
        assertEquals(listOf(0, 2), pieces.map { it.first })
        assertEquals(1190f, pieces[1].second.xs.first(), 0.001f)
    }

    @Test
    fun aSplitStroke_isOneUndoStep() {
        val ink = NotebookInk()
        ink.load("p0", emptyList()); ink.load("p1", emptyList())
        ink.addAcross(mapOf("p0" to listOf(testStroke("a", 1f to 1f)), "p1" to listOf(testStroke("b", 2f to 2f))))
        assertEquals(1, ink.strokes("p0").size)
        assertEquals(1, ink.strokes("p1").size)
        ink.undo()
        assertEquals(0, ink.strokes("p0").size)
        assertEquals(0, ink.strokes("p1").size)
        assertEquals(false, ink.canUndo)
    }
}
