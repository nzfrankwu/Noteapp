package com.nzfrankwu.noteapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun onlyWebLinks_canBeOpened() {
        assertTrue(Links.isOpenable("https://canvas.example.edu/courses/1"))
        assertTrue(Links.isOpenable("HTTP://example.com"))
        assertFalse(Links.isOpenable("javascript:alert(1)"))
        assertFalse(Links.isOpenable("file:///sdcard/x.pdf"))
        assertFalse(Links.isOpenable("intent://x#Intent;end"))
        assertFalse(Links.isOpenable(null))
        assertFalse(Links.isOpenable(""))
    }

    @Test
    fun host_isReadForButtonLabels() {
        assertEquals("canvas.example.edu", Links.host("https://canvas.example.edu/courses/1?x=2"))
        assertEquals("example.com", Links.host("https://www.example.com"))
        assertEquals("example.com", Links.host("https://user@example.com:8443/a"))
        assertNull(Links.host("not a link"))
        assertNull(Links.host(null))
    }

    @Test
    fun relativeCanvasLinks_areCompletedWithTheCanvasSite() {
        val base = "https://canvas.example.edu/courses/5/assignments/9"
        assertEquals("https://canvas.example.edu/courses/5/files/2", Links.resolve(base, "/courses/5/files/2"))
        assertEquals("https://other.example.com/x", Links.resolve(base, "https://other.example.com/x"))
        assertNull(Links.resolve(base, "javascript:alert(1)"))
        assertNull(Links.resolve(null, "/courses/5/files/2"))
        assertNull(Links.resolve(base, "bad link with spaces%%"))
    }
}
