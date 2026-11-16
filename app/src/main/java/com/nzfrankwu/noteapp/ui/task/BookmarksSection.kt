package com.nzfrankwu.noteapp.ui.task

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nzfrankwu.noteapp.R
import com.nzfrankwu.noteapp.domain.BookmarkEntry
import com.nzfrankwu.noteapp.domain.BookmarkGroup
import com.nzfrankwu.noteapp.domain.Bookmarks
import com.nzfrankwu.noteapp.ui.theme.Dimens

/**
 * The task's page bookmarks (PHASE-4.md 4.8): grouped by notebook, in page order, each "p. 12"
 * and its note. Tap one to open the notebook there; ✎ adds or changes the note; the bin deletes it.
 * A bookmark made with the bookmark pen (PHASE-5.md FB5) shows its picture ([pictureFile]).
 */
@Composable
fun BookmarksSection(
    groups: List<BookmarkGroup>,
    onOpen: (BookmarkEntry) -> Unit,
    onEditNote: (BookmarkEntry, String) -> Unit,
    onDelete: (BookmarkEntry) -> Unit,
    pictureFile: (BookmarkEntry) -> java.io.File? = { null },
) {
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp).testTag("bookmarks")) {
        Text("Bookmarks", style = MaterialTheme.typography.titleMedium)
        if (groups.isEmpty()) {
            Text(
                "No bookmarks yet. In a notebook, tap the bookmark button to bookmark a page for this task.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        groups.forEach { group ->
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 6.dp)) {
                    Row(Modifier.padding(horizontal = Dimens.TilePadding, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.ic_note), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Text(
                            group.notebookTitle,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 10.dp),
                        )
                    }
                    group.bookmarks.forEachIndexed { i, entry ->
                        if (i > 0) HorizontalDivider(Modifier.padding(horizontal = Dimens.TilePadding))
                        BookmarkRow(entry, pictureFile, onOpen = { onOpen(entry) }, onEdit = { editing = entry.id }, onDelete = { onDelete(entry) })
                    }
                }
            }
        }
    }
    val entry = editing?.let { id -> groups.flatMap { it.bookmarks }.firstOrNull { it.id == id } }
    if (entry != null) {
        NoteDialog(
            pageNumber = entry.pageNumber,
            initial = entry.note.orEmpty(),
            onSave = { onEditNote(entry, it); editing = null },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun BookmarkRow(entry: BookmarkEntry, pictureFile: (BookmarkEntry) -> java.io.File?, onOpen: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(start = Dimens.TilePadding, end = 4.dp, top = 2.dp, bottom = 2.dp)
            .testTag("bookmark-${entry.pageNumber}-${entry.note.orEmpty()}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            Bookmarks.pageLabel(entry.pageNumber),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.widthIn(min = 52.dp),
        )
        if (entry.image != null) BookmarkPicture(entry, pictureFile)
        Text(
            entry.note ?: if (entry.image != null) "" else "No note",
            style = MaterialTheme.typography.bodyMedium,
            color = if (entry.note == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        IconButton(onClick = onEdit) {
            Icon(painterResource(R.drawable.ic_edit), contentDescription = "Edit the note on ${Bookmarks.pageLabel(entry.pageNumber)}", modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = onDelete) {
            Icon(painterResource(R.drawable.ic_delete), contentDescription = "Delete the bookmark on ${Bookmarks.pageLabel(entry.pageNumber)}", modifier = Modifier.size(20.dp))
        }
    }
}

/** The bookmark pen's picture (FB5), small, loaded off the main thread. */
@Composable
private fun BookmarkPicture(entry: BookmarkEntry, pictureFile: (BookmarkEntry) -> java.io.File?) {
    val picture by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, entry.id, entry.image) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            pictureFile(entry)?.let { com.nzfrankwu.noteapp.data.ImageImport.loadForScreen(it, PICTURE_PX) }
        }?.asImageBitmap()
    }
    Box(
        Modifier
            .padding(vertical = 4.dp)
            .size(width = 120.dp, height = 72.dp)
            .background(androidx.compose.ui.graphics.Color.White, MaterialTheme.shapes.small)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
            .testTag("bookmark-picture-${entry.pageNumber}"),
        contentAlignment = Alignment.Center,
    ) {
        picture?.let {
            androidx.compose.foundation.Image(
                it,
                contentDescription = "Bookmarked part of ${Bookmarks.pageLabel(entry.pageNumber)}",
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier.padding(2.dp),
            )
        }
    }
}

/** Bookmark pictures in the list are loaded at most this big (pixels). */
private const val PICTURE_PX = 360

@Composable
private fun NoteDialog(pageNumber: Int, initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Note on ${Bookmarks.pageLabel(pageNumber)}") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(Bookmarks.MAX_NOTE_LENGTH) },
                label = { Text("Note (optional)") },
                maxLines = 5,
                modifier = Modifier.fillMaxWidth().testTag("bookmark-note-edit"),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }, modifier = Modifier.testTag("bookmark-note-save")) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
