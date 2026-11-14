package com.nzfrankwu.noteapp.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded

/** A folder plus its linked course's code, for showing folder tiles on the home screen. */
data class FolderListItem(
    @Embedded val folder: Folder,
    @ColumnInfo(name = "course_code") val courseCode: String?,
)
