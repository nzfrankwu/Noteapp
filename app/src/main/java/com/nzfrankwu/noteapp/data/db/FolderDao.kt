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

    @Query("SELECT * FROM folders")
    suspend fun getAll(): List<Folder>

    /** All folders, A–Z by name. Updates automatically whenever folders change. */

    /** Top-level folders (the home screen's) A–Z, each with its linked course's code. Updates live. */
    @Query(
        """
        SELECT folders.*, courses.code AS course_code, courses.archived_at AS course_archived_at
        FROM folders LEFT JOIN courses ON courses.id = folders.course_id
        WHERE folders.parent_id IS NULL
        ORDER BY folders.name COLLATE NOCASE
        """,
    )
    fun observeTopLevelWithCourse(): Flow<List<FolderListItem>>

    /** Every folder (subfolders too) A–Z, updating live (for working out where a subfolder sits). */
    @Query("SELECT * FROM folders ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Folder>>

    /** The folders inside [parentId], A–Z, each with how many things it holds. Updates live. */
    @Query(
        """
        SELECT folders.*,
            (SELECT COUNT(*) FROM folders AS inner_folders WHERE inner_folders.parent_id = folders.id)
            + (SELECT COUNT(*) FROM notebooks WHERE notebooks.folder_id = folders.id AND notebooks.deleted_at IS NULL)
            AS item_count
        FROM folders
        WHERE folders.parent_id = :parentId
        ORDER BY folders.name COLLATE NOCASE
        """,
    )
    fun observeChildren(parentId: String): Flow<List<SubfolderListItem>>

    /** How many folders are directly inside [parentId]. */
    @Query("SELECT COUNT(*) FROM folders WHERE parent_id = :parentId")
    suspend fun countChildren(parentId: String): Int

    /** One folder with its course code, updating live (null once deleted). */
    @Query(
        """
        SELECT folders.*, courses.code AS course_code, courses.archived_at AS course_archived_at
        FROM folders LEFT JOIN courses ON courses.id = folders.course_id
        WHERE folders.id = :id
        """,
    )
    fun observeWithCourse(id: String): Flow<FolderListItem?>
}
