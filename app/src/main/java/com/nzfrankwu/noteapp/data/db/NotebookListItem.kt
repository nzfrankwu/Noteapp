package com.nzfrankwu.noteapp.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded

/** A notebook plus how many pages it has, for notebook tiles. */
data class NotebookListItem(
    @Embedded val notebook: Notebook,
    @ColumnInfo(name = "page_count") val pageCount: Int,
)
