package com.nzfrankwu.noteapp.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * A course, e.g. "ELECTENG 204". Tasks belong to a course; a folder can be linked to one.
 *
 * A course is either created by you (typing a course code on a folder) or imported from Canvas.
 * If you made "ELECTENG 204" yourself and Canvas later finds the same code, Canvas sync fills in
 * [canvasCourseId] on this same row, so your tasks and Canvas's end up in one course.
 */
@Entity(
    tableName = "courses",
    indices = [
        // Each Canvas course is stored at most once. (Many courses can have no Canvas ID.)
        Index(value = ["canvas_course_id"], unique = true),
    ],
)
data class Course(
    /** Permanent random ID (UUID). Never reused, so backups and restores can match rows. */
    @PrimaryKey val id: String,
    /** Course code as shown, e.g. "ELECTENG 204". */
    val code: String,
    /** Full name, e.g. "Engineering Electromagnetics", if known. */
    val name: String?,
    /** Canvas's own ID for this course, or null for a course you created yourself. */
    @ColumnInfo(name = "canvas_course_id") val canvasCourseId: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    /** Last time anything in this row changed (used by Phase 6 backup to upload only changes). */
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)
