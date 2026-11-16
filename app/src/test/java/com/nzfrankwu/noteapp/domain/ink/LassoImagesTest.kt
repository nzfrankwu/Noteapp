package com.nzfrankwu.noteapp.domain.ink

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

/** The lasso with pictures (PHASE-3.md 3.6): picking them, and moving, resizing and turning them. */
class LassoImagesTest {
    private val photo = InkImage("i", "images/p.jpg", 100f, 100f, 200f, 100f)

    /** A square loop from ([l], [t]) to ([r], [b]). */
    private fun loop(l: Float, t: Float, r: Float, b: Float) = floatArrayOf(l, r, r, l) to floatArrayOf(t, t, b, b)

    @Test
    fun aLoopAroundAPicture_picksIt() {
        val (xs, ys) = loop(50f, 50f, 350f, 250f)
        assertTrue(InkGeometry.imageInLoop(photo, xs, ys))
    }

    @Test
    fun aLoopAroundOneCorner_picksIt_likeStrokes() {
        val (xs, ys) = loop(80f, 80f, 120f, 120f)
        assertTrue(InkGeometry.imageInLoop(photo, xs, ys))
    }

    @Test
    fun aLoopDrawnInsideThePicture_picksIt() {
        val (xs, ys) = loop(150f, 120f, 200f, 180f)
        assertTrue(InkGeometry.imageInLoop(photo, xs, ys))
    }

    @Test
    fun aLoopCrossingJustAnEdge_picksIt() {
        // A thin loop across the middle of the picture: no corner inside, no loop point on it.
        val xs = floatArrayOf(150f, 160f, 160f, 150f)
        val ys = floatArrayOf(50f, 50f, 300f, 300f)
        assertTrue(InkGeometry.imageInLoop(photo, xs, ys))
    }

    @Test
    fun aLoopElsewhere_doesNot() {
        val (xs, ys) = loop(400f, 400f, 500f, 500f)
        assertFalse(InkGeometry.imageInLoop(photo, xs, ys))
        assertFalse(InkGeometry.imageInLoop(photo, floatArrayOf(1f, 2f), floatArrayOf(1f, 2f)))
    }

    @Test
    fun theSelectionBox_coversStrokesAndPictures() {
        val stroke = testStroke("s", 0f to 0f, 10f to 10f)
        val both = InkGeometry.boundsOf(listOf(stroke), listOf(photo))!!
        assertTrue(both[0] <= 0f && both[1] <= 0f) // the stroke's corner
        assertEquals(300f, both[2], 0.01f) // the picture's right edge
        assertEquals(200f, both[3], 0.01f) // and its bottom
        assertArrayEquals(floatArrayOf(100f, 100f, 300f, 200f), InkGeometry.boundsOf(emptyList(), listOf(photo)), 0.01f)
        assertNull(InkGeometry.boundsOf(emptyList(), emptyList()))
    }

    @Test
    fun movingTheBox_movesThePicture() {
        val from = floatArrayOf(100f, 100f, 300f, 200f)
        val to = LassoBox.moved(from, 40f, -10f)
        val moved = LassoBox.mapImage(photo, from, to)
        assertEquals(140f, moved.left, 0.01f)
        assertEquals(90f, moved.top, 0.01f)
        assertEquals(200f, moved.width, 0.01f)
        assertEquals(photo.id, moved.id)
        assertEquals(photo.asset, moved.asset)
    }

    @Test
    fun resizingTheBox_resizesThePicture() {
        val from = floatArrayOf(100f, 100f, 300f, 200f)
        val to = floatArrayOf(100f, 100f, 500f, 300f) // twice as big, top-left fixed
        val bigger = LassoBox.mapImage(photo, from, to)
        assertEquals(100f, bigger.left, 0.01f)
        assertEquals(100f, bigger.top, 0.01f)
        assertEquals(400f, bigger.width, 0.01f)
        assertEquals(200f, bigger.height, 0.01f)
    }

    @Test
    fun cropping_keepsThePartWhereItWas_atTheSameScale() {
        // 200 × 100 on the page from a 2000 × 1000 file: 10 pixels per page unit.
        val cropped = photo.cropped("images/c.jpg", 2000, 1000, left = 500, top = 200, right = 1500, bottom = 700)
        assertEquals("images/c.jpg", cropped.asset)
        assertEquals(photo.id, cropped.id)
        assertEquals(150f, cropped.left, 0.01f) // 100 + 500/10
        assertEquals(120f, cropped.top, 0.01f) // 100 + 200/10
        assertEquals(100f, cropped.width, 0.01f)
        assertEquals(50f, cropped.height, 0.01f)
    }

    @Test
    fun croppingATurnedPicture_turnsTheKeptPartWithIt() {
        // Half a turn: the right half of the picture now shows on the page's left.
        val turned = photo.copy(rotation = PI.toFloat())
        val rightHalf = turned.cropped("images/r.jpg", 2000, 1000, 1000, 0, 2000, 1000)
        assertEquals(turned.centreX - 50f, rightHalf.centreX, 0.01f)
        assertEquals(turned.centreY, rightHalf.centreY, 0.01f)
        assertEquals(100f, rightHalf.width, 0.01f)
        assertEquals(PI.toFloat(), rightHalf.rotation, 0.0001f)
    }

    @Test
    fun turningTheBox_turnsThePicture_aboutTheBoxCentre() {
        // The picture is the left half of a box; a half turn puts it on the right, upside down.
        val half = InkImage("h", "images/h.png", 0f, 0f, 100f, 100f)
        val box = floatArrayOf(0f, 0f, 200f, 100f)
        val turned = LassoBox.mapImage(half, box, box, PI.toFloat())
        assertEquals(150f, turned.centreX, 0.01f)
        assertEquals(50f, turned.centreY, 0.01f)
        assertEquals(PI.toFloat(), turned.rotation, 0.0001f)
    }
}
