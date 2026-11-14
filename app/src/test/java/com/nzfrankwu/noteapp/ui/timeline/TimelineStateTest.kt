package com.nzfrankwu.noteapp.ui.timeline

import com.nzfrankwu.noteapp.data.db.Course
import com.nzfrankwu.noteapp.data.db.Task
import com.nzfrankwu.noteapp.data.db.TaskWithCourse
import com.nzfrankwu.noteapp.domain.TaskSource
import com.nzfrankwu.noteapp.domain.TaskStatus
import com.nzfrankwu.noteapp.domain.TimelinePlacement
import com.nzfrankwu.noteapp.domain.TimelineZoom
import com.nzfrankwu.noteapp.ui.calendar.TaskViewFilters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class TimelineStateTest {
    private val nz = ZoneId.of("Pacific/Auckland")
    private val now = local("2026-11-15T12:00")
    private val start = LocalDate.of(2026, 11, 9) // Monday; 2 weeks = 9–22 Nov
    private fun local(text: String): Instant = LocalDateTime.parse(text).atZone(nz).toInstant()

    private fun task(
        id: String,
        due: String?,
        created: String = "2026-11-10T09:00",
        available: String? = null,
        target: String? = null,
        status: TaskStatus = TaskStatus.NOT_STARTED,
        courseId: String = "c1",
    ) = TaskWithCourse(
        Task(
            id = id, title = id, courseId = courseId, dueAt = due?.let(::local), url = null, descriptionHtml = null,
            status = status, targetAt = target?.let(::local), notes = null, source = TaskSource.CANVAS, canvasAssignmentId = null,
            canvasSubmissionState = null, canvasSubmittedAt = null, removedFromCanvas = false,
            createdAt = local(created), updatedAt = local(created), availableAt = available?.let(::local),
        ),
        courseCode = "ELECTENG 204",
    )

    private val courses = listOf(
        Course("c1", "ELECTENG 204", null, null, Instant.EPOCH, Instant.EPOCH),
        Course("c2", "COMPSYS 201", null, null, Instant.EPOCH, Instant.EPOCH),
    )

    private fun build(tasks: List<TaskWithCourse>, filters: TaskViewFilters = TaskViewFilters()) =
        TimelineState.build(tasks, courses, start, TimelineZoom.TWO_WEEKS, filters, now, nz)

    @Test
    fun barRunsFromAvailableFrom_toDue() {
        val row = build(listOf(task("lab", "2026-11-13T12:00", available = "2026-11-11T00:00"))).rows.single()
        val bar = row.placement as TimelinePlacement.Visible
        assertEquals(2.0, bar.start, 1e-9)
        assertEquals(4.5, bar.end, 1e-9)
    }

    @Test
    fun withoutAvailableFrom_theBarStartsWhenTheTaskWasAdded() {
        val row = build(listOf(task("own", "2026-11-13T12:00", created = "2026-11-12T00:00"))).rows.single()
        assertEquals(3.0, (row.placement as TimelinePlacement.Visible).start, 1e-9)
    }

    @Test
    fun rowsAreByDueDate_andUndatedAreOnlyCounted() {
        val state = build(
            listOf(
                task("later", "2026-11-20T23:59"),
                task("sooner", "2026-11-16T23:59"),
                task("undated", null),
            ),
        )
        assertEquals(listOf("sooner", "later"), state.rows.map { it.task.id })
        assertEquals(1, state.undatedCount)
    }

    @Test
    fun offScreenTasks_becomeArrows_thatJumpToThem() {
        val state = build(
            listOf(
                task("old", "2026-11-02T23:59", created = "2026-10-20T09:00"),
                task("future", "2026-12-20T23:59", available = "2026-12-01T09:00"),
            ),
        )
        val old = state.rows.first { it.task.id == "old" }
        assertEquals(TimelinePlacement.Before, old.placement)
        assertEquals(LocalDate.of(2026, 11, 2), old.jumpTo)
        val future = state.rows.first { it.task.id == "future" }
        assertEquals(TimelinePlacement.After, future.placement)
        assertEquals(LocalDate.of(2026, 12, 1), future.jumpTo)
    }

    @Test
    fun targetDate_isMarked_whenVisible() {
        val state = build(
            listOf(
                task("a", "2026-11-20T23:59", target = "2026-11-18T12:00"),
                task("b", "2026-11-20T23:59", target = "2026-12-18T12:00"),
            ),
        )
        assertEquals(9.5, state.rows.first { it.task.id == "a" }.target!!, 1e-9)
        assertNull(state.rows.first { it.task.id == "b" }.target)
    }

    @Test
    fun finishedAndOtherCourses_followTheFilters() {
        val tasks = listOf(
            task("done", "2026-11-13T12:00", status = TaskStatus.DONE),
            task("open", "2026-11-14T12:00"),
            task("other course", "2026-11-14T12:00", courseId = "c2"),
            task("other undated", null, courseId = "c2"),
        )
        assertEquals(listOf("open", "other course"), build(tasks).rows.map { it.task.id })
        assertEquals(listOf("done", "open", "other course"), build(tasks, TaskViewFilters(showFinished = true)).rows.map { it.task.id })
        val c1 = build(tasks, TaskViewFilters(courseId = "c1"))
        assertEquals(listOf("open"), c1.rows.map { it.task.id })
        assertEquals(0, c1.undatedCount)
    }

    @Test
    fun archivedCourses_areNotOfferedAsFilters_andAStaleFilterResets() {
        val archivedC2 = listOf(courses[0], courses[1].copy(archivedAt = now))
        val tasks = listOf(task("open", "2026-11-14T12:00"), task("other", "2026-11-14T12:00", courseId = "c2"))
        val state = TimelineState.build(tasks, archivedC2, start, TimelineZoom.TWO_WEEKS, TaskViewFilters(courseId = "c2"), now, nz)
        assertEquals(listOf("c1"), state.courses.map { it.id })
        assertNull(state.filters.courseId)
    }

    @Test
    fun nowLine_isAtNow_whenVisible() {
        val state = build(emptyList())
        assertEquals(6.5, state.nowPosition!!, 1e-9) // Sunday 15 Nov, midday
        assertTrue(state.days.size == 14)
        val later = TimelineState.build(emptyList(), emptyList(), LocalDate.of(2026, 11, 23), TimelineZoom.TWO_WEEKS, TaskViewFilters(), now, nz)
        assertNull(later.nowPosition)
    }
}
