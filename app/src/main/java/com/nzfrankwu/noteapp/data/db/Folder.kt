package com.nzfrankwu.noteapp.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * A folder on the home screen. It can be linked to one course, and then shows that course's
 * tasks (notebooks arrive in Phase 2).
 *
 * Tasks belong to the course, not the folder, so deleting a folder never deletes tasks.
 * If the linked course is deleted, the folder stays and just becomes unlinked (SET_NULL).
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
    ],
    indices = [
        // A course is linked to at most one folder. (Many folders can be unlinked.)
        Index(value = ["course_id"], unique = true),
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
)
