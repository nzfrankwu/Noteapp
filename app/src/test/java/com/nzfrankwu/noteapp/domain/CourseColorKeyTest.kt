package com.nzfrankwu.noteapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class CourseColorKeyTest {
    private val size = 7

    @Test
    fun sameCode_writtenDifferently_getsTheSameSlot() {
        val slot = CourseColorKey.slot("ELECTENG 204", size)
        assertEquals(slot, CourseColorKey.slot("electeng204", size))
        assertEquals(slot, CourseColorKey.slot("  Electeng   204 ", size))
    }

    @Test
    fun slots_neverChange() {
        // Pinned values: if these fail, every course would silently change colour on the tablet.
        assertEquals(EXPECTED_ELECTENG_204, CourseColorKey.slot("ELECTENG 204", size))
        assertEquals(EXPECTED_COMPSYS_201, CourseColorKey.slot("COMPSYS 201", size))
    }

    @Test
    fun slot_isAlwaysInsideThePalette() {
        val codes = (100..999).map { "ENGGEN $it" } + listOf("", "Ä 1", "ELECTENG/COMPSYS 209")
        for (code in codes) {
            val slot = CourseColorKey.slot(code, size)
            assertTrue("$code → $slot", slot in 0 until size)
        }
    }

    @Test
    fun codes_spreadOverTheWholePalette() {
        val used = (100..999).map { CourseColorKey.slot("ENGGEN $it", size) }.groupingBy { it }.eachCount()
        assertEquals(size, used.size)
        // Each slot gets a fair share (900 codes / 7 ≈ 129 on average).
        assertTrue(used.toString(), used.values.all { it in 90..170 })
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptyPalette_isRejected() {
        CourseColorKey.slot("ELECTENG 204", 0)
    }

    // ---- Next free colour, in first-seen order ----

    private fun seen(code: String, day: Int) = CourseSeen(code, Instant.parse("2026-11-01T00:00:00Z").plusSeconds(day * 86_400L))

    @Test
    fun coursesTakeColoursInTheOrderTheyWereFirstSeen() {
        val slots = CourseColorKey.slotsInOrder(
            listOf(seen("ELECTENG 292", 3), seen("COMPSYS 201", 1), seen("ELECTENG 204", 2)),
            size,
        )
        assertEquals(mapOf("COMPSYS201" to 0, "ELECTENG204" to 1, "ELECTENG292" to 2), slots)
    }

    @Test
    fun upToPaletteSizeCourses_neverShareAColour() {
        val courses = (1..size).map { seen("COURSE $it", it) }
        assertEquals(size, CourseColorKey.slotsInOrder(courses, size).values.distinct().size)
    }

    @Test
    fun aNewCourse_neverChangesTheOlderCoursesColours() {
        val before = listOf(seen("COMPSYS 201", 1), seen("ELECTENG 204", 2))
        val after = before + seen("AAA 100", 5) // sorts first by code, but was seen last
        val old = CourseColorKey.slotsInOrder(before, size)
        val new = CourseColorKey.slotsInOrder(after, size)
        assertEquals(old, new - "AAA100")
        assertEquals(2, new["AAA100"])
    }

    @Test
    fun afterTheLastColour_itWrapsRound_soANewSemesterStillGetsDifferentColours() {
        val lastYear = (1..5).map { seen("OLD $it", it) }
        val thisYear = (1..4).map { seen("NEW $it", 10 + it) }
        val slots = CourseColorKey.slotsInOrder(lastYear + thisYear, size)
        assertEquals(listOf(5, 6, 0, 1), (1..4).map { slots.getValue("NEW$it") })
    }

    @Test
    fun sameTime_isOrderedByCode_soTheResultIsAlwaysTheSame() {
        val a = CourseColorKey.slotsInOrder(listOf(seen("ELECTENG 204", 1), seen("COMPSYS 201", 1)), size)
        val b = CourseColorKey.slotsInOrder(listOf(seen("COMPSYS 201", 1), seen("ELECTENG 204", 1)), size)
        assertEquals(a, b)
        assertEquals(0, a["COMPSYS201"])
    }

    @Test
    fun codesWrittenDifferently_countOnce() {
        val slots = CourseColorKey.slotsInOrder(listOf(seen("ELECTENG 204", 1), seen("electeng204", 2), seen("COMPSYS 201", 3)), size)
        assertEquals(mapOf("ELECTENG204" to 0, "COMPSYS201" to 1), slots)
    }

    private companion object {
        const val EXPECTED_ELECTENG_204 = 4
        const val EXPECTED_COMPSYS_201 = 6
    }
}
