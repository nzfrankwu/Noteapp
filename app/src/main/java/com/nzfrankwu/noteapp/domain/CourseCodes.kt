package com.nzfrankwu.noteapp.domain

/** Rules for course codes like "ELECTENG 204". */
object CourseCodes {
    private val whitespace = Regex("\\s+")

    /**
     * Tidies a typed course code for saving and display: trims, squeezes repeated spaces, and
     * uppercases. "  electeng   204 " becomes "ELECTENG 204". Returns null if nothing was typed.
     */
    fun tidy(input: String?): String? =
        input?.trim()?.replace(whitespace, " ")?.uppercase()?.ifEmpty { null }

    /**
     * Key for deciding whether two codes mean the same course, ignoring spaces and case:
     * "ELECTENG 204", "electeng204" and "Electeng  204" all match.
     */
    fun matchKey(code: String): String = code.replace(whitespace, "").uppercase()
}
