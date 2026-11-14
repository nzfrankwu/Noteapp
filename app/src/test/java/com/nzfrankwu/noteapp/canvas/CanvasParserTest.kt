package com.nzfrankwu.noteapp.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Parsing made-up sample Canvas responses (app/src/test/resources/canvas/). No real data. */
class CanvasParserTest {
    private fun sample(name: String): String =
        checkNotNull(javaClass.getResource("/canvas/$name")) { "missing sample $name" }.readText()

    // ---- The while(1); guard ----

    @Test
    fun guard_isRemoved() {
        assertEquals("[1,2]", CanvasParser.stripGuard("while(1);[1,2]"))
    }

    @Test
    fun bodyWithoutGuard_isUnchanged() {
        assertEquals("[1,2]", CanvasParser.stripGuard("[1,2]"))
        assertEquals("[1,2]", CanvasParser.stripGuard("  \n[1,2]"))
    }

    // ---- User ----

    @Test
    fun user_isRead() {
        assertEquals(CanvasUser(1001, "Test Student"), CanvasParser.parseUser(sample("user_self.json")))
    }

    // ---- Courses ----

    @Test
    fun courses_areRead_andTidied() {
        val courses = CanvasParser.parseCourses(sample("courses.json"))
        assertEquals(
            listOf(
                CanvasCourse(555, "Engineering Electromagnetics", "ELECTENG 204"),
                CanvasCourse(556, "Circuits and Systems", "ELECTENG 292"),
            ),
            courses,
        )
    }

    @Test
    fun accessRestrictedCourses_areSkipped() {
        assertFalse(CanvasParser.parseCourses(sample("courses.json")).any { it.id == 99L })
    }

    // ---- Assignments ----

    private val assignments by lazy { CanvasParser.parseAssignments(sample("assignments.json")).associateBy { it.id } }

    @Test
    fun allAssignments_areRead_includingUndatedAndQuizzes() {
        assertEquals(setOf(3L, 4L, 5L, 6L), assignments.keys)
    }

    @Test
    fun datedAssignment_hasItsFields() {
        val a = assignments.getValue(3)
        assertEquals(555L, a.courseId)
        assertEquals("Assignment 3", a.name)
        assertEquals(Instant.parse("2026-11-20T10:59:00Z"), a.dueAt)
        assertEquals("<p>Answer <strong>all</strong> questions.</p>", a.descriptionHtml)
        assertEquals("https://canvas.example.edu/courses/555/assignments/3", a.htmlUrl)
        assertEquals(CanvasSubmission("unsubmitted", null), a.submission)
        assertFalse(a.submission!!.isSubmitted)
    }

    @Test
    fun undatedAssignment_hasNoDueDate_andNoSubmission() {
        val a = assignments.getValue(4)
        assertNull(a.dueAt)
        assertNull(a.descriptionHtml)
        assertNull(a.submission)
    }

    @Test
    fun quiz_withTimeZoneOffset_andSubmission() {
        val quiz = assignments.getValue(5)
        assertEquals(Instant.parse("2026-11-27T10:59:00Z"), quiz.dueAt) // 11:59 pm NZDT
        assertNull("Empty description counts as none", quiz.descriptionHtml)
        assertTrue(quiz.submission!!.isSubmitted)
        assertEquals(Instant.parse("2026-11-26T03:15:00Z"), quiz.submission!!.submittedAt)
    }

    @Test
    fun oddValues_areHandledGently() {
        val odd = assignments.getValue(6)
        assertEquals("Untitled assignment", odd.name)
        assertNull("An unreadable date becomes no date", odd.dueAt)
        assertFalse("Graded without a submission time isn't 'submitted'", odd.submission!!.isSubmitted)
    }

    // ---- Paging ----

    @Test
    fun nextPage_isFoundInTheLinkHeader() {
        val header = "<https://canvas.example.edu/api/v1/courses/555/assignments?page=2&per_page=100>; rel=\"next\", " +
            "<https://canvas.example.edu/api/v1/courses/555/assignments?page=1&per_page=100>; rel=\"first\", " +
            "<https://canvas.example.edu/api/v1/courses/555/assignments?page=3&per_page=100>; rel=\"last\""
        assertEquals("https://canvas.example.edu/api/v1/courses/555/assignments?page=2&per_page=100", CanvasParser.nextPageUrl(header))
    }

    @Test
    fun nextPage_inAnyOrder() {
        val header = "<https://canvas.example.edu/x?page=1>; rel=\"current\",<https://canvas.example.edu/x?page=2>; rel=\"next\""
        assertEquals("https://canvas.example.edu/x?page=2", CanvasParser.nextPageUrl(header))
    }

    @Test
    fun lastPage_hasNoNext() {
        val header = "<https://canvas.example.edu/x?page=3>; rel=\"current\", <https://canvas.example.edu/x?page=1>; rel=\"first\""
        assertNull(CanvasParser.nextPageUrl(header))
        assertNull(CanvasParser.nextPageUrl(null))
        assertNull(CanvasParser.nextPageUrl(""))
    }

    // ---- Unreadable responses ----

    @Test
    fun loginPageInsteadOfData_isReportedClearly() {
        val html = "<!DOCTYPE html><html><body>Log in to Canvas</body></html>"
        assertThrows(CanvasFormatException::class.java) { CanvasParser.parseCourses(html) }
    }

    @Test
    fun errorObjectInsteadOfList_isReportedClearly() {
        val error = "while(1);{\"errors\":[{\"message\":\"user authorization required\"}]}"
        assertThrows(CanvasFormatException::class.java) { CanvasParser.parseAssignments(error) }
    }
}
