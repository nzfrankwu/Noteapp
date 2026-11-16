package com.nzfrankwu.noteapp.domain.ink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pen-up handoff's bookkeeping (feedback F30, F36). */
class HandoffBookTest {
    @Test
    fun aWholeStroke_isLetGoOfOnceDrawn() {
        val book = HandoffBook<String>()
        assertTrue(book.expect(listOf("s1"), "wet1"))
        assertTrue(book.hasWaiting)
        assertEquals("wet1", book.drawn("s1"))
        assertFalse(book.hasWaiting)
        assertNull("Drawing it again changes nothing", book.drawn("s1"))
        assertTrue(book.release("wet1"))
        assertFalse("Released once only", book.release("wet1"))
    }

    @Test
    fun aSplitStroke_waitsForEveryPiece() {
        val book = HandoffBook<String>()
        book.expect(listOf("a", "b", "c"), "wet")
        assertNull(book.drawn("a"))
        assertNull(book.drawn("a")) // a piece drawn twice counts once
        assertNull(book.drawn("c"))
        assertEquals("wet", book.drawn("b"))
    }

    @Test
    fun aStrokeWithNoPieces_isLetGoOfAtOnce() {
        val book = HandoffBook<String>()
        assertFalse(book.expect(emptyList(), "wet"))
        assertFalse(book.hasWaiting)
    }

    @Test
    fun theTimeout_releasesWhatsStillWaiting_andLaterDrawsAreIgnored() {
        val book = HandoffBook<String>()
        book.expect(listOf("a", "b"), "wet")
        book.expect(listOf("x"), "other")
        assertNull(book.drawn("a"))
        assertTrue(book.release("wet")) // timed out before "b" was drawn
        assertNull(book.drawn("b"))
        assertTrue("The other stroke still waits", book.hasWaiting)
        assertEquals("other", book.drawn("x"))
    }
}
