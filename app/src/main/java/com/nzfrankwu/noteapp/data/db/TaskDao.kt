package com.nzfrankwu.noteapp.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
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

    /** Every task (for the home screen's Due soon list). */
    @Query("SELECT * FROM tasks")
    fun observeAll(): Flow<List<Task>>

    /** The tasks of one course (for a course folder). */
    @Query("SELECT * FROM tasks WHERE course_id = :courseId")
    fun observeForCourse(courseId: String): Flow<List<Task>>
}
