package com.nzfrankwu.noteapp.ui.calendar

import com.nzfrankwu.noteapp.data.db.Task
import com.nzfrankwu.noteapp.data.db.TaskWithCourse
import com.nzfrankwu.noteapp.domain.TaskSource
import com.nzfrankwu.noteapp.domain.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

class CalendarStateTest {
    private val nz = ZoneId.of("Pacific/Auckland")
    private val now = local("2026-11-15T10:00")
    private fun local(text: String): Instant = LocalDateTime.parse(text).atZone(nz).toInstant()

    private fun task(id: String, courseId: String, due: String?, status: TaskStatus = TaskStatus.NOT_STARTED) = TaskWithCourse(
        Task(
            id = id, title = id, courseId = courseId, dueAt = due?.let(::local), url = null, descriptionHtml = null,
            status = status, targetAt = null, notes = null, source = TaskSource.MANUAL, canvasAssignmentId = null,
            canvasSubmissionState = null, canvasSubmittedAt = null, removedFromCanvas = false,
            createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
        ),
        courseCode = if (courseId == "c1") "ELECTENG 204" else "COMPSYS 201",
    )

    private val tasks = listOf(
        task("lab", "c1", "2026-11-20T23:59"),
        task("quiz", "c2", "2026-11-20T10:00"),
        task("done", "c1", "2026-11-20T09:00", TaskStatus.DONE),
        task("early", "c1", "2026-10-27T12:00"), // in the grid, but in October
    )

    private fun build(filters: TaskViewFilters = TaskViewFilters()) =
        CalendarState.build(tasks, emptyList(), YearMonth.of(2026, 11), filters, now, nz)

    private val friday = LocalDate.of(2026, 11, 20)

    @Test
    fun days_knowIfTheyAreInTheMonth_andToday() {
        val state = build()
        assertFalse(state.day(LocalDate.of(2026, 10, 27))!!.inMonth)
        assertTrue(state.day(friday)!!.inMonth)
        assertTrue(state.day(LocalDate.of(2026, 11, 15))!!.isToday)
        assertEquals(1, state.weeks.flatten().count { it.isToday })
    }

    @Test
    fun tasksSitOnTheirDays_inTimeOrder_withoutFinished() {
        val state = build()
        assertEquals(listOf("quiz", "lab"), state.day(friday)!!.entries.map { it.task.id })
        assertEquals(listOf("early"), state.day(LocalDate.of(2026, 10, 27))!!.entries.map { it.task.id })
    }

    @Test
    fun showFinished_addsThem() {
        val state = build(TaskViewFilters(showFinished = true))
        assertEquals(listOf("done", "quiz", "lab"), state.day(friday)!!.entries.map { it.task.id })
    }

    @Test
    fun courseFilter_showsOnlyThatCourse() {
        val state = build(TaskViewFilters(courseId = "c2"))
        assertEquals(listOf("quiz"), state.day(friday)!!.entries.map { it.task.id })
        assertTrue(state.day(LocalDate.of(2026, 10, 27))!!.entries.isEmpty())
    }
}
