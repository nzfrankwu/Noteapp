package com.nzfrankwu.noteapp.domain

import java.time.Duration

/**
 * Courses that ended (IDEA.md §2, archiving): Canvas stops listing a course as active once its
 * semester is over. After it has been missing for [AFTER], Home suggests archiving it. The wait
 * means a short gap at the start or end of a semester never triggers it.
 */
object EndedCourses {
    val AFTER: Duration = Duration.ofDays(14)

    /**
     * The new "missing since" record after a successful sync: courses Canvas didn't list keep the
     * time they were first missed (or get [now]); courses that are listed again, archived or gone
     * are dropped.
     */
    fun update(missingSince: Map<String, Long>, notListed: Set<String>, now: Long): Map<String, Long> =
        notListed.associateWith { id -> missingSince[id] ?: now }

    /** The courses to suggest archiving: missing for [AFTER] and not answered with "Not now". */
    fun toSuggest(missingSince: Map<String, Long>, hidden: Set<String>, now: Long): Set<String> =
        missingSince.filter { (id, since) -> id !in hidden && now - since >= AFTER.toMillis() }.keys

    /** "ELECTENG 204 isn't in your Canvas courses any more. Archive it?" */
    fun message(codes: List<String>): String = when (codes.size) {
        1 -> "${codes[0]} isn't in your Canvas courses any more. Archive it? Its notes stay; its tasks leave Home."
        else -> "${codes.dropLast(1).joinToString(", ")} and ${codes.last()} aren't in your Canvas courses any more. " +
            "Archive them? Their notes stay; their tasks leave Home."
    }
}
