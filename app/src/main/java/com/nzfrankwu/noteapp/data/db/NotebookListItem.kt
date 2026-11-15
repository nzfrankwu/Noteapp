package com.nzfrankwu.noteapp.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded

/** A notebook plus how many pages it has, and whether it's an imported PDF, for notebook tiles. */
data class NotebookListItem(
    @Embedded val notebook: Notebook,
    @ColumnInfo(name = "page_count") val pageCount: Int,
    /** True if any of its pages shows a page of an imported PDF (Phase 3). */
    @ColumnInfo(name = "is_pdf") val isPdf: Boolean = false,
)
