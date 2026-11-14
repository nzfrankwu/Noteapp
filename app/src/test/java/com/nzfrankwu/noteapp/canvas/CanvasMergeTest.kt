package com.nzfrankwu.noteapp.canvas

import com.nzfrankwu.noteapp.data.db.Task
import com.nzfrankwu.noteapp.domain.TaskSource
import com.nzfrankwu.noteapp.domain.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class CanvasMergeTest {
    private val t0 = Instant.parse("2026-11-01T00:00:00Z")
    private val now = Instant.parse("2026-11-14T10:00:00Z")
    private val due = Instant.parse("2026-11-20T10:59:00Z")
    private val submittedAt = Instant.parse("2026-11-19T08:00:00Z")
    private val opens = Instant.parse("2026-11-09T19:00:00Z")

    private fun assignment(
        name: String = "Assignment 3",
        dueAt: Instant? = due,
        submission: CanvasSubmission? = CanvasSubmission("unsubmitted", null),
        description: String? = "<p>Do it</p>",
        unlockAt: Instant? = opens,
    ) = CanvasAssignment(
        id = 3, courseId = 555, name = name, dueAt = dueAt, descriptionHtml = description,
        htmlUrl = "https://canvas.example.edu/courses/555/assignments/3", submission = submission,
        unlockAt = unlockAt,
    )

    private fun existing(
        status: TaskStatus = TaskStatus.NOT_STARTED,
        submittedAtSeen: Instant? = null,
        stateSeen: String? = "unsubmitted",
        removed: Boolean = false,
    ) = CanvasMerge.newTask(assignment(), "c1", "task-1", t0).copy(
        status = status,
        targetAt = Instant.parse("2026-11-18T20:00:00Z"),
        notes = "my notes",
        canvasSubmittedAt = submittedAtSeen,
        canvasSubmissionState = stateSeen,
        removedFromCanvas = removed,
    )

    // ---- New tasks ----

    @Test
    fun newAssignment_becomesANotStartedCanvasTask() {
        val task = CanvasMerge.newTask(assignment(), "c1", "task-1", now)
        assertEquals("Assignment 3", task.title)
        assertEquals("c1", task.courseId)
        assertEquals(due, task.dueAt)
        assertEquals(TaskStatus.NOT_STARTED, task.status)
        assertEquals(TaskSource.CANVAS, task.source)
        assertEquals(3L, task.canvasAssignmentId)
        assertEquals("<p>Do it</p>", task.descriptionHtml)
        assertEquals("https://canvas.example.edu/courses/555/assignments/3", task.url)
        assertEquals(opens, task.availableAt)
    }

    @Test
    fun newAssignment_withoutAvailableFrom_hasNone() {
        assertEquals(null, CanvasMerge.newTask(assignment(unlockAt = null), "c1", "t", now).availableAt)
    }

    // ---- "Available from" (database version 2) ----

    @Test
    fun availableFrom_followsCanvas_whenItChanges() {
        val later = Instant.parse("2026-11-12T19:00:00Z")
        val merged = CanvasMerge.update(existing(status = TaskStatus.STARTED), assignment(unlockAt = later), "c1", now)
        assertEquals(later, merged.availableAt)
        assertEquals(now, merged.updatedAt)
        assertEquals(TaskStatus.STARTED, merged.status)
        assertEquals("my notes", merged.notes)
    }

    @Test
    fun availableFrom_removedOnCanvas_isCleared() {
        val merged = CanvasMerge.update(existing(), assignment(unlockAt = null), "c1", now)
        assertEquals(null, merged.availableAt)
    }

    @Test
    fun taskFromBeforeTheUpdate_getsItsAvailableFrom_onTheNextSync_andKeepsYourFields() {
        // Right after the 1 → 2 migration, Canvas tasks have no "available from" yet.
        val migrated = existing(status = TaskStatus.STUCK).copy(availableAt = null)
        val merged = CanvasMerge.update(migrated, assignment(), "c1", now)
        assertEquals(opens, merged.availableAt)
        assertEquals(TaskStatus.STUCK, merged.status)
        assertEquals(Instant.parse("2026-11-18T20:00:00Z"), merged.targetAt)
        assertEquals("my notes", merged.notes)
        assertEquals(t0, merged.createdAt)
        assertEquals(now, merged.updatedAt)
    }

    @Test
    fun newAssignment_alreadySubmitted_startsAsSubmitted() {
        val task = CanvasMerge.newTask(assignment(submission = CanvasSubmission("submitted", submittedAt)), "c1", "t", now)
        assertEquals(TaskStatus.SUBMITTED, task.status)
        assertEquals(submittedAt, task.canvasSubmittedAt)
    }

    @Test
    fun newAssignment_withoutDueDate_staysUndated() {
        assertEquals(null, CanvasMerge.newTask(assignment(dueAt = null), "c1", "t", now).dueAt)
    }

    // ---- Updates never touch your fields ----

    @Test
    fun canvasChanges_updateCanvasFields_butKeepYours() {
        val before = existing(status = TaskStatus.STUCK)
        val newDue = Instant.parse("2026-11-27T10:59:00Z")
        val after = CanvasMerge.update(before, assignment(name = "Assignment 3 (extended)", dueAt = newDue, description = "<p>New</p>"), "c1", now)
        assertEquals("Assignment 3 (extended)", after.title)
        assertEquals(newDue, after.dueAt)
        assertEquals("<p>New</p>", after.descriptionHtml)
        // yours
        assertEquals(TaskStatus.STUCK, after.status)
        assertEquals(before.targetAt, after.targetAt)
        assertEquals("my notes", after.notes)
        assertEquals(now, after.updatedAt)
    }

    @Test
    fun nothingChanged_returnsTheSameTask_withoutBumpingTheTime() {
        val before = existing()
        assertSame(before, CanvasMerge.update(before, assignment(), "c1", now))
    }

    @Test
    fun reappearingAssignment_isNoLongerMarkedRemoved() {
        val after = CanvasMerge.update(existing(removed = true), assignment(), "c1", now)
        assertFalse(after.removedFromCanvas)
    }

    // ---- Auto-status ----

    @Test
    fun newSubmission_movesTaskForwardToSubmitted() {
        for (status in listOf(TaskStatus.NOT_STARTED, TaskStatus.STARTED, TaskStatus.STUCK, TaskStatus.DOUBLE_CHECKING)) {
            val after = CanvasMerge.update(existing(status = status), assignment(submission = CanvasSubmission("submitted", submittedAt)), "c1", now)
            assertEquals("from $status", TaskStatus.SUBMITTED, after.status)
            assertEquals(submittedAt, after.canvasSubmittedAt)
        }
    }

    @Test
    fun done_isNeverOverridden() {
        val after = CanvasMerge.update(existing(status = TaskStatus.DONE), assignment(submission = CanvasSubmission("submitted", submittedAt)), "c1", now)
        assertEquals(TaskStatus.DONE, after.status)
    }

    @Test
    fun neverMovesBackwards_whenCanvasSaysUnsubmitted() {
        val after = CanvasMerge.update(
            existing(status = TaskStatus.SUBMITTED, submittedAtSeen = submittedAt, stateSeen = "submitted"),
            assignment(submission = CanvasSubmission("unsubmitted", null)),
            "c1", now,
        )
        assertEquals(TaskStatus.SUBMITTED, after.status)
    }

    @Test
    fun movingItBackYourself_sticks_forTheSameSubmission() {
        // Auto-moved to Submitted earlier; you then moved it back to Double-checking.
        val yours = existing(status = TaskStatus.DOUBLE_CHECKING, submittedAtSeen = submittedAt, stateSeen = "submitted")
        val after = CanvasMerge.update(yours, assignment(submission = CanvasSubmission("submitted", submittedAt)), "c1", now)
        assertEquals(TaskStatus.DOUBLE_CHECKING, after.status)
    }

    @Test
    fun aResubmission_movesItForwardAgain() {
        val yours = existing(status = TaskStatus.DOUBLE_CHECKING, submittedAtSeen = submittedAt, stateSeen = "submitted")
        val later = submittedAt.plusSeconds(3600)
        val after = CanvasMerge.update(yours, assignment(submission = CanvasSubmission("submitted", later)), "c1", now)
        assertEquals(TaskStatus.SUBMITTED, after.status)
    }

    @Test
    fun gradedWithoutASubmission_doesNotCountAsSubmitted() {
        val after = CanvasMerge.update(existing(), assignment(submission = CanvasSubmission("graded", null)), "c1", now)
        assertEquals(TaskStatus.NOT_STARTED, after.status)
    }

    @Test
    fun submittedStateWithoutTime_countsOnceOnly() {
        val first = CanvasMerge.update(existing(status = TaskStatus.STARTED), assignment(submission = CanvasSubmission("submitted", null)), "c1", now)
        assertEquals(TaskStatus.SUBMITTED, first.status)
        val movedBack = first.copy(status = TaskStatus.STARTED)
        val second = CanvasMerge.update(movedBack, assignment(submission = CanvasSubmission("submitted", null)), "c1", now)
        assertEquals(TaskStatus.STARTED, second.status)
    }

    @Test
    fun responseWithoutSubmissionInfo_doesNotForgetTheSubmission() {
        // You moved an auto-submitted task back; then one sync's response leaves out submissions.
        val yours = existing(status = TaskStatus.DOUBLE_CHECKING, submittedAtSeen = submittedAt, stateSeen = "submitted")
        val afterGap = CanvasMerge.update(yours, assignment(submission = null), "c1", now)
        assertEquals(submittedAt, afterGap.canvasSubmittedAt)
        // The next normal sync shows the same old submission: it must not count as new.
        val next = CanvasMerge.update(afterGap, assignment(submission = CanvasSubmission("submitted", submittedAt)), "c1", now)
        assertEquals(TaskStatus.DOUBLE_CHECKING, next.status)
    }

    @Test
    fun noSubmissionInfo_leavesStatusAlone() {
        val after = CanvasMerge.update(existing(status = TaskStatus.STARTED), assignment(submission = null), "c1", now)
        assertEquals(TaskStatus.STARTED, after.status)
    }

    // ---- Removed from Canvas ----

    private fun canvasTask(id: String, assignmentId: Long, removed: Boolean = false) =
        existing(removed = removed).copy(id = id, canvasAssignmentId = assignmentId)

    @Test
    fun missingAssignments_areMarkedRemoved_notDeleted() {
        val tasks = listOf(canvasTask("a", 1), canvasTask("b", 2), canvasTask("c", 3))
        val changed = CanvasMerge.markRemoved(tasks, fetchedAssignmentIds = setOf(1, 3), now = now)
        assertEquals(listOf("b"), changed.map { it.id })
        assertTrue(changed.single().removedFromCanvas)
        assertEquals(now, changed.single().updatedAt)
    }

    @Test
    fun alreadyRemoved_orOwnTasks_areLeftAlone() {
        val own = canvasTask("own", 9).copy(source = TaskSource.MANUAL, canvasAssignmentId = null)
        val alreadyRemoved = canvasTask("old", 8, removed = true)
        assertTrue(CanvasMerge.markRemoved(listOf(own, alreadyRemoved), emptySet(), now).isEmpty())
    }
}
