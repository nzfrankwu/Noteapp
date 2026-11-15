package com.nzfrankwu.noteapp.domain.ink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TestWritingTest {
    @Test
    fun aTestPage_isFullOfStrokes_thatStayOnThePage() {
        val page = TestWriting.page(595f, 842f, seed = 1)
        assertTrue("A full page of writing: ${page.size} strokes", page.size > 500)
        val box = InkGeometry.boundsOf(page)!!
        assertTrue(box[0] >= 0f && box[1] >= 0f && box[2] <= 595f && box[3] <= 842f)
        assertEquals(page.size, page.map { it.id }.toSet().size)
    }

    @Test
    fun theSameSeed_givesTheSamePage_andItSavesAndLoads() {
        val a = TestWriting.page(595f, 842f, seed = 7)
        assertEquals(a, TestWriting.page(595f, 842f, seed = 7))
        assertEquals(a, PageInkCodec.decode(PageInkCodec.encode(a)))
    }

    @Test
    fun strokeBounds_areRememberedAndCorrect() {
        val s = testStroke("s", 5f to 9f, 1f to 20f, 12f to 3f)
        assertEquals(listOf(1f, 3f, 12f, 20f), s.bounds().toList())
        assertTrue("Worked out once", s.bounds() === s.bounds())
    }
}
