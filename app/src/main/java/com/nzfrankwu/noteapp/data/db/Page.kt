package com.nzfrankwu.noteapp.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nzfrankwu.noteapp.domain.PageSize
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
 * Database version 5 (Phase 3) added the page's own size and, for a page of an imported PDF, which
 * PDF page it shows. A page with no size of its own uses its notebook's.
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
    /**
     * This page's own size in page units (1/72 inch), or null to use the notebook's. Added in
     * version 5. [width] and [height] are both set or both null.
     */
    val width: Float? = null,
    val height: Float? = null,
    /**
     * For a page of an imported PDF: the PDF's file name inside the notebook's folder (see
     * NotebookFiles), or null for a plain page. Added in version 5.
     */
    @ColumnInfo(name = "pdf_file") val pdfFile: String? = null,
    /** Which page of [pdfFile] this is (0 = the first), or null for a plain page. Added in version 5. */
    @ColumnInfo(name = "pdf_page") val pdfPage: Int? = null,
) {
    /** True if this page shows a page of an imported PDF. */
    val isPdf: Boolean get() = pdfFile != null

    /** This page's size: its own, or else [notebook]'s. */
    fun size(notebook: Notebook): PageSize = PageSize(width ?: notebook.pageWidth, height ?: notebook.pageHeight)
}
