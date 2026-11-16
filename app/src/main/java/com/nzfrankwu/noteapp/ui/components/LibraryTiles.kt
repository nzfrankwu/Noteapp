package com.nzfrankwu.noteapp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nzfrankwu.noteapp.R
import com.nzfrankwu.noteapp.domain.PageTemplate
import com.nzfrankwu.noteapp.ui.theme.Dimens
import com.nzfrankwu.noteapp.ui.theme.PaperLine
import com.nzfrankwu.noteapp.ui.theme.PaperWhite

/** One action in a tile's ⋮ menu. */
data class TileAction(val label: String, val destructive: Boolean = false, val onClick: () -> Unit)

/**
 * A notebook in a folder's grid: a picture of its first page, its title, and "date · N pages".
 * Tap opens it; ⋮ (or a long press) shows [actions]. Until thumbnails exist (step 2.11), or when
 * [thumbnail] is null, the page's paper pattern is drawn instead.
 */
@Composable
fun NotebookTile(
    title: String,
    subtitle: String,
    template: PageTemplate,
    pageWidth: Float,
    pageHeight: Float,
    onOpen: () -> Unit,
    actions: List<TileAction>,
    modifier: Modifier = Modifier,
    thumbnail: ImageBitmap? = null,
    /** An imported PDF: a small "PDF" tag sits on its picture. */
    isPdf: Boolean = false,
) {
    LibraryTile(
        title = title,
        subtitle = subtitle,
        onOpen = onOpen,
        actions = actions,
        modifier = modifier,
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .aspectRatio(pageWidth / pageHeight)
                .shadow(2.dp, MaterialTheme.shapes.extraSmall)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(PaperWhite)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.extraSmall),
        ) {
            if (thumbnail != null) {
                androidx.compose.foundation.Image(thumbnail, contentDescription = null, modifier = Modifier.fillMaxSize())
            } else if (!isPdf) {
                Canvas(Modifier.fillMaxSize()) { drawPaperPattern(template, pageWidth, pageHeight, PaperLine) }
            }
            if (isPdf) {
                Text(
                    "PDF",
                    style = MaterialTheme.typography.labelSmall,
                    color = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier
                        .align(androidx.compose.ui.Alignment.BottomStart)
                        .padding(4.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(com.nzfrankwu.noteapp.ui.theme.Red40)
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                        .testTag("pdf-tag"),
                )
            }
        }
    }
}

/** A folder inside a folder: a big folder icon, its name, and how many things it holds. */
@Composable
fun SubfolderTile(
    name: String,
    subtitle: String,
    onOpen: () -> Unit,
    actions: List<TileAction>,
    modifier: Modifier = Modifier,
    /** The icon's colour (the course's colour), or null for the app's blue. */
    tint: androidx.compose.ui.graphics.Color? = null,
) {
    LibraryTile(title = name, subtitle = subtitle, onOpen = onOpen, actions = actions, modifier = modifier) {
        Icon(
            painter = painterResource(R.drawable.ic_folder),
            contentDescription = null,
            tint = tint ?: MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(96.dp),
        )
    }
}

/** The shared tile frame: picture area on top, then title and subtitle, and a ⋮ menu. */
@Composable
private fun LibraryTile(
    title: String,
    subtitle: String,
    onOpen: () -> Unit,
    actions: List<TileAction>,
    modifier: Modifier = Modifier,
    picture: @Composable () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(onClick = onOpen, onLongClick = { menuOpen = true })
            .testTag("tile-$title"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Box {
            Column(Modifier.fillMaxWidth().padding(Dimens.TilePadding), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height(Dimens.LibraryTileThumbHeight), contentAlignment = Alignment.Center) {
                    picture()
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (actions.isNotEmpty()) {
                Box(Modifier.align(Alignment.TopEnd)) {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_more_vert),
                            contentDescription = "Options for $title",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        actions.forEach { action ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        action.label,
                                        color = if (action.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                    )
                                },
                                onClick = {
                                    menuOpen = false
                                    action.onClick()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
