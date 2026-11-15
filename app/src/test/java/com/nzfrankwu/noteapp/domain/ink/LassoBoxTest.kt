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
}
