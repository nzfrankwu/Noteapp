package com.nzfrankwu.noteapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CourseCodesTest {
    @Test
    fun tidy_trimsSqueezesSpacesAndUppercases() {
        assertEquals("ELECTENG 204", CourseCodes.tidy("  electeng   204 "))
    }

    @Test
    fun tidy_leavesAGoodCodeAlone() {
        assertEquals("ELECTENG 204", CourseCodes.tidy("ELECTENG 204"))
    }

    @Test
    fun tidy_nothingTyped_isNoCode() {
        assertNull(CourseCodes.tidy(null))
        assertNull(CourseCodes.tidy(""))
        assertNull(CourseCodes.tidy("   "))
    }

    @Test
    fun matchKey_ignoresSpacesAndCase() {
        val key = CourseCodes.matchKey("ELECTENG 204")
        assertEquals(key, CourseCodes.matchKey("electeng204"))
        assertEquals(key, CourseCodes.matchKey("Electeng   204"))
    }

    @Test
    fun matchKey_differentCourses_dontMatch() {
        assertNotEquals(CourseCodes.matchKey("ELECTENG 204"), CourseCodes.matchKey("ELECTENG 292"))
    }
}
