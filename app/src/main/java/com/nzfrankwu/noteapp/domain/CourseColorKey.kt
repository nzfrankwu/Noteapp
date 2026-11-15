package com.nzfrankwu.noteapp.domain

import java.time.Instant

/** A course as the colour rule sees it: its code and when Noteapp first saw it. */
data class CourseSeen(val code: String, val firstSeen: Instant)

/**
 * Picks each course's colour slot (decided with the user, 15 Nov 2026: "next free colour").
 *
 * - Courses take the colours in the order Noteapp first saw them ([slotsInOrder]): the 1st course
 *   gets colour 1, the 2nd colour 2, and so on, wrapping round after the last colour. So up to
 *   palette-size courses never share a colour, and a semester's new courses (seen one after
 *   another) get different colours from each other.
 * - A course keeps its colour, because courses are never deleted and their first-seen time
 *   never changes.
 * - Codes match ignoring spaces and case ([CourseCodes.matchKey]).
 * - A code that isn't in the list yet (e.g. while typing a new folder's code) falls back to
 *   [slot], a fixed hash of the code.
 *
 * Choosing your own colours comes later (IDEA.md §2.2).
 */
object CourseColorKey {
    /** Colour slot per course (keyed by [CourseCodes.matchKey]), in first-seen order. */
    fun slotsInOrder(courses: List<CourseSeen>, paletteSize: Int): Map<String, Int> {
        require(paletteSize > 0) { "paletteSize must be positive" }
        val keys = courses
            .sortedWith(compareBy<CourseSeen> { it.firstSeen }.thenBy { CourseCodes.matchKey(it.code) })
            .map { CourseCodes.matchKey(it.code) }
            .distinct()
        return keys.withIndex().associate { (index, key) -> key to index % paletteSize }
    }

    /**
     * The fallback slot for a code with no first-seen time: FNV-1a over its [CourseCodes.matchKey],
     * written out here (not `String.hashCode`) so it never changes with a library or platform.
     */
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
