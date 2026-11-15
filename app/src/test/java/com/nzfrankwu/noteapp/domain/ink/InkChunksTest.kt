package com.nzfrankwu.noteapp.domain.ink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class InkChunksTest {
    private fun strokes(n: Int, prefix: String = "s") = List(n) { testStroke("$prefix$it", it.toFloat() to 0f, it + 1f to 1f) }

    private fun flat(chunks: List<InkChunk>) = chunks.flatMap { it.strokes }

    /** How many chunks of [after] are the very same objects as in [before]. */
    private fun reused(before: List<InkChunk>, after: List<InkChunk>) = after.count { a -> before.any { it === a } }

    @Test
    fun aFullPage_isSplitIntoSmallChunks_inOrder() {
        val page = strokes(100)
        val chunks = InkChunks.reconcile(emptyList(), page)
        assertEquals(page, flat(chunks))
        assertEquals(listOf(32, 32, 32, 4), chunks.map { it.strokes.size })
    }

    @Test
    fun anEmptyPage_hasNoChunks() {
        assertEquals(emptyList<InkChunk>(), InkChunks.reconcile(emptyList(), emptyList()))
    }

    @Test
    fun nothingChanged_reusesEveryChunk() {
        val page = strokes(100)
        val before = InkChunks.reconcile(emptyList(), page)
        val after = InkChunks.reconcile(before, page)
        assertEquals(before.size, reused(before, after))
    }

    @Test
    fun writingAStroke_onlyRemakesTheLastChunk() {
        val page = strokes(100)
        val before = InkChunks.reconcile(emptyList(), page)
        val longer = page + testStroke("new", 0f to 0f)
        val after = InkChunks.reconcile(before, longer)
        assertEquals(longer, flat(after))
        assertEquals(3, reused(before, after))
        assertEquals(listOf(32, 32, 32, 5), after.map { it.strokes.size })
    }

    @Test
    fun writingOnAPageWhoseLastChunkIsFull_startsANewChunk_thatFillsUp() {
        var page = strokes(64)
        var chunks = InkChunks.reconcile(emptyList(), page)
        repeat(40) {
            page = page + testStroke("w$it", 0f to 0f)
            chunks = InkChunks.reconcile(chunks, page)
            assertEquals(page, flat(chunks))
        }
        assertEquals(listOf(32, 32, 32, 8), chunks.map { it.strokes.size })
    }

    @Test
    fun erasingAStroke_onlyRemakesItsChunk() {
        val page = strokes(100)
        val before = InkChunks.reconcile(emptyList(), page)
        val erased = page.filter { it.id != "s40" } // in the second chunk
        val after = InkChunks.reconcile(before, erased)
        assertEquals(erased, flat(after))
        assertEquals(3, reused(before, after))
        assertSame(before[0], after[0])
    }

    @Test
    fun undoingAnErase_keepsTheDrawingOrder() {
        val page = strokes(100)
        val full = InkChunks.reconcile(emptyList(), page)
        val erased = InkChunks.reconcile(full, page.filter { it.id != "s40" })
        val back = InkChunks.reconcile(erased, page)
        assertEquals(page, flat(back))
        assertTrue("The untouched chunks are still reused", reused(erased, back) >= 2)
    }

    @Test
    fun movingStrokes_remakesOnlyTheirChunks() {
        val page = strokes(100)
        val before = InkChunks.reconcile(emptyList(), page)
        val moved = page.map { if (it.id == "s70" || it.id == "s71") it.translated(5f, 5f) else it }
        val after = InkChunks.reconcile(before, moved)
        assertEquals(moved.map { it.id }, flat(after).map { it.id })
        assertEquals(3, reused(before, after))
    }

    @Test
    fun chunksNeverGrowPastTheLimit_whateverTheEdits() {
        var page = strokes(200)
        var chunks = InkChunks.reconcile(emptyList(), page)
        // Erase every third stroke, then write 50 more, then bring a few back in the middle.
        page = page.filterIndexed { i, _ -> i % 3 != 0 }
        chunks = InkChunks.reconcile(chunks, page)
        page = page + strokes(50, "n")
        chunks = InkChunks.reconcile(chunks, page)
        page = page.take(20) + strokes(5, "u") + page.drop(20)
        chunks = InkChunks.reconcile(chunks, page)
        assertEquals(page, flat(chunks))
        assertTrue(chunks.all { it.strokes.size in 1..InkChunks.MAX })
    }
}
