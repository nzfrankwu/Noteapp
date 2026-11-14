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

    @Query("SELECT * FROM courses")
    suspend fun getAll(): List<Course>

    /** All courses, A–Z by code. Updates automatically whenever courses change. */
    @Query("SELECT * FROM courses ORDER BY code COLLATE NOCASE")
    fun observeAll(): Flow<List<Course>>

    /**
     * Current (not archived) Canvas courses that no folder is linked to yet (for "Link your
     * courses" and the "New from Canvas" prompt), A–Z by code.
     */
    @Query(
        "SELECT * FROM courses WHERE canvas_course_id IS NOT NULL AND archived_at IS NULL " +
            "AND id NOT IN (SELECT course_id FROM folders WHERE course_id IS NOT NULL) ORDER BY code COLLATE NOCASE",
    )
    fun observeCanvasCoursesWithoutFolder(): Flow<List<Course>>

    /** Every course with its folder (if any), A–Z by code, for Settings → Courses. Updates live. */
    @Query(
        "SELECT courses.*, folders.id AS folder_id, folders.name AS folder_name " +
            "FROM courses LEFT JOIN folders ON folders.course_id = courses.id ORDER BY courses.code COLLATE NOCASE",
    )
    fun observeAllWithFolder(): Flow<List<CourseListItem>>
}
