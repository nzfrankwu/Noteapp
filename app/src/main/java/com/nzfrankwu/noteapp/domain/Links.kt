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
}
