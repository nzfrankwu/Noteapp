package com.nzfrankwu.noteapp.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CanvasSignInTest {
    private val base = "https://canvas.example.edu"

    // ---- The Canvas address ----

    @Test
    fun plainHostName_becomesAnHttpsAddress() {
        assertEquals(base, CanvasSignIn.normalizeAddress("canvas.example.edu"))
        assertEquals(base, CanvasSignIn.normalizeAddress("  Canvas.Example.edu  "))
    }

    @Test
    fun pathsAreDropped() {
        assertEquals(base, CanvasSignIn.normalizeAddress("https://canvas.example.edu/courses/555/assignments"))
    }

    @Test
    fun unusualPort_isKept() {
        assertEquals("https://canvas.example.edu:8443", CanvasSignIn.normalizeAddress("https://canvas.example.edu:8443/"))
    }

    @Test
    fun plainHttp_isRefused_soCookiesNeverTravelUnencrypted() {
        assertNull(CanvasSignIn.normalizeAddress("http://canvas.example.edu"))
    }

    @Test
    fun nonsense_isRefused() {
        assertNull(CanvasSignIn.normalizeAddress(""))
        assertNull(CanvasSignIn.normalizeAddress(null))
        assertNull(CanvasSignIn.normalizeAddress("canvas"))
        assertNull(CanvasSignIn.normalizeAddress("not a website at all"))
        assertNull(CanvasSignIn.normalizeAddress("ftp://canvas.example.edu"))
    }

    // ---- Spotting the end of the login ----

    @Test
    fun canvasDashboard_looksSignedIn() {
        assertTrue(CanvasSignIn.looksSignedIn("https://canvas.example.edu/", base))
        assertTrue(CanvasSignIn.looksSignedIn("https://canvas.example.edu/?login_success=1", base))
        assertTrue(CanvasSignIn.looksSignedIn("https://canvas.example.edu/courses/555", base))
    }

    @Test
    fun universityLoginSite_isNotSignedInYet() {
        assertFalse(CanvasSignIn.looksSignedIn("https://login.example.edu/sso?SAMLRequest=abc", base))
    }

    @Test
    fun canvasLoginPages_areNotSignedInYet() {
        assertFalse(CanvasSignIn.looksSignedIn("https://canvas.example.edu/login", base))
        assertFalse(CanvasSignIn.looksSignedIn("https://canvas.example.edu/login/saml", base))
        assertFalse(CanvasSignIn.looksSignedIn("https://canvas.example.edu/login/canvas", base))
    }

    @Test
    fun lookalikeHosts_andPlainHttp_dontCount() {
        assertFalse(CanvasSignIn.looksSignedIn("https://canvas.example.edu.evil.com/", base))
        assertFalse(CanvasSignIn.looksSignedIn("http://canvas.example.edu/", base))
        assertFalse(CanvasSignIn.looksSignedIn(null, base))
    }
}
