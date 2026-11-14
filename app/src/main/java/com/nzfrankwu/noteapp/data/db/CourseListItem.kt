package com.nzfrankwu.noteapp.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded

/** A course plus the folder linked to it (if any), for Settings → Courses. */
data class CourseListItem(
    @Embedded val course: Course,
    @ColumnInfo(name = "folder_id") val folderId: String?,
    @ColumnInfo(name = "folder_name") val folderName: String?,
) {
    val isArchived: Boolean get() = course.archivedAt != null
}
