package com.nzfrankwu.noteapp.domain

/**
 * Picks a course's colour slot from its code, so the same course always gets the same colour
 * (on every screen, after every restart, and on any tablet).
 *
 * The code is first reduced with [CourseCodes.matchKey], so "ELECTENG 204" and "electeng204"
 * share a colour. The hash is FNV-1a, written out here rather than relying on `String.hashCode`,
 * so the colours never change because of a library or platform change.
 *
 * Two courses can land on the same slot (there are only 7 colours). Choosing your own
 * colours comes later (IDEA.md §2.2).
 */
object CourseColorKey {
    fun slot(code: String, paletteSize: Int): Int {
        require(paletteSize > 0) { "paletteSize must be positive" }
        var hash = 0x811C9DC5.toInt()
        for (byte in CourseCodes.matchKey(code).toByteArray(Charsets.UTF_8)) {
            hash = hash xor (byte.toInt() and 0xFF)
            hash *= 0x01000193
        }
        return Integer.remainderUnsigned(hash, paletteSize)
    }
}
