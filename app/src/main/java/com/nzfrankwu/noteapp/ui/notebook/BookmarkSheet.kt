package com.nzfrankwu.noteapp.ui.notebook

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nzfrankwu.noteapp.data.TaskChoices
import com.nzfrankwu.noteapp.data.db.TaskWithCourse
import com.nzfrankwu.noteapp.domain.Bookmarks
import com.nzfrankwu.noteapp.ui.task.PickerHeading
import java.time.Instant
import java.time.ZoneId

/**
 * Saving a bookmark (PHASE-4.md 4.5): which task it's for (the notebook's linked tasks first,
 * then this course's, then "Other tasks"), an optional note, and Save. [note] prefills the note
 * (the selected PDF text, 4.7). A task that isn't linked yet gets linked when it's saved.
 */
@Composable
fun BookmarkSheet(
    pageNumber: Int,
    linked: List<TaskWithCourse>,
    others: TaskChoices,
    note: String,
    now: Instant,
    zone: ZoneId,
    onSave: (TaskWithCourse, String) -> Unit,
    onCancel: () -> Unit,
) {
    var picked by rememberSaveable { mutableStateOf(linked.singleOrNull()?.task?.id) }
    var text by rememberSaveable { mutableStateOf(note) }
    val all = linked + others.inCourse + others.others
    val task = all.firstOrNull { it.task.id == picked }
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(0.9f).testTag("bookmark-sheet"),
        ) {
            Column(Modifier.padding(24.dp)) {
                Text("Bookmark ${Bookmarks.pageLabel(pageNumber)} for a task", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.size(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(Bookmarks.MAX_NOTE_LENGTH) },
                    label = { Text("Note (optional)") },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth().testTag("bookmark-note"),
                )
                Spacer(Modifier.size(8.dp))
                Box(Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 320.dp)) {
                    LazyColumn {
                        if (linked.isNotEmpty()) {
                            item(key = "linked-h") { PickerHeading("Linked to this notebook", 0) }
                            items(linked, key = { "l-" + it.task.id }) { t -> Choice(t, t.task.id == picked, now, zone) { picked = t.task.id } }
                        }
                        val rest = TaskChoices(others.inCourse, others.others)
                        if (rest.inCourse.isNotEmpty()) {
                            item(key = "course-h") { PickerHeading("This course", 0) }
                            items(rest.inCourse, key = { "c-" + it.task.id }) { t -> Choice(t, t.task.id == picked, now, zone) { picked = t.task.id } }
                        }
                        if (rest.others.isNotEmpty()) {
                            item(key = "other-h") { PickerHeading("Other tasks", 0) }
                            items(rest.others, key = { "o-" + it.task.id }) { t -> Choice(t, t.task.id == picked, now, zone) { picked = t.task.id } }
                        }
                        if (all.isEmpty()) item(key = "empty") { com.nzfrankwu.noteapp.ui.task.PickerEmpty("No tasks yet. Add one from a course folder first.") }
                    }
                }
                HorizontalDivider(Modifier.padding(top = 8.dp))
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onCancel, modifier = Modifier.testTag("bookmark-cancel")) { Text("Cancel") }
                    TextButton(
                        onClick = { task?.let { onSave(it, text) } },
                        enabled = task != null,
                        modifier = Modifier.testTag("bookmark-save"),
                    ) { Text("Save") }
                }
            }
        }
    }
}

@Composable
private fun Choice(item: TaskWithCourse, selected: Boolean, now: Instant, zone: ZoneId, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        TaskPickRow(item, now, zone, onClick)
    }
}
