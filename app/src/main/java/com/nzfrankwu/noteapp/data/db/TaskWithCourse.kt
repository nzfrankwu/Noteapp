package com.nzfrankwu.noteapp.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import java.time.Instant

/** A task plus its course's code (null if it has no course), for task cards and Due soon. */
data class TaskWithCourse(
    @Embedded val task: Task,
    @ColumnInfo(name = "course_code") val courseCode: String?,
    /** When the task's course was archived, or null (current course, or no course). */
    @ColumnInfo(name = "course_archived_at") val courseArchivedAt: Instant? = null,
) {
    /** True when the task's course is archived: hidden from Home, and no reminders. */
    val isCourseArchived: Boolean get() = courseArchivedAt != null
}
