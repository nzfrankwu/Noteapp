package com.nzfrankwu.noteapp.domain.ink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PanPhysicsTest {
    @Test
    fun aFastFlick_travelsSeveralScreens_andSlowsDown() {
        // A quick flick on a tablet is around 6,000 px/s; a page is about 1,300 px wide.
        assertTrue(Fling.totalDistance(6_000f) > 2 * 1_300f)
        assertTrue(Fling.velocityAfter(6_000f, 0.5f) < 6_000f)
        // Moving step by step adds up to the same as one big step.
        val steps = (0 until 60).sumOf { Fling.distance(Fling.velocityAfter(6_000f, it / 60f), 1 / 60f).toDouble() }
        assertEquals(Fling.distance(6_000f, 1f).toDouble(), steps, 0.5)
    }

    @Test
    fun aSlowRelease_doesntFling() {
        assertNull(Fling.start(100f, 50f, lockedSideways = false))
    }

    @Test
    fun aMostlySidewaysFlick_movesOnlySideways() {
        assertEquals(-5_000f to 0f, Fling.start(-5_000f, 900f, lockedSideways = false))
        assertEquals(3_000f to 0f, Fling.start(3_000f, 2_000f, lockedSideways = true))
        assertEquals(1_000f to 3_000f, Fling.start(1_000f, 3_000f, lockedSideways = false))
    }

    @Test
    fun aSidewaysDrag_locksSideways_andAClearVerticalMoveReleasesIt() {
        val lock = AxisLock()
        lock.step(10f, 1f)
        lock.step(20f, 3f) // past the decide distance, mostly sideways
        assertEquals(AxisLock.State.SIDEWAYS, lock.state)
        assertEquals(15f to 0f, lock.step(15f, 4f)) // small wobble swallowed
        repeat(4) { lock.step(0f, 30f) } // a clear move down
        assertEquals(AxisLock.State.FREE, lock.state)
        assertEquals(0f to 10f, lock.step(0f, 10f))
    }

    @Test
    fun aDiagonalDrag_staysFree() {
        val lock = AxisLock()
        lock.step(15f, 15f)
        lock.step(15f, 15f)
        assertEquals(AxisLock.State.FREE, lock.state)
        assertEquals(5f to 5f, lock.step(5f, 5f))
    }
}
