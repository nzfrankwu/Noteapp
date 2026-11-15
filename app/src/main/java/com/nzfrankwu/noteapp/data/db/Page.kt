package com.nzfrankwu.noteapp.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nzfrankwu.noteapp.domain.PageTemplate
import java.time.Instant

/**
 * One page of a [Notebook]. Added in database version 4 (Phase 2).
 *
 * The page's strokes are saved in a file named after [id] (see NotebookFiles), not here. This row
 * only says where the page sits in its notebook.
 *
 * Deleting a page only sets [deletedAt] and keeps its file, so it can be brought back.
 *
 * Fields added in later database versions go at the END (see Migrations.kt).
 */
@Entity(
    tableName = "pages",
    foreignKeys = [
        ForeignKey(
            entity = Notebook::class,
            parentColumns = ["id"],
            childColumns = ["notebook_id"],
            // Notebooks are never removed from the database before Phase 6's "Recently deleted"
            // empties them for good; then their pages go too.
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["notebook_id"])],
)
data class Page(
    /** Permanent random ID (UUID). Also the name of the page's stroke file. */
    @PrimaryKey val id: String,
    @ColumnInfo(name = "notebook_id") val notebookId: String,
    /** Order in the notebook: pages are shown by position (smallest first), then by [createdAt]. */
    val position: Int,
    /** This page's own pattern, or null to use the notebook's (per-page choice comes in Phase 7). */
    val template: PageTemplate? = null,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    /** Last time the page (including its strokes) changed. */
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
    /** When it was deleted (it's hidden, but its strokes are kept), or null. */
    @ColumnInfo(name = "deleted_at") val deletedAt: Instant? = null,
)
