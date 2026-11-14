package com.nzfrankwu.noteapp.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseDao {
    @Insert
    suspend fun insert(course: Course)

    @Update
    suspend fun update(course: Course)

    /**
     * Inserts the course, or updates it in place if a course with the same [Course.id] exists.
     *
     * Never use @Insert(onConflict = REPLACE) for courses: REPLACE deletes the old row and
     * inserts a new one, and that delete would silently unlink the course's folder and tasks.
     */
    @Upsert
    suspend fun upsert(course: Course)

    @Delete
    suspend fun delete(course: Course)

    @Query("SELECT * FROM courses WHERE id = :id")
    suspend fun getById(id: String): Course?

    @Query("SELECT * FROM courses WHERE canvas_course_id = :canvasCourseId")
    suspend fun getByCanvasId(canvasCourseId: Long): Course?

    /** All courses, A–Z by code. Updates automatically whenever courses change. */
    @Query("SELECT * FROM courses ORDER BY code COLLATE NOCASE")
    fun observeAll(): Flow<List<Course>>
}
