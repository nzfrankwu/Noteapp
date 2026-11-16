package com.nzfrankwu.noteapp.ui.notebook

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nzfrankwu.noteapp.R
import com.nzfrankwu.noteapp.data.db.NotebookListItem
import com.nzfrankwu.noteapp.ui.components.LocalThumbnails
import com.nzfrankwu.noteapp.ui.components.drawPaperPattern
import com.nzfrankwu.noteapp.ui.components.rememberThumbnail
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The recent notebooks panel (PHASE-5.md 5.7, like `design-reference/recent doc tab.jpg`): the
 * notebooks opened most recently, newest first, as tiles. Tap one to open it in the active pane;
 * ⋮ → "Open beside this one" splits the screen; hold one and drag it onto the pages to split
 * there (5.9). The notebooks already open ([openIds]) are outlined and can't be dragged.
 */
@Composable
fun RecentPanel(
    items: List<NotebookListItem>,
    openIds: Set<String>,
    canSplit: Boolean,
    onOpen: (String) -> Unit,
    onOpenBeside: (String) -> Unit,
    onClose: () -> Unit,
    onDragStart: (NotebookListItem, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.width(RecentPanelWidth).fillMaxHeight().testTag("recent-panel"),
        shape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.padding(top = 8.dp)) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Recent", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose, modifier = Modifier.testTag("recent-close")) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = "Close the recent notebooks")
                }
            }
            Text(
                if (canSplit) "Hold a notebook and drag it onto the page to open both side by side."
                else "Make the window bigger to open two notebooks at once.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            if (items.isEmpty()) {
                Text(
                    "Notebooks you open show here.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(items, key = { it.notebook.id }) { item ->
                    RecentTile(
                        item = item,
                        open = item.notebook.id in openIds,
                        canSplit = canSplit,
                        onOpen = { onOpen(item.notebook.id) },
                        onOpenBeside = { onOpenBeside(item.notebook.id) },
                        onDragStart = { onDragStart(item, it) },
                        onDrag = onDrag,
                        onDragEnd = onDragEnd,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentTile(
    item: NotebookListItem,
    open: Boolean,
    canSplit: Boolean,
    onOpen: () -> Unit,
    onOpenBeside: () -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
) {
    val nb = item.notebook
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var menu by remember { mutableStateOf(false) }
    val latestDrag by rememberUpdatedState(onDrag)
    val thumbnail by rememberThumbnail(LocalThumbnails.current, nb.id, nb.updatedAt.toEpochMilli())
    Column(
        Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords = it }
            .then(
                if (open || !canSplit) {
                    Modifier
                } else {
                    // Hold, then drag the notebook out onto the pages (PHASE-5.md 5.9).
                    Modifier.pointerInput(nb.id) {
                        var at = Offset.Zero
                        detectDragGesturesAfterLongPress(
                            onDragStart = { local ->
                                at = (coords?.positionInRoot() ?: Offset.Zero) + local
                                onDragStart(at)
                            },
                            onDragEnd = onDragEnd,
                            onDragCancel = onDragEnd,
                        ) { change, amount ->
                            change.consume()
                            at += amount
                            latestDrag(at)
                        }
                    }
                },
            )
            .clickable(onClick = onOpen)
            .semantics { contentDescription = if (open) "${nb.title} (open)" else nb.title }
            .testTag("recent-${nb.title}"),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio((nb.pageWidth / nb.pageHeight).coerceIn(0.5f, 1.6f))
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White)
                .border(if (open) 3.dp else 1.dp, if (open) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.12f), RoundedCornerShape(6.dp)),
        ) {
            val picture = thumbnail
            if (picture != null) {
                Image(picture, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth())
            } else {
                androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().aspectRatio((nb.pageWidth / nb.pageHeight).coerceIn(0.5f, 1.6f))) {
                    drawPaperPattern(nb.template, nb.pageWidth, nb.pageHeight, Color(0xFFC9D1DC))
                }
            }
        }
        Spacer(Modifier.size(4.dp))
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(nb.title, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${DateShort.format(nb.updatedAt.atZone(ZoneId.systemDefault()))} · ${item.pageCount} ${if (item.pageCount == 1) "page" else "pages"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp).testTag("recent-menu-${nb.title}")) {
                    Icon(painterResource(R.drawable.ic_more_vert), contentDescription = "More for ${nb.title}", modifier = Modifier.size(18.dp))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Open beside this one") },
                        enabled = canSplit && !open,
                        onClick = { menu = false; onOpenBeside() },
                        modifier = Modifier.testTag("open-beside"),
                    )
                }
            }
        }
    }
}

private val DateShort: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")

/** The panel's width. */
val RecentPanelWidth = 300.dp
