package com.nzfrankwu.noteapp.domain.ink

import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * A small group of neighbouring strokes that's drawn (recorded) once and then reused: the screen
 * only redraws the groups that changed, however full the page is. [strokes] never changes; a
 * change makes a new chunk with a new [key].
 */
class InkChunk(val strokes: List<InkStroke>) {
    val key: Long = nextKey.incrementAndGet()

    private companion object {
        val nextKey = AtomicLong()
    }
}

/**
 * Keeps a page's strokes split into [InkChunk]s as the page is edited (PHASE-2.md 2.12).
 * The chunks, in order, always hold exactly the page's strokes in their drawing order.
 */
object InkChunks {
    /** Most strokes in one chunk: small enough that redrawing one is instant. */
    const val MAX = 32

    /**
     * The chunks for [strokes], reusing every chunk of [old] whose strokes are untouched (the
     * same stroke objects, in the same order, side by side). So writing a stroke only remakes
     * the last chunk, and erasing or moving strokes only the chunks they were in.
     */
    fun reconcile(old: List<InkChunk>, strokes: List<InkStroke>): List<InkChunk> {
        if (strokes.isEmpty()) return emptyList()
        val owner = IdentityHashMap<InkStroke, InkChunk>()
        for (chunk in old) for (stroke in chunk.strokes) owner[stroke] = chunk

        // Split into runs of strokes that came from the same old chunk (or are all new).
        val runs = ArrayList<InkChunk>()
        var i = 0
        while (i < strokes.size) {
            val chunk = owner[strokes[i]]
            var j = i + 1
            while (j < strokes.size && j - i < MAX && owner[strokes[j]] === chunk) j++
            val untouched = chunk != null && j - i == chunk.strokes.size &&
                (0 until j - i).all { strokes[i + it] === chunk.strokes[it] }
            runs += if (untouched) chunk!! else InkChunk(strokes.subList(i, j).toList())
            i = j
        }

        // Join small neighbours, so writing stroke after stroke fills a chunk instead of making
        // a new one each time.
        val joined = ArrayList<InkChunk>(runs.size)
        for (chunk in runs) {
            val last = joined.lastOrNull()
            if (last != null && last.strokes.size + chunk.strokes.size <= MAX) {
                joined[joined.lastIndex] = InkChunk(last.strokes + chunk.strokes)
            } else {
                joined += chunk
            }
        }
        return joined
    }
}
