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
        toggle.picked(EditorTool.PEN_2) // …until a tool is picked on the toolbar
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
        assertTrue(PressureErase.isHard(1.0f, 0.9f, penReportsPressure = true)) // the hardest press
        assertTrue(PressureErase.isHard(1.0f, 0.98f, penReportsPressure = true))
        assertFalse(PressureErase.isHard(1.0f, 0.9f, penReportsPressure = false)) // a fixed "1.0"
        assertFalse(PressureErase.isHard(0.95f, 0.9f, penReportsPressure = false))
        assertFalse(PressureErase.reportsPressure(first = 1f, now = 1f))
        assertTrue(PressureErase.reportsPressure(first = 0.2f, now = 0.4f))
    }

    // ---- A test line (for stability) ----

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
        // Phase 5 (FB4): pen 3 went and the bookmark pen came; the other keys stay as saved.
        assertEquals(setOf("pen1", "pen2", "highlighter", "eraser", "lasso", "bookmark"), EditorTool.entries.map { it.key }.toSet())
    }

    @Test
    fun aSavedPen3_opensAsPen1_andTwoPensRemain() {
        assertEquals(EditorTool.PEN_1, EditorTool.fromKey("pen3"))
        assertEquals(EditorTool.PEN_COUNT, InkToolSettings().pens.size)
        assertEquals(listOf(InkPalette.NAVY, InkPalette.RED), InkToolSettings().pens.map { it.colorArgb })
        assertEquals(false, EditorTool.BOOKMARK.draws)
        assertEquals(null, EditorTool.BOOKMARK.penSlot)
    }

    @Test
    fun oldDefaultPenColours_moveToTheNewOnes_once_butChosenColoursStay() {
        // Saved before Phase 5 (no version): black and blue were the defaults.
        assertEquals(InkPalette.NAVY, PenDefaults.savedColor(InkPalette.BLACK, null, 0))
        assertEquals(InkPalette.RED, PenDefaults.savedColor(InkPalette.BLUE, null, 1))
        // Picked on purpose: kept.
        assertEquals(InkPalette.GREEN, PenDefaults.savedColor(InkPalette.GREEN, null, 0))
        assertEquals(InkPalette.BLACK, PenDefaults.savedColor(InkPalette.BLACK, null, 1))
        // Saved after Phase 5: black is a choice now, kept.
        assertEquals(InkPalette.BLACK, PenDefaults.savedColor(InkPalette.BLACK, PenDefaults.VERSION, 0))
        // Nothing saved: the new defaults.
        assertEquals(InkPalette.NAVY, PenDefaults.savedColor(null, null, 0))
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

    @Test
    fun penButtonKeys_includeTheOppoPencilsF21_andAndroidsStylusButtons() {
        assertTrue(PenButtonKeys.isPenButton(keyCode = 334, scanCode = 191)) // OPPO Pencil 2
        assertTrue(PenButtonKeys.isPenButton(keyCode = 0, scanCode = 191)) // F21 without a name
        assertTrue(PenButtonKeys.isPenButton(keyCode = 308, scanCode = 0))
        assertFalse(PenButtonKeys.isPenButton(keyCode = 29, scanCode = 30)) // the letter A
        assertFalse(PenButtonKeys.isPenButton(keyCode = 4, scanCode = 158)) // Back
    }

    @Test
    fun pressHold_erasesOnlyAfterAHardStillPress_ofTheSetTime() {
        val hold = PressHold(holdMs = 500)
        assertFalse(hold.feed(0.95f, 0.9f, true, 100f, 100f, 0))
        assertFalse(hold.feed(0.97f, 0.9f, true, 101f, 101f, 300)) // still, but not long enough
        assertTrue(hold.feed(0.96f, 0.9f, true, 102f, 100f, 520))
    }

    @Test
    fun pressHold_startsAgain_whenThePressEasesOrThePenMoves() {
        val hold = PressHold(holdMs = 500)
        hold.feed(0.95f, 0.9f, true, 100f, 100f, 0)
        assertFalse(hold.feed(0.5f, 0.9f, true, 100f, 100f, 300)) // eased off
        assertFalse(hold.feed(0.95f, 0.9f, true, 100f, 100f, 600)) // the wait starts again here
        assertFalse(hold.feed(0.95f, 0.9f, true, 140f, 100f, 900)) // moved away: again
        assertTrue(hold.feed(0.95f, 0.9f, true, 141f, 101f, 1_400))
        // A hold time of 0 erases at once, like before.
        assertTrue(PressHold(holdMs = 0).feed(0.95f, 0.9f, true, 0f, 0f, 0))
        // A pen without real pressure never erases.
        assertFalse(PressHold(holdMs = 0).feed(1f, 0.9f, false, 0f, 0f, 0))
    }

    @Test
    fun stillTimer_countsTimeSpentNearOnePlace() {
        val t = StillTimer(radius = 3f)
        assertEquals(0L, t.feed(0f, 0f, 1_000))
        assertEquals(200L, t.feed(2f, 1f, 1_200))
        assertEquals(0L, t.feed(10f, 0f, 1_300))
        assertEquals(100L, t.feed(11f, 0f, 1_400))
    }

    @Test
    fun straightLines_snapGentlyToHorizontalVerticalAnd45() {
        // 2 degrees off horizontal: snaps flat, same length.
        val (x, y) = Straighten.snappedEnd(0f, 0f, 100f, 3.5f)
        assertEquals(0f, y, 0.01f)
        assertEquals(kotlin.math.hypot(100f, 3.5f), x, 0.01f)
        // Near 45 degrees (down-right on screen): snaps onto the diagonal.
        val (dx, dy) = Straighten.snappedEnd(10f, 10f, 110f, 113f)
        assertEquals(dx - 10f, dy - 10f, 0.01f)
        // 20 degrees: left alone.
        assertEquals(100f to 36.4f, Straighten.snappedEnd(0f, 0f, 100f, 36.4f))
        // Straight up.
        val (ux, _) = Straighten.snappedEnd(50f, 200f, 52f, 100f)
        assertEquals(50f, ux, 0.01f)
    }

    @Test
    fun aStraightLineStroke_runsFromStartToEnd_withEvenPressure() {
        val s = Straighten.line("id", InkTool.PEN, 0xFF000000.toInt(), 1.5f, 10f, 20f, 110f, 20f, 0.4f, InkStroke.TOOL_STYLUS)
        assertEquals(10f, s.xs.first(), 0.001f)
        assertEquals(110f, s.xs.last(), 0.001f)
        assertTrue(s.ys.all { it == 20f })
        assertTrue(s.pressure.all { it == 0.4f })
        assertTrue(s.pointCount > 10)
        assertTrue((1 until s.pointCount).all { s.times[it] > s.times[it - 1] })
    }
}
