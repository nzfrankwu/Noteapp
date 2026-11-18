package com.nzfrankwu.noteapp.ui.notebook

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nzfrankwu.noteapp.data.db.Page
import com.nzfrankwu.noteapp.domain.ink.EditorTool
import com.nzfrankwu.noteapp.domain.ink.InkToolSettings
import com.nzfrankwu.noteapp.domain.ink.PageStrip
import com.nzfrankwu.noteapp.domain.ink.TextSelection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * One notebook's pages in the editor (PHASE-5.md 5.6): its view model, where its pages are on
 * screen ([view]), its page strip, and a layer its pages can be captured from. The editor shows
 * one pane, or two side by side (split view); its top bar and toolbar work on the active one.
 */
@Stable
/** Words to find on the page a notebook opens at (a search hit, PHASE-7.md 7.9), or null. */
val LocalOpenAtFind = androidx.compose.runtime.staticCompositionLocalOf<String?> { null }

/** Opens "Find in this notebook" for a notebook ID (PHASE-7.md 7.9); null where there's none. */
val LocalFindInNotebook = androidx.compose.runtime.staticCompositionLocalOf<((String) -> Unit)?> { null }

/** A page picked in "Find in this notebook": the open editor goes there itself (keeping its undo and split view). */
data class FindRequest(
    val notebookId: String,
    val pageId: String,
    val query: String,
    /** Where a handwritten match is on the page (its line); null for PDF and text box matches. */
    val spot: com.nzfrankwu.noteapp.domain.PageSpot? = null,
)

/** Where "Find in this notebook" leaves the page to go to, for the notebook already open. */
val LocalFindRequests = androidx.compose.runtime.staticCompositionLocalOf<kotlinx.coroutines.flow.MutableStateFlow<FindRequest?>?> { null }

class PaneState(val viewModel: NotebookViewModel) {
    val view = EditorViewState()

    /** The pages laid out side by side (null until the notebook has loaded). */
    var strip by mutableStateOf<PageStrip?>(null)
        internal set

    /** True while the pages are being recorded into [layer] to be read back as a picture. */
    var capturing by mutableStateOf(false)
        private set

    internal var layer: GraphicsLayer? = null

    val pages: List<Page> get() = viewModel.pages.value

    /** The page in the middle of the pane right now (read when a button is tapped). */
    fun pageNow(): Int = view.camera?.let { strip?.currentPage(it, view.viewSize.width.toFloat()) } ?: 0

    /** A picture of the pages as they're drawn now (overlays too), or null. */
    suspend fun capture(): android.graphics.Bitmap? {
        capturing = true
        try {
            withFrameNanos { }
            withFrameNanos { }
            return runCatching { layer?.toImageBitmap()?.asAndroidBitmap() }.getOrNull()
        } finally {
            capturing = false
        }
    }

    /** Shows page [index] once the page list has caught up with pages just added (so it's fitted to its own size). */
    fun showPage(scope: CoroutineScope, index: Int, settings: InkToolSettings) {
        scope.launch {
            val s = kotlinx.coroutines.withTimeoutOrNull(2_000) {
                snapshotFlow { strip }.first { it != null && it.pageCount > index }
            } ?: strip ?: return@launch
            view.animateTo(scope, s.fit(settings.fitMode, index.coerceAtMost(s.pageCount - 1), view.viewSize.width.toFloat(), view.viewSize.height.toFloat()))
        }
    }

    /** Finishes typing in a text box, if one is open (before anything else changes the notebook). */
    fun finishTyping() = finishTextEditing(view, viewModel)
}

/**
 * Draws [pane]'s pages and the menus that belong to them (lasso, PDF text, text box, Paste, page
 * scroller), fits the page when the size is known, keeps the page across rotations, and opens at a
 * bookmark ([openAtPage], [openAtSpot]). Things that need the whole editor (the bookmark sheet,
 * cropping a picture) go out through [onBookmark] and [onCrop].
 */
@Composable
fun NotebookPane(
    pane: PaneState,
    settings: InkToolSettings,
    snackbar: SnackbarHostState,
    onBookmark: ((BookmarkRequest) -> Unit)?,
    onCrop: (CropTarget) -> Unit,
    modifier: Modifier = Modifier,
    openAtPage: String? = null,
    openAtSpot: com.nzfrankwu.noteapp.domain.PageSpot? = null,
) {
    val viewModel = pane.viewModel
    val view = pane.view
    val notebook by viewModel.notebook.collectAsStateWithLifecycle()
    val pages by viewModel.pages.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val layer = rememberGraphicsLayer()
    SideEffect { pane.layer = layer }

    // Link notices (PHASE-4.md), cleared as soon as they're taken (so one never shows twice).
    val links = viewModel.links
    LaunchedEffect(links) {
        links ?: return@LaunchedEffect
        links.notice.filterNotNull().collectLatest { notice ->
            links.noticeShown()
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(
                notice.text,
                actionLabel = if (notice.undo != null) "Undo" else null,
                duration = if (notice.undo != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) notice.undo?.invoke()
        }
    }
    // A text box being typed in is kept too when the app goes to the background (Phase 5).
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { pane.finishTyping(); viewModel.saveNow() }
    DisposableEffect(pane) { onDispose { pane.finishTyping() } }
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); viewModel.messageShown() }
    }
    val damaged = viewModel.pageStatus.values.count { it is PageStatus.Damaged }
    LaunchedEffect(damaged) {
        if (damaged > 0) snackbar.showSnackbar("A page couldn't be read. Its file is kept safe and won't be changed.")
    }
    LaunchedEffect(settings.tool) { if (settings.tool != EditorTool.LASSO) view.lasso = null }
    // Selected text remembers its page's place, which moves when pages are added or deleted.
    val pageIds = pages.map { it.id }
    LaunchedEffect(pageIds) { view.textSel = null }

    val nb = notebook
    val sizes = nb?.let { n -> pages.map { it.size(n) } }
    val strip = remember(sizes) { sizes?.let { PageStrip(it) } }
    SideEffect { pane.strip = strip }
    // Fit the page once the pane's size is known, and refit the same page after rotating.
    // Rotating rebuilds the screen, so the page being viewed is kept in saved state.
    val viewSize = view.viewSize
    var fittedFor by remember { mutableStateOf(IntSize.Zero) }
    var savedPage by rememberSaveable { mutableIntStateOf(0) }
    var openedAtBookmark by rememberSaveable { mutableStateOf(false) }
    val openAtFind = LocalOpenAtFind.current
    var foundOnOpen by rememberSaveable { mutableStateOf(false) }
    val findScope = androidx.compose.runtime.rememberCoroutineScope()
    val findRequests = LocalFindRequests.current
    // "Find in this notebook" picked a page of this notebook: go there and show the words.
    LaunchedEffect(findRequests, strip) {
        val requests = findRequests ?: return@LaunchedEffect
        val s = strip ?: return@LaunchedEffect
        requests.collect { r ->
            if (r == null || r.notebookId != pane.viewModel.notebookId) return@collect
            // Coming back from the search screen rebuilds this pane: wait until it has its size
            // and has done its first fit, or that fit would put it back on the old page (and a
            // request taken before the size was known used to be dropped).
            val size = snapshotFlow { view.viewSize.takeIf { it.width > 0 && it.height > 0 && fittedFor == it } }
                .filterNotNull().first()
            if (requests.value != r) return@collect
            requests.value = null
            val index = pages.indexOfFirst { it.id == r.pageId }
            if (index < 0) return@collect
            val w = size.width.toFloat()
            val h = size.height.toFloat()
            val spot = r.spot?.takeIf { it.isValid }
            if (spot != null) {
                // A handwritten match: zoom to its line, as when opened from Home's search.
                view.camera = s.focus(index, spot.left, spot.top, spot.width, spot.height, w, h)
            } else {
                view.camera = s.fit(settings.fitMode, index, w, h)
                findScope.launch { showFound(pane, pages[index], index, r.query, s, w, h) }
            }
        }
    }
    LaunchedEffect(viewSize, strip != null && pages.isNotEmpty()) {
        if (strip == null || pages.isEmpty() || viewSize.width == 0 || fittedFor == viewSize) return@LaunchedEffect
        // Opened at a bookmark: that page, zoomed to its spot (once; afterwards it's like any other page).
        val bookmarked = if (!openedAtBookmark && view.camera == null) pages.indexOfFirst { it.id == openAtPage } else -1
        if (bookmarked >= 0) {
            val w = viewSize.width.toFloat()
            val h = viewSize.height.toFloat()
            view.camera = openAtSpot?.takeIf { it.isValid }?.let { strip.focus(bookmarked, it.left, it.top, it.width, it.height, w, h) }
                ?: strip.fit(settings.fitMode, bookmarked, w, h)
            openedAtBookmark = true
            fittedFor = viewSize
            // Opened from a search (PHASE-7.md 7.9): select the words found and zoom to them.
            if (openAtFind != null && !foundOnOpen) {
                foundOnOpen = true
                val page = pages[bookmarked]
                findScope.launch { showFound(pane, page, bookmarked, openAtFind, strip, w, h) }
            }
            return@LaunchedEffect
        }
        val page = (view.camera?.let { strip.currentPage(it, fittedFor.width.toFloat()) } ?: savedPage)
            .coerceIn(0, pages.size - 1)
        view.camera = strip.fit(settings.fitMode, page, viewSize.width.toFloat(), viewSize.height.toFloat())
        fittedFor = viewSize
    }
    LaunchedEffect(strip) {
        if (strip == null) return@LaunchedEffect
        snapshotFlow { view.camera?.let { strip.currentPage(it, view.viewSize.width.toFloat()) } }
            .collect { if (it != null) savedPage = it }
    }

    // The bookmark pen picked part of a page (PHASE-5.md FB5): a picture of it as it looks on
    // screen (the pen's loop is gone first), then the bookmark sheet.
    LaunchedEffect(pane, strip) {
        snapshotFlow { view.bookmarkArea }.filterNotNull().collect { area ->
            view.bookmarkArea = null
            val s = strip ?: return@collect
            val cam = view.camera ?: return@collect
            val b = area.box
            val full = pane.capture()
            val picture = full?.let { bmp ->
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    cropForBookmark(
                        bmp,
                        cam.toScreenX(s.pageLeft(area.pageIndex) + b[0]), cam.toScreenY(s.pageTop(area.pageIndex) + b[1]),
                        cam.toScreenX(s.pageLeft(area.pageIndex) + b[2]), cam.toScreenY(s.pageTop(area.pageIndex) + b[3]),
                    )
                }
            }
            onBookmark?.invoke(
                BookmarkRequest(
                    area.pageId, area.pageIndex + 1,
                    spot = com.nzfrankwu.noteapp.domain.PageSpot(b[0], b[1], b[2] - b[0], b[3] - b[1]),
                    note = "",
                    picture = picture,
                ),
            )
        }
    }

    Box(modifier) {
        if (nb != null && pages.isNotEmpty()) {
            Box(
                Modifier.fillMaxSize().drawWithContent {
                    // While capturing, the pages are recorded into a layer that can be read back.
                    if (pane.capturing) {
                        layer.record { this@drawWithContent.drawContent() }
                        drawLayer(layer)
                    } else {
                        drawContent()
                    }
                },
            ) {
                NotebookCanvas(viewModel = viewModel, notebook = nb, pages = pages, settings = settings, view = view)
            }
        }
        if (strip != null) {
            LassoMenu(
                view, strip,
                onResize = { sel -> view.lasso = sel.copy(handles = !sel.handles) },
                onCrop = { sel ->
                    val image = sel.images.single()
                    scope.launch {
                        val full = viewModel.loadFullPicture(image)
                        if (full == null) snackbar.showSnackbar("That picture couldn't be read.")
                        else onCrop(CropTarget(sel.pageIndex, sel.pageId, image, full))
                    }
                },
                onDuplicate = { sel ->
                    val copies = viewModel.duplicate(sel.pageId, sel.strokes, sel.images, sel.texts)
                    view.lasso = LassoState.Selected.of(sel.pageIndex, sel.pageId, copies.strokes, copies.images, handles = sel.handles, texts = copies.texts) ?: sel
                },
                onCopy = { sel ->
                    viewModel.copy(sel.strokes, sel.images, sel.texts)
                    scope.launch { snackbar.showSnackbar("Copied. Hold a finger on a page to paste.") }
                },
                onCut = { sel ->
                    viewModel.cut(sel.pageId, sel.strokes, sel.images, sel.texts)
                    view.lasso = null
                    scope.launch { snackbar.showSnackbar("Cut. Hold a finger on a page to paste.") }
                },
                onDelete = { sel ->
                    viewModel.deleteSelection(sel.pageId, sel.strokes.mapTo(HashSet()) { it.id }, sel.images.mapTo(HashSet()) { it.id }, sel.texts.mapTo(HashSet()) { it.id })
                    view.lasso = null
                },
                onBookmark = if (onBookmark == null) null else { sel ->
                    val b = sel.box()
                    onBookmark(
                        BookmarkRequest(
                            sel.pageId, sel.pageIndex + 1,
                            spot = com.nzfrankwu.noteapp.domain.PageSpot(b[0], b[1], b[2] - b[0], b[3] - b[1]),
                            note = "",
                        ),
                    )
                },
            )
            TextMenu(
                view, strip,
                onCopy = { sel ->
                    val clip = android.content.ClipData.newPlainText("PDF text", TextSelection.copyText(sel.text, sel.range))
                    context.getSystemService(android.content.ClipboardManager::class.java)?.setPrimaryClip(clip)
                    view.textSel = null
                    // Android 13+ shows its own "Copied" confirmation.
                    if (android.os.Build.VERSION.SDK_INT < 33) scope.launch { snackbar.showSnackbar("Copied.") }
                },
                onHighlight = { sel ->
                    val style = settings.highlighter
                    val strokes = TextSelection.strokes(
                        TextSelection.highlightMarks(sel.text, sel.range), com.nzfrankwu.noteapp.domain.ink.InkTool.HIGHLIGHTER,
                        style.colorArgb, size = null,
                    ) { java.util.UUID.randomUUID().toString() }
                    viewModel.addTextMarks(sel.pageId, strokes)
                    view.textSel = null
                },
                onUnderline = { sel ->
                    // The pen in use, or else the first pen: its colour, at a neat steady width.
                    val style = settings.penStyle(settings.tool)?.takeIf { settings.tool.penSlot != null } ?: settings.pens.first()
                    val strokes = TextSelection.strokes(
                        TextSelection.underlineMarks(sel.text, sel.range, UNDERLINE_WIDTH), com.nzfrankwu.noteapp.domain.ink.InkTool.PEN_STEADY,
                        style.colorArgb, size = UNDERLINE_WIDTH,
                    ) { java.util.UUID.randomUUID().toString() }
                    viewModel.addTextMarks(sel.pageId, strokes)
                    view.textSel = null
                },
                onBookmark = if (onBookmark == null) null else { sel ->
                    onBookmark(
                        BookmarkRequest(
                            sel.pageId, sel.pageIndex + 1,
                            spot = TextSelection.spotOf(sel.text, sel.range),
                            note = com.nzfrankwu.noteapp.domain.Bookmarks.noteFromSelectedText(TextSelection.copyText(sel.text, sel.range)),
                        ),
                    )
                    view.textSel = null
                },
            )
            TextBoxEditor(view, strip, viewModel)
            PasteBubble(view) { at ->
                view.pasteAt = null
                val cam = view.camera ?: return@PasteBubble
                val wx = cam.toWorldX(at.x)
                val index = strip.pageAt(wx)
                val page = pages.getOrNull(index) ?: return@PasteBubble
                val x = wx - strip.pageLeft(index)
                val y = cam.toWorldY(at.y) - strip.pageTop(index)
                scope.launch {
                    val pasted = viewModel.paste(page.id, x, y)
                    if (!pasted.isEmpty) {
                        // Pasted things come selected, ready to move (that needs the lasso).
                        if (settings.tool != EditorTool.LASSO) viewModel.pickTool(EditorTool.LASSO)
                        view.lasso = LassoState.Selected.of(index, page.id, pasted.strokes, pasted.images, texts = pasted.texts)
                    }
                }
            }
            PageScroller(view, strip, pages.size)
        }
    }
}

/**
 * Shows where the search words are on page [index] (PHASE-7.md 7.9): on a PDF page the matching
 * words are selected (Copy, Highlight, Bookmark work as usual) and zoomed to; otherwise the first
 * text box holding them is zoomed to. Nothing happens if they aren't found (the page is shown).
 */
private suspend fun showFound(pane: PaneState, page: com.nzfrankwu.noteapp.data.db.Page, index: Int, query: String, strip: PageStrip, w: Float, h: Float) {
    val words = com.nzfrankwu.noteapp.data.SearchIndex.words(query)
    if (words.isEmpty()) return
    val vm = pane.viewModel
    val view = pane.view
    if (page.isPdf) {
        val text = vm.pageText(page)
        val range = com.nzfrankwu.noteapp.domain.ink.TextSelection.find(text, words) ?: return
        view.textSel = TextSel(index, page.id, text, range)
        val spot = com.nzfrankwu.noteapp.domain.ink.TextSelection.spotOf(text, range)
        focusAround(pane, page, index, spot.top, spot.height, strip, w, h)
        return
    }
    // Text boxes: wait for the page to load (a few seconds at most).
    kotlinx.coroutines.withTimeoutOrNull(5_000) {
        while (!vm.isEditable(page.id)) kotlinx.coroutines.delay(50)
    } ?: return
    val box = vm.ink.texts(page.id).firstOrNull { t -> words.all { t.text.contains(it, ignoreCase = true) } }
        ?: vm.ink.texts(page.id).firstOrNull { t -> t.text.contains(words[0], ignoreCase = true) }
        ?: return
    focusAround(pane, page, index, box.top, box.height, strip, w, h)
}

/** Zooms to a band across the whole page width around [top]..[top]+[height], so the words show in context. */
private fun focusAround(pane: PaneState, page: com.nzfrankwu.noteapp.data.db.Page, index: Int, top: Float, height: Float, strip: PageStrip, w: Float, h: Float) {
    val nb = pane.viewModel.notebook.value ?: return
    val size = page.size(nb)
    val band = (size.height * 0.5f).coerceAtLeast(height)
    val bandTop = (top + height / 2f - band / 2f).coerceIn(0f, (size.height - band).coerceAtLeast(0f))
    pane.view.camera = strip.focus(index, 0f, bandTop, size.width, band, w, h)
}
