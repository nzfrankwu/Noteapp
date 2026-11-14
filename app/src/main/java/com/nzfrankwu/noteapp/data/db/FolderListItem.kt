package com.nzfrankwu.noteapp.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import java.time.Instant

/** A folder plus its linked course's code, for showing folder tiles on the home screen. */
data class FolderListItem(
    @Embedded val folder: Folder,
    @ColumnInfo(name = "course_code") val courseCode: String?,
    /** When the linked course was archived, or null. */
    @ColumnInfo(name = "course_archived_at") val courseArchivedAt: Instant? = null,
) {
    /** True when the folder's course is archived: the folder moves to "Archived" on Home. */
    val isArchived: Boolean get() = courseArchivedAt != null
}
