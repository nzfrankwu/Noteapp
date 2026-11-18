package com.nzfrankwu.noteapp.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nzfrankwu.noteapp.R
import com.nzfrankwu.noteapp.data.FolderRepository
import com.nzfrankwu.noteapp.data.NotebookRepository
import com.nzfrankwu.noteapp.data.SearchIndex
import com.nzfrankwu.noteapp.data.TaskRepository
import com.nzfrankwu.noteapp.domain.TaskStatus
import com.nzfrankwu.noteapp.ui.components.CourseTag
import com.nzfrankwu.noteapp.ui.components.RoundIconButton
import com.nzfrankwu.noteapp.ui.components.TaskCard
import com.nzfrankwu.noteapp.ui.theme.Dimens
import com.nzfrankwu.noteapp.ui.theme.LocalCourseColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.ZoneId

/**
 * A page whose words match (PHASE-7.md 7.9): which notebook and page, and the text around the
 * match. [together] = the words are next to each other there, in order (listed first).
 */
data class NoteHit(
    val notebookId: String,
    val notebookTitle: String,
    val pageId: String,
    /** 1 = the first page. */
    val pageNumber: Int,
    val isPdf: Boolean,
    val snippet: String?,
    val together: Boolean = false,
    /** Found only in handwriting (7.10): shown with a pen icon, and opened zoomed to [spot]. */
    val handwritten: Boolean = false,
    /** Found only by forgiving matching (a letter or two off, messy handwriting): listed last. */
    val close: Boolean = false,
    /** Where to zoom when it opens (a handwriting line's band), or null. */
    val spot: com.nzfrankwu.noteapp.domain.PageSpot? = null,
)

/** The pages found for [query] (only shown while the box still says [query]). */
data class NoteHits(val query: String, val hits: List<NoteHit>)

/**
 * What a course folder's search covers: the folder and every folder inside it, the notebooks in
 * them, and the course's tasks (a subfolder's course is its top folder's).
 */
data class FolderScope(val name: String, val folderIds: Set<String>, val notebookIds: Set<String>, val courseId: String?) {
    companion object {
        fun of(
            folderId: String,
            folders: List<com.nzfrankwu.noteapp.data.db.Folder>,
            notebooks: List<com.nzfrankwu.noteapp.data.db.NotebookListItem>,
        ): FolderScope? {
            val byId = folders.associateBy { it.id }
            val root = byId[folderId] ?: return null
            val children = folders.groupBy { it.parentId }
            val ids = LinkedHashSet<String>()
            val todo = ArrayDeque(listOf(folderId))
            while (todo.isNotEmpty()) {
                val id = todo.removeFirst()
                if (ids.add(id)) children[id].orEmpty().forEach { todo.addLast(it.id) }
            }
            var top: com.nzfrankwu.noteapp.data.db.Folder = root
            val seen = HashSet<String>()
            while (top.parentId != null && seen.add(top.id)) top = byId[top.parentId] ?: break
            val notebookIds = notebooks.filter { it.notebook.folderId in ids }.mapTo(HashSet()) { it.notebook.id }
            return FolderScope(root.name, ids, notebookIds, top.courseId)
        }
    }

    /** Only what's in this folder. */
    fun filter(r: SearchIndex.Results) = SearchIndex.Results(
        tasks = if (courseId == null) emptyList() else r.tasks.filter { it.task.courseId == courseId },
        notebooks = r.notebooks.filter { it.item.notebook.id in notebookIds },
        folders = r.folders.filter { it.folder.id in folderIds && it.folder.id != folderIds.first() },
    )
}

/**
 * Search (PHASE-7.md 7.8–7.9, IDEA.md §2.11): names and tasks, and the words in the notes (PDF
 * text and text boxes, from [noteText]; handwriting joins in 7.10). With [notebookId] it's "Find
 * in this notebook": only that notebook's pages. With [folderId] it's a course folder's search:
 * that folder and the folders in it, and the course's tasks ([FolderScope]).
 */
@OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val tasks: TaskRepository,
    private val notebooks: NotebookRepository,
    folders: FolderRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val noteText: com.nzfrankwu.noteapp.data.search.NoteTextStore? = null,
    val notebookId: String? = null,
    /** A course folder to search in (with its subfolders), or null. */
    val folderId: String? = null,
    /** How far handwriting has been read, or null when it's all done (refreshed every few seconds). */
    handwritingStatus: (suspend () -> String?)? = null,
) : ViewModel() {
    val handwritingStatus: StateFlow<String?> = kotlinx.coroutines.flow.flow {
        if (handwritingStatus == null) return@flow
        while (true) {
            emit(runCatching { handwritingStatus() }.onFailure { android.util.Log.w("NoteappSearch", "Search status failed", it) }.getOrNull())
            kotlinx.coroutines.delay(3_000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The notebook being searched in ("Find in this notebook"), or null. */
    val scopeTitle: StateFlow<String?> = (notebookId?.let { id -> notebooks.observeNotebook(id).map { it?.title } } ?: flowOf(null))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    /** What's typed in the search box (kept across rotation by the view model). */
    val query = MutableStateFlow("")

    private val index: StateFlow<SearchIndex?> = combine(
        tasks.observeAllWithCourse(), notebooks.observeAllListItems(), folders.observeAllFolders(), tasks.observeCourses(),
    ) { t, n, f, c -> SearchIndex(t, n, f, c) }
        // Off the main thread: reading every task's description etc. froze the screen (18 Nov).
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The course folder being searched in, or null (everything, or one notebook). */
    val folderScope: StateFlow<FolderScope?> = (
        if (folderId == null) flowOf(null)
        else combine(folders.observeAllFolders(), notebooks.observeAllListItems()) { f, n -> FolderScope.of(folderId, f, n) }
            .flowOn(kotlinx.coroutines.Dispatchers.Default)
        ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val results: StateFlow<SearchIndex.Results> = combine(index, query, folderScope) { idx, q, scope ->
        val all = idx?.search(q, clock.instant()) ?: SearchIndex.Results()
        when {
            folderId == null -> all
            scope == null -> SearchIndex.Results() // not loaded yet
            else -> scope.filter(all)
        }
    }
        // Every key press searches again: never on the main thread (it made typing lag).
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchIndex.Results())

    /** Pages whose words match, by notebook (most recently changed first), then page. */
    val noteHits: StateFlow<NoteHits> = combine(query.debounce(150), folderScope) { q, scope -> q to scope }
        .mapLatest { (q, scope) ->
            if (folderId != null && scope == null) NoteHits("", emptyList()) else NoteHits(q.trim(), findInNotes(q, scope?.notebookIds))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NoteHits("", emptyList()))

    private suspend fun findInNotes(query: String, onlyNotebooks: Set<String>? = null): List<NoteHit> {
        val store = noteText ?: return emptyList()
        val words = SearchIndex.words(query)
        if (words.isEmpty()) return emptyList()
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val exact = store.search(words, notebookId, limit = MAX_NOTE_HITS, notebookIds = onlyNotebooks)
            // Messy handwriting: pages whose handwriting is a letter or two off every word.
            val exactIds = exact.mapTo(HashSet()) { it.pageId }
            val close = store.handwritten(notebookId, onlyNotebooks).filter { h ->
                h.pageId !in exactIds && com.nzfrankwu.noteapp.domain.FuzzyWords.matchesAll(
                    words, listOfNotNull(h.pdfText, h.boxText, h.handAlt).joinToString("\n"),
                )
            }.take(MAX_NOTE_HITS)
            val closeIds = close.mapTo(HashSet()) { it.pageId }
            val byNotebook = (exact + close).groupBy { it.notebookId }
            byNotebook.mapNotNull { (nbId, pageHits) ->
                val nb = notebooks.getNotebook(nbId)?.takeIf { it.deletedAt == null } ?: return@mapNotNull null
                val pages = notebooks.getPages(nbId)
                val number = pages.withIndex().associate { (i, p) -> p.id to i + 1 }
                nb to pageHits.mapNotNull { h ->
                    val n = number[h.pageId] ?: return@mapNotNull null
                    val typed = listOfNotNull(h.pdfText, h.boxText).joinToString("\n")
                    val inTyped = words.all { typed.contains(it, ignoreCase = true) }
                    val text = if (inTyped) typed else listOfNotNull(typed.ifEmpty { null }, h.handAlt ?: h.handText).joinToString("\n")
                    val page = pages.first { it.id == h.pageId }
                    val line = if (inTyped) -1 else handwritingLine(h, words)
                    NoteHit(
                        nbId, nb.title, h.pageId, n, h.pdfText != null,
                        // For handwriting: the most likely reading of the line found.
                        if (inTyped) SearchIndex.snippet(typed, words)
                        else h.handText?.split('\n')?.getOrNull(line) ?: SearchIndex.snippet(h.handText, words),
                        SearchIndex.hasPhrase(text, words),
                        handwritten = !inTyped,
                        close = h.pageId in closeIds,
                        spot = if (inTyped) null else handwritingSpot(h, line, page.size(nb).width),
                    )
                }.sortedBy { it.pageNumber }
            }.sortedByDescending { it.first.updatedAt }.flatMap { it.second }
                // Pages with the words together first, close (forgiving) matches last; otherwise
                // notebook by notebook, page by page.
                .sortedBy { if (it.close) 2 else if (it.together) 0 else 1 }
        }
    }

    /**
     * The handwriting line holding the search words: every word in one line's readings, else the
     * first word, counting forgiving matches too; -1 if none.
     */
    private fun handwritingLine(hit: com.nzfrankwu.noteapp.data.search.NoteTextStore.Hit, words: List<String>): Int {
        val lines = (hit.handAlt ?: hit.handText)?.split('\n') ?: return -1
        return lines.indexOfFirst { com.nzfrankwu.noteapp.domain.FuzzyWords.matchesAll(words, it) }
            .takeIf { it >= 0 } ?: lines.indexOfFirst { com.nzfrankwu.noteapp.domain.FuzzyWords.matchesAll(words.take(1), it) }
    }

    /** The band of handwriting line [index] (the whole page width), or null. */
    private fun handwritingSpot(hit: com.nzfrankwu.noteapp.data.search.NoteTextStore.Hit, index: Int, pageWidth: Float): com.nzfrankwu.noteapp.domain.PageSpot? {
        val bands = hit.handLines?.split(';')?.map { band ->
            band.split(',').mapNotNull { it.toFloatOrNull() }
        } ?: return null
        val band = bands.getOrNull(index)?.takeIf { it.size == 2 } ?: return null
        val pad = (band[1] - band[0]) * 2f
        return com.nzfrankwu.noteapp.domain.PageSpot(0f, (band[0] - pad).coerceAtLeast(0f), pageWidth, band[1] - band[0] + 2 * pad)
    }

    fun setStatus(taskId: String, status: TaskStatus) {
        viewModelScope.launch { tasks.setStatus(taskId, status) }
    }

    companion object {
        const val MAX_NOTE_HITS = 200
    }
}

/**
 * A search box at the top; results below in three groups: Tasks, Notebooks, Folders & courses
 * (the layout the user OK'd, 18 Nov 2026). One centred column, like Settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onBack: () -> Unit,
    onOpenTask: (String) -> Unit,
    onOpenNotebook: (String) -> Unit,
    onOpenFolder: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** A page whose words match: open it there, with [query] to show where (PHASE-7.md 7.9). */
    onOpenPage: (notebookId: String, pageId: String, query: String, spot: com.nzfrankwu.noteapp.domain.PageSpot?) -> Unit =
        { nb, _, _, _ -> onOpenNotebook(nb) },
    clock: Clock = Clock.systemDefaultZone(),
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val allResults by viewModel.results.collectAsStateWithLifecycle()
    val found by viewModel.noteHits.collectAsStateWithLifecycle()
    // Only the hits for what's in the box now: never the last query's while the next is looked up.
    val notesCurrent = found.query == query.trim()
    val noteHits = if (notesCurrent) found.hits else emptyList()
    val scopeTitle by viewModel.scopeTitle.collectAsStateWithLifecycle()
    val scoped = viewModel.notebookId != null
    val folderScope by viewModel.folderScope.collectAsStateWithLifecycle()
    val handStatus by viewModel.handwritingStatus.collectAsStateWithLifecycle()
    // Find in this notebook: only its pages.
    val results = if (scoped) SearchIndex.Results() else allResults
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    // The keyboard opens once the screen has slid in: both at once made the opening stutter.
    LaunchedEffect(Unit) {
        if (viewModel.query.value.isEmpty()) {
            kotlinx.coroutines.delay(KEYBOARD_DELAY_MS)
            focus.requestFocus()
        }
    }
    val now = clock.instant()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    // Centred over the results (the end padding balances the Back button).
                    Box(Modifier.fillMaxWidth().padding(end = 56.dp), contentAlignment = Alignment.Center) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { viewModel.query.value = it },
                        placeholder = {
                            Text(
                                when {
                                    scoped -> "Find in ${scopeTitle ?: "this notebook"}"
                                    viewModel.folderId != null -> "Search in ${folderScope?.name ?: "this folder"}"
                                    else -> "Search notes, notebooks and tasks"
                                },
                            )
                        },
                        singleLine = true,
                        leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { viewModel.query.value = ""; focus.requestFocus() }, modifier = Modifier.testTag("search-clear")) {
                                    Icon(painterResource(R.drawable.ic_close), "Clear")
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { focusManager.clearFocus() }),
                        shape = MaterialTheme.shapes.extraLarge,
                        modifier = Modifier
                            .widthIn(max = Dimens.ReadingMaxWidth)
                            .fillMaxWidth()
                            .focusRequester(focus)
                            .testTag("search-field"),
                    )
                    }
                },
                navigationIcon = {
                    RoundIconButton(R.drawable.ic_arrow_back, "Back", onBack, Modifier.padding(start = 8.dp, end = 8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { inner ->
        Box(Modifier.padding(inner).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            val words = SearchIndex.words(query)
            when {
                words.isEmpty() -> Column {
                    Hint(
                        when {
                            scoped -> "Type to find words in this notebook: PDF pages, text boxes and handwriting."
                            viewModel.folderId != null -> "Type to search this folder: the words in its notebooks (PDFs, " +
                                "text boxes and handwriting), its notebooks and folders, and the course's tasks."
                            else -> "Type to search the words in your notes (PDFs, text boxes and handwriting), your " +
                                "notebooks, tasks (titles, descriptions and your notes) and folders."
                        },
                    )
                    handStatus?.let { HandStatus(it) }
                }
                results.isEmpty && noteHits.isEmpty() && !notesCurrent -> Box(Modifier.fillMaxSize())
                results.isEmpty && noteHits.isEmpty() -> Column {
                    Hint("Nothing matches “${query.trim()}”.")
                    handStatus?.let { HandStatus(it) }
                }
                else -> LazyColumn(
                    modifier = Modifier.widthIn(max = Dimens.ReadingMaxWidth).fillMaxWidth().testTag("search-results"),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(Dimens.ScreenPadding),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // While handwriting is still being read, say how far it got (18 Nov), so a
                    // missing handwritten match isn't taken for a broken search.
                    handStatus?.let { status -> item(key = "hand-status") { HandStatus(status, inList = true) } }
                    // Notes first (what you asked for, 18 Nov): the words in your notes, then
                    // notebooks, tasks and folders.
                    if (noteHits.isNotEmpty()) {
                        item(key = "h-notes") { Group("In your notes", noteHits.size) }
                        items(noteHits, key = { "p-" + it.pageId }) { hit ->
                            ResultRow(
                                icon = when {
                                    hit.handwritten -> R.drawable.ic_edit
                                    hit.isPdf -> R.drawable.ic_picture_as_pdf
                                    else -> R.drawable.ic_text_fields
                                },
                                title = if (scoped) "Page ${hit.pageNumber}" else "${hit.notebookTitle} · page ${hit.pageNumber}",
                                detail = hit.snippet,
                                detailLines = 2,
                                onClick = { onOpenPage(hit.notebookId, hit.pageId, query.trim(), hit.spot) },
                                tag = "search-page-${hit.pageNumber}",
                            )
                        }
                    }
                    if (results.notebooks.isNotEmpty()) {
                        item(key = "h-notebooks") { Group("Notebooks", results.notebooks.size) }
                        items(results.notebooks, key = { "n-" + it.item.notebook.id }) { hit ->
                            val pages = hit.item.pageCount
                            ResultRow(
                                icon = if (hit.item.isPdf) R.drawable.ic_picture_as_pdf else R.drawable.ic_note,
                                title = hit.item.notebook.title,
                                detail = listOfNotNull(hit.place?.let { "in $it" }, "$pages ${if (pages == 1) "page" else "pages"}").joinToString(" · "),
                                onClick = { onOpenNotebook(hit.item.notebook.id) },
                                tag = "search-notebook-${hit.item.notebook.title}",
                            )
                        }
                    }
                    if (results.tasks.isNotEmpty()) {
                        item(key = "h-tasks") { Group("Tasks", results.tasks.size) }
                        items(results.tasks, key = { "t-" + it.task.id }) { t ->
                            TaskCard(
                                task = t.task,
                                courseCode = t.courseCode,
                                now = now,
                                zone = zone,
                                onClick = { onOpenTask(t.task.id) },
                                onStatusChange = { viewModel.setStatus(t.task.id, it) },
                            )
                        }
                    }
                    if (results.folders.isNotEmpty()) {
                        item(key = "h-folders") { Group("Folders & courses", results.folders.size) }
                        items(results.folders, key = { "f-" + it.folder.id }) { hit ->
                            ResultRow(
                                icon = R.drawable.ic_folder,
                                title = hit.folder.name,
                                detail = hit.place?.let { "in $it" },
                                courseCode = hit.courseCode,
                                onClick = { onOpenFolder(hit.folder.id) },
                                tag = "search-folder-${hit.folder.name}",
                            )
                        }
                    }
                }
            }
        }
    }
}

/** How long the search screen waits before opening the keyboard (its slide-in takes about this long). */
private const val KEYBOARD_DELAY_MS = 300L

/** "Read 40 of 252 pages so far": so a missing handwriting match isn't taken for a broken search. */
@Composable
private fun HandStatus(text: String, inList: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.widthIn(max = Dimens.ReadingMaxWidth)
            .padding(horizontal = if (inList) 0.dp else Dimens.ScreenPadding * 2)
            .testTag("search-hand-status"),
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.widthIn(max = Dimens.ReadingMaxWidth).padding(Dimens.ScreenPadding * 2).testTag("search-hint"),
    )
}

@Composable
private fun Group(title: String, count: Int) {
    Text(
        "$title ($count)",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )
}

@Composable
private fun ResultRow(icon: Int, title: String, detail: String?, onClick: () -> Unit, tag: String, courseCode: String? = null, detailLines: Int = 1) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).testTag(tag),
    ) {
        Row(Modifier.padding(Dimens.TilePadding), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                painterResource(icon), null,
                tint = if (courseCode != null) LocalCourseColors.current.of(courseCode).accent else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (courseCode != null) CourseTag(courseCode)
                if (detail != null) {
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = detailLines, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
