package com.nzfrankwu.noteapp.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nzfrankwu.noteapp.R
import com.nzfrankwu.noteapp.data.db.Folder
import com.nzfrankwu.noteapp.data.db.FolderListItem
import com.nzfrankwu.noteapp.ui.components.EmptyState
import com.nzfrankwu.noteapp.ui.components.FolderTile
import com.nzfrankwu.noteapp.ui.components.RoundIconButton
import com.nzfrankwu.noteapp.ui.theme.Dimens
import com.nzfrankwu.noteapp.ui.theme.NoteAppTheme
import java.time.Instant

/**
 * Below this width the home screen stacks Due soon above Folders (split-screen, small tablets);
 * at or above it they sit side by side. The OPPO Pad 3 is 800 dp wide in portrait and 1120 dp in
 * landscape, so it gets side by side in both orientations.
 */
val WideLayoutMinWidth = 720.dp

/** Due soon's share of the width in the side-by-side layout. */
private const val DueSoonWidthFraction = 0.4f

/** The home screen, connected to its [HomeViewModel]. */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenSettings: () -> Unit,
    onOpenFolder: (folderId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val dialog by viewModel.dialog.collectAsStateWithLifecycle()

    HomeContent(
        folders = folders,
        onOpenSettings = onOpenSettings,
        onNewFolder = viewModel::startNewFolder,
        onOpenFolder = onOpenFolder,
        onRenameFolder = viewModel::startRename,
        onDeleteFolder = viewModel::startDelete,
        modifier = modifier,
    )

    when (val d = dialog) {
        is FolderDialog.New -> FolderEditDialog(
            title = "New folder",
            name = d.name,
            onNameChange = viewModel::onNameChange,
            courseCode = d.courseCode,
            onCourseCodeChange = viewModel::onCourseCodeChange,
            error = d.error,
            confirmLabel = "Create",
            onConfirm = viewModel::confirmDialog,
            onDismiss = viewModel::dismissDialog,
        )
        is FolderDialog.Rename -> FolderEditDialog(
            title = "Rename folder",
            name = d.name,
            onNameChange = viewModel::onNameChange,
            courseCode = "",
            onCourseCodeChange = null,
            error = d.error,
            confirmLabel = "Save",
            onConfirm = viewModel::confirmDialog,
            onDismiss = viewModel::dismissDialog,
        )
        is FolderDialog.ConfirmDelete -> DeleteFolderDialog(
            name = d.name,
            courseCode = d.courseCode,
            onConfirm = viewModel::confirmDialog,
            onDismiss = viewModel::dismissDialog,
        )
        null -> Unit
    }
}

/** The home screen's layout, given plain data (used directly by previews and tests). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    folders: List<FolderListItem>?,
    onOpenSettings: () -> Unit,
    onNewFolder: () -> Unit,
    onOpenFolder: (folderId: String) -> Unit,
    onRenameFolder: (FolderListItem) -> Unit,
    onDeleteFolder: (FolderListItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    RoundIconButton(
                        icon = R.drawable.ic_settings,
                        contentDescription = "Settings",
                        onClick = onOpenSettings,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { innerPadding ->
        val folderActions = FolderActions(onNewFolder, onOpenFolder, onRenameFolder, onDeleteFolder)
        BoxWithConstraints(Modifier.padding(innerPadding).fillMaxSize()) {
            if (maxWidth >= WideLayoutMinWidth) {
                WideHome(folders, folderActions)
            } else {
                NarrowHome(folders, folderActions)
            }
        }
    }
}

private class FolderActions(
    val onNew: () -> Unit,
    val onOpen: (String) -> Unit,
    val onRename: (FolderListItem) -> Unit,
    val onDelete: (FolderListItem) -> Unit,
)

/** Side by side: Due soon panel on the left, folders on the right. */
@Composable
private fun WideHome(folders: List<FolderListItem>?, actions: FolderActions) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = Dimens.ScreenPadding, end = Dimens.ScreenPadding, bottom = Dimens.ScreenPadding)
            .testTag("home-wide"),
        horizontalArrangement = Arrangement.spacedBy(Dimens.PaneGap),
    ) {
        DueSoonPanel(
            fillHeight = true,
            modifier = Modifier.weight(DueSoonWidthFraction).fillMaxHeight(),
        )
        Column(Modifier.weight(1f - DueSoonWidthFraction).fillMaxHeight()) {
            FoldersHeader(onNewFolder = actions.onNew)
            when {
                folders == null -> Unit // still loading: show nothing rather than flash "empty"
                folders.isEmpty() -> NoFolders(Modifier.fillMaxWidth().weight(1f))
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(Dimens.FolderTileMinWidth),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.ItemGap),
                    verticalArrangement = Arrangement.spacedBy(Dimens.ItemGap),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(folders, key = { it.folder.id }) { item -> FolderTileFor(item, actions) }
                }
            }
        }
    }
}

/** Stacked: Due soon on top, then the folders, all scrolling together as one grid. */
@Composable
private fun NarrowHome(folders: List<FolderListItem>?, actions: FolderActions) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(Dimens.FolderTileMinWidth),
        horizontalArrangement = Arrangement.spacedBy(Dimens.ItemGap),
        verticalArrangement = Arrangement.spacedBy(Dimens.ItemGap),
        contentPadding = PaddingValues(start = Dimens.ScreenPadding, end = Dimens.ScreenPadding, bottom = Dimens.ScreenPadding),
        modifier = Modifier.fillMaxSize().testTag("home-narrow"),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            DueSoonPanel(
                fillHeight = false,
                modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.NarrowPanelMinHeight),
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            FoldersHeader(onNewFolder = actions.onNew, modifier = Modifier.padding(top = Dimens.PaneGap))
        }
        if (folders != null && folders.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { NoFolders(Modifier.fillMaxWidth()) }
        }
        items(folders.orEmpty(), key = { it.folder.id }) { item -> FolderTileFor(item, actions) }
    }
}

@Composable
private fun FolderTileFor(item: FolderListItem, actions: FolderActions) {
    FolderTile(
        name = item.folder.name,
        courseCode = item.courseCode,
        onOpen = { actions.onOpen(item.folder.id) },
        onRename = { actions.onRename(item) },
        onDelete = { actions.onDelete(item) },
    )
}

@Composable
private fun FoldersHeader(onNewFolder: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(bottom = Dimens.ItemGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionTitle("Folders", Modifier.padding(start = 4.dp))
        Spacer(Modifier.weight(1f))
        FilledTonalButton(
            onClick = onNewFolder,
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_add),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text("New folder")
        }
    }
}

@Composable
private fun NoFolders(modifier: Modifier = Modifier) {
    EmptyState(
        title = "No folders yet",
        message = "Tap “New folder” to make one for each of your courses.",
        modifier = modifier,
    )
}

/**
 * The "Due soon" list (empty until tasks arrive in F2/F3).
 * [fillHeight]: true when the panel has a fixed height (side by side), so the empty message can
 * be centred in it; false in the scrolling stacked layout, where there's no height to fill.
 */
@Composable
private fun DueSoonPanel(fillHeight: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(Dimens.PanelPadding)) {
            SectionTitle("Due soon")
            EmptyState(
                title = "Nothing due yet",
                message = "Tasks you add, and assignments from Canvas, will show up here.",
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (fillHeight) Modifier.weight(1f) else Modifier),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    )
}

private val previewFolders = listOf(
    FolderListItem(Folder("1", "Electromagnetics", "c1", Instant.EPOCH, Instant.EPOCH), "ELECTENG 204"),
    FolderListItem(Folder("2", "Circuits", "c2", Instant.EPOCH, Instant.EPOCH), "ELECTENG 292"),
    FolderListItem(Folder("3", "Personal", null, Instant.EPOCH, Instant.EPOCH), null),
)

@Preview(widthDp = 1120, heightDp = 800)
@Composable
private fun HomeWidePreview() {
    NoteAppTheme { HomeContent(previewFolders, {}, {}, {}, {}, {}) }
}

@Preview(widthDp = 560, heightDp = 800)
@Composable
private fun HomeNarrowPreview() {
    NoteAppTheme { HomeContent(previewFolders, {}, {}, {}, {}, {}) }
}
