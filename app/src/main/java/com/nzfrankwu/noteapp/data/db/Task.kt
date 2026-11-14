package com.nzfrankwu.noteapp.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nzfrankwu.noteapp.domain.TaskLike
import com.nzfrankwu.noteapp.domain.TaskSource
import com.nzfrankwu.noteapp.domain.TaskStatus
import java.time.Instant

/**
 * A task: a Canvas assignment or one of your own (IDEA.md §2.3).
 *
 * The fields come in three groups, which matters for Canvas sync (IDEA.md §3.3):
 *  - Canvas-owned: for Canvas tasks, sync overwrites these with what Canvas says.
 *  - Yours: sync never touches these (except auto-status moving forward to Submitted).
 *  - Canvas bookkeeping: used by sync itself.
 * For your own tasks (source = MANUAL), everything is yours.
 */
@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = Course::class,
            parentColumns = ["id"],
            childColumns = ["course_id"],
            // If a course is ever deleted, its tasks are kept and become "no course".
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["course_id"]),
        // Each Canvas assignment is imported at most once.
        Index(value = ["canvas_assignment_id"], unique = true),
    ],
)
data class Task(
    /** Permanent random ID (UUID). */
    @PrimaryKey val id: String,

    // ---- Canvas-owned (for Canvas tasks) ----
    override val title: String,
    @ColumnInfo(name = "course_id") val courseId: String?,
    /** Real due date, or null for "No due date". */
    @ColumnInfo(name = "due_at") override val dueAt: Instant?,
    /** Link: "Open in Canvas" for Canvas tasks, or any link you added to your own task. */
    val url: String?,
    /** Canvas's description, as HTML. Null for your own tasks. */
    @ColumnInfo(name = "description_html") val descriptionHtml: String?,

    // ---- Yours ----
    override val status: TaskStatus,
    /** "My target date": your own do-by date, used for sorting and reminders when set. */
    @ColumnInfo(name = "target_at") override val targetAt: Instant?,
    /** Your own notes on the task. */
    val notes: String?,

    // ---- Canvas bookkeeping ----
    val source: TaskSource,
    /** Canvas's ID for this assignment; null for your own tasks. */
    @ColumnInfo(name = "canvas_assignment_id") val canvasAssignmentId: Long?,
    /** Canvas's submission state, e.g. "unsubmitted", "submitted", "graded". */
    @ColumnInfo(name = "canvas_submission_state") val canvasSubmissionState: String?,
    /**
     * Latest submission time seen from Canvas. Auto-status only reacts when this changes,
     * so if you move a task back from Submitted yourself, sync won't fight you.
     */
    @ColumnInfo(name = "canvas_submitted_at") val canvasSubmittedAt: Instant?,
    /** True if the assignment disappeared from Canvas. It's kept, never deleted. */
    @ColumnInfo(name = "removed_from_canvas") val removedFromCanvas: Boolean,

    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
) : TaskLike
