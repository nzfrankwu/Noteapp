package com.nzfrankwu.noteapp.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded

/** A task plus its course's code (null if it has no course), for task cards and Due soon. */
data class TaskWithCourse(
    @Embedded val task: Task,
    @ColumnInfo(name = "course_code") val courseCode: String?,
)
