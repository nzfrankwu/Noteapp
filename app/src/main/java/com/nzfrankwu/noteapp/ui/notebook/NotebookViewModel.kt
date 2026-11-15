package com.nzfrankwu.noteapp.ui.notebook

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.Bitmap
import com.nzfrankwu.noteapp.data.InkSketch
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
import com.nzfrankwu.noteapp.domain.ink.TestWriting
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

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
            // A page just added here was made ready meanwhile (and may already have writing).
            if (pageStatus[page.id] == PageStatus.Ready) continue
            when (read) {
                PageRead.Missing -> ink.load(page.id, emptyList()).also { pageStatus[page.id] = PageStatus.Ready }
                is PageRead.Ok -> ink.load(page.id, read.strokes).also { pageStatus[page.id] = PageStatus.Ready }
                is PageRead.Damaged -> pageStatus[page.id] = PageStatus.Damaged(read.reason)
            }
            version++
        }
    }

    fun isEditable(pageId: String): Boolean = pageStatus[pageId] == PageStatus.Ready

    // ---- Getting pages ready to draw ----

    /** The ready-to-draw strokes of the pages near the screen. */
    val drawables = StrokeDrawables()
    private val preparing = HashSet<String>()

    /**
     * Gets a page's strokes ready to draw (does nothing if they are, or it's under way). A page
     * with little writing is ready at once; a full page is built in the background, so opening
     * or scrolling to it never freezes the screen, and it appears a moment later.
     */
    fun prepareToDraw(pageId: String) {
        if (drawables.isReady(pageId) || pageId in preparing || !isEditable(pageId)) return
        val strokes = ink.strokes(pageId).toList()
        if (strokes.size <= BUILD_NOW_LIMIT) {
            drawables.put(pageId, emptyList()) // built one by one as they're first drawn
            return
        }
        preparing += pageId
        viewModelScope.launch {
            val built = withContext(Dispatchers.Default) { strokes.map { it to InkBrushes.toDrawable(it) } }
            drawables.put(pageId, built)
            preparing -= pageId
            version++
        }
    }

    // ---- Quick previews (PHASE-2.md F8) ----

    /**
     * A small, quickly drawn picture of each page near the screen (ink only, about 256 px wide).
     * The screen stretches it over a page that isn't drawn in full yet, or while pages fly past,
     * so a long notebook never shows empty pages while scrolling (like Notein's blurry pages).
     */
    val previews = mutableStateMapOf<String, ImageBitmap>()
    private var previewOrder: List<String> = emptyList()
    private var previewJob: Job? = null
    private val pageEdits = HashMap<String, Int>()

    /** Wants previews of the pages around page [index]: nearest first. Far ones are dropped. */
    fun requestPreviews(index: Int) {
        val list = pages.value
        if (list.isEmpty()) return
        val keep = list.indices.filter { kotlin.math.abs(it - index) <= PREVIEW_KEEP }.mapTo(HashSet()) { list[it].id }
        previews.keys.filter { it !in keep }.forEach { previews.remove(it) }
        previewOrder = list.indices
            .filter { kotlin.math.abs(it - index) <= PREVIEW_RANGE }
            .sortedBy { kotlin.math.abs(it - index) }
            .map { list[it].id }
        if (previewJob?.isActive == true) return
        previewJob = viewModelScope.launch {
            delay(PREVIEW_DELAY_MS) // let a burst of edits or scrolling settle first
            while (true) {
                val nb = notebook.value ?: break
                val pageId = previewOrder.firstOrNull { it !in previews && isEditable(it) } ?: break
                val edits = pageEdits[pageId] ?: 0
                val strokes = ink.strokes(pageId).toList()
                val size = pages.value.firstOrNull { it.id == pageId }?.size(nb)
                    ?: com.nzfrankwu.noteapp.domain.PageSize(nb.pageWidth, nb.pageHeight)
                val picture = if (strokes.isEmpty()) EMPTY_PREVIEW else withContext(Dispatchers.Default) {
                    renderPreview(strokes, size.width, size.height)
                }
                // Changed while it was drawn: draw it again (the loop comes back to it).
                if ((pageEdits[pageId] ?: 0) == edits) previews[pageId] = picture
            }
        }
    }

    private fun renderPreview(strokes: List<InkStroke>, pageWidth: Float, pageHeight: Float): ImageBitmap {
        val scale = PREVIEW_WIDTH_PX / pageWidth
        val bitmap = Bitmap.createBitmap(PREVIEW_WIDTH_PX.toInt(), (pageHeight * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        InkSketch.draw(android.graphics.Canvas(bitmap), strokes, scale, widthFactor = PREVIEW_LINE_FACTOR, minWidth = 0.7f)
        return bitmap.asImageBitmap()
    }

    /** Keeps only the pages near the screen ready to draw (the rest are rebuilt when needed). */
    fun keepReadyOnly(pageIds: Set<String>) = drawables.keepOnly(pageIds) { ink.strokes(it) }

    // ---- Editing ----

    /** New strokes from the pen layer, already on their page. Smooths them as set (Stability). */
    fun addStrokes(pageId: String, strokes: List<InkStroke>) {
        if (!isEditable(pageId) || strokes.isEmpty()) return
        val stability = settings.value.stability
        val finished = strokes.map { Smoothing.smoothed(it, stability) }
        ink.add(pageId, finished)
        changed(pageId)
        addPageAfterIfLast(pageId)
    }

    private var addingNextPage = false

    /**
     * Writing on the last page adds an empty page after it (PHASE-2.md F13), so there's always a
     * fresh page to the right. It isn't an undo step: undoing the writing leaves the empty page.
     */
    private fun addPageAfterIfLast(pageId: String) {
        val list = pages.value
        if (addingNextPage || list.lastOrNull()?.id != pageId) return
        addingNextPage = true
        viewModelScope.launch {
            try {
                val id = addPage(list.size, recordUndo = false)
                // Wait for the page list to show it, so more writing meanwhile doesn't add another.
                if (id != null) withTimeoutOrNull(2_000) { pages.first { now -> now.any { it.id == id } } }
            } finally {
                addingNextPage = false
            }
        }
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

    // ---- Copy, cut, duplicate and paste (PHASE-2.md F12) ----

    /** Strokes copied or cut with the lasso, ready to paste (this notebook only, until it closes). */
    var clipboard by mutableStateOf<List<InkStroke>?>(null)
        private set

    fun copy(strokes: List<InkStroke>) {
        if (strokes.isNotEmpty()) clipboard = strokes
    }

    fun cut(pageId: String, strokes: List<InkStroke>) {
        copy(strokes)
        deleteStrokes(pageId, strokes.mapTo(HashSet()) { it.id })
    }

    /** Adds a copy of [strokes] a little down and to the right; returns the copy (for selecting it). */
    fun duplicate(pageId: String, strokes: List<InkStroke>): List<InkStroke> {
        if (!isEditable(pageId) || strokes.isEmpty()) return emptyList()
        val copies = strokes.map { it.mapped(1f, 0f, 0f, DUPLICATE_OFFSET, DUPLICATE_OFFSET, newId = java.util.UUID.randomUUID().toString()) }
        ink.add(pageId, copies)
        changed(pageId)
        return copies
    }

    /** Pastes the clipboard centred on ([x], [y]) (page units); returns what was pasted. */
    fun paste(pageId: String, x: Float, y: Float): List<InkStroke> {
        val strokes = clipboard ?: return emptyList()
        if (!isEditable(pageId)) return emptyList()
        val box = InkGeometry.boundsOf(strokes) ?: return emptyList()
        val dx = x - (box[0] + box[2]) / 2
        val dy = y - (box[1] + box[3]) / 2
        val pasted = strokes.map { it.mapped(1f, 0f, 0f, dx, dy, newId = java.util.UUID.randomUUID().toString()) }
        ink.add(pageId, pasted)
        changed(pageId)
        return pasted
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

    /**
     * Adds an empty page at [index] and returns its ID (null if it failed). [recordUndo] makes it
     * an undo step (the Add page button); a page added by itself after writing isn't one.
     */
    suspend fun addPage(index: Int, recordUndo: Boolean = true): String? {
        val result = notebooks.addPage(notebookId, index)
        if (result !is NotebookResult.Success) return null
        // The page list may already have shown (and readied) the new page; keep anything on it.
        if (!ink.isLoaded(result.id)) ink.load(result.id, emptyList())
        pageStatus[result.id] = PageStatus.Ready
        if (recordUndo) {
            ink.recordPageEdit(InkEdit.AddPage(result.id, index))
            refreshUndo()
        }
        version++
        return result.id
    }

    /**
     * Debug builds only: adds [count] pages full of pretend handwriting at the end, to check that
     * a long notebook stays smooth. They're ordinary pages (saved, and deletable).
     */
    fun addTestPages(count: Int) {
        val nb = notebook.value ?: return
        viewModelScope.launch {
            val seedBase = pages.value.size
            repeat(count) { n ->
                val result = notebooks.addPage(notebookId, Int.MAX_VALUE) as? NotebookResult.Success ?: return@launch
                val strokes = withContext(Dispatchers.Default) {
                    TestWriting.page(nb.pageWidth, nb.pageHeight, seedBase + n) { java.util.UUID.randomUUID().toString() }
                }
                ink.load(result.id, strokes)
                pageStatus[result.id] = PageStatus.Ready
                dirty.add(result.id)
            }
            version++
            saveNow()
            _message.value = "Added $count test pages."
        }
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
        pageEdits[pageId] = (pageEdits[pageId] ?: 0) + 1
        previews.remove(pageId) // redrawn by the next requestPreviews
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
        // The tile shows the first page: redraw it only when that page changed.
        val thumbnail = if (pages.value.firstOrNull()?.id in toSave) thumbnailJob() else null
        return saveScope.launch {
            saveLock.withLock {
                val saved = ArrayList<String>()
                for ((pageId, strokes) in toSave) {
                    // A failed write is retried here, in the scope that outlives the screen, so a
                    // save made while leaving the notebook still gets its retries.
                    if (writeWithRetries(pageId, strokes)) {
                        saved += pageId
                    } else {
                        withContext(Dispatchers.Main) {
                            dirty.add(pageId) // try again with the next change or when leaving
                            _message.value = "Couldn't save a page. Noteapp will try again."
                        }
                    }
                }
                // The picture first, then the "changed" time that makes tiles reload it. Neither
                // may crash the app: the strokes themselves are already safely on disk.
                thumbnail?.let { withContext(io) { runCatching { it() } } }
                saved.forEach { runCatching { notebooks.markPageEdited(it) } }
            }
        }
    }

    /** Writes a page's file, trying again after short pauses if it fails. True once it's saved. */
    private suspend fun writeWithRetries(pageId: String, strokes: List<InkStroke>): Boolean {
        for (pause in SAVE_RETRY_PAUSES_MS) {
            delay(pause)
            try {
                withContext(io) { files.write(notebookId, pageId, strokes) }
                return true
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // try again after the next pause
            }
        }
        return false
    }

    /** Draws the tile picture from the first page as it is now (null if it can't yet). */
    private fun thumbnailJob(template: com.nzfrankwu.noteapp.domain.PageTemplate? = null): (() -> Unit)? {
        val t = thumbnails ?: return null
        val nb = notebook.value ?: return null
        val first = pages.value.firstOrNull() ?: return null
        if (!isEditable(first.id)) return null
        val strokes = ink.strokes(first.id).toList()
        val paper = first.template ?: template ?: nb.template
        val size = first.size(nb)
        return { t.render(notebookId, paper, size.width, size.height, strokes) }
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
        /** Preview width in pixels: small, so it's quick to draw and looks soft when stretched (F8). */
        const val PREVIEW_WIDTH_PX = 320f

        /** Preview lines are thinner than the pen's size, to look like the real ink. */
        private const val PREVIEW_LINE_FACTOR = 0.5f

        /** Previews are drawn for pages this far either side of the screen, and kept a little further. */
        const val PREVIEW_RANGE = 12
        const val PREVIEW_KEEP = 18
        private const val PREVIEW_DELAY_MS = 150L

        /** The preview of a blank page: nothing to draw. */
        private val EMPTY_PREVIEW: ImageBitmap by lazy { ImageBitmap(1, 1) }

        /** Pages with at most this many strokes are built as they're drawn; fuller ones in the background. */
        const val BUILD_NOW_LIMIT = 120

        /** A changed page is saved this long after the last change. */
        const val SAVE_DELAY_MS = 400L

        /** How far a duplicate lands from the original (page units, about 5 mm). */
        const val DUPLICATE_OFFSET = 14f

        /** Pauses before each try at writing a page (the first try is immediate). */
        private val SAVE_RETRY_PAUSES_MS = longArrayOf(0L, 500L, 2_000L, 5_000L)

        /** One save at a time across every open notebook. */
        private val saveLock = Mutex()
    }
}
