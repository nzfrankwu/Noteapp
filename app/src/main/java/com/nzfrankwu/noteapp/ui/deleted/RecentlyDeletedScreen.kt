package com.nzfrankwu.noteapp.ui.deleted

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nzfrankwu.noteapp.R
import com.nzfrankwu.noteapp.data.FolderRepository
import com.nzfrankwu.noteapp.data.FolderRow
import com.nzfrankwu.noteapp.data.NotebookPurge
import com.nzfrankwu.noteapp.data.NotebookRepository
import com.nzfrankwu.noteapp.data.NotebookResult
import com.nzfrankwu.noteapp.data.ShareTargets
import com.nzfrankwu.noteapp.data.db.DeletedNotebook
import com.nzfrankwu.noteapp.ui.components.EmptyState
import com.nzfrankwu.noteapp.ui.components.LocalThumbnails
import com.nzfrankwu.noteapp.ui.components.NotebookTile
import com.nzfrankwu.noteapp.ui.components.RoundIconButton
import com.nzfrankwu.noteapp.ui.components.TileAction
import com.nzfrankwu.noteapp.ui.components.rememberThumbnail
import com.nzfrankwu.noteapp.ui.task.PickFolderSheet
import com.nzfrankwu.noteapp.ui.theme.Dimens
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Recently deleted (PHASE-6.md 6.4): restore a deleted notebook, or delete it for good. */
class RecentlyDeletedViewModel(
    private val notebooks: NotebookRepository,
    folders: FolderRepository,
    private val purge: NotebookPurge,
) : ViewModel() {
    /** Null while loading. */
    val items: StateFlow<List<DeletedNotebook>?> =
        notebooks.observeDeleted().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val folderTree: StateFlow<List<FolderRow>> =
        folders.observeAllFolders().map { ShareTargets.folderTree(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** A notebook whose folder is gone: the folder picker is open for it. */
    private val _needsFolder = MutableStateFlow<DeletedNotebook?>(null)
    val needsFolder: StateFlow<DeletedNotebook?> = _needsFolder.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun restore(item: DeletedNotebook) {
        viewModelScope.launch {
            if (notebooks.hasFolder(item.notebook.id)) {
                report(notebooks.restoreFromDeleted(item.notebook.id), item.notebook.title, item.folderName)
            } else {
                _needsFolder.value = item
            }
        }
    }

    fun restoreInto(folderId: String) {
        val item = _needsFolder.value ?: return
        _needsFolder.value = null
        viewModelScope.launch {
            val name = folderTree.value.firstOrNull { it.folder.id == folderId }?.folder?.name
            report(notebooks.restoreFromDeleted(item.notebook.id, folderId), item.notebook.title, name)
        }
    }

    fun cancelRestore() {
        _needsFolder.value = null
    }

    fun deleteForGood(item: DeletedNotebook) {
        viewModelScope.launch {
            _message.value = if (purge.deleteForGood(item.notebook.id)) "Deleted “${item.notebook.title}” for good." else "Couldn't delete “${item.notebook.title}”."
        }
    }

    fun messageShown() {
        _message.value = null
    }

    private fun report(result: NotebookResult, title: String, folder: String?) {
        _message.value = when (result) {
            is NotebookResult.Success -> if (folder != null) "Restored “$title” to $folder." else "Restored “$title”."
            is NotebookResult.Failure -> "Couldn't restore “$title”."
        }
    }
}

/** When it was deleted and how long it stays: "Deleted 3 days ago · 27 days left". */
object DeletedText {
    fun subtitle(deletedAt: Instant, now: Instant, zone: ZoneId): String {
        val days = ChronoUnit.DAYS.between(deletedAt.atZone(zone).toLocalDate(), now.atZone(zone).toLocalDate())
        val ago = when {
            days <= 0 -> "Deleted today"
            days == 1L -> "Deleted yesterday"
            else -> "Deleted $days days ago"
        }
        return "$ago · ${left(deletedAt, now)}"
    }

    /** "It's deleted for good in 27 days." (the restore dialog). */
    fun untilGone(deletedAt: Instant, now: Instant): String {
        val days = daysLeft(deletedAt, now)
        return if (days >= 2) "It's deleted for good in $days days." else "It's deleted for good within a day."
    }

    /** "27 days left", or "Less than a day left" at the end. */
    fun left(deletedAt: Instant, now: Instant): String {
        val days = daysLeft(deletedAt, now)
        return if (days >= 2) "$days days left" else "Less than a day left"
    }

    /** Whole days left before the 30-day purge, rounded up (30 right after deleting). */
    private fun daysLeft(deletedAt: Instant, now: Instant): Long {
        val millis = Duration.between(now, deletedAt.plus(NotebookPurge.KEEP)).toMillis()
        val day = Duration.ofDays(1).toMillis()
        return if (millis <= 0) 0 else (millis + day - 1) / day
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentlyDeletedScreen(
    viewModel: RecentlyDeletedViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    clock: Clock = Clock.systemDefaultZone(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val needsFolder by viewModel.needsFolder.collectAsStateWithLifecycle()
    val folderTree by viewModel.folderTree.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var choosing by remember { mutableStateOf<DeletedNotebook?>(null) }
    var confirmDelete by remember { mutableStateOf<DeletedNotebook?>(null) }
    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbar.showSnackbar(text)
        viewModel.messageShown() // only after it's shown (see HANDOFF gotchas)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Recently deleted") },
                navigationIcon = {
                    RoundIconButton(icon = R.drawable.ic_arrow_back, contentDescription = "Back", onClick = onBack, modifier = Modifier.padding(start = 8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        val list = items
        when {
            list == null -> Unit
            list.isEmpty() -> EmptyState(
                title = "Nothing here",
                message = "Notebooks and PDFs you delete stay here for 30 days, so you can bring them back.",
                modifier = Modifier.padding(padding).fillMaxSize().testTag("deleted-empty"),
            )
            else -> {
                val now = clock.instant()
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(Dimens.LibraryTileMinWidth),
                    contentPadding = PaddingValues(Dimens.ScreenPadding),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.ItemGap),
                    verticalArrangement = Arrangement.spacedBy(Dimens.ItemGap),
                    modifier = Modifier.padding(padding).fillMaxSize().testTag("deleted-grid"),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            "Notebooks and PDFs you delete stay here for 30 days, then they're deleted for good. Tap one to restore it.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(list, key = { it.notebook.id }) { item ->
                        val nb = item.notebook
                        NotebookTile(
                            thumbnail = rememberThumbnail(LocalThumbnails.current, nb.id, nb.updatedAt.toEpochMilli()).value,
                            title = nb.title,
                            subtitle = nb.deletedAt?.let { DeletedText.left(it, now) }.orEmpty(),
                            template = nb.template,
                            pageWidth = nb.pageWidth,
                            pageHeight = nb.pageHeight,
                            isPdf = item.isPdf,
                            onOpen = { choosing = item },
                            actions = listOf(
                                TileAction("Restore") { viewModel.restore(item) },
                                TileAction("Delete now", destructive = true) { confirmDelete = item },
                            ),
                        )
                    }
                }
            }
        }
    }

    choosing?.let { item ->
        val pages = if (item.pageCount == 1) "1 page" else "${item.pageCount} pages"
        val from = item.folderName?.let { "from $it" } ?: "(its folder was deleted too)"
        AlertDialog(
            onDismissRequest = { choosing = null },
            title = { Text(item.notebook.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("$pages, $from.", style = MaterialTheme.typography.bodyMedium)
                    item.notebook.deletedAt?.let {
                        Text(
                            "${DeletedText.subtitle(it, clock.instant(), clock.zone).substringBefore(" ·")}. ${DeletedText.untilGone(it, clock.instant())}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { choosing = null; viewModel.restore(item) }, modifier = Modifier.testTag("deleted-restore")) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = { choosing = null; confirmDelete = item }, modifier = Modifier.testTag("deleted-delete-now")) {
                    Text("Delete now", color = MaterialTheme.colorScheme.error)
                }
            },
        )
    }
    confirmDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete “${item.notebook.title}” for good?") },
            text = { Text("Its pages, pictures and PDF are deleted from this tablet. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = null; viewModel.deleteForGood(item) }, modifier = Modifier.testTag("deleted-confirm-delete")) {
                    Text("Delete for good", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
    needsFolder?.let {
        PickFolderSheet(
            folders = folderTree,
            onPick = viewModel::restoreInto,
            onCancel = viewModel::cancelRestore,
            title = "Restore “${it.notebook.title}” to…",
        )
    }
}
