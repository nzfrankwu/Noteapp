package com.nzfrankwu.noteapp.ui.folder

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nzfrankwu.noteapp.data.db.Notebook
import com.nzfrankwu.noteapp.data.db.NotebookListItem
import com.nzfrankwu.noteapp.data.db.SubfolderListItem
import com.nzfrankwu.noteapp.domain.PageTemplate
import com.nzfrankwu.noteapp.ui.components.NotebookTile
import com.nzfrankwu.noteapp.ui.components.SubfolderTile
import com.nzfrankwu.noteapp.ui.components.TileAction
import com.nzfrankwu.noteapp.ui.components.drawPaperPattern
import com.nzfrankwu.noteapp.ui.theme.Dimens
import com.nzfrankwu.noteapp.ui.theme.LocalCourseColors
import com.nzfrankwu.noteapp.ui.theme.PaperLineStrong
import com.nzfrankwu.noteapp.ui.theme.PaperWhite
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One tile in a folder's grid: a subfolder or a notebook. */
sealed interface LibraryItem {
    val key: String
    data class Sub(val item: SubfolderListItem) : LibraryItem { override val key = "folder-${item.folder.id}" }
    data class Book(val item: NotebookListItem) : LibraryItem { override val key = "notebook-${item.notebook.id}" }
}

/** The tiles to show for [filter]: subfolders first (A–Z), then notebooks (latest first). */
fun FolderContents.itemsFor(filter: LibraryFilter): List<LibraryItem> {
    val subs = if (filter == LibraryFilter.NOTEBOOKS) emptyList() else subfolders.map { LibraryItem.Sub(it) }
    val books = if (filter == LibraryFilter.FOLDERS) emptyList() else notebooks.map { LibraryItem.Book(it) }
    return subs + books
}

/** How many tiles fit across [width]. */
fun libraryColumns(width: Dp): Int =
    ((width + Dimens.ItemGap) / (Dimens.LibraryTileMinWidth + Dimens.ItemGap)).toInt().coerceAtLeast(2)

/** "12 Nov · 3 pages" (the year is added when it isn't this year). */
fun notebookSubtitle(item: NotebookListItem, now: Instant, zone: ZoneId): String {
    val date = item.notebook.updatedAt.atZone(zone)
    val pattern = if (date.year == now.atZone(zone).year) "d MMM" else "d MMM yyyy"
    val pages = if (item.pageCount == 1) "1 page" else "${item.pageCount} pages"
    return "${date.format(DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH))} · $pages"
}

fun subfolderSubtitle(item: SubfolderListItem): String = when (item.itemCount) {
    0 -> "Empty"
    1 -> "1 item"
    else -> "${item.itemCount} items"
}

/** One row of the grid. Short rows keep their tiles the same width as full ones. */
@Composable
fun LibraryRow(
    items: List<LibraryItem>,
    columns: Int,
    now: Instant,
    zone: ZoneId,
    onOpenFolder: (String) -> Unit,
    onOpenNotebook: (String) -> Unit,
    onRenameFolder: (SubfolderListItem) -> Unit,
    onDeleteFolder: (SubfolderListItem) -> Unit,
    onRenameNotebook: (NotebookListItem) -> Unit,
    onDeleteNotebook: (NotebookListItem) -> Unit,
    /** The course the folder belongs to: subfolder icons take its colour. */
    courseCode: String? = null,
) {
    val folderTint = courseCode?.let { LocalCourseColors.current.of(it).accent }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.ItemGap)) {
        items.forEach { entry ->
            val tileModifier = Modifier.weight(1f)
            when (entry) {
                is LibraryItem.Sub -> SubfolderTile(
                    name = entry.item.folder.name,
                    subtitle = subfolderSubtitle(entry.item),
                    onOpen = { onOpenFolder(entry.item.folder.id) },
                    tint = folderTint,
                    actions = listOf(
                        TileAction("Rename") { onRenameFolder(entry.item) },
                        TileAction("Delete", destructive = true) { onDeleteFolder(entry.item) },
                    ),
                    modifier = tileModifier,
                )
                is LibraryItem.Book -> NotebookTile(
                    title = entry.item.notebook.title,
                    subtitle = notebookSubtitle(entry.item, now, zone),
                    template = entry.item.notebook.template,
                    pageWidth = entry.item.notebook.pageWidth,
                    pageHeight = entry.item.notebook.pageHeight,
                    onOpen = { onOpenNotebook(entry.item.notebook.id) },
                    actions = listOf(
                        TileAction("Rename") { onRenameNotebook(entry.item) },
                        TileAction("Delete", destructive = true) { onDeleteNotebook(entry.item) },
                    ),
                    modifier = tileModifier,
                )
            }
        }
        repeat(columns - items.size) { Spacer(Modifier.weight(1f)) }
    }
}

/** "New notebook": a name, and the paper (Lined, Blank, Grid or Dot grid). */
@Composable
fun NewNotebookDialog(
    title: String,
    template: PageTemplate,
    error: String?,
    onTitleChange: (String) -> Unit,
    onPickTemplate: (PageTemplate) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New notebook") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = onTitleChange,
                    label = { Text("Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("notebook-name-field"),
                )
                Text("Paper", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PageTemplate.entries.forEach { option ->
                        TemplateChoice(option, selected = option == template, onClick = { onPickTemplate(option) })
                    }
                }
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = title.isNotBlank(), modifier = Modifier.testTag("dialog-confirm")) {
                Text("Create")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun TemplateChoice(template: PageTemplate, selected: Boolean, onClick: () -> Unit) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .testTag("template-${template.key}")
            .padding(4.dp),
    ) {
        Box(
            Modifier
                .width(56.dp)
                .height(56.dp * Notebook.A4_HEIGHT / Notebook.A4_WIDTH)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(PaperWhite)
                .border(if (selected) 2.dp else 1.dp, borderColor, MaterialTheme.shapes.extraSmall),
        ) {
            Canvas(Modifier.size(56.dp, 56.dp * Notebook.A4_HEIGHT / Notebook.A4_WIDTH)) {
                drawPaperPattern(template, Notebook.A4_WIDTH, Notebook.A4_HEIGHT, PaperLineStrong)
            }
        }
        Text(
            template.label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
