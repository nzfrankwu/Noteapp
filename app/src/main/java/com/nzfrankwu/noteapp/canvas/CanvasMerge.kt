package com.nzfrankwu.noteapp.canvas

import com.nzfrankwu.noteapp.data.db.Task
import com.nzfrankwu.noteapp.domain.TaskSource
import com.nzfrankwu.noteapp.domain.TaskStatus
import java.time.Instant

/**
 * How Canvas data is merged into your tasks (IDEA.md §3.3). Pure rules, no database or network,
 * so every case is unit-tested.
 *
 * - Canvas-owned fields (title, course, due date, description, link) follow Canvas.
 * - Your fields (status, target date, notes) are never overwritten, except auto-status:
 *   when Canvas shows a NEW submission, the task moves forward to Submitted (never backwards,
 *   never over Done). Moving it back yourself afterwards sticks, because the same submission
 *   doesn't trigger again.
 * - Assignments missing from Canvas are marked "removed from Canvas", never deleted, and only
 *   after a complete fetch of that course.
 */
object CanvasMerge {

    /** A task for an assignment seen for the first time. Already submitted on Canvas → Submitted. */
    fun newTask(assignment: CanvasAssignment, courseId: String, id: String, now: Instant): Task = Task(
        id = id,
        title = assignment.name,
        courseId = courseId,
        dueAt = assignment.dueAt,
        url = assignment.htmlUrl,
        descriptionHtml = assignment.descriptionHtml,
        status = if (assignment.submission?.isSubmitted == true) TaskStatus.SUBMITTED else TaskStatus.NOT_STARTED,
        targetAt = null,
        notes = null,
        source = TaskSource.CANVAS,
        canvasAssignmentId = assignment.id,
        canvasSubmissionState = assignment.submission?.workflowState,
        canvasSubmittedAt = assignment.submission?.submittedAt,
        removedFromCanvas = false,
        createdAt = now,
        updatedAt = now,
    )

    /**
     * The task after applying Canvas's latest data. Returns [existing] itself (same object) when
     * nothing changed, so "last changed" isn't bumped and backup won't re-upload it.
     */
    fun update(existing: Task, assignment: CanvasAssignment, courseId: String, now: Instant): Task {
        val submission = assignment.submission
        val merged = existing.copy(
            // Canvas-owned
            title = assignment.name,
            courseId = courseId,
            dueAt = assignment.dueAt,
            url = assignment.htmlUrl,
            descriptionHtml = assignment.descriptionHtml,
            // Yours, except auto-status moving forward on a new submission
            status = autoStatus(existing, submission),
            // Bookkeeping: remember the latest submission seen. If Canvas leaves the submission
            // out of a response, keep what we knew, so an old submission never looks "new" later.
            canvasSubmissionState = submission?.workflowState ?: existing.canvasSubmissionState,
            canvasSubmittedAt = submission?.submittedAt ?: existing.canvasSubmittedAt,
            removedFromCanvas = false, // it's on Canvas again
        )
        return if (merged == existing) existing else merged.copy(updatedAt = now)
    }

    /**
     * Status after a sync. Moves to Submitted only for a NEW submission (one not seen before),
     * and only forward: Submitted and Done are left alone.
     */
    fun autoStatus(existing: Task, submission: CanvasSubmission?): TaskStatus {
        if (submission == null || !submission.isSubmitted) return existing.status
        if (existing.status.isFinished) return existing.status

        val seenBefore = CanvasSubmission(existing.canvasSubmissionState, existing.canvasSubmittedAt)
        val isNewSubmission = if (submission.submittedAt != null) {
            submission.submittedAt != existing.canvasSubmittedAt
        } else {
            !seenBefore.isSubmitted // no times to compare: new if it wasn't submitted last time
        }
        return if (isNewSubmission) TaskStatus.SUBMITTED else existing.status
    }

    /**
     * After a COMPLETE fetch of one course's assignments: the Canvas tasks of that course that
     * Canvas no longer lists, marked as removed. Returns only tasks that change.
     * Never call this after a partial or failed fetch, or everything would look removed.
     */
    fun markRemoved(courseTasks: List<Task>, fetchedAssignmentIds: Set<Long>, now: Instant): List<Task> =
        courseTasks
            .filter { it.source == TaskSource.CANVAS && !it.removedFromCanvas }
            .filter { it.canvasAssignmentId != null && it.canvasAssignmentId !in fetchedAssignmentIds }
            .map { it.copy(removedFromCanvas = true, updatedAt = now) }
}
