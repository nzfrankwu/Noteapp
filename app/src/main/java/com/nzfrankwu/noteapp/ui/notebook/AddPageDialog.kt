package com.nzfrankwu.noteapp.ui.notebook

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nzfrankwu.noteapp.R
import com.nzfrankwu.noteapp.domain.PageTemplate
import com.nzfrankwu.noteapp.ui.components.drawPaperPattern
import com.nzfrankwu.noteapp.ui.theme.PaperLine
import com.nzfrankwu.noteapp.ui.theme.PaperWhite

/** What the "Add Page" dialog asks for. */
sealed interface AddPageChoice {
    data class Blank(val template: PageTemplate) : AddPageChoice
    data object Photos : AddPageChoice
    data object Camera : AddPageChoice
    data object PdfPages : AddPageChoice
    data object WholePdf : AddPageChoice
}

/**
 * "Add Page" (feedback F34, like Notein's, from the user's screenshot): Cancel · Add Page · Add
 * across the top, the page styles to pick from, then ways to add pages from elsewhere. Pages go
 * after the page on screen.
 */
@Composable
fun AddPageDialog(current: PageTemplate, onChoice: (AddPageChoice) -> Unit, onDismiss: () -> Unit) {
    var template by remember { mutableStateOf(current) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(0.9f).testTag("add-page-dialog"),
        ) {
            Column(Modifier.padding(16.dp)) {
                Box(Modifier.fillMaxWidth()) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface),
                        modifier = Modifier.align(Alignment.CenterStart),
                    ) { Text("Cancel") }
                    Text("Add Page", style = MaterialTheme.typography.titleLarge, modifier = Modifier.align(Alignment.Center))
                    Button(
                        onClick = { onChoice(AddPageChoice.Blank(template)) },
                        modifier = Modifier.align(Alignment.CenterEnd).testTag("add-page-add"),
                    ) { Text("Add") }
                }
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    for (t in PageTemplate.entries) {
                        Column(
                            Modifier.padding(horizontal = 8.dp).clickable { template = t }.semantics {
                                contentDescription = t.label
                                selected = t == template
                            },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            val shape = RoundedCornerShape(4.dp)
                            Canvas(
                                Modifier.width(96.dp).aspectRatio(595f / 842f).clip(shape).background(PaperWhite)
                                    .border(if (t == template) 3.dp else 1.dp, if (t == template) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape),
                            ) { drawPaperPattern(t, 595f, 842f, PaperLine) }
                            Spacer(Modifier.height(8.dp))
                            Text(t.label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                    Column {
                        ChoiceRow(R.drawable.ic_photo_library, "Import from Photo Library", "add-page-photos") { onChoice(AddPageChoice.Photos) }
                        HorizontalDivider()
                        ChoiceRow(R.drawable.ic_photo_camera, "Add from Camera", "add-page-camera") { onChoice(AddPageChoice.Camera) }
                        HorizontalDivider()
                        ChoiceRow(R.drawable.ic_note, "Add Page from PDF", "add-page-pdf-pages") { onChoice(AddPageChoice.PdfPages) }
                        HorizontalDivider()
                        ChoiceRow(R.drawable.ic_picture_as_pdf, "Add the entire PDF", "add-page-whole-pdf") { onChoice(AddPageChoice.WholePdf) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceRow(icon: Int, label: String, tag: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 18.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null)
        Spacer(Modifier.width(20.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * "Add Page from PDF": the PDF's pages as small pictures; tap to pick (in order of the PDF), then
 * Add. [thumbnail] draws page i.
 */
@Composable
fun PdfPagePicker(
    pageCount: Int,
    aspect: (Int) -> Float,
    thumbnail: suspend (Int, Int) -> ImageBitmap?,
    onAdd: (List<Int>) -> Unit,
    onDismiss: () -> Unit,
) {
    val picked = remember { mutableStateListOf<Int>() }
    val thumbPx = with(LocalDensity.current) { 140.dp.roundToPx() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(0.9f).testTag("pdf-page-picker"),
        ) {
            Column(Modifier.padding(16.dp)) {
                Box(Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterStart)) { Text("Cancel") }
                    Text("Choose pages", style = MaterialTheme.typography.titleLarge, modifier = Modifier.align(Alignment.Center))
                    Button(
                        onClick = { onAdd(picked.sorted()) },
                        enabled = picked.isNotEmpty(),
                        modifier = Modifier.align(Alignment.CenterEnd).testTag("pdf-pages-add"),
                    ) { Text(if (picked.size > 1) "Add ${picked.size}" else "Add") }
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = {
                        if (picked.size == pageCount) picked.clear() else { picked.clear(); picked.addAll(0 until pageCount) }
                    }) { Text(if (picked.size == pageCount) "Select none" else "Select all") }
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(120.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                ) {
                    items((0 until pageCount).toList(), key = { it }) { i ->
                        val image by produceState<ImageBitmap?>(null, i) { value = thumbnail(i, thumbPx) }
                        val isPicked = i in picked
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val shape = RoundedCornerShape(6.dp)
                            Box(
                                Modifier.fillMaxWidth().aspectRatio(aspect(i)).clip(shape).background(PaperWhite)
                                    .border(if (isPicked) 3.dp else 1.dp, if (isPicked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
                                    .clickable { if (isPicked) picked.remove(i) else picked.add(i) }
                                    .semantics {
                                        contentDescription = "Page ${i + 1}"
                                        selected = isPicked
                                    },
                            ) {
                                image?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
                                if (isPicked) {
                                    Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                                }
                            }
                            Text("${i + 1}", style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
    }
}
