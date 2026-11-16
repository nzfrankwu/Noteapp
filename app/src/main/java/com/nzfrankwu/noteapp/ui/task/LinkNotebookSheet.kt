package com.nzfrankwu.noteapp.ui.task

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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nzfrankwu.noteapp.R
import com.nzfrankwu.noteapp.data.FolderRow
import com.nzfrankwu.noteapp.data.NotebookChoices
import com.nzfrankwu.noteapp.data.NotebookGroup

/**
 * "Link notebook" from a task (PHASE-4.md): ＋ New notebook for this task, then the notebooks in
 * the task's course folder (and its subfolders), under folder headings, then "Other folders ▸" with every other folder's.
 * Notebooks already linked aren't offered.
 */
@Composable
fun LinkNotebookSheet(
    choices: NotebookChoices,
    onPick: (notebookId: String) -> Unit,
    onNewNotebook: () -> Unit,
    onCancel: () -> Unit,
) {
    var showOthers by remember { mutableStateOf(choices.inCourse.isEmpty()) }
    PickerDialog(title = "Link a notebook", testTag = "link-notebook-sheet", onCancel = onCancel) {
        item(key = "new") {
            PickerRow(R.drawable.ic_add, "New notebook for this task", depth = 0, emphasised = true, testTag = "new-notebook-for-task", onClick = onNewNotebook)
            HorizontalDivider()
        }
        if (choices.inCourse.isNotEmpty()) {
            notebookGroups(choices.inCourse, onPick, prefix = "c")
        }
        if (choices.others.isNotEmpty()) {
            item(key = "others") {
                Row(
                    Modifier.fillMaxWidth().clickable { showOthers = !showOthers }.padding(vertical = 12.dp, horizontal = 4.dp).testTag("other-folders"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Other folders", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Icon(
                        painterResource(R.drawable.ic_chevron_right),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp).rotate(if (showOthers) 90f else 0f),
                    )
                }
            }
            if (showOthers) notebookGroups(choices.others, onPick, prefix = "o")
        }
        if (choices.isEmpty) {
            item(key = "empty") { PickerEmpty("No other notebooks yet. Make a new one for this task.") }
        }
    }
}

/** Where a new notebook goes, when the task's course has no folder. */
@Composable
fun PickFolderSheet(folders: List<FolderRow>, onPick: (folderId: String) -> Unit, onCancel: () -> Unit) {
    PickerDialog(title = "Put the new notebook in…", testTag = "pick-folder-sheet", onCancel = onCancel) {
        if (folders.isEmpty()) item { PickerEmpty("No folders yet. Make one on the home screen first.") }
        items(folders, key = { it.folder.id }) { row ->
            PickerRow(R.drawable.ic_folder, row.folder.name, row.depth, testTag = "pick-folder-${row.folder.name}") { onPick(row.folder.id) }
        }
    }
}

private fun LazyListScope.notebookGroups(groups: List<NotebookGroup>, onPick: (String) -> Unit, prefix: String) {
    for (group in groups) {
        item(key = "$prefix-h-${group.folder.folder.id}") { PickerHeading(group.folder.folder.name, group.folder.depth) }
        items(group.notebooks, key = { "$prefix-n-${it.id}" }) { nb ->
            PickerRow(R.drawable.ic_note, nb.title, group.folder.depth + 1, testTag = "pick-notebook-${nb.title}") { onPick(nb.id) }
        }
    }
}

/** The dialog frame the pickers share (the same look as "Add to Noteapp"). */
@Composable
fun PickerDialog(title: String, testTag: String, onCancel: () -> Unit, subtitle: String? = null, content: LazyListScope.() -> Unit) {
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(0.9f).testTag(testTag),
        ) {
            Column(Modifier.padding(24.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                if (subtitle != null) {
                    Spacer(Modifier.size(4.dp))
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.size(12.dp))
                Box(Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 440.dp)) {
                    LazyColumn(content = content)
                }
                HorizontalDivider(Modifier.padding(top = 8.dp))
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onCancel, modifier = Modifier.testTag("picker-cancel")) { Text("Cancel") }
                }
            }
        }
    }
}

@Composable
fun PickerHeading(text: String, depth: Int) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = (depth * 20).dp + 4.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
fun PickerRow(
    icon: Int,
    label: String,
    depth: Int,
    testTag: String? = null,
    emphasised: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .padding(start = (depth * 20).dp + 4.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (emphasised) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) trailing()
    }
}

@Composable
fun PickerEmpty(text: String) {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
