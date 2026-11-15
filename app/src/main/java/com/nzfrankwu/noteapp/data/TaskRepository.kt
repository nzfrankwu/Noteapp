package com.nzfrankwu.noteapp.data

import androidx.room.withTransaction
import com.nzfrankwu.noteapp.data.db.Course
import com.nzfrankwu.noteapp.data.db.NoteAppDatabase
import com.nzfrankwu.noteapp.data.db.Task
import com.nzfrankwu.noteapp.data.db.TaskWithCourse
import com.nzfrankwu.noteapp.domain.Links
import com.nzfrankwu.noteapp.domain.TaskSource
import com.nzfrankwu.noteapp.domain.TaskStatus
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** What you fill in when adding or editing a task. Times are already-converted instants. */
data class TaskDraft(
    val title: String,
    val courseId: String?,
    val dueAt: Instant? = null,
    val targetAt: Instant? = null,
    val url: String? = null,
    val notes: String? = null,
)

/** Why a task change couldn't be made. The screen turns these into messages. */
sealed interface TaskError {
    data object BlankTitle : TaskError
    /** Every task needs a course (decision for Phase 1). */
    data object NoCourse : TaskError
    data object NotFound : TaskError
    /** Canvas tasks can't be deleted (sync would bring them back), unless they were removed from Canvas. */
    data object CanvasTaskCantBeDeleted : TaskError
}

sealed interface TaskResult {
    data class Success(val taskId: String) : TaskResult
    data class Failure(val error: TaskError) : TaskResult
}

/** Result of deleting: the deleted task is returned so the screen can offer Undo. */
sealed interface DeleteTaskResult {
    data class Deleted(val task: Task) : DeleteTaskResult
    data class Failure(val error: TaskError) : DeleteTaskResult
}

/**
 * Adding, editing and deleting tasks. Screens go through this, so the rules live in one tested
 * place. For Canvas tasks, only your own fields change (status, target date, notes); the rest
 * comes from Canvas (IDEA.md §3.3).
 */
class TaskRepository(
    private val db: NoteAppDatabase,
    private val clock: Clock = Clock.systemUTC(),
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val taskDao = db.taskDao()
    private val courseDao = db.courseDao()

    /** Every task with its course code, archived courses included (e.g. inside a course folder). */
    fun observeAllWithCourse(): Flow<List<TaskWithCourse>> = taskDao.observeAllWithCourse()

    /** The tasks Home shows: everything except archived courses' tasks. */
    fun observeActiveWithCourse(): Flow<List<TaskWithCourse>> = taskDao.observeActiveWithCourse()

    fun observeForCourse(courseId: String): Flow<List<Task>> = taskDao.observeForCourse(courseId)

    fun observeTask(taskId: String): Flow<TaskWithCourse?> = taskDao.observeWithCourse(taskId)

    suspend fun getTask(taskId: String): Task? = taskDao.getById(taskId)

    /** All courses A–Z by code, for the course picker in the task form. */
    fun observeCourses(): Flow<List<Course>> = courseDao.observeAll()

    /** Adds one of your own tasks. Status starts as Not started. */
    suspend fun createTask(draft: TaskDraft): TaskResult = db.withTransaction {
        validate(draft)?.let { return@withTransaction TaskResult.Failure(it) }
        val now = clock.instant()
        val task = Task(
            id = newId(),
            title = draft.title.trim(),
            courseId = draft.courseId,
            dueAt = draft.dueAt,
            url = Links.normalize(draft.url),
            descriptionHtml = null,
            status = TaskStatus.NOT_STARTED,
            targetAt = draft.targetAt,
            notes = cleanNotes(draft.notes),
            source = TaskSource.MANUAL,
            canvasAssignmentId = null,
            canvasSubmissionState = null,
            canvasSubmittedAt = null,
            removedFromCanvas = false,
            createdAt = now,
            updatedAt = now,
            availableAt = null, // only Canvas sets "available from"; the timeline uses createdAt
        )
        taskDao.insert(task)
        TaskResult.Success(task.id)
    }

    /**
     * Saves edits. Your own tasks: every field. Canvas tasks: only target date and notes
     * (title, course, due date and link belong to Canvas and are ignored here).
     */
    suspend fun updateTask(taskId: String, draft: TaskDraft): TaskResult = db.withTransaction {
        val task = taskDao.getById(taskId) ?: return@withTransaction TaskResult.Failure(TaskError.NotFound)
        val updated = when (task.source) {
            TaskSource.CANVAS -> task.copy(targetAt = draft.targetAt, notes = cleanNotes(draft.notes))
            TaskSource.MANUAL -> {
                validate(draft)?.let { return@withTransaction TaskResult.Failure(it) }
                task.copy(
                    title = draft.title.trim(),
                    courseId = draft.courseId,
                    dueAt = draft.dueAt,
                    targetAt = draft.targetAt,
                    url = Links.normalize(draft.url),
                    notes = cleanNotes(draft.notes),
                )
            }
        }
        taskDao.update(updated.copy(updatedAt = clock.instant()))
        TaskResult.Success(taskId)
    }

    suspend fun setStatus(taskId: String, status: TaskStatus): TaskResult = db.withTransaction {
        val task = taskDao.getById(taskId) ?: return@withTransaction TaskResult.Failure(TaskError.NotFound)
        if (task.status != status) taskDao.update(task.copy(status = status, updatedAt = clock.instant()))
        TaskResult.Success(taskId)
    }

    /** Deletes a task and returns it, so the screen can offer Undo ([restoreTask]). */
    suspend fun deleteTask(taskId: String): DeleteTaskResult = db.withTransaction {
        val task = taskDao.getById(taskId) ?: return@withTransaction DeleteTaskResult.Failure(TaskError.NotFound)
        if (task.source == TaskSource.CANVAS && !task.removedFromCanvas) {
            return@withTransaction DeleteTaskResult.Failure(TaskError.CanvasTaskCantBeDeleted)
        }
        taskDao.delete(task)
        DeleteTaskResult.Deleted(task)
    }

    /** Undo for [deleteTask]: puts the exact same task back (same ID and dates). */
    suspend fun restoreTask(task: Task) = db.withTransaction {
        if (taskDao.getById(task.id) != null) return@withTransaction
        // If its course was deleted in the meantime, bring the task back without a course.
        val courseStillThere = task.courseId?.let { courseDao.getById(it) } != null
        taskDao.insert(if (courseStillThere || task.courseId == null) task else task.copy(courseId = null))
    }

    private suspend fun validate(draft: TaskDraft): TaskError? = when {
        draft.title.isBlank() -> TaskError.BlankTitle
        draft.courseId == null || courseDao.getById(draft.courseId) == null -> TaskError.NoCourse
        else -> null
    }

    private fun cleanNotes(notes: String?): String? = notes?.trim()?.ifEmpty { null }
}
