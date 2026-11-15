package com.nzfrankwu.noteapp.ui.notebook

import androidx.compose.runtime.mutableStateMapOf
import androidx.ink.strokes.Stroke
import com.nzfrankwu.noteapp.domain.ink.InkStroke

/**
 * The ready-to-draw (Jetpack Ink) version of each saved stroke, page by page. Building one takes
 * a moment (it works out the stroke's outline), so a full page is built in the background before
 * it's shown, and kept while the page is near the screen. Main thread only.
 */
class StrokeDrawables {
    private val pages = HashMap<String, HashMap<String, Pair<InkStroke, Stroke?>>>()

    /**
     * Which pages are ready, as Compose state: a page layer that found its page not ready yet
     * redraws by itself once it is. (A plain set left small pages blurry after a fast scroll
     * until something else redrew them: PHASE-2.md F16.)
     */
    private val ready = mutableStateMapOf<String, Boolean>()

    /** True once the page's strokes can be drawn without a pause. Read in a composable to wait for it. */
    fun isReady(pageId: String): Boolean = ready[pageId] == true

    /** Marks a page ready, with whatever was built for it in the background. */
    fun put(pageId: String, built: List<Pair<InkStroke, Stroke?>>) {
        val map = pages.getOrPut(pageId) { HashMap() }
        for (entry in built) map[entry.first.id] = entry
        if (ready[pageId] != true) ready[pageId] = true
    }

    /**
     * The drawable for [stroke] on a ready page. A stroke not built yet (just written, moved or
     * brought back by undo) is built now: one at a time that's instant.
     */
    fun get(pageId: String, stroke: InkStroke): Stroke? {
        val map = pages[pageId] ?: return null
        val hit = map[stroke.id]
        if (hit != null && hit.first === stroke) return hit.second
        val built = InkBrushes.toDrawable(stroke)
        map[stroke.id] = stroke to built
        return built
    }

    /** Forgets pages far from the screen, and erased strokes on the others, to save memory. */
    fun keepOnly(pageIds: Set<String>, liveStrokes: (pageId: String) -> List<InkStroke>) {
        pages.keys.retainAll(pageIds)
        ready.keys.filter { it !in pageIds }.forEach { ready.remove(it) }
        for ((pageId, map) in pages) {
            val live = liveStrokes(pageId)
            if (map.size > live.size * 2 + 200) {
                val ids = live.mapTo(HashSet()) { it.id }
                map.keys.retainAll(ids)
            }
        }
    }
}
