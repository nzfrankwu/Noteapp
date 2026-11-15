package com.nzfrankwu.noteapp.ui.notebook

import androidx.compose.runtime.snapshots.Snapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F16: a page layer that found its page not ready must be told when it becomes ready, or small
 * pages stay blurry after a fast scroll until something else redraws them.
 */
class StrokeDrawablesTest {
    @Test
    fun readiness_isComposeState_soAWaitingPageRedrawsWhenReady() {
        val drawables = StrokeDrawables()
        var reads = 0
        val snapshot = Snapshot.takeSnapshot(readObserver = { reads++ })
        val ready = snapshot.enter { drawables.isReady("p1") }
        snapshot.dispose()
        assertFalse(ready)
        assertTrue("isReady must be a state read", reads > 0)

        // Marking it ready is a state change a reader would see.
        var changed = false
        val handle = Snapshot.registerApplyObserver { _, _ -> changed = true }
        drawables.put("p1", emptyList())
        Snapshot.sendApplyNotifications()
        handle.dispose()
        assertTrue(changed)
        assertTrue(drawables.isReady("p1"))
    }

    @Test
    fun forgottenPages_areNoLongerReady() {
        val drawables = StrokeDrawables()
        drawables.put("p1", emptyList())
        drawables.put("p2", emptyList())
        drawables.keepOnly(setOf("p2")) { emptyList() }
        assertFalse(drawables.isReady("p1"))
        assertTrue(drawables.isReady("p2"))
    }
}
