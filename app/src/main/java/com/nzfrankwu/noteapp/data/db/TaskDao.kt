package com.nzfrankwu.noteapp.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.nzfrankwu.noteapp.domain.TaskSource
import kotlinx.coroutines.flow.Flow

/**
 * Database access for tasks. Lists come back unsorted on purpose: sorting rules (due date,
 * target date, overdue first...) live in plain Kotlin in domain/, where they're unit-tested.
 */
@Dao
interface TaskDao {
    @Insert
    suspend fun insert(task: Task)

    @Update
    suspend fun update(task: Task)

    @Delete
    suspend fun delete(task: Task)

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: String): Task?

    /** One task, updating live (null once it's deleted). */
    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observeById(id: String): Flow<Task?>

    @Query("SELECT * FROM tasks WHERE canvas_assignment_id = :canvasAssignmentId")
    suspend fun getByCanvasAssignmentId(canvasAssignmentId: Long): Task?

    /** One course's tasks from one source (e.g. its Canvas tasks, for marking removed ones). */
    @Query("SELECT * FROM tasks WHERE course_id = :courseId AND source = :source")
    suspend fun getForCourseAndSource(courseId: String, source: TaskSource): List<Task>

    /** Every task (for the home screen's Due soon list). */
    @Query("SELECT * FROM tasks")
    fun observeAll(): Flow<List<Task>>

    /** The tasks of one course (for a course folder). */
    @Query("SELECT * FROM tasks WHERE course_id = :courseId")
    fun observeForCourse(courseId: String): Flow<List<Task>>

    /** Every task with its course code, archived courses included. Updates live. */
    @Query(
        "SELECT tasks.*, courses.code AS course_code, courses.archived_at AS course_archived_at " +
            "FROM tasks LEFT JOIN courses ON courses.id = tasks.course_id",
    )
    fun observeAllWithCourse(): Flow<List<TaskWithCourse>>

    /**
     * The tasks Home shows (Due soon, Calendar, Timeline): every task except those of archived
     * courses. Tasks without a course are included. Updates live.
     */
    @Query(
        "SELECT tasks.*, courses.code AS course_code, courses.archived_at AS course_archived_at " +
            "FROM tasks LEFT JOIN courses ON courses.id = tasks.course_id WHERE courses.archived_at IS NULL",
    )
    fun observeActiveWithCourse(): Flow<List<TaskWithCourse>>

    /** One task with its course code, updating live (null once it's deleted). */
    @Query(
        "SELECT tasks.*, courses.code AS course_code, courses.archived_at AS course_archived_at " +
            "FROM tasks LEFT JOIN courses ON courses.id = tasks.course_id WHERE tasks.id = :id",
    )
    fun observeWithCourse(id: String): Flow<TaskWithCourse?>
}
