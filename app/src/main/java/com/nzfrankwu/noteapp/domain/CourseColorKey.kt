package com.nzfrankwu.noteapp.domain

import java.time.Instant

/**
 * A course as the colour rule sees it: its code, when Noteapp first saw it, and the colour you
 * chose for it ([CourseColorChoice] key; null = automatic).
 */
data class CourseSeen(val code: String, val firstSeen: Instant, val chosen: String? = null)

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
 * - A colour you chose (Phase 7, 7.7) wins over the automatic one ([chosenSlots]). Every course
 *   still counts in [slotsInOrder], so choosing one course's colour never moves the others.
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

    /** The palette slot of each course whose colour you chose, keyed by [CourseCodes.matchKey]. */
    fun chosenSlots(courses: List<CourseSeen>): Map<String, Int> =
        courses.mapNotNull { c -> CourseColorChoice.fromKey(c.chosen)?.let { CourseCodes.matchKey(c.code) to it.slot } }.toMap()

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
