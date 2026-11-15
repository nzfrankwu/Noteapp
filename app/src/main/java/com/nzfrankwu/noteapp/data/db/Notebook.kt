package com.nzfrankwu.noteapp.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nzfrankwu.noteapp.domain.PageTemplate
import java.time.Instant

/**
 * A handwritten notebook in a folder (IDEA.md §2.5). Added in database version 4 (Phase 2).
 *
 * Its pages are rows in [Page]; each page's strokes live in their own file (see NotebookFiles),
 * not in the database, so a backup can upload only the pages that changed (IDEA.md §2.9).
 *
 * Deleting a notebook only sets [deletedAt]: it disappears from the folder but nothing is lost,
 * and "Recently deleted" (Phase 6) can bring it back.
 *
 * Fields added in later database versions go at the END (see Migrations.kt).
 */
@Entity(
    tableName = "notebooks",
    foreignKeys = [
        ForeignKey(
            entity = Folder::class,
            parentColumns = ["id"],
            childColumns = ["folder_id"],
            // Folders holding notebooks can't be deleted (FolderRepository refuses). If one ever
            // is, its notebooks are kept, just without a folder: never deleted with it.
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index(value = ["folder_id"])],
)
data class Notebook(
    /** Permanent random ID (UUID). Also the name of the notebook's folder of page files. */
    @PrimaryKey val id: String,
    /** The folder it's in. Null only if that folder was somehow deleted (see above). */
    @ColumnInfo(name = "folder_id") val folderId: String?,
    val title: String,
    /** The paper pattern of its pages. */
    val template: PageTemplate,
    /**
     * Page size in page units (1 unit = 1/72 inch, so A4 portrait is 595 × 842). Stroke positions
     * are saved in these units, so they don't depend on the screen or zoom.
     */
    @ColumnInfo(name = "page_width") val pageWidth: Float,
    @ColumnInfo(name = "page_height") val pageHeight: Float,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    /** Last time the notebook or any of its pages changed (Phase 6 backs up only changes). */
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
    /** When it was deleted (it's hidden, but kept), or null. */
    @ColumnInfo(name = "deleted_at") val deletedAt: Instant? = null,
) {
    companion object {
        /** A4 portrait in page units (1/72 inch). */
        const val A4_WIDTH = 595f
        const val A4_HEIGHT = 842f
    }
}
