package com.nzfrankwu.noteapp.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CourseColorsTest {
    @Test
    fun lightAndDark_haveTheSameNumberOfColours() {
        // Otherwise a course would be one colour in light mode and an unrelated one in dark mode.
        assertEquals(7, LightCourseColors.palette.size)
        assertEquals(LightCourseColors.palette.size, DarkCourseColors.palette.size)
    }

    @Test
    fun paletteColours_areAllDifferent() {
        for (set in listOf(LightCourseColors, DarkCourseColors)) {
            assertEquals(set.palette.size, set.palette.map { it.accent }.distinct().size)
        }
    }

    @Test
    fun noCourse_isNeutral() {
        assertEquals(LightCourseColors.none, LightCourseColors.of(null))
        assertEquals(DarkCourseColors.none, DarkCourseColors.of(null))
    }

    @Test
    fun sameCourse_sameColour_howeverItIsWritten() {
        assertEquals(LightCourseColors.of("ELECTENG 204"), LightCourseColors.of("electeng204"))
    }

    @Test
    fun differentCourses_canDiffer() {
        assertNotEquals(LightCourseColors.of("ELECTENG 204"), LightCourseColors.of("COMPSYS 201"))
    }
}
