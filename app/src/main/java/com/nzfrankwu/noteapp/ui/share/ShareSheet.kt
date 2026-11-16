package com.nzfrankwu.noteapp.ui.share

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nzfrankwu.noteapp.R
import com.nzfrankwu.noteapp.data.FolderRow
import com.nzfrankwu.noteapp.data.NotebookGroup
import com.nzfrankwu.noteapp.data.SharedFiles
import com.nzfrankwu.noteapp.data.SharedKind
import com.nzfrankwu.noteapp.ui.components.SegmentedTabs

/** Where pictures shared into Noteapp go. */
enum class PictureTarget(val label: String) {
    NOTEBOOK("Add to a notebook"),
    NEW_NOTEBOOK("New notebook in a folder"),
}

/**
 * "Add to Noteapp" (PHASE-3.md 3.8, layout OK'd by the user on 16 Nov 2026): where a PDF or
 * pictures shared from another app go.
 *  - A PDF: "Import “…” into…" and the folders (subfolders indented). Tap one.
 *  - Pictures: tabs Add to a notebook (notebooks under folder headings) | New notebook in a
 *    folder (folders). Tap one.
 * Nothing is saved until a folder or notebook is tapped; Cancel drops the share.
 */
@Composable
fun ShareSheet(
    shared: SharedFiles,
    folders: List<FolderRow>,
    notebooks: List<NotebookGroup>,
    busy: Boolean,
    onPickFolder: (folderId: String) -> Unit,
    onPickNotebook: (notebookId: String) -> Unit,
    onCancel: () -> Unit,
) {
    var target by remember { mutableStateOf(PictureTarget.NOTEBOOK) }
    Dialog(onDismissRequest = { if (!busy) onCancel() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(0.9f).testTag("share-sheet"),
        ) {
            Column(Modifier.padding(24.dp)) {
                Text("Add to Noteapp", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.size(8.dp))
                val count = shared.files.size
                Text(
                    when (shared.kind) {
                        SharedKind.PDF -> "Import “${shared.files.firstOrNull()?.displayName ?: "PDF"}” into…"
                        SharedKind.PICTURES -> if (count == 1) "1 picture" else "$count pictures (one page each)"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (shared.kind == SharedKind.PICTURES) {
                    Spacer(Modifier.size(16.dp))
                    SegmentedTabs(
                        options = PictureTarget.entries,
                        selected = target,
                        label = { it.label },
                        testTag = { "share-target-${it.name.lowercase()}" },
                        onSelect = { target = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.size(12.dp))
                Box(Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 420.dp)) {
                    val showNotebooks = shared.kind == SharedKind.PICTURES && target == PictureTarget.NOTEBOOK
                    when {
                        busy -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                        showNotebooks && notebooks.isEmpty() -> Empty("No notebooks yet. Use “New notebook in a folder”.")
                        !showNotebooks && folders.isEmpty() -> Empty("No folders yet. Make one on the home screen first.")
                        showNotebooks -> LazyColumn(Modifier.testTag("share-notebooks")) {
                            for (group in notebooks) {
                                item(key = "h-" + group.folder.folder.id) {
                                    Text(
                                        group.folder.folder.name,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(start = (group.folder.depth * 20).dp, top = 12.dp, bottom = 4.dp),
                                    )
                                }
                                items(group.notebooks, key = { "n-" + it.id }) { nb ->
                                    TargetRow(R.drawable.ic_note, nb.title, group.folder.depth + 1) { onPickNotebook(nb.id) }
                                }
                            }
                        }
                        else -> LazyColumn(Modifier.testTag("share-folders")) {
                            items(folders, key = { it.folder.id }) { row ->
                                TargetRow(R.drawable.ic_folder, row.folder.name, row.depth) { onPickFolder(row.folder.id) }
                            }
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(top = 8.dp))
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onCancel, enabled = !busy, modifier = Modifier.testTag("share-cancel")) { Text("Cancel") }
                }
            }
        }
    }
}

@Composable
private fun TargetRow(icon: Int, label: String, depth: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = (depth * 20).dp + 4.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Empty(text: String) {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
