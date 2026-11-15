package com.nzfrankwu.noteapp.domain.ink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InkGeometryTest {
    private val horizontal = testStroke("h", 0f to 50f, 50f to 50f, 100f to 50f, size = 2f)

    @Test
    fun eraserCrossingAStroke_touchesIt() {
        assertTrue(InkGeometry.eraserTouches(horizontal, 40f, 0f, 40f, 100f, radius = 1f))
    }

    @Test
    fun eraserJustNearAStroke_touchesIt_withinItsWidth() {
        // Stroke half-width 1 + eraser radius 3 = reach 4.
        assertTrue(InkGeometry.eraserTouches(horizontal, 20f, 54f, 30f, 54f, radius = 3f))
        assertFalse(InkGeometry.eraserTouches(horizontal, 20f, 55f, 30f, 55f, radius = 3f))
    }

    @Test
    fun eraserFarAway_doesNotTouch() {
        assertFalse(InkGeometry.eraserTouches(horizontal, 0f, 200f, 100f, 200f, radius = 5f))
    }

    @Test
    fun eraserTap_onASinglePointStroke() {
        val dot = testStroke("dot", 10f to 10f, size = 4f)
        assertTrue(InkGeometry.eraserTouches(dot, 12f, 12f, 12f, 12f, radius = 1.5f))
        assertFalse(InkGeometry.eraserTouches(dot, 20f, 20f, 20f, 20f, radius = 1.5f))
    }

    private val square = floatArrayOf(0f, 100f, 100f, 0f) to floatArrayOf(0f, 0f, 100f, 100f)

    @Test
    fun strokeInsideTheLoop_isSelected() {
        val s = testStroke("in", 10f to 10f, 50f to 50f, 90f to 90f)
        assertTrue(InkGeometry.insideLasso(s, square.first, square.second))
    }

    @Test
    fun aStrokeOnlyPartlyInside_isStillSelected() {
        // F19: you needn't circle the whole stroke.
        val mostlyOut = testStroke("b", 90f to 90f, 150f to 150f, 160f to 160f)
        assertTrue(InkGeometry.insideLasso(mostlyOut, square.first, square.second))
    }

    @Test
    fun aLineCrossingTheLoop_withNoPointInside_isSelected() {
        val across = testStroke("x", -50f to 50f, 150f to 50f)
        assertTrue(InkGeometry.insideLasso(across, square.first, square.second))
    }

    @Test
    fun aStrokeWhollyOutside_isNotSelected() {
        val outside = testStroke("o", 150f to 150f, 200f to 160f, 260f to 120f)
        assertFalse(InkGeometry.insideLasso(outside, square.first, square.second))
    }

    @Test
    fun aLoopWithTwoPoints_selectsNothing() {
        assertFalse(InkGeometry.insideLasso(horizontal, floatArrayOf(0f, 100f), floatArrayOf(0f, 100f)))
    }

    @Test
    fun pointInPolygon_handlesAConcaveShape() {
        // An "L" shape: the inner corner region (60, 30) is outside.
        val xs = floatArrayOf(0f, 50f, 50f, 100f, 100f, 0f)
        val ys = floatArrayOf(0f, 0f, 50f, 50f, 100f, 100f)
        assertTrue(InkGeometry.pointInPolygon(25f, 25f, xs, ys))
        assertFalse(InkGeometry.pointInPolygon(75f, 25f, xs, ys))
        assertTrue(InkGeometry.pointInPolygon(75f, 75f, xs, ys))
    }

    @Test
    fun boundsOf_includesTheStrokeWidth() {
        val b = InkGeometry.boundsOf(listOf(horizontal, testStroke("v", 10f to 0f, 10f to 80f, size = 4f)))!!
        assertEquals(listOf(-1f, -2f, 101f, 82f), b.toList())
    }

    @Test
    fun movingAndResizing_keepsTheShape() {
        val moved = horizontal.translated(5f, -10f)
        assertEquals(listOf(5f, 55f, 105f), moved.xs.toList())
        assertEquals(listOf(40f, 40f, 40f), moved.ys.toList())

        val doubled = horizontal.mapped(scale = 2f, originX = 0f, originY = 50f, dx = 0f, dy = 0f)
        assertEquals(listOf(0f, 100f, 200f), doubled.xs.toList())
        assertEquals(4f, doubled.size)
        assertEquals(horizontal.pressure.toList(), doubled.pressure.toList())
    }

    @Test
    fun segmentDistance_ofCrossingSegments_isZero() {
        assertEquals(0f, InkGeometry.segmentDistance(0f, 0f, 10f, 10f, 0f, 10f, 10f, 0f))
        assertEquals(5f, InkGeometry.segmentDistance(0f, 0f, 10f, 0f, 5f, 5f, 5f, 20f))
    }
}
