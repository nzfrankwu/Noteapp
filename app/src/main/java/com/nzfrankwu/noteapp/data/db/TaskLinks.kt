package com.nzfrankwu.noteapp.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nzfrankwu.noteapp.domain.PageSpot
import java.time.Instant

/**
 * A notebook linked to a task (IDEA.md §2.4). Added in database version 6 (Phase 4).
 *
 * A task can have many notebooks and a notebook many tasks; each pair is stored once.
 * If the task or the notebook row is ever removed for good, the link goes with it. (Deleting a
 * task is a hard delete with Undo, which puts its links back; notebooks are only hidden.)
 *
 * Fields added in later database versions go at the END (see Migrations.kt).
 */
@Entity(
    tableName = "task_notebook_links",
    primaryKeys = ["task_id", "notebook_id"],
    foreignKeys = [
        ForeignKey(entity = Task::class, parentColumns = ["id"], childColumns = ["task_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Notebook::class, parentColumns = ["id"], childColumns = ["notebook_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["notebook_id"])],
)
data class TaskNotebookLink(
    @ColumnInfo(name = "task_id") val taskId: String,
    @ColumnInfo(name = "notebook_id") val notebookId: String,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
)

/**
 * A page bookmarked for a task, with an optional quick note (IDEA.md §2.4). Added in database
 * version 6 (Phase 4).
 *
 * It points at a page by ID, not by number: the page number shown is the page's place in its
 * notebook now, so it stays right when pages are added or deleted before it.
 *
 * The spot ([spotLeft], [spotTop], [spotWidth], [spotHeight], in page units, 1/72 inch) is the part
 * of the page it was made from (a lasso selection or selected PDF text); all four are null for a
 * bookmark of the whole page.
 *
 * Fields added in later database versions go at the END (see Migrations.kt).
 */
@Entity(
    tableName = "task_bookmarks",
    foreignKeys = [
        ForeignKey(entity = Task::class, parentColumns = ["id"], childColumns = ["task_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Notebook::class, parentColumns = ["id"], childColumns = ["notebook_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Page::class, parentColumns = ["id"], childColumns = ["page_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["task_id"]), Index(value = ["notebook_id"]), Index(value = ["page_id"])],
)
data class TaskBookmark(
    /** Permanent random ID (UUID). */
    @PrimaryKey val id: String,
    @ColumnInfo(name = "task_id") val taskId: String,
    @ColumnInfo(name = "notebook_id") val notebookId: String,
    @ColumnInfo(name = "page_id") val pageId: String,
    /** The quick note, or null. */
    val note: String?,
    @ColumnInfo(name = "spot_left") val spotLeft: Float? = null,
    @ColumnInfo(name = "spot_top") val spotTop: Float? = null,
    @ColumnInfo(name = "spot_width") val spotWidth: Float? = null,
    @ColumnInfo(name = "spot_height") val spotHeight: Float? = null,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
) {
    /** The spot on the page, or null for the whole page. */
    fun spot(): PageSpot? =
        if (spotLeft != null && spotTop != null && spotWidth != null && spotHeight != null) {
            PageSpot(spotLeft, spotTop, spotWidth, spotHeight)
        } else {
            null
        }
}

/** A notebook linked to a task, for the notebook chips on task cards. */
data class LinkedNotebook(
    @ColumnInfo(name = "task_id") val taskId: String,
    @ColumnInfo(name = "notebook_id") val notebookId: String,
    val title: String,
)

/** A bookmark with where it is: its notebook's title and the page's number (1 = the first). */
data class BookmarkWithPage(
    @Embedded val bookmark: TaskBookmark,
    @ColumnInfo(name = "notebook_title") val notebookTitle: String,
    @ColumnInfo(name = "page_number") val pageNumber: Int,
)
