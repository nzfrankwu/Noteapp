package com.nzfrankwu.noteapp.domain.ink

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class LassoBoxTest {
    private val box = floatArrayOf(10f, 20f, 110f, 70f) // 100 wide, 50 tall

    @Test
    fun moving_shiftsTheWholeBox() {
        assertArrayEquals(floatArrayOf(15f, 10f, 115f, 60f), LassoBox.moved(box, 5f, -10f), 0.001f)
    }

    @Test
    fun anEdgeHandle_stretchesOneWay_andTheOppositeSideStays() {
        assertArrayEquals(floatArrayOf(10f, 20f, 160f, 70f), LassoBox.dragged(box, BoxHandle.RIGHT, 160f, 999f), 0.001f)
        assertArrayEquals(floatArrayOf(10f, 0f, 110f, 70f), LassoBox.dragged(box, BoxHandle.TOP, -5f, 0f), 0.001f)
    }

    @Test
    fun aCornerHandle_stretchesFreely_orKeepsTheShape() {
        assertArrayEquals(floatArrayOf(0f, 0f, 110f, 70f), LassoBox.dragged(box, BoxHandle.TOP_LEFT, 0f, 0f), 0.001f)
        // Keeping the shape: twice as wide → twice as tall too.
        assertArrayEquals(floatArrayOf(10f, 20f, 210f, 120f), LassoBox.dragged(box, BoxHandle.BOTTOM_RIGHT, 210f, 30f, keepShape = true), 0.001f)
    }

    @Test
    fun aHandle_cantFlipTheBoxInsideOut() {
        val squeezed = LassoBox.dragged(box, BoxHandle.LEFT, 500f, 0f)
        assertEquals(110f - LassoBox.MIN_SIDE, squeezed[0], 0.001f)
        assertEquals(110f, squeezed[2], 0.001f)
    }

    @Test
    fun mapping_movesAndStretchesStrokes_intoTheNewBox() {
        val s = testStroke("a", 10f to 20f, 110f to 70f, size = 2f)
        val to = floatArrayOf(0f, 0f, 200f, 50f) // twice as wide, same height
        val m = LassoBox.map(s, box, to)
        assertArrayEquals(floatArrayOf(0f, 200f), m.xs, 0.001f)
        assertArrayEquals(floatArrayOf(0f, 50f), m.ys, 0.001f)
        assertEquals("a", m.id)
        assertEquals(2f * kotlin.math.sqrt(2f), m.size, 0.001f)
    }

    @Test
    fun handles_sitOnTheCornersAndEdgeMiddles() {
        assertEquals(110f to 70f, BoxHandle.BOTTOM_RIGHT.at(box))
        assertEquals(60f to 20f, BoxHandle.TOP.at(box))
        assertEquals(10f to 45f, BoxHandle.LEFT.at(box))
    }
    @Test
    fun rotating_aQuarterTurn_turnsStrokesAroundTheBoxCentre() {
        // The box's centre is (60, 45). A point to its right ends up below it (clockwise on screen).
        val s = testStroke("r", 110f to 45f, 60f to 45f)
        val m = LassoBox.map(s, box, box, (Math.PI / 2).toFloat())
        assertEquals(60f, m.xs[0], 0.01f)
        assertEquals(95f, m.ys[0], 0.01f)
        assertEquals(60f, m.xs[1], 0.01f) // the centre stays put
        assertEquals(45f, m.ys[1], 0.01f)
        assertEquals(s.size, m.size, 0.001f) // turning alone doesn't change the thickness
    }

    @Test
    fun rotating_withoutTurning_isTheSameAsBefore() {
        val s = testStroke("a", 10f to 20f, 110f to 70f)
        val to = floatArrayOf(0f, 0f, 200f, 50f)
        assertArrayEquals(LassoBox.map(s, box, to).xs, LassoBox.map(s, box, to, 0f).xs, 0.001f)
    }

    @Test
    fun theRotateHandle_givesTheAngleTurned_andSnapsToQuarterTurns() {
        // From straight above the centre to straight right of it: a quarter turn clockwise.
        assertEquals((Math.PI / 2).toFloat(), LassoBox.rotation(0f, 0f, 0f, -10f, 10f, 0f), 0.001f)
        // A little off a quarter turn snaps to it...
        assertEquals((Math.PI / 2).toFloat(), LassoBox.rotation(0f, 0f, 0f, -10f, 10f, 0.4f), 0.001f)
        // ...but a clear angle stays as it is (45°).
        assertEquals((Math.PI / 4).toFloat(), LassoBox.rotation(0f, 0f, 0f, -10f, 10f, -10f), 0.001f)
        // Turning back past the start gives a negative angle, not nearly a full turn.
        assertEquals((-Math.PI / 4).toFloat(), LassoBox.rotation(0f, 0f, 0f, -10f, -10f, -10f), 0.001f)
    }

    @Test
    fun rotating_turnsThePensDirection_butKeepsNotReported() {
        val base = testStroke("o", 0f to 0f, 10f to 0f)
        val s = InkStroke(base.id, base.tool, base.colorArgb, base.size, base.xs, base.ys, base.times, base.pressure, base.tilt, floatArrayOf(0.5f, InkStroke.NONE))
        val m = LassoBox.map(s, floatArrayOf(0f, 0f, 10f, 10f), floatArrayOf(0f, 0f, 10f, 10f), 1f)
        assertEquals(1.5f, m.orientation[0], 0.001f)
        assertEquals(InkStroke.NONE, m.orientation[1], 0.0f)
    }
}
