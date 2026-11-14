package com.nzfrankwu.noteapp.domain

/** Rules for links typed or shared into the app. */
object Links {
    private val scheme = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")

    /**
     * Tidies a typed link so it can be opened: trims it, returns null if empty, and adds
     * "https://" when there's no scheme ("canvas.example.edu/x" → "https://canvas.example.edu/x").
     */
    fun normalize(input: String?): String? {
        val trimmed = input?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        return if (scheme.containsMatchIn(trimmed)) trimmed else "https://$trimmed"
    }

    /**
     * Whether a saved link may be opened from the app: only web links (http/https). Anything
     * else (e.g. "file:", "intent:" or "javascript:") is shown as text but never opened.
     */
    fun isOpenable(url: String?): Boolean {
        val scheme = url?.trim()?.substringBefore("://", missingDelimiterValue = "")?.lowercase() ?: return false
        return scheme == "https" || scheme == "http"
    }

    /** The site's name for a button label, e.g. "canvas.example.edu" (null if it can't be read). */
    fun host(url: String?): String? =
        url?.trim()?.substringAfter("://", missingDelimiterValue = "")
            ?.substringBefore('/')?.substringBefore('?')?.substringBefore('#')
            ?.substringAfter('@')?.substringBefore(':')
            ?.removePrefix("www.")?.lowercase()?.ifEmpty { null }

    /**
     * Turns a link found in a Canvas description into a full link. Canvas often writes links
     * relative to itself ("/courses/1/files/2"); these are completed with [base]'s site.
     * Returns null for anything that isn't a web link afterwards.
     */
    fun resolve(base: String?, link: String): String? {
        val full = try {
            if (base != null && isOpenable(base)) java.net.URI(base.trim()).resolve(link.trim()).toString() else link.trim()
        } catch (_: Exception) {
            return null
        }
        return full.takeIf { isOpenable(it) }
    }
}
