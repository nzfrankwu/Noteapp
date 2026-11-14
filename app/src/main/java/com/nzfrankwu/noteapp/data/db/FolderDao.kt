package com.nzfrankwu.noteapp.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Insert
    suspend fun insert(folder: Folder)

    @Update
    suspend fun update(folder: Folder)

    @Delete
    suspend fun delete(folder: Folder)

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getById(id: String): Folder?

    /** One folder, updating live (null once it's deleted). */
    @Query("SELECT * FROM folders WHERE id = :id")
    fun observeById(id: String): Flow<Folder?>

    /** The folder linked to a course, if any. */
    @Query("SELECT * FROM folders WHERE course_id = :courseId")
    suspend fun getByCourseId(courseId: String): Folder?

    /** All folders, A–Z by name. Updates automatically whenever folders change. */
    @Query("SELECT * FROM folders ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Folder>>

    /** All folders A–Z, each with its linked course's code (if any). Updates live. */
    @Query(
        """
        SELECT folders.*, courses.code AS course_code
        FROM folders LEFT JOIN courses ON courses.id = folders.course_id
        ORDER BY folders.name COLLATE NOCASE
        """,
    )
    fun observeAllWithCourse(): Flow<List<FolderListItem>>
}
