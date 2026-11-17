package com.nzfrankwu.noteapp.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

/** When Home suggests archiving a course Canvas stopped listing. */
class EndedCoursesTest {
    private val day = Duration.ofDays(1).toMillis()
    private val now = 1_800_000_000_000L

    @Test
    fun aMissingCourse_keepsTheTimeItWasFirstMissed() {
        val first = EndedCourses.update(emptyMap(), setOf("c1"), now)
        assertEquals(mapOf("c1" to now), first)
        assertEquals(mapOf("c1" to now), EndedCourses.update(first, setOf("c1"), now + 5 * day))
    }

    @Test
    fun aCourseListedAgain_orArchived_isForgotten() {
        assertEquals(emptyMap<String, Long>(), EndedCourses.update(mapOf("c1" to now), emptySet(), now + day))
    }

    @Test
    fun suggested_onlyAfterTwoWeeks_andNotAfterNotNow() {
        val missing = mapOf("c1" to now - 14 * day, "c2" to now - 13 * day, "c3" to now - 30 * day)
        assertEquals(setOf("c1", "c3"), EndedCourses.toSuggest(missing, emptySet(), now))
        assertEquals(setOf("c1"), EndedCourses.toSuggest(missing, setOf("c3"), now))
    }

    @Test
    fun theWords() {
        assertEquals(
            "ELECTENG 204 isn't in your Canvas courses any more. Archive it? Its notes stay; its tasks leave Home.",
            EndedCourses.message(listOf("ELECTENG 204")),
        )
        assertEquals(
            "COMPSYS 201, ELECTENG 204 and ELECTENG 292 aren't in your Canvas courses any more. Archive them? " +
                "Their notes stay; their tasks leave Home.",
            EndedCourses.message(listOf("COMPSYS 201", "ELECTENG 204", "ELECTENG 292")),
        )
    }
}
