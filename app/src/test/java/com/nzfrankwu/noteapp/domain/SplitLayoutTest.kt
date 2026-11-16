package com.nzfrankwu.noteapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Split view's rules (PHASE-5.md 5.7–5.9). */
class SplitLayoutTest {
    @Test
    fun theDivider_movesFreelyBetween25And75() {
        assertEquals(DividerRelease.Keep(0.4f), SplitLayout.release(0.4f))
        assertEquals(DividerRelease.Keep(0.25f), SplitLayout.release(0.25f))
        assertEquals(DividerRelease.Keep(0.75f), SplitLayout.release(0.75f))
    }

    @Test
    fun betweenTheFreeZoneAndTheEdge_itSpringsBack() {
        assertEquals(DividerRelease.Keep(0.75f), SplitLayout.release(0.85f))
        assertEquals(DividerRelease.Keep(0.75f), SplitLayout.release(0.91f))
        assertEquals(DividerRelease.Keep(0.25f), SplitLayout.release(0.1f))
    }

    @Test
    fun veryNearAnEdge_theSqueezedPaneCloses() {
        assertEquals(DividerRelease.Close(PaneSide.SECOND), SplitLayout.release(0.95f))
        assertEquals(DividerRelease.Close(PaneSide.FIRST), SplitLayout.release(0.05f))
        assertTrue(SplitLayout.closing(0.95f))
        assertTrue(SplitLayout.closing(0.02f))
        assertFalse(SplitLayout.closing(0.5f))
        assertFalse(SplitLayout.closing(0.9f))
    }

    @Test
    fun whileDragged_theDividerStaysOnScreen() {
        assertEquals(0.97f, SplitLayout.dragged(1.2f), 0f)
        assertEquals(0.03f, SplitLayout.dragged(-0.5f), 0f)
        assertEquals(0.6f, SplitLayout.dragged(0.6f), 0f)
    }

    @Test
    fun savedShares_areMadeSafe() {
        assertEquals(0.5f, SplitLayout.saved(null), 0f)
        assertEquals(0.5f, SplitLayout.saved(0.95f), 0f)
        assertEquals(0.3f, SplitLayout.saved(0.3f), 0f)
    }

    @Test
    fun theFirstMode_followsTheShape_andModesSave() {
        assertEquals(SplitMode.SIDE_BY_SIDE, SplitMode.forShape(1280f, 800f))
        assertEquals(SplitMode.TOP_BOTTOM, SplitMode.forShape(800f, 1280f))
        assertEquals(SplitMode.TOP_BOTTOM, SplitMode.fromKey("stacked"))
        assertNull(SplitMode.fromKey("nonsense"))
    }

    @Test
    fun onlyBigEnoughWindows_split() {
        assertTrue(SplitLayout.canSplit(1280f, 800f))
        assertTrue(SplitLayout.canSplit(800f, 1280f))
        assertFalse(SplitLayout.canSplit(640f, 800f)) // half of a tablet in Android's split screen
    }

    @Test
    fun aDrop_landsInTheHalfItsOver() {
        assertEquals(PaneSide.FIRST, SplitLayout.dropSide(100f, 900f, 1000f, 1000f, SplitMode.SIDE_BY_SIDE))
        assertEquals(PaneSide.SECOND, SplitLayout.dropSide(700f, 100f, 1000f, 1000f, SplitMode.SIDE_BY_SIDE))
        assertEquals(PaneSide.FIRST, SplitLayout.dropSide(900f, 100f, 1000f, 1000f, SplitMode.TOP_BOTTOM))
        assertEquals(PaneSide.SECOND, SplitLayout.dropSide(100f, 700f, 1000f, 1000f, SplitMode.TOP_BOTTOM))
    }

    @Test
    fun recentNotebooks_newestFirst_noRepeats_atMost30() {
        var list = emptyList<String>()
        for (i in 1..35) list = RecentNotebooks.opened(list, "n$i")
        assertEquals(30, list.size)
        assertEquals("n35", list.first())
        list = RecentNotebooks.opened(list, "n20")
        assertEquals(listOf("n20", "n35", "n34"), list.take(3))
        assertEquals(30, list.size)
        assertEquals(list, RecentNotebooks.decode(RecentNotebooks.encode(list)))
        assertEquals(listOf("a", "b"), RecentNotebooks.decode(" a, ,b,a "))
        assertEquals(emptyList<String>(), RecentNotebooks.decode(null))
    }
}
