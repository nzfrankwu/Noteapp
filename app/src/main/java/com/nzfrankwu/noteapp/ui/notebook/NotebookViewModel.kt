package com.nzfrankwu.noteapp.ui.notebook

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
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
import com.nzfrankwu.noteapp.domain.ink.changedPages
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
                is PageRead.Ok -> ink.load(page.id, read.strokes, read.images).also { pageStatus[page.id] = PageStatus.Ready }
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
                val page = pages.value.firstOrNull { it.id == pageId }
                val pdfBack = page?.let { p -> pdfFileOf(p)?.let { f -> pdf.wholePage(f, p.pdfPage ?: 0, size, PREVIEW_WIDTH_PX / size.width) } }
                val pictures = ink.images(pageId).mapNotNull { img -> imageBitmaps[img.asset]?.let { img to it.asAndroidBitmap() } }
                val picture = if (strokes.isEmpty() && pdfBack == null && pictures.isEmpty()) EMPTY_PREVIEW else withContext(Dispatchers.Default) {
                    renderPreview(strokes, size.width, size.height, pdfBack?.bitmap, pictures)
                }
                // Changed while it was drawn: draw it again (the loop comes back to it).
                if ((pageEdits[pageId] ?: 0) == edits) previews[pageId] = picture
            }
        }
    }

    private fun renderPreview(
        strokes: List<InkStroke>, pageWidth: Float, pageHeight: Float, pdfPage: Bitmap? = null,
        pictures: List<Pair<com.nzfrankwu.noteapp.domain.ink.InkImage, Bitmap>> = emptyList(),
    ): ImageBitmap {
        val scale = PREVIEW_WIDTH_PX / pageWidth
        val bitmap = Bitmap.createBitmap(PREVIEW_WIDTH_PX.toInt(), (pageHeight * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        if (pdfPage != null && !pdfPage.isRecycled) {
            canvas.drawBitmap(pdfPage, null, android.graphics.Rect(0, 0, bitmap.width, bitmap.height), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
        }
        InkSketch.drawImages(canvas, pictures, scale)
        InkSketch.draw(canvas, strokes, scale, widthFactor = PREVIEW_LINE_FACTOR, minWidth = 0.7f)
        return bitmap.asImageBitmap()
    }

    // ---- Export as PDF (Phase 3) ----

    /** Where an export is. */
    sealed interface ExportState {
        data class Working(val done: Int, val total: Int) : ExportState
        data class Ready(val file: java.io.File) : ExportState
        data object Failed : ExportState
    }

    var exportState by mutableStateOf<ExportState?>(null)
        private set
    private var exportJob: Job? = null

    /**
     * Saves the notebook, then writes it as a PDF to [dir] (a cache folder, e.g. exports/), named
     * after the notebook. The screen then offers Share or Save to device.
     */
    fun exportPdf(dir: java.io.File) {
        if (exportState is ExportState.Working) return
        val nb = notebook.value ?: return
        val list = pages.value
        if (list.isEmpty()) return
        exportState = ExportState.Working(0, list.size)
        exportJob = viewModelScope.launch {
            exportState = try {
                saveNow()?.join() // the export reads the saved pages
                val out = java.io.File(dir, com.nzfrankwu.noteapp.data.PdfExport.fileNameFor(nb.title))
                com.nzfrankwu.noteapp.data.PdfExport(files, io).export(nb, list, out, InkBrushes::exportShape) { done, total ->
                    viewModelScope.launch { if (exportState is ExportState.Working) exportState = ExportState.Working(done, total) }
                }
                ExportState.Ready(out)
            } catch (e: kotlinx.coroutines.CancellationException) {
                null
            } catch (e: Exception) {
                ExportState.Failed
            } catch (e: OutOfMemoryError) {
                ExportState.Failed
            }
        }
    }

    fun cancelExport() {
        exportJob?.cancel()
        exportState = null
    }

    fun exportShown() {
        if (exportState !is ExportState.Working) exportState = null
    }

    // ---- Pictures (Phase 3) ----

    private val imageImport = com.nzfrankwu.noteapp.data.ImageImport(files, io)

    /** Pictures ready for the screen, by file name (shrunk to about the screen's size). */
    val imageBitmaps = mutableStateMapOf<String, ImageBitmap>()
    private val imageLoads = HashSet<String>()

    /** True while pictures are being brought in (the screen shows it). */
    var insertingImages by mutableStateOf(false)
        private set

    /** Loads the pictures on the pages [near] for the screen, and forgets the others. */
    fun prepareImages(near: List<Page>) {
        val wanted = near.flatMap { ink.images(it.id) }.mapTo(HashSet()) { it.asset }
        imageBitmaps.keys.filter { it !in wanted }.forEach { imageBitmaps.remove(it) }
        for (asset in wanted) {
            if (asset in imageBitmaps || !imageLoads.add(asset)) continue
            viewModelScope.launch {
                try {
                    val file = runCatching { files.assetFile(notebookId, asset) }.getOrNull()
                    val bitmap = file?.let { withContext(io) { com.nzfrankwu.noteapp.data.ImageImport.loadForScreen(it, IMAGE_SCREEN_PX) } }
                    if (bitmap != null) imageBitmaps[asset] = bitmap.asImageBitmap()
                } finally {
                    imageLoads.remove(asset)
                }
            }
        }
    }

    /**
     * Inserts pictures on page [pageIndex]: in its middle, about 60% of the page wide, each a
     * little lower-right of the one before. One undo step. Returns the pictures placed.
     */
    suspend fun insertImages(pageIndex: Int, sources: List<() -> java.io.InputStream?>): List<com.nzfrankwu.noteapp.domain.ink.InkImage> {
        val nb = notebook.value ?: return emptyList()
        val page = pages.value.getOrNull(pageIndex) ?: return emptyList()
        if (!isEditable(page.id) || sources.isEmpty()) return emptyList()
        insertingImages = true
        try {
            val size = page.size(nb)
            val placed = ArrayList<com.nzfrankwu.noteapp.domain.ink.InkImage>()
            for (open in sources) {
                val imported = imageImport.import(notebookId, open) ?: continue
                val image = com.nzfrankwu.noteapp.domain.ink.InkImage.centred(
                    java.util.UUID.randomUUID().toString(), imported.asset, imported.widthPx, imported.heightPx, size.width, size.height,
                )
                val shift = placed.size * CASCADE_STEP
                placed += image.copy(left = image.left + shift, top = image.top + shift)
            }
            if (placed.isNotEmpty() && isEditable(page.id)) {
                ink.addImages(page.id, placed)
                changed(page.id)
            }
            return placed
        } finally {
            insertingImages = false
        }
    }

    /** A PDF copied into this notebook's folder, ready to add pages from (F34). */
    class CopiedPdf(val name: String, val file: java.io.File, val sizes: List<com.nzfrankwu.noteapp.domain.PageSize>)

    /**
     * Copies the PDF [open] reads into this notebook's folder and reads its pages. Null, with
     * [onFail] told why, if it can't be read, is password-protected or isn't a PDF (then the copy
     * is removed again: nothing uses it).
     */
    suspend fun copyPdfIn(open: () -> java.io.InputStream?, onFail: (String) -> Unit): CopiedPdf? = withContext(io) {
        val name = try {
            open()?.use { files.importPdf(notebookId, it) }
        } catch (e: Exception) {
            null
        } ?: return@withContext null.also { withContext(Dispatchers.Main) { onFail(com.nzfrankwu.noteapp.data.PdfImport.CANT_READ) } }
        val file = files.assetFile(notebookId, name)
        when (val info = com.nzfrankwu.noteapp.data.PdfPages.readInfo(file)) {
            is com.nzfrankwu.noteapp.data.PdfInfo.Ok -> CopiedPdf(name, file, info.pageSizes)
            else -> {
                file.delete()
                withContext(Dispatchers.Main) {
                    onFail(if (info == com.nzfrankwu.noteapp.data.PdfInfo.PasswordProtected) com.nzfrankwu.noteapp.data.PdfImport.PASSWORD else com.nzfrankwu.noteapp.data.PdfImport.BROKEN)
                }
                null
            }
        }
    }

    /** A small picture of page [index] of [pdf], for choosing pages (F34). */
    suspend fun pdfThumbnail(pdf: CopiedPdf, index: Int, widthPx: Int): ImageBitmap? {
        val size = pdf.sizes.getOrNull(index) ?: return null
        return this.pdf.wholePage(pdf.file, index, size, widthPx / size.width)?.bitmap?.asImageBitmap()
    }

    /** Adds pages [indices] of [pdf] after page [afterIndex], in order. Returns how many were added. */
    suspend fun insertPdfPages(afterIndex: Int, pdf: CopiedPdf, indices: List<Int>): Int {
        val chosen = indices.filter { it in pdf.sizes.indices }.map { it to pdf.sizes[it] }
        val ids = notebooks.insertPdfPages(notebookId, afterIndex + 1, pdf.name, chosen) ?: return 0
        version++
        return ids.size
    }

    /** Forgets a copied PDF nobody used (the person cancelled choosing pages). */
    fun discardPdf(pdf: CopiedPdf) {
        viewModelScope.launch(io) {
            // Only if no page uses it (a page may if the same copy was used before).
            if (pages.value.none { it.pdfFile == pdf.name }) pdf.file.delete()
        }
    }

    /** The full picture file of [image], for cropping (null if it can't be read). */
    suspend fun loadFullPicture(image: com.nzfrankwu.noteapp.domain.ink.InkImage): Bitmap? = withContext(io) {
        runCatching { android.graphics.BitmapFactory.decodeFile(files.assetFile(notebookId, image.asset).path) }.getOrNull()
    }

    /**
     * Crops [image] (on [pageId]) to [box] (left, top, right, bottom in [picture]'s pixels): the
     * kept part is saved as a new picture file and takes the old one's place, one undo step (F33).
     * The old file is kept, so Undo brings the whole picture back. Returns the cropped picture.
     */
    suspend fun cropImage(pageId: String, image: com.nzfrankwu.noteapp.domain.ink.InkImage, picture: Bitmap, box: IntArray): com.nzfrankwu.noteapp.domain.ink.InkImage? {
        if (!isEditable(pageId)) return null
        val part = withContext(Dispatchers.Default) { Bitmap.createBitmap(picture, box[0], box[1], box[2] - box[0], box[3] - box[1]) }
        val saved = imageImport.importBitmap(notebookId, part) ?: return null
        val cropped = image.cropped(saved.asset, picture.width, picture.height, box[0], box[1], box[2], box[3])
        if (!isEditable(pageId)) return null
        ink.replace(pageId, emptyList(), listOf(cropped))
        changed(pageId)
        return cropped
    }

    /** Inserts a screenshot snip in the middle of page [pageIndex] (one undo step); returns it placed. */
    suspend fun insertSnip(pageIndex: Int, bitmap: Bitmap): List<com.nzfrankwu.noteapp.domain.ink.InkImage> {
        val nb = notebook.value ?: return emptyList()
        val page = pages.value.getOrNull(pageIndex) ?: return emptyList()
        if (!isEditable(page.id)) return emptyList()
        insertingImages = true
        try {
            val imported = imageImport.importBitmap(notebookId, bitmap) ?: return emptyList()
            val size = page.size(nb)
            val image = com.nzfrankwu.noteapp.domain.ink.InkImage.centred(
                java.util.UUID.randomUUID().toString(), imported.asset, imported.widthPx, imported.heightPx, size.width, size.height,
            )
            if (!isEditable(page.id)) return emptyList()
            ink.addImages(page.id, listOf(image))
            changed(page.id)
            return listOf(image)
        } finally {
            insertingImages = false
        }
    }

    /**
     * Inserts a picture as a page of its own after page [afterIndex]: the page takes the
     * picture's shape (its longer side as long as an A4 page's) and the picture fills it.
     */
    suspend fun insertImageAsPage(afterIndex: Int, open: () -> java.io.InputStream?): String? {
        insertingImages = true
        try {
            val imported = imageImport.import(notebookId, open) ?: return null
            val size = com.nzfrankwu.noteapp.domain.PageSize.forPicture(imported.widthPx, imported.heightPx)
            val pageId = addPage(afterIndex + 1, size = size) ?: return null
            ink.addImages(
                pageId,
                listOf(com.nzfrankwu.noteapp.domain.ink.InkImage(java.util.UUID.randomUUID().toString(), imported.asset, 0f, 0f, size.width, size.height)),
            )
            changed(pageId)
            return pageId
        } finally {
            insertingImages = false
        }
    }

    // ---- PDF pages (Phase 3) ----

    /** Draws imported PDFs' pages (one background thread; closed with the notebook). */
    private val pdf = com.nzfrankwu.noteapp.data.PdfPages()

    /** A drawn part of a PDF page, ready for the screen: [left]..[bottom] in page units. */
    class PdfLayer(val image: ImageBitmap, val left: Float, val top: Float, val right: Float, val bottom: Float, val scale: Float)

    /** Each PDF page near the screen, drawn whole at about the settled zoom. */
    val pdfPictures = mutableStateMapOf<String, PdfLayer>()

    /** Zoomed in further than a whole-page picture allows: a sharp picture of just the part on screen. */
    val pdfDetail = mutableStateMapOf<String, PdfLayer>()
    private var pdfJob: Job? = null
    private var detailJob: Job? = null

    private fun pdfFileOf(page: Page): java.io.File? =
        page.pdfFile?.let { runCatching { files.assetFile(notebookId, it) }.getOrNull() }

    private fun com.nzfrankwu.noteapp.data.PdfPicture.toLayer() = PdfLayer(bitmap.asImageBitmap(), left, top, right, bottom, scale)

    /**
     * Gets the PDF pages among [near] (nearest first) drawn at about [scale] pixels per page unit,
     * and forgets the pictures of pages further away.
     */
    fun preparePdf(near: List<Page>, scale: Float) {
        val nb = notebook.value ?: return
        if (scale <= 0f) return
        val wanted = near.filter { it.isPdf }
        val keep = wanted.mapTo(HashSet()) { it.id }
        pdfPictures.keys.filter { it !in keep }.forEach { pdfPictures.remove(it) }
        pdfDetail.keys.filter { it !in keep }.forEach { pdfDetail.remove(it) }
        if (wanted.isEmpty()) return
        pdfJob?.cancel()
        pdfJob = viewModelScope.launch {
            for (page in wanted) {
                val size = page.size(nb)
                val target = com.nzfrankwu.noteapp.data.PdfPages.wholePageScale(size, scale)
                val have = pdfPictures[page.id]
                if (have != null && kotlin.math.abs(have.scale - target) <= target * 0.05f) continue
                val file = pdfFileOf(page) ?: continue
                pdf.wholePage(file, page.pdfPage ?: 0, size, scale)?.let { pdfPictures[page.id] = it.toLayer() }
            }
        }
    }

    /**
     * When zoomed in past what a whole-page picture holds, draws just the [regions] on screen
     * (page ID → left, top, right, bottom in page units) at [scale]. An empty map clears them.
     */
    fun preparePdfDetail(regions: Map<Page, FloatArray>, scale: Float) {
        val nb = notebook.value ?: return
        detailJob?.cancel()
        val needed = regions.filterKeys { page ->
            page.isPdf && scale > com.nzfrankwu.noteapp.data.PdfPages.wholePageScale(page.size(nb), scale = Float.MAX_VALUE) * 1.15f
        }
        val neededIds = needed.keys.mapTo(HashSet()) { it.id }
        pdfDetail.keys.filter { it !in neededIds }.forEach { pdfDetail.remove(it) }
        if (needed.isEmpty()) return
        detailJob = viewModelScope.launch {
            for ((page, r) in needed) {
                val file = pdfFileOf(page) ?: continue
                pdf.region(file, page.pdfPage ?: 0, r[0], r[1], r[2], r[3], scale)?.let { pdfDetail[page.id] = it.toLayer() }
            }
        }
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

    /**
     * New strokes on several pages at once (one stroke split where it crossed a page edge, F36):
     * one undo step. Pages that can't take writing right now are left out.
     */
    fun addStrokesAcross(byPage: Map<String, List<InkStroke>>) {
        val ok = byPage.filter { (pageId, strokes) -> isEditable(pageId) && strokes.isNotEmpty() }
        if (ok.size <= 1) {
            ok.entries.firstOrNull()?.let { (pageId, strokes) -> addStrokes(pageId, strokes) }
            return
        }
        val stability = settings.value.stability
        ink.addAcross(ok.mapValues { (_, strokes) -> strokes.map { Smoothing.smoothed(it, stability) } })
        ok.keys.forEach { changed(it) }
        val order = pages.value.withIndex().associate { (i, p) -> p.id to i }
        addPageAfterIfLast(ok.keys.maxBy { order[it] ?: -1 })
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

    /** The pictures the lasso loop picks (a corner inside, or touching the loop). */
    fun imagesInLoop(pageId: String, xs: FloatArray, ys: FloatArray): List<com.nzfrankwu.noteapp.domain.ink.InkImage> =
        if (!isEditable(pageId)) emptyList() else ink.images(pageId).filter { InkGeometry.imageInLoop(it, xs, ys) }

    /** The topmost picture at ([x], [y]) on the page (page units), for a lasso tap. */
    fun imageAt(pageId: String, x: Float, y: Float): com.nzfrankwu.noteapp.domain.ink.InkImage? =
        if (!isEditable(pageId)) null else ink.images(pageId).lastOrNull { it.contains(x, y) }

    /** Saves the lasso's move, resize or turn of strokes and pictures (same IDs, new places): one undo step. */
    fun replaceSelection(pageId: String, strokes: List<InkStroke>, images: List<com.nzfrankwu.noteapp.domain.ink.InkImage>) {
        if (!isEditable(pageId) || (strokes.isEmpty() && images.isEmpty())) return
        ink.replace(pageId, strokes, images)
        changed(pageId)
    }

    /** Deletes the lasso's strokes and pictures (one undo step). Picture files are kept for undo. */
    fun deleteSelection(pageId: String, strokeIds: Set<String>, imageIds: Set<String> = emptySet()) {
        if (!isEditable(pageId)) return
        if (ink.remove(pageId, strokeIds, imageIds) > 0) changed(pageId)
    }

    // ---- Copy, cut, duplicate and paste (PHASE-2.md F12) ----

    /** Strokes and pictures copied or cut with the lasso (this notebook only, until it closes). */
    data class Clip(val strokes: List<InkStroke>, val images: List<com.nzfrankwu.noteapp.domain.ink.InkImage> = emptyList())

    /** What was copied or cut, ready to paste. */
    var clipboard by mutableStateOf<Clip?>(null)
        private set

    fun copy(strokes: List<InkStroke>, images: List<com.nzfrankwu.noteapp.domain.ink.InkImage> = emptyList()) {
        if (strokes.isNotEmpty() || images.isNotEmpty()) clipboard = Clip(strokes, images)
    }

    fun cut(pageId: String, strokes: List<InkStroke>, images: List<com.nzfrankwu.noteapp.domain.ink.InkImage> = emptyList()) {
        copy(strokes, images)
        deleteSelection(pageId, strokes.mapTo(HashSet()) { it.id }, images.mapTo(HashSet()) { it.id })
    }

    /** Adds a copy a little down and to the right (one undo step); returns the copy (for selecting it). */
    fun duplicate(pageId: String, strokes: List<InkStroke>, images: List<com.nzfrankwu.noteapp.domain.ink.InkImage> = emptyList()): Clip {
        if (!isEditable(pageId) || (strokes.isEmpty() && images.isEmpty())) return Clip(emptyList())
        val copies = Clip(
            strokes.map { it.mapped(1f, 0f, 0f, DUPLICATE_OFFSET, DUPLICATE_OFFSET, newId = java.util.UUID.randomUUID().toString()) },
            // A copied picture shares the same picture file (files are never deleted while in use).
            images.map { it.copy(id = java.util.UUID.randomUUID().toString(), left = it.left + DUPLICATE_OFFSET, top = it.top + DUPLICATE_OFFSET) },
        )
        ink.add(pageId, copies.strokes, copies.images)
        changed(pageId)
        return copies
    }

    /** Pastes the clipboard centred on ([x], [y]) (page units); returns what was pasted. */
    fun paste(pageId: String, x: Float, y: Float): Clip {
        val clip = clipboard ?: return Clip(emptyList())
        if (!isEditable(pageId)) return Clip(emptyList())
        val box = InkGeometry.boundsOf(clip.strokes, clip.images) ?: return Clip(emptyList())
        val dx = x - (box[0] + box[2]) / 2
        val dy = y - (box[1] + box[3]) / 2
        val pasted = Clip(
            clip.strokes.map { it.mapped(1f, 0f, 0f, dx, dy, newId = java.util.UUID.randomUUID().toString()) },
            clip.images.map { it.copy(id = java.util.UUID.randomUUID().toString(), left = it.left + dx, top = it.top + dy) },
        )
        ink.add(pageId, pasted.strokes, pasted.images)
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
            is InkEdit.Add, is InkEdit.Remove, is InkEdit.Replace,
            is InkEdit.AddImages, is InkEdit.RemoveImages, is InkEdit.ReplaceImages, is InkEdit.Group,
            -> edit.changedPages.forEach { changed(it) }
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
    suspend fun addPage(
        index: Int,
        recordUndo: Boolean = true,
        size: com.nzfrankwu.noteapp.domain.PageSize? = null,
        template: com.nzfrankwu.noteapp.domain.PageTemplate? = null,
    ): String? {
        val result = notebooks.addPage(notebookId, index, size, template = template)
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
        // Strokes AND pictures: a save must never drop a page's pictures.
        val toSave = dirty.associateWith { ink.strokes(it).toList() to ink.images(it).toList() }
        dirty.clear()
        // The tile shows the first page: redraw it only when that page changed.
        val thumbnail = if (pages.value.firstOrNull()?.id in toSave) thumbnailJob() else null
        return saveScope.launch {
            saveLock.withLock {
                val saved = ArrayList<String>()
                for ((pageId, content) in toSave) {
                    // A failed write is retried here, in the scope that outlives the screen, so a
                    // save made while leaving the notebook still gets its retries.
                    if (writeWithRetries(pageId, content.first, content.second)) {
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
    private suspend fun writeWithRetries(pageId: String, strokes: List<InkStroke>, images: List<com.nzfrankwu.noteapp.domain.ink.InkImage>): Boolean {
        for (pause in SAVE_RETRY_PAUSES_MS) {
            delay(pause)
            try {
                withContext(io) { files.write(notebookId, pageId, strokes, images) }
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
        val pdfFile = pdfFileOf(first)
        val pdfPage = first.pdfPage ?: 0
        val images = ink.images(first.id).mapNotNull { img -> runCatching { img to files.assetFile(notebookId, img.asset) }.getOrNull() }
        return { t.render(notebookId, paper, size.width, size.height, strokes, pdfFile, pdfPage, images) }
    }

    /** Redraws the tile picture (e.g. after the first page changed). */
    private fun refreshThumbnail(template: com.nzfrankwu.noteapp.domain.PageTemplate? = null) {
        val job = thumbnailJob(template) ?: return
        saveScope.launch(io) { runCatching { job() } }
    }

    override fun onCleared() {
        saveNow()
        pdf.close()
        super.onCleared()
    }

    companion object {
        /** Pictures are shown shrunk to about this many pixels on their longest side. */
        const val IMAGE_SCREEN_PX = 2048

        /** Each further picture inserted at once sits this much (page units) lower-right. */
        private const val CASCADE_STEP = 24f

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
