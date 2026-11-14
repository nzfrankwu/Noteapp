package com.nzfrankwu.noteapp.canvas

import java.time.Instant

// What Noteapp reads from Canvas (only the fields it uses). Canvas is read-only for Noteapp:
// these are filled from GET responses and never sent back (IDEA.md §3.5).

/** The signed-in Canvas user ("Signed in as …"). */
data class CanvasUser(val id: Long, val name: String)

/** One of the user's courses. */
data class CanvasCourse(val id: Long, val name: String, val courseCode: String)

/** One assignment (quizzes included), as Canvas reports it. */
data class CanvasAssignment(
    val id: Long,
    val courseId: Long,
    val name: String,
    /** Null means the assignment has no due date. */
    val dueAt: Instant?,
    val descriptionHtml: String?,
    /** The assignment's page in Canvas ("Open in Canvas"). */
    val htmlUrl: String?,
    /** The user's own submission, if Canvas included it. */
    val submission: CanvasSubmission?,
)

/** The user's submission for an assignment. */
data class CanvasSubmission(
    /** e.g. "unsubmitted", "submitted", "graded", "pending_review". */
    val workflowState: String?,
    val submittedAt: Instant?,
) {
    /**
     * True when the user has actually handed it in. ("graded" without a submission time can
     * mean the teacher graded something not submitted through Canvas, so it doesn't count.)
     */
    val isSubmitted: Boolean
        get() = submittedAt != null || workflowState == "submitted" || workflowState == "pending_review"
}
