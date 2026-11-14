package com.nzfrankwu.noteapp.canvas

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Rules for the Canvas address and the in-app sign-in (IDEA.md §3.1, §3.5). Pure, unit-tested. */
object CanvasSignIn {
    /**
     * Turns what you typed into the school's Canvas address, e.g. "canvas.example.edu" or
     * "https://canvas.example.edu/courses/1" → "https://canvas.example.edu". Returns null if it
     * isn't a usable address. Plain http:// is refused: the sign-in cookies must only ever travel
     * encrypted.
     */
    fun normalizeAddress(input: String?): String? {
        val typed = input?.trim().orEmpty()
        if (typed.isEmpty() || typed.startsWith("http://", ignoreCase = true)) return null
        val url = (if ("://" in typed) typed else "https://$typed").toHttpUrlOrNull() ?: return null
        if (!url.isHttps || '.' !in url.host) return null
        return if (url.port == 443) "https://${url.host}" else "https://${url.host}:${url.port}"
    }

    /**
     * True when the in-app browser shows a normal Canvas page, i.e. the login seems finished and
     * it's time to ask Canvas "who am I?" to confirm. False while still on the university's
     * login (single sign-on) site or on Canvas's own login pages.
     */
    fun looksSignedIn(pageUrl: String?, canvasAddress: String): Boolean {
        val page = pageUrl?.toHttpUrlOrNull() ?: return false
        val base = canvasAddress.toHttpUrlOrNull() ?: return false
        if (!page.isHttps || page.host != base.host) return false
        val path = page.encodedPath
        return loginPaths.none { path == it || path.startsWith("$it/") }
    }

    private val loginPaths = listOf("/login", "/saml", "/logout", "/auth")
}
