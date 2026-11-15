package com.nzfrankwu.noteapp.domain.ink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorToolsTest {
    // ---- Pen button ----

    @Test
    fun penButton_switchesToLasso_andBackToThePreviousTool() {
        val toggle = PenButtonToggle()
        assertEquals(EditorTool.LASSO, toggle.click(EditorTool.HIGHLIGHTER))
        assertEquals(EditorTool.HIGHLIGHTER, toggle.click(EditorTool.LASSO))
        assertEquals(EditorTool.LASSO, toggle.click(EditorTool.PEN_2))
        assertEquals(EditorTool.PEN_2, toggle.click(EditorTool.LASSO))
    }

    @Test
    fun penButton_whenLassoWasPickedOnTheToolbar_goesToPen1() {
        val toggle = PenButtonToggle()
        toggle.click(EditorTool.ERASER) // remembers the eraser…
        toggle.picked(EditorTool.PEN_3) // …until a tool is picked on the toolbar
        assertEquals(EditorTool.PEN_1, toggle.click(EditorTool.LASSO))
    }

    // ---- Press hard to erase ----

    @Test
    fun pressingHard_erases_onlyAboveTheLevel() {
        assertTrue(PressureErase.isHard(0.93f, 0.9f, penReportsPressure = true))
        assertTrue(PressureErase.isHard(0.9f, 0.9f, penReportsPressure = true))
        assertFalse(PressureErase.isHard(0.89f, 0.9f, penReportsPressure = true))
    }

    @Test
    fun pensWithoutPressure_neverErase() {
        assertFalse(PressureErase.isHard(1.0f, 0.9f, penReportsPressure = true)) // a fixed "1.0"
        assertFalse(PressureErase.isHard(0.95f, 0.9f, penReportsPressure = false))
        assertFalse(PressureErase.reportsPressure(listOf(1f, 1f, 1f)))
        assertTrue(PressureErase.reportsPressure(listOf(0.2f, 0.4f)))
    }

    // ---- Hold to straighten ----

    private fun line(holdMs: Int, wobble: Float = 0f): InkStroke {
        val xs = ArrayList<Float>(); val ys = ArrayList<Float>(); val ts = ArrayList<Int>()
        for (i in 0..20) { xs += i * 5f; ys += 100f + (if (i % 2 == 0) 2f else -2f); ts += i * 10 } // a wobbly 100-unit line
        val end = ts.last()
        var t = 10
        while (t <= holdMs) { xs += 100f + (if (t % 20 == 0) wobble else 0f); ys += 100f; ts += end + t; t += 10 }
        val n = xs.size
        return InkStroke("l", InkTool.PEN, 0, 1.5f, xs.toFloatArray(), ys.toFloatArray(), ts.toIntArray(),
            FloatArray(n) { 0.5f }, FloatArray(n) { InkStroke.NONE }, FloatArray(n) { InkStroke.NONE })
    }

    @Test
    fun aLineEndingInAHold_isStraightened() {
        val stroke = line(holdMs = 600)
        assertTrue(Straighten.wanted(stroke))
        val straight = Straighten.straightened(stroke)
        assertEquals("l", straight.id)
        assertTrue(straight.ys.all { it == straight.ys[0] || kotlin.math.abs(it - 100f) < 2.01f })
        assertEquals(stroke.xs.first(), straight.xs.first())
        assertEquals(stroke.xs.last(), straight.xs.last())
    }

    @Test
    fun aLineWithoutAHold_staysAsDrawn() {
        assertFalse(Straighten.wanted(line(holdMs = 100)))
    }

    @Test
    fun aHoldWithTheHandMoving_isNotAHold() {
        assertFalse(Straighten.wanted(line(holdMs = 600, wobble = 8f)))
    }

    @Test
    fun aDotHeldStill_isNotStraightened() {
        val n = 60
        val dot = InkStroke("d", InkTool.PEN, 0, 1.5f, FloatArray(n) { 50f }, FloatArray(n) { 50f }, IntArray(n) { it * 10 },
            FloatArray(n) { 0.5f }, FloatArray(n) { InkStroke.NONE }, FloatArray(n) { InkStroke.NONE })
        assertFalse(Straighten.wanted(dot))
    }

    // ---- Stability ----

    @Test
    fun stabilityZero_leavesTheStroke_andHigherSmoothsIt() {
        val stroke = line(holdMs = 0)
        assertEquals(stroke, Smoothing.smoothed(stroke, 0))
        val smooth = Smoothing.smoothed(stroke, 100)
        val wobbleBefore = stroke.ys.drop(1).dropLast(1).maxOf { kotlin.math.abs(it - 100f) }
        val wobbleAfter = smooth.ys.drop(3).dropLast(3).maxOf { kotlin.math.abs(it - 100f) }
        assertTrue(wobbleAfter < wobbleBefore)
        assertEquals(stroke.xs.first(), smooth.xs.first())
        assertEquals(stroke.xs.last(), smooth.xs.last())
    }

    // ---- Settings ----

    @Test
    fun toolKeys_neverChange() {
        assertEquals(setOf("pen1", "pen2", "pen3", "highlighter", "eraser", "lasso"), EditorTool.entries.map { it.key }.toSet())
    }

    @Test
    fun penStyles_belongToTheirSlot() {
        val settings = InkToolSettings()
        val red = PenStyle(InkPalette.RED, 2.6f)
        val changed = settings.withStyle(EditorTool.PEN_2, red)
        assertEquals(red, changed.penStyle(EditorTool.PEN_2))
        assertEquals(settings.penStyle(EditorTool.PEN_1), changed.penStyle(EditorTool.PEN_1))
        assertEquals(null, settings.penStyle(EditorTool.ERASER))
    }
}
