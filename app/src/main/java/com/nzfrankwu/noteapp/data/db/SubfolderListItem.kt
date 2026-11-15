package com.nzfrankwu.noteapp.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded

/** A subfolder plus how many things are in it (subfolders and notebooks), for folder tiles. */
data class SubfolderListItem(
    @Embedded val folder: Folder,
    @ColumnInfo(name = "item_count") val itemCount: Int,
)
