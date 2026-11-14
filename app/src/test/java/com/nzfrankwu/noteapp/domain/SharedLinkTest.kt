package com.nzfrankwu.noteapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedLinkTest {
    @Test
    fun browserShare_linkAsText_titleAsSubject() {
        assertEquals(
            SharedLinkDraft("https://example.com/notes/week-5", "Week 5 notes"),
            SharedLink.parse("https://example.com/notes/week-5", "Week 5 notes"),
        )
    }

    @Test
    fun titleAndLinkInOneText() {
        assertEquals(
            SharedLinkDraft("https://canvas.example.edu/courses/555/assignments/3", "Assignment 3 - ELECTENG 204"),
            SharedLink.parse("Assignment 3 - ELECTENG 204 https://canvas.example.edu/courses/555/assignments/3", null),
        )
    }

    @Test
    fun linkFirst_thenTitle_separatorsTidied() {
        assertEquals(
            SharedLinkDraft("https://example.com/a", "Tutorial sheet"),
            SharedLink.parse("https://example.com/a — Tutorial sheet", null),
        )
    }

    @Test
    fun trailingPunctuation_isNotPartOfTheLink() {
        assertEquals("https://example.com/x", SharedLink.parse("See https://example.com/x.", null).url)
        assertEquals("https://example.com/y", SharedLink.parse("(https://example.com/y)", null).url)
    }

    @Test
    fun noLink_keepsTheTextAsTitle() {
        assertEquals(SharedLinkDraft(null, "Remember to email the tutor"), SharedLink.parse("Remember to email the tutor", null))
    }

    @Test
    fun nothingShared_givesAnEmptyDraft() {
        assertEquals(SharedLinkDraft(null, ""), SharedLink.parse(null, null))
    }

    @Test
    fun veryLongTitles_areShortened() {
        assertEquals(200, SharedLink.parse("x".repeat(500), null).title.length)
    }

    @Test
    fun canvasLink_givesItsCourseId() {
        assertEquals(555L, SharedLink.canvasCourseId("https://canvas.example.edu/courses/555/assignments/3", "canvas.example.edu"))
        assertEquals(555L, SharedLink.canvasCourseId("https://Canvas.Example.edu/courses/555", "canvas.example.edu"))
    }

    @Test
    fun nonCanvasOrOtherLinks_giveNoCourse() {
        assertNull(SharedLink.canvasCourseId("https://example.com/courses/555", "canvas.example.edu"))
        assertNull(SharedLink.canvasCourseId("https://canvas.example.edu/calendar", "canvas.example.edu"))
        assertNull(SharedLink.canvasCourseId("https://canvas.example.edu/courses/abc", "canvas.example.edu"))
        assertNull(SharedLink.canvasCourseId(null, "canvas.example.edu"))
        assertNull(SharedLink.canvasCourseId("https://canvas.example.edu/courses/555", null))
    }
}
