package com.nzfrankwu.noteapp.ui.notebook

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nzfrankwu.noteapp.data.LinkChoices
import com.nzfrankwu.noteapp.data.TaskChoices
import com.nzfrankwu.noteapp.data.TaskFilter
import com.nzfrankwu.noteapp.data.db.TaskWithCourse
import com.nzfrankwu.noteapp.domain.Bookmarks
import com.nzfrankwu.noteapp.ui.components.PickerEmpty
import com.nzfrankwu.noteapp.ui.components.PickerHeading
import java.time.Instant
import java.time.ZoneId

/**
 * Saving a bookmark (PHASE-4.md 4.5, PHASE-5.md FB1, FB3, FB5): which task it's for (the
 * notebook's linked tasks first, then a search with course and due-date filters), an optional
 * note, and Save. [note] prefills the note (the selected PDF text, 4.7); [picture] is the part of
 * the page the bookmark pen selected (FB5), shown above the note. A task that isn't linked yet
 * gets linked when it's saved. The picked task has a check mark.
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
    picture: ImageBitmap? = null,
) {
    var picked by rememberSaveable { mutableStateOf(linked.singleOrNull()?.task?.id) }
    var text by rememberSaveable { mutableStateOf(note) }
    var filter by remember { mutableStateOf(TaskFilter()) }
    val all = linked + others.all
    val task = all.firstOrNull { it.task.id == picked }
    val linkedShown = linked.filter { LinkChoices.matches(it, filter.query) }
    val shown = LinkChoices.filter(others, filter, now)
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(0.9f).testTag("bookmark-sheet"),
        ) {
            Column(Modifier.padding(24.dp)) {
                Text("Bookmark ${Bookmarks.pageLabel(pageNumber)} for a task", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.size(12.dp))
                if (picture != null) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 160.dp)
                            .background(Color.White, RoundedCornerShape(8.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                            .padding(4.dp),
                    ) {
                        Image(
                            picture,
                            contentDescription = "The bookmarked part of the page",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 152.dp).testTag("bookmark-picture"),
                        )
                    }
                    Spacer(Modifier.size(8.dp))
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(Bookmarks.MAX_NOTE_LENGTH) },
                    label = { Text(if (picture != null) "Add text (optional)" else "Note (optional)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth().testTag("bookmark-note"),
                )
                Spacer(Modifier.size(8.dp))
                Box(Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 360.dp)) {
                    LazyColumn {
                        if (linkedShown.isNotEmpty()) {
                            item(key = "linked-h") { PickerHeading("Linked to this notebook", 0) }
                            items(linkedShown, key = { "l-" + it.task.id }) { t ->
                                TaskPickRow(t, now, zone, selected = t.task.id == picked) { picked = t.task.id }
                            }
                        }
                        item(key = "search") { TaskSearch(filter, { filter = it }, others, Modifier.padding(top = 8.dp)) }
                        if (shown.isEmpty()) {
                            item(key = "empty") {
                                PickerEmpty(if (all.isEmpty()) "No tasks yet. Add one from a course folder first." else "No tasks match.")
                            }
                        }
                        items(shown, key = { "o-" + it.task.id }) { t ->
                            TaskPickRow(t, now, zone, selected = t.task.id == picked) { picked = t.task.id }
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(top = 8.dp))
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                    if (task != null) {
                        Text(
                            "For: ${task.task.title}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.weight(1f).padding(top = 12.dp).testTag("bookmark-for"),
                        )
                    }
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
