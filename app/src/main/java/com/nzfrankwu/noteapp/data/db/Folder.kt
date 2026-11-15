package com.nzfrankwu.noteapp.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * A folder. Top-level folders are on the home screen; a top-level folder can be linked to one
 * course, and then shows that course's tasks. Folders hold notebooks and other folders
 * (subfolders, Phase 2), which belong to the top folder's course and can't link their own.
 *
 * Tasks belong to the course, not the folder, so deleting a folder never deletes tasks.
 * If the linked course is deleted, the folder stays and just becomes unlinked (SET_NULL).
 *
 * Fields added in later database versions go at the END (see Migrations.kt).
 */
@Entity(
    tableName = "folders",
    foreignKeys = [
        ForeignKey(
            entity = Course::class,
            parentColumns = ["id"],
            childColumns = ["course_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = Folder::class,
            parentColumns = ["id"],
            childColumns = ["parent_id"],
            // Folders that hold anything can't be deleted (FolderRepository refuses). If one ever
            // is, its subfolders are kept and move to the top level: never deleted with it.
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        // A course is linked to at most one folder. (Many folders can be unlinked.)
        Index(value = ["course_id"], unique = true),
        Index(value = ["parent_id"]),
    ],
)
data class Folder(
    /** Permanent random ID (UUID). */
    @PrimaryKey val id: String,
    val name: String,
    /** The linked course, or null if this folder isn't linked to a course. */
    @ColumnInfo(name = "course_id") val courseId: String?,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,

    // ---- Added in database version 4 ----
    /** The folder this one is inside, or null for a top-level folder (shown on the home screen). */
    @ColumnInfo(name = "parent_id") val parentId: String? = null,
)
