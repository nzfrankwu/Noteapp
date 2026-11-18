package com.nzfrankwu.noteapp.ui.home

import com.nzfrankwu.noteapp.data.db.Task
import com.nzfrankwu.noteapp.data.db.TaskWithCourse
import com.nzfrankwu.noteapp.domain.DueGroup
import com.nzfrankwu.noteapp.domain.TaskSource
import com.nzfrankwu.noteapp.domain.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class DueSoonStateTest {
    private val nz = ZoneId.of("Pacific/Auckland")
    private val now = Instant.parse("2026-11-15T00:00:00Z") // Sun 15 Nov, 1 pm NZ

    private fun task(title: String, dueInDays: Long?, status: TaskStatus = TaskStatus.NOT_STARTED, course: String? = "ELECTENG 204") =
        TaskWithCourse(
            Task(
                id = title, title = title, courseId = null, dueAt = dueInDays?.let { now.plus(it, ChronoUnit.DAYS) },
                url = null, descriptionHtml = null, status = status, targetAt = null, notes = null,
                source = TaskSource.MANUAL, canvasAssignmentId = null, canvasSubmissionState = null,
                canvasSubmittedAt = null, removedFromCanvas = false, createdAt = now, updatedAt = now,
            ),
            course,
        )

    private fun build(vararg tasks: TaskWithCourse) =
        DueSoonState.build(tasks.toList(), now, nz, noDueDateExpanded = false, showFinished = false)

    @Test
    fun noTasks_isEmpty() {
        val state = build()
        assertTrue(state.loaded)
        assertTrue(state.isEmpty)
    }

    @Test
    fun datedTasks_acrossCourses_areInSectionsInOrder() {
        val state = build(
            task("Later one", 20, course = "ELECTENG 292"),
            task("Late", -1),
            task("Tomorrow", 1),
        )
        assertEquals(listOf(DueGroup.OVERDUE, DueGroup.TOMORROW, DueGroup.LATER), state.dated.map { it.group })
        assertEquals("ELECTENG 292", state.dated.last().tasks.single().item.courseCode)
    }

    @Test
    fun undatedAndFinished_areKeptApart() {
        val state = build(
            task("Someday", null),
            task("Handed in", 3, TaskStatus.DONE),
            task("Upcoming", 3),
        )
        assertEquals(listOf("Someday"), state.noDueDate.map { it.title })
        assertEquals(listOf("Handed in"), state.finished.map { it.title })
        assertEquals(listOf("Upcoming"), state.dated.flatMap { it.tasks }.map { it.title })
    }

    @Test
    fun finished_newestFirst_undatedLast() {
        val state = build(
            task("Old", -10, TaskStatus.DONE),
            task("Undated done", null, TaskStatus.DONE),
            task("Recent", -1, TaskStatus.DONE),
        )
        assertEquals(listOf("Recent", "Old", "Undated done"), state.finished.map { it.title })
    }

    @Test
    fun taskWithoutCourse_isStillShown() {
        val state = build(task("Orphan", 2, course = null))
        assertEquals(null, state.dated.single().tasks.single().item.courseCode)
    }
}
