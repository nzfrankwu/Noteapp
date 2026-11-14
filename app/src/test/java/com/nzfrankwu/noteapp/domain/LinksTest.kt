package com.nzfrankwu.noteapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinksTest {
    @Test
    fun fullLink_isKept() {
        assertEquals("https://canvas.example.edu/courses/1", Links.normalize("https://canvas.example.edu/courses/1"))
        assertEquals("http://example.com", Links.normalize("http://example.com"))
    }

    @Test
    fun linkWithoutScheme_getsHttps() {
        assertEquals("https://canvas.example.edu/courses/1", Links.normalize("canvas.example.edu/courses/1"))
    }

    @Test
    fun spaces_areTrimmed() {
        assertEquals("https://example.com", Links.normalize("  https://example.com  "))
    }

    @Test
    fun nothingTyped_isNoLink() {
        assertNull(Links.normalize(null))
        assertNull(Links.normalize(""))
        assertNull(Links.normalize("   "))
    }
}
