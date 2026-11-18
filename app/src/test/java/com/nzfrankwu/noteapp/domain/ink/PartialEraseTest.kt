package com.nzfrankwu.noteapp.domain.ink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Phase 7, 7.4: the partial eraser cuts strokes where it passes. */
class PartialEraseTest {
    private var ids = 0
    private val newId = { "piece${ids++}" }

    /** A horizontal line from x=0 to x=100 at y=50, with a point every 10 units; size 2. */
    private fun line(id: String = "s", withPressure: Boolean = true) =
        testStroke(id, *Array(11) { (it * 10f) to 50f }, withPressure = withPressure)

    private fun near(expected: Float, actual: Float, tolerance: Float = 0.05f) =
        assertTrue("expected $expected, was $actual", abs(expected - actual) <= tolerance)

    @Test
    fun anEraserThatMissesTheStroke_returnsNull() {
        assertNull(PartialErase.cut(line(), 50f, 0f, 50f, 20f, 5f, newId))
    }

    @Test
    fun crossingTheMiddle_leavesTwoPieces_cutWhereTheEraserReaches() {
        // A vertical eraser stroke at x=50, radius 5; the line is 2 wide, so it reaches 6 units.
        val pieces = PartialErase.cut(line(), 50f, 0f, 50f, 100f, 5f, newId)!!
        assertEquals(2, pieces.size)
        val (left, right) = pieces
        near(0f, left.xs.first())
        near(44f, left.xs.last())
        near(56f, right.xs.first())
        near(100f, right.xs.last())
        assertTrue(left.ys.all { it == 50f } && right.ys.all { it == 50f })
        // The pieces keep the stroke's look.
        val s = line()
        for (p in pieces) {
            assertEquals(s.tool, p.tool)
            assertEquals(s.colorArgb, p.colorArgb)
            assertEquals(s.size, p.size)
            assertTrue("new IDs", p.id.startsWith("piece"))
        }
        // Total length shrank by the 12 units rubbed out.
        near(88f, PartialErase.length(left) + PartialErase.length(right), 0.2f)
    }

    @Test
    fun erasingAnEnd_leavesOnePiece() {
        val pieces = PartialErase.cut(line(), 100f, 0f, 100f, 100f, 5f, newId)!!
        assertEquals(1, pieces.size)
        near(0f, pieces[0].xs.first())
        near(94f, pieces[0].xs.last())
    }

    @Test
    fun erasingAlongTheWholeStroke_removesIt() {
        assertEquals(emptyList<InkStroke>(), PartialErase.cut(line(), -10f, 50f, 110f, 50f, 5f, newId))
    }

    @Test
    fun aDotThatIsTouched_goesCompletely() {
        val dot = testStroke("d", 20f to 20f)
        assertEquals(emptyList<InkStroke>(), PartialErase.cut(dot, 20f, 20f, 20f, 20f, 3f, newId))
    }

    @Test
    fun twoHoles_inAZigzag_leaveThreePieces() {
        // Up and down three times; a horizontal eraser line through the middle cuts each crossing.
        val zigzag = testStroke("z", 0f to 0f, 20f to 100f, 40f to 0f, 60f to 100f)
        val pieces = PartialErase.cut(zigzag, -10f, 50f, 70f, 50f, 3f, newId)!!
        assertEquals(4, pieces.size)
        assertTrue(pieces.all { it.pointCount >= 2 })
    }

    @Test
    fun pressureAndTime_areInterpolatedAtTheCut_andEachPieceStartsAtTime0() {
        val pieces = PartialErase.cut(line(), 55f, 0f, 55f, 100f, 4f, newId)!!
        val left = pieces[0]
        // Cut at x = 55 - 4 - 1 = 50 → exactly point 5 (pressure 0.35, time 40).
        near(50f, left.xs.last())
        near(0.35f, left.pressure.last(), 0.002f)
        assertEquals(40, left.times.last())
        val right = pieces[1]
        near(60f, right.xs.first())
        assertEquals(0, right.times.first())
        assertTrue("times never go back", right.times.toList().zipWithNext().all { (a, b) -> b >= a })
        near(0.36f, right.pressure.first(), 0.002f)
    }

    @Test
    fun notReportedPressure_staysNotReported() {
        val pieces = PartialErase.cut(line(withPressure = false), 50f, 0f, 50f, 100f, 5f, newId)!!
        assertTrue(pieces.all { p -> p.pressure.all { it == InkStroke.NONE } })
    }

    @Test
    fun aTinyLeftover_isDropped_notKeptAsADot() {
        // Erase from x=4 onwards: the 0..~-2 leftover has fewer than 2 points?
        val short = testStroke("t", 0f to 50f, 10f to 50f)
        val pieces = PartialErase.cut(short, 10f, 50f, 10f, 50f, 9.5f, newId)!!
        // Reach is 10.5: only x < -0.5 would survive, which isn't on the stroke.
        assertEquals(emptyList<InkStroke>(), pieces)
    }

    // ---- Undo: one step per drag, drawing order kept ----

    @Test
    fun split_keepsTheDrawingOrder_andUndoBringsTheStrokeBack() {
        val ink = NotebookInk()
        ink.add("p", listOf(testStroke("a", 0f to 0f, 1f to 1f)))
        ink.add("p", listOf(line("b")))
        ink.add("p", listOf(testStroke("c", 0f to 0f, 1f to 1f)))
        val pieces = PartialErase.cut(ink.strokes("p")[1], 50f, 0f, 50f, 100f, 5f, newId)!!
        ink.split("p", mapOf("b" to pieces))
        assertEquals(listOf("a", "piece0", "piece1", "c"), ink.strokes("p").map { it.id })

        ink.undo()
        assertEquals(listOf("a", "b", "c"), ink.strokes("p").map { it.id })
        ink.redo()
        assertEquals(listOf("a", "piece0", "piece1", "c"), ink.strokes("p").map { it.id })
    }

    @Test
    fun oneEraserDrag_isOneUndoStep_evenAcrossManySamples() {
        val ink = NotebookInk()
        ink.add("p", listOf(line("b"), testStroke("x", 0f to 90f, 100f to 90f)))
        // Sample 1 cuts b; sample 2 cuts one of b's pieces again; sample 3 removes x whole.
        val first = PartialErase.cut(ink.strokes("p")[0], 30f, 0f, 30f, 60f, 3f, newId)!!
        ink.split("p", mapOf("b" to first), drag = 7L)
        val right = ink.strokes("p").first { it.id == first[1].id }
        ink.split("p", mapOf(right.id to PartialErase.cut(right, 70f, 0f, 70f, 60f, 3f, newId)!!), drag = 7L)
        ink.erase("p", setOf("x"), drag = 7L)
        assertEquals(3, ink.strokes("p").size)

        // A different drag is its own step.
        ink.add("p", listOf(testStroke("y", 0f to 0f, 5f to 5f)))
        ink.undo()
        assertEquals(3, ink.strokes("p").size)
        ink.undo() // the whole first drag at once
        assertEquals(listOf("b", "x"), ink.strokes("p").map { it.id })
        assertTrue(ink.strokes("p")[0] == line("b"))
        assertTrue("next is adding the strokes", ink.undo() is InkEdit.Add)
    }

    @Test
    fun theSameDragKey_afterAnotherEdit_startsANewStep() {
        val ink = NotebookInk()
        ink.add("p", listOf(testStroke("a", 0f to 0f, 1f to 0f), testStroke("b", 0f to 9f, 1f to 9f)))
        ink.erase("p", setOf("a"), drag = 1L)
        ink.add("p", listOf(testStroke("c", 0f to 0f, 1f to 0f)))
        ink.erase("p", setOf("b"), drag = 1L)
        ink.undo()
        assertEquals(listOf("b", "c"), ink.strokes("p").map { it.id })
    }
}
