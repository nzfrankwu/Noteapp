package com.nzfrankwu.noteapp.ui.notebook

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nzfrankwu.noteapp.data.InkSettingsStore
import com.nzfrankwu.noteapp.data.NotebookFiles
import com.nzfrankwu.noteapp.data.NotebookRepository
import com.nzfrankwu.noteapp.data.NotebookResult
import com.nzfrankwu.noteapp.data.PageRead
import com.nzfrankwu.noteapp.data.Thumbnails
import com.nzfrankwu.noteapp.data.db.Notebook
import com.nzfrankwu.noteapp.data.db.Page
import com.nzfrankwu.noteapp.domain.ink.EditorTool
import com.nzfrankwu.noteapp.domain.ink.InkEdit
import com.nzfrankwu.noteapp.domain.ink.InkGeometry
import com.nzfrankwu.noteapp.domain.ink.InkStroke
import com.nzfrankwu.noteapp.domain.ink.InkToolSettings
import com.nzfrankwu.noteapp.domain.ink.NotebookInk
import com.nzfrankwu.noteapp.domain.ink.PenButtonToggle
import com.nzfrankwu.noteapp.domain.ink.Smoothing
import com.nzfrankwu.noteapp.domain.ink.Straighten
import com.nzfrankwu.noteapp.ui.folder.message
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Whether a page's strokes are ready to show and edit. */
sealed interface PageStatus {
    data object Loading : PageStatus
    data object Ready : PageStatus
    /** Its file couldn't be read: shown as damaged, never written over (the file is kept). */
    data class Damaged(val reason: String) : PageStatus
}

/**
 * An open notebook (PHASE-2.md 2.5–2.10): its pages, the strokes on them with undo and redo,
 * the toolbar's choices, and autosave.
 *
 * Strokes live in Compose state ([version] changes on every edit) so a finished stroke is drawn
 * in the same frame it leaves the pen layer, without flicker.
 *
 * Saving: a changed page is written [SAVE_DELAY_MS] after the last change, and at once by
 * [saveNow] (leaving the notebook, the app going to the background). Saves run in [saveScope],
 * which outlives this screen, one at a time, so a save is never cut off by closing the notebook.
 */
class NotebookViewModel(
    val notebookId: String,
    private val notebooks: NotebookRepository,
    private val files: NotebookFiles,
    private val settingsStore: InkSettingsStore,
    private val saveScope: CoroutineScope,
    private val thumbnails: Thumbnails? = null,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    val notebook: StateFlow<Notebook?> = notebooks.observeNotebook(notebookId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val pages: StateFlow<List<Page>> = notebooks.observePages(notebookId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val settings: StateFlow<InkToolSettings> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, InkToolSettings())

    /** The strokes, page by page. Read [version] in a composable to redraw when they change. */
    val ink = NotebookInk()
    var version by mutableIntStateOf(0)
        private set
    val pageStatus = mutableStateMapOf<String, PageStatus>()

    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    /** A short message for the snackbar (e.g. a save that failed), or null. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun messageShown() { _message.value = null }

    private val penButton = PenButtonToggle()
    private val dirty = LinkedHashSet<String>()
    private var saveJob: Job? = null

    init {
        files.cleanUpUnfinishedSaves(notebookId)
        viewModelScope.launch {
            var firstPage: String? = null
            pages.collect { list ->
                loadNewPages(list)
                val first = list.firstOrNull()?.id
                // A different first page (added in front, deleted, undone) changes the tile picture.
                if (firstPage != null && first != firstPage) refreshThumbnail()
                if (first != null) firstPage = first
            }
        }
    }

    // ---- Loading ----

    private suspend fun loadNewPages(pages: List<Page>) {
        for (page in pages) {
            if (pageStatus.containsKey(page.id)) continue
            pageStatus[page.id] = PageStatus.Loading
            val read = withContext(io) { files.read(notebookId, page.id) }
            when (read) {
                PageRead.Missing -> ink.load(page.id, emptyList()).also { pageStatus[page.id] = PageStatus.Ready }
                is PageRead.Ok -> ink.load(page.id, read.strokes).also { pageStatus[page.id] = PageStatus.Ready }
                is PageRead.Damaged -> pageStatus[page.id] = PageStatus.Damaged(read.reason)
            }
            version++
        }
    }

    fun isEditable(pageId: String): Boolean = pageStatus[pageId] == PageStatus.Ready

    // ---- Editing ----

    /** New strokes from the pen layer, already on their page. Straightens and smooths them as set. */
    fun addStrokes(pageId: String, strokes: List<InkStroke>) {
        if (!isEditable(pageId) || strokes.isEmpty()) return
        val s = settings.value
        val finished = strokes.map { stroke ->
            val straight = if (s.holdToStraighten && Straighten.wanted(stroke)) Straighten.straightened(stroke) else stroke
            if (straight === stroke) Smoothing.smoothed(stroke, s.stability) else straight
        }
        ink.add(pageId, finished)
        changed(pageId)
    }

    /** Erases whatever the eraser touches moving from (ax, ay) to (bx, by) on the page (page units). */
    fun eraseAlong(pageId: String, ax: Float, ay: Float, bx: Float, by: Float, radius: Float) {
        if (!isEditable(pageId)) return
        val hit = ink.strokes(pageId).filter { InkGeometry.eraserTouches(it, ax, ay, bx, by, radius) }
        if (hit.isEmpty()) return
        ink.erase(pageId, hit.map { it.id }.toSet())
        changed(pageId)
    }

    /** Strokes on the page inside a lasso loop (page units). */
    fun strokesInLoop(pageId: String, xs: FloatArray, ys: FloatArray): List<InkStroke> =
        if (!isEditable(pageId)) emptyList() else ink.strokes(pageId).filter { InkGeometry.insideLasso(it, xs, ys) }

    /** Saves the lasso's move or resize (same stroke IDs, new positions). */
    fun replaceStrokes(pageId: String, after: List<InkStroke>) {
        if (!isEditable(pageId) || after.isEmpty()) return
        ink.replace(pageId, after)
        changed(pageId)
    }

    fun deleteStrokes(pageId: String, ids: Set<String>) {
        if (!isEditable(pageId)) return
        if (ink.erase(pageId, ids) > 0) changed(pageId)
    }

    fun undo() {
        val edit = ink.undo() ?: return
        afterUndoRedo(edit, undoing = true)
    }

    fun redo() {
        val edit = ink.redo() ?: return
        afterUndoRedo(edit, undoing = false)
    }

    private fun afterUndoRedo(edit: InkEdit, undoing: Boolean) {
        when (edit) {
            is InkEdit.Add -> changed(edit.pageId)
            is InkEdit.Remove -> changed(edit.pageId)
            is InkEdit.Replace -> changed(edit.pageId)
            is InkEdit.AddPage -> viewModelScope.launch {
                if (undoing) notebooks.deletePage(edit.pageId) else notebooks.restorePage(edit.pageId, edit.index)
            }
            is InkEdit.DeletePage -> viewModelScope.launch {
                if (undoing) notebooks.restorePage(edit.pageId, edit.index) else notebooks.deletePage(edit.pageId)
            }
        }
        refreshUndo()
        version++
    }

    // ---- Pages ----

    /** Adds an empty page at [index] and returns its ID (null if it failed). */
    suspend fun addPage(index: Int): String? {
        val result = notebooks.addPage(notebookId, index)
        if (result !is NotebookResult.Success) return null
        ink.load(result.id, emptyList())
        pageStatus[result.id] = PageStatus.Ready
        ink.recordPageEdit(InkEdit.AddPage(result.id, index))
        refreshUndo()
        version++
        return result.id
    }

    /** Deletes a page (kept, hidden; Undo brings it back). The last page can't be deleted. */
    fun deletePage(pageId: String) {
        val index = pages.value.indexOfFirst { it.id == pageId }
        if (index < 0) return
        viewModelScope.launch {
            saveNow() // its strokes are on disk before it's hidden
            when (val result = notebooks.deletePage(pageId)) {
                is NotebookResult.Success -> {
                    ink.recordPageEdit(InkEdit.DeletePage(pageId, index))
                    refreshUndo()
                    version++
                }
                is NotebookResult.Failure -> _message.value = result.error.message()
            }
        }
    }

    /** Changes the notebook's paper (every page without its own). */
    fun setTemplate(template: com.nzfrankwu.noteapp.domain.PageTemplate) {
        refreshThumbnail(template)
        viewModelScope.launch { notebooks.setTemplate(notebookId, template) }
    }

    // ---- Tools ----

    fun pickTool(tool: EditorTool) {
        penButton.picked(tool)
        updateSettings { it.copy(tool = tool) }
    }

    /** The stylus side button was clicked: lasso, or back to the previous tool. */
    fun penButtonClicked() {
        val next = penButton.click(settings.value.tool)
        updateSettings { it.copy(tool = next) }
    }

    fun updateSettings(change: (InkToolSettings) -> InkToolSettings) {
        viewModelScope.launch { settingsStore.update(change) }
    }

    // ---- Saving ----

    private fun changed(pageId: String) {
        dirty.add(pageId)
        refreshUndo()
        version++
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DELAY_MS)
            saveNow()
        }
    }

    private fun refreshUndo() {
        canUndo = ink.canUndo
        canRedo = ink.canRedo
    }

    /** Writes every changed page now. Safe to call often; does nothing when all is saved. */
    fun saveNow(): Job? {
        if (dirty.isEmpty()) return null
        saveJob?.cancel()
        val toSave = dirty.associateWith { ink.strokes(it).toList() }
        dirty.clear()
        val thumbnail = thumbnailJob()
        return saveScope.launch {
            saveLock.withLock {
                val saved = ArrayList<String>()
                for ((pageId, strokes) in toSave) {
                    try {
                        withContext(io) { files.write(notebookId, pageId, strokes) }
                        saved += pageId
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            dirty.add(pageId) // try again with the next change or when leaving
                            _message.value = "Couldn't save a page. Noteapp will try again."
                        }
                    }
                }
                // The picture first, then the "changed" time that makes tiles reload it.
                thumbnail?.let { withContext(io) { runCatching { it() } } }
                saved.forEach { notebooks.markPageEdited(it) }
            }
        }
    }

    /** Draws the tile picture from the first page as it is now (null if it can't yet). */
    private fun thumbnailJob(template: com.nzfrankwu.noteapp.domain.PageTemplate? = null): (() -> Unit)? {
        val t = thumbnails ?: return null
        val nb = notebook.value ?: return null
        val first = pages.value.firstOrNull() ?: return null
        if (!isEditable(first.id)) return null
        val strokes = ink.strokes(first.id).toList()
        val paper = first.template ?: template ?: nb.template
        return { t.render(notebookId, paper, nb.pageWidth, nb.pageHeight, strokes) }
    }

    /** Redraws the tile picture (e.g. after the first page changed). */
    private fun refreshThumbnail(template: com.nzfrankwu.noteapp.domain.PageTemplate? = null) {
        val job = thumbnailJob(template) ?: return
        saveScope.launch(io) { runCatching { job() } }
    }

    override fun onCleared() {
        saveNow()
        super.onCleared()
    }

    companion object {
        /** A changed page is saved this long after the last change. */
        const val SAVE_DELAY_MS = 400L

        /** One save at a time across every open notebook. */
        private val saveLock = Mutex()
    }
}
