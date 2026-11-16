package com.nzfrankwu.noteapp.ui.notebook

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nzfrankwu.noteapp.BuildConfig
import com.nzfrankwu.noteapp.R
import com.nzfrankwu.noteapp.domain.ink.TextSelection
import com.nzfrankwu.noteapp.domain.PageTemplate
import com.nzfrankwu.noteapp.domain.ink.EditorTool
import com.nzfrankwu.noteapp.domain.ink.InkToolSettings
import com.nzfrankwu.noteapp.domain.ink.PageStrip
import com.nzfrankwu.noteapp.ui.components.RoundIconButton
import com.nzfrankwu.noteapp.ui.theme.DeskDark
import com.nzfrankwu.noteapp.ui.theme.DeskLight
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * An open notebook (PHASE-2.md 2.6): the pages side by side filling the screen, floating
 * buttons over them (F17), the floating toolbar, and a page-number badge. Changes save themselves.
 *
 * Where the pages are on screen lives in [EditorViewState], which only the canvas and the small
 * overlays read, so scrolling and zooming don't rebuild this whole screen.
 */
@Composable
fun NotebookScreen(
    viewModel: NotebookViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** Opens a linked task's page (Phase 4). */
    onOpenTask: (String) -> Unit = {},
    /** A page to open at (a bookmark, Phase 4): its ID, and the spot to zoom to (null = the whole page). */
    openAtPage: String? = null,
    openAtSpot: com.nzfrankwu.noteapp.domain.PageSpot? = null,
) {
    val notebook by viewModel.notebook.collectAsStateWithLifecycle()
    val pages by viewModel.pages.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val view = remember { EditorViewState() }
    var showSettings by remember { mutableStateOf(false) }
    // Back closes the settings panel, then lets go of a lasso selection, before leaving.
    // (Read through derivedStateOf: dragging a selection must not recompose this screen.)
    val hasSelection by remember(view) { derivedStateOf { view.lasso != null || view.textSel != null || view.editingText != null || view.textMode } }
    BackHandler(enabled = showSettings || hasSelection) {
        when {
            showSettings -> showSettings = false
            view.editingText != null -> finishTextEditing(view, viewModel)
            view.textMode -> view.textMode = false
            view.textSel != null -> view.textSel = null
            else -> view.lasso = null
        }
    }
    // Touching the page (pen or finger) closes the tool's settings panel (F27, F29).
    LaunchedEffect(view) {
        var seen = view.pageTouches
        snapshotFlow { view.pageTouches }.collect { if (it != seen) { seen = it; showSettings = false } }
    }
    var menuOpen by remember { mutableStateOf(false) }
    var imageMenuOpen by remember { mutableStateOf(false) }
    // The screenshot tool (PHASE-3.md 3.9): the still picture being cropped, and a capture of the pages.
    var snip by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    // The "Add Page" menu (F34), and a PDF whose pages are being chosen.
    var addMenuOpen by remember { mutableStateOf(false) }
    var choosingPdf by remember { mutableStateOf<NotebookViewModel.CopiedPdf?>(null) }
    var pdfWhole by remember { mutableStateOf(false) }
    var addAfter by remember { mutableIntStateOf(0) }
    // A picture being cropped (F33).
    var cropping by remember { mutableStateOf<CropTarget?>(null) }
    var capturing by remember { mutableStateOf(false) }
    val pagesLayer = androidx.compose.ui.graphics.rememberGraphicsLayer()
    val context = androidx.compose.ui.platform.LocalContext.current
    // Android's screen-capture prompt (split screen); then the capture service takes one frame.
    val askToCapture = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        if (result.resultCode == android.app.Activity.RESULT_OK && data != null) {
            scope.launch {
                snip = com.nzfrankwu.noteapp.capture.ScreenCapture.capture(context, result.resultCode, data)
                if (snip == null) snackbar.showSnackbar("The screenshot couldn't be taken.")
            }
        }
    }

    // Links to tasks (PHASE-4.md 4.3): the chip by Back, and "Link to a task".
    val links = viewModel.links
    val linkedTasks by (links?.tasks ?: remember { kotlinx.coroutines.flow.MutableStateFlow(emptyList()) }).collectAsStateWithLifecycle()
    var linkingTask by rememberSaveable { mutableStateOf(false) }
    // Link notices, cleared as soon as they're taken (so one never shows twice).
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
    val zone = remember { java.time.ZoneId.systemDefault() }
    fun nowInstant(): java.time.Instant = java.time.Instant.now()
    // A bookmark being saved (PHASE-4.md 4.5): its page, spot and prefilled note.
    var bookmarking by remember { mutableStateOf<BookmarkRequest?>(null) }

    // A text box being typed in is kept too when the app goes to the background (Phase 5).
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { finishTextEditing(view, viewModel); viewModel.saveNow() }
    DisposableEffect(view, viewModel) { onDispose { finishTextEditing(view, viewModel) } }
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
    // The pen's side button, when it arrives as a key (the OPPO Pencil 2's does).
    DisposableEffect(viewModel) {
        val click: () -> Unit = { viewModel.penButtonClicked() }
        PenButtonKeyEvents.onClick = click
        onDispose { if (PenButtonKeyEvents.onClick === click) PenButtonKeyEvents.onClick = null }
    }

    val nb = notebook
    val sizes = nb?.let { n -> pages.map { it.size(n) } }
    val strip = remember(sizes) { sizes?.let { PageStrip(it) } }
    // Fit the page once the screen size is known, and refit the same page after rotating.
    // Rotating rebuilds the screen, so the page being viewed is kept in saved state.
    val viewSize = view.viewSize
    var fittedFor by remember { mutableStateOf(IntSize.Zero) }
    var savedPage by rememberSaveable { mutableIntStateOf(0) }
    var openedAtBookmark by rememberSaveable { mutableStateOf(false) }
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

    /** The page in the middle of the screen right now (read when a button is tapped). */
    fun pageNow(): Int = view.camera?.let { strip?.currentPage(it, view.viewSize.width.toFloat()) } ?: 0

    // "Add Page" (F34): photos and camera pictures become pages of their own; PDFs add their pages.
    val latestStrip by androidx.compose.runtime.rememberUpdatedState(strip)
    // Shows page [index] once the page list has caught up with pages just added (so it's fitted to its own size).
    fun showPage(index: Int) {
        scope.launch {
            val s = kotlinx.coroutines.withTimeoutOrNull(2_000) {
                androidx.compose.runtime.snapshotFlow { latestStrip }.first { it != null && it.pageCount > index }
            } ?: latestStrip ?: return@launch
            view.animateTo(scope, s.fit(settings.fitMode, index.coerceAtMost(s.pageCount - 1), view.viewSize.width.toFloat(), view.viewSize.height.toFloat()))
        }
    }
    fun addPicturePages(uris: List<android.net.Uri>) {
        val resolver = context.contentResolver
        scope.launch {
            var at = addAfter
            var failed = 0
            for (uri in uris) {
                if (viewModel.insertImageAsPage(at) { openPicture(resolver, uri) } != null) at++ else failed++
            }
            uris.filter { it.scheme == "file" }.forEach { runCatching { java.io.File(it.path!!).delete() } }
            if (at > addAfter) showPage(addAfter + 1)
            if (failed > 0) snackbar.showSnackbar(if (failed == 1) "A picture couldn't be read." else "$failed pictures couldn't be read.")
        }
    }
    val pickPagePhotos = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia(20),
    ) { uris -> if (uris.isNotEmpty()) addPicturePages(uris) }
    var pageCameraFile by remember { mutableStateOf<java.io.File?>(null) }
    val takePagePhoto = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicture(),
    ) { ok ->
        val file = pageCameraFile
        if (ok && file != null && file.length() > 0) addPicturePages(listOf(android.net.Uri.fromFile(file)))
    }
    val pickPagePdf = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val resolver = context.contentResolver
        scope.launch {
            val pdf = viewModel.copyPdfIn({ resolver.openInputStream(uri) }) { message -> scope.launch { snackbar.showSnackbar(message) } }
                ?: return@launch
            if (pdfWhole) {
                // "Add the entire PDF": every page, after the page on screen.
                if (viewModel.insertPdfPages(addAfter, pdf, pdf.sizes.indices.toList()) > 0) showPage(addAfter + 1)
            } else {
                choosingPdf = pdf
            }
        }
    }

    fun leave() {
        finishTextEditing(view, viewModel)
        viewModel.saveNow()
        onBack()
    }

    val dark = isSystemInDarkTheme()
    // No top bar (F17, like Notein): the pages fill the screen up to the status bar, and the
    // buttons float over them.
    Box(modifier.fillMaxSize().background(if (dark) DeskDark else DeskLight).statusBarsPadding()) {
        Box(Modifier.fillMaxSize()) {
            if (nb != null && pages.isNotEmpty()) {
                Box(
                    Modifier.fillMaxSize().drawWithContent {
                        // While capturing, the pages are recorded into a layer that can be read back.
                        if (capturing) {
                            pagesLayer.record { this@drawWithContent.drawContent() }
                            drawLayer(pagesLayer)
                        } else {
                            drawContent()
                        }
                    },
                ) {
                    NotebookCanvas(viewModel = viewModel, notebook = nb, pages = pages, settings = settings, view = view)
                }
            }
            FloatingToolbar(
                settings = settings,
                viewSize = viewSize,
                showSettings = showSettings,
                onShowSettings = { showSettings = it },
                viewModel = viewModel,
                onToolPicked = { finishTextEditing(view, viewModel); view.textMode = false },
            )
            if (strip != null) {
                LassoMenu(
                    view, strip,
                    onResize = { sel -> view.lasso = sel.copy(handles = !sel.handles) },
                    onCrop = { sel ->
                        val image = sel.images.single()
                        scope.launch {
                            val full = viewModel.loadFullPicture(image)
                            if (full == null) snackbar.showSnackbar("That picture couldn't be read.")
                            else cropping = CropTarget(sel.pageIndex, sel.pageId, image, full)
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
                    onBookmark = if (links == null) null else { sel ->
                        val b = sel.box()
                        bookmarking = BookmarkRequest(
                            sel.pageId, sel.pageIndex + 1,
                            spot = com.nzfrankwu.noteapp.domain.PageSpot(b[0], b[1], b[2] - b[0], b[3] - b[1]),
                            note = "",
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
                    onBookmark = if (links == null) null else { sel ->
                        bookmarking = BookmarkRequest(
                            sel.pageId, sel.pageIndex + 1,
                            spot = TextSelection.spotOf(sel.text, sel.range),
                            note = com.nzfrankwu.noteapp.domain.Bookmarks.noteFromSelectedText(TextSelection.copyText(sel.text, sel.range)),
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
                    val pasted = viewModel.paste(page.id, wx - strip.pageLeft(index), cam.toWorldY(at.y) - strip.pageTop(index))
                    if (!pasted.isEmpty) {
                        // Pasted things come selected, ready to move (that needs the lasso).
                        if (settings.tool != EditorTool.LASSO) viewModel.pickTool(EditorTool.LASSO)
                        view.lasso = LassoState.Selected.of(index, page.id, pasted.strokes, pasted.images, texts = pasted.texts)
                    }
                }
                PageScroller(view, strip, pages.size)
            }
            // ---- Floating buttons (F17) ----
            EditorTopBar(
                canUndo = viewModel.canUndo,
                canRedo = viewModel.canRedo,
                onBack = ::leave,
                onUndo = { finishTextEditing(view, viewModel); view.lasso = null; viewModel.undo() },
                onRedo = { finishTextEditing(view, viewModel); view.lasso = null; viewModel.redo() },
                textMode = view.textMode,
                onText = {
                    finishTextEditing(view, viewModel)
                    view.lasso = null
                    view.textMode = !view.textMode
                },
                onImage = { imageMenuOpen = !imageMenuOpen; menuOpen = false },
                onAddMenu = { view.lasso = null; imageMenuOpen = false; menuOpen = false; addAfter = pageNow(); addMenuOpen = true },
                onScreenshot = {
                    imageMenuOpen = false
                    menuOpen = false
                    view.lasso = null
                    val activity = context as? android.app.Activity
                    if (activity?.isInMultiWindowMode == true) {
                        // Sharing the screen with another app: Android's capture prompt, then one frame.
                        val manager = context.getSystemService(android.media.projection.MediaProjectionManager::class.java)
                        // Android 14+: ask for the whole screen (not "one app"), as IDEA.md §2.7 wants.
                        val ask = if (android.os.Build.VERSION.SDK_INT >= 34) {
                            manager.createScreenCaptureIntent(android.media.projection.MediaProjectionConfig.createConfigForDefaultDisplay())
                        } else {
                            manager.createScreenCaptureIntent()
                        }
                        runCatching { askToCapture.launch(ask) }
                    } else {
                        // Noteapp alone: a picture of the pages themselves (no prompt).
                        scope.launch {
                            capturing = true
                            androidx.compose.runtime.withFrameNanos { }
                            androidx.compose.runtime.withFrameNanos { }
                            snip = runCatching { com.nzfrankwu.noteapp.capture.ScreenCapture.readable(pagesLayer.toImageBitmap().asAndroidBitmap()) }.getOrNull()
                            capturing = false
                            if (snip == null) snackbar.showSnackbar("The screenshot couldn't be taken.")
                        }
                    }
                },
                imageMenuOpen = imageMenuOpen,
                onAddPage = {
                    view.lasso = null // a selection remembers its page's place, which moves
                    scope.launch {
                        val at = pageNow() + 1
                        if (viewModel.addPage(at) != null && strip != null) {
                            val grown = strip.withPageAdded(at)
                            view.animateTo(scope, grown.fit(settings.fitMode, at, view.viewSize.width.toFloat(), view.viewSize.height.toFloat()))
                        }
                    }
                },
                menuOpen = menuOpen,
                onMenuOpen = { menuOpen = it },
                onBookmark = if (links == null) null else {
                    {
                        val index = pageNow()
                        pages.getOrNull(index)?.let { bookmarking = BookmarkRequest(it.id, index + 1, spot = null, note = "") }
                    }
                },
                taskChip = {
                    if (links != null) {
                        LinkedTaskChip(
                            tasks = linkedTasks,
                            now = nowInstant(),
                            zone = zone,
                            onOpenTask = { viewModel.saveNow(); onOpenTask(it) },
                            onUnlink = links::unlink,
                            onLinkTask = { linkingTask = true },
                        )
                    }
                },
            ) {
                if (links != null && linkedTasks.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("Link to a task…") },
                        onClick = { menuOpen = false; linkingTask = true },
                        modifier = Modifier.testTag("menu-link-task"),
                    )
                    HorizontalDivider()
                }
                Text(
                    "Paper",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
                PageTemplate.entries.forEach { t ->
                    DropdownMenuItem(
                        text = { Text(if (nb?.template == t) "✓  ${t.label}" else "    ${t.label}") },
                        onClick = { menuOpen = false; viewModel.setTemplate(t) },
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Export as PDF") },
                    onClick = {
                        menuOpen = false
                        view.lasso = null
                        viewModel.exportPdf(java.io.File(context.cacheDir, EXPORT_DIR))
                    },
                    modifier = Modifier.testTag("export-pdf"),
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Delete this page", color = MaterialTheme.colorScheme.error) },
                    enabled = pages.size > 1,
                    onClick = {
                        menuOpen = false
                        view.lasso = null
                        pages.getOrNull(pageNow())?.let { viewModel.deletePage(it.id) }
                        scope.launch { snackbar.showSnackbar("Page deleted. Tap Undo to bring it back.") }
                    },
                )
                if (BuildConfig.DEBUG) {
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Add 50 pages of test writing (debug)") },
                        onClick = { menuOpen = false; viewModel.addTestPages(50) },
                        modifier = Modifier.testTag("add-test-pages"),
                    )
                }
            }
            if (imageMenuOpen) {
                // Under the Image button, at the right (like Notein): picking a picture inserts it.
                ImageMenu(
                    onInsert = { uris, asNewPage ->
                        imageMenuOpen = false
                        val resolver = context.contentResolver
                        val at = pageNow()
                        scope.launch {
                            val sources = uris.map { uri -> { openPicture(resolver, uri) } }
                            if (asNewPage) {
                                val pageId = viewModel.insertImageAsPage(at, sources.first())
                                if (pageId == null) snackbar.showSnackbar("That picture couldn't be read.")
                                else if (strip != null) {
                                    val grown = strip.withPageAdded(at + 1)
                                    view.animateTo(scope, grown.fit(settings.fitMode, at + 1, view.viewSize.width.toFloat(), view.viewSize.height.toFloat()))
                                }
                            } else {
                                val placed = viewModel.insertImages(at, sources)
                                val pageId = pages.getOrNull(at)?.id
                                if (placed.isNotEmpty() && pageId != null) {
                                    // New pictures come selected, ready to move or resize (PHASE-3.md).
                                    if (settings.tool != EditorTool.LASSO) viewModel.pickTool(EditorTool.LASSO)
                                    view.lasso = LassoState.Selected.of(at, pageId, emptyList(), placed)
                                }
                                if (placed.size < uris.size) {
                                    snackbar.showSnackbar(if (placed.isEmpty()) "That picture couldn't be read." else "${uris.size - placed.size} pictures couldn't be read.")
                                }
                            }
                            uris.filter { it.scheme == "file" }.forEach { runCatching { java.io.File(it.path!!).delete() } }
                        }
                    },
                    onDismiss = { imageMenuOpen = false },
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 64.dp, end = 12.dp),
                )
            }
            ExportDialogs(viewModel, nb?.title.orEmpty())
            val request = bookmarking
            if (request != null && links != null) {
                val linked by links.tasks.collectAsStateWithLifecycle()
                val all by links.allChoices.collectAsStateWithLifecycle()
                val linkedIds = linked.mapTo(HashSet()) { it.task.id }
                BookmarkSheet(
                    pageNumber = request.pageNumber,
                    linked = linked,
                    others = com.nzfrankwu.noteapp.data.TaskChoices(
                        all.inCourse.filter { it.task.id !in linkedIds },
                        all.others.filter { it.task.id !in linkedIds },
                        all.courseId,
                    ),
                    note = request.note,
                    now = nowInstant(),
                    zone = zone,
                    onSave = { task, note ->
                        bookmarking = null
                        links.addBookmark(task, request.pageId, note, request.spot)
                    },
                    onCancel = { bookmarking = null },
                )
            }
            if (linkingTask && links != null) {
                val choices by links.choices.collectAsStateWithLifecycle()
                LinkTaskSheet(
                    choices = choices,
                    now = nowInstant(),
                    zone = zone,
                    onPick = { linkingTask = false; links.link(it) },
                    onCancel = { linkingTask = false },
                )
            }
            if (addMenuOpen) {
                AddPageDialog(
                    current = pages.getOrNull(addAfter)?.template ?: nb?.template ?: PageTemplate.DEFAULT,
                    onDismiss = { addMenuOpen = false },
                    onChoice = { choice ->
                        addMenuOpen = false
                        when (choice) {
                            is AddPageChoice.Blank -> scope.launch {
                                if (viewModel.addPage(addAfter + 1, template = choice.template) != null) showPage(addAfter + 1)
                            }
                            AddPageChoice.Photos -> pickPagePhotos.launch(
                                androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                            AddPageChoice.Camera -> {
                                val dir = java.io.File(context.cacheDir, CAMERA_DIR).apply { mkdirs() }
                                val file = java.io.File(dir, "page-${System.currentTimeMillis()}.jpg")
                                pageCameraFile = file
                                runCatching { takePagePhoto.launch(androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".files", file)) }
                            }
                            AddPageChoice.PdfPages -> { pdfWhole = false; pickPagePdf.launch(arrayOf("application/pdf")) }
                            AddPageChoice.WholePdf -> { pdfWhole = true; pickPagePdf.launch(arrayOf("application/pdf")) }
                        }
                    },
                )
            }
            choosingPdf?.let { pdf ->
                PdfPagePicker(
                    pageCount = pdf.sizes.size,
                    aspect = { pdf.sizes[it].width / pdf.sizes[it].height },
                    thumbnail = { i, px -> viewModel.pdfThumbnail(pdf, i, px) },
                    onDismiss = { choosingPdf = null; viewModel.discardPdf(pdf) },
                    onAdd = { chosen ->
                        choosingPdf = null
                        scope.launch {
                            val added = viewModel.insertPdfPages(addAfter, pdf, chosen)
                            if (added > 0) showPage(addAfter + 1)
                        }
                    },
                )
            }
            cropping?.let { target ->
                val image = remember(target) { target.picture.asImageBitmap() }
                SnipOverlay(
                    picture = image,
                    startWhole = true,
                    onCancel = { cropping = null },
                    onConfirm = { box ->
                        cropping = null
                        scope.launch {
                            val cropped = viewModel.cropImage(target.pageId, target.image, target.picture, box)
                            if (cropped == null) {
                                snackbar.showSnackbar("The picture couldn't be cropped.")
                            } else {
                                view.lasso = LassoState.Selected.of(target.pageIndex, target.pageId, emptyList(), listOf(cropped))
                            }
                        }
                    },
                )
            }
            snip?.let { picture ->
                val image = remember(picture) { picture.asImageBitmap() }
                SnipOverlay(
                    picture = image,
                    onCancel = { snip = null },
                    onConfirm = { box ->
                        snip = null
                        val at = pageNow()
                        scope.launch {
                            val cropped = android.graphics.Bitmap.createBitmap(picture, box[0], box[1], box[2] - box[0], box[3] - box[1])
                            if (com.nzfrankwu.noteapp.capture.ScreenCapture.isAllBlack(cropped)) {
                                snackbar.showSnackbar("That app doesn't allow screenshots, so this part came out black.")
                                return@launch
                            }
                            val placed = viewModel.insertSnip(at, cropped)
                            val pageId = pages.getOrNull(at)?.id
                            if (placed.isNotEmpty() && pageId != null) {
                                // The snip comes selected, ready to drag (PHASE-3.md).
                                if (settings.tool != EditorTool.LASSO) viewModel.pickTool(EditorTool.LASSO)
                                view.lasso = LassoState.Selected.of(at, pageId, emptyList(), placed)
                            } else {
                                snackbar.showSnackbar("The screenshot couldn't be added.")
                            }
                        }
                    },
                )
            }
            if (viewModel.insertingImages) {
                androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter).testTag("inserting-images"))
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** A bookmark waiting for its task and note: page [pageId] (number [pageNumber]), at [spot] or the whole page. */
data class BookmarkRequest(val pageId: String, val pageNumber: Int, val spot: com.nzfrankwu.noteapp.domain.PageSpot?, val note: String)

/** A picture being cropped: where it is, and its full picture file. */
private class CropTarget(val pageIndex: Int, val pageId: String, val image: com.nzfrankwu.noteapp.domain.ink.InkImage, val picture: android.graphics.Bitmap)

/** Exported PDFs wait here (cache) to be shared or saved; see res/xml/file_paths.xml. */
private const val EXPORT_DIR = "exports"

/**
 * Export as PDF (PHASE-3.md 3.7): a progress dialog, then Share… (Android's share sheet) or
 * Save to device (Android's "save as").
 */
@Composable
private fun ExportDialogs(viewModel: NotebookViewModel, title: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var saved by remember { mutableStateOf<String?>(null) }
    val state = viewModel.exportState
    val saveAs = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri ->
        val file = (viewModel.exportState as? NotebookViewModel.ExportState.Ready)?.file
        if (uri != null && file != null) {
            scope.launch {
                saved = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                        "Saved."
                    }.getOrElse { "Couldn't save the PDF there." }
                }
            }
        }
    }
    when (state) {
        is NotebookViewModel.ExportState.Working -> androidx.compose.material3.AlertDialog(
            onDismissRequest = {},
            title = { Text("Exporting PDF…") },
            text = {
                Column {
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { if (state.total == 0) 0f else state.done.toFloat() / state.total },
                        modifier = Modifier.fillMaxWidth().testTag("export-progress"),
                    )
                    Text("Page ${state.done} of ${state.total}", modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = {},
            dismissButton = { androidx.compose.material3.TextButton(onClick = viewModel::cancelExport) { Text("Cancel") } },
        )
        is NotebookViewModel.ExportState.Ready -> androidx.compose.material3.AlertDialog(
            onDismissRequest = { saved = null; viewModel.exportShown() },
            title = { Text("PDF ready") },
            text = { Text(saved ?: "“${state.file.name}” is ready to share or save.") },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".files", state.file)
                        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(android.content.Intent.EXTRA_STREAM, uri)
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(android.content.Intent.createChooser(send, "Share PDF"))
                    },
                    modifier = Modifier.testTag("export-share"),
                ) { Text("Share…") }
            },
            dismissButton = {
                Row {
                    androidx.compose.material3.TextButton(onClick = { saveAs.launch(state.file.name) }, modifier = Modifier.testTag("export-save")) {
                        Text("Save to device")
                    }
                    androidx.compose.material3.TextButton(onClick = { saved = null; viewModel.exportShown() }) { Text("Close") }
                }
            },
        )
        NotebookViewModel.ExportState.Failed -> androidx.compose.material3.AlertDialog(
            onDismissRequest = viewModel::exportShown,
            title = { Text("Couldn't export") },
            text = { Text("The PDF couldn't be made. Your notes are safe; try again.") },
            confirmButton = { androidx.compose.material3.TextButton(onClick = viewModel::exportShown) { Text("OK") } },
        )
        null -> Unit
    }
}

/** Opens a chosen picture: from another app (content://) or our own camera file (file://, cache only). */
private fun openPicture(resolver: android.content.ContentResolver, uri: android.net.Uri): java.io.InputStream? =
    if (uri.scheme == "file") uri.path?.let { java.io.File(it).inputStream() } else resolver.openInputStream(uri)

/** The floating toolbar (or its tucked-away button), where it was left; its settings panel beside it. */
@Composable
private fun FloatingToolbar(
    settings: InkToolSettings,
    viewSize: IntSize,
    showSettings: Boolean,
    onShowSettings: (Boolean) -> Unit,
    viewModel: NotebookViewModel,
    /** A tool was picked on the toolbar (it turns the T button off). */
    onToolPicked: () -> Unit = {},
) {
    val fullW = viewSize.width.toFloat()
    val fullH = viewSize.height.toFloat()
    if (fullW <= 0f) return
    val density = LocalDensity.current
    // A drag in progress (pixels); saved as fractions of the screen when the drag ends.
    var dragX by remember { mutableFloatStateOf(Float.NaN) }
    var dragY by remember { mutableFloatStateOf(Float.NaN) }
    val toolbarPx = with(density) { ToolbarWidth.toPx() }
    val margin = with(density) { 8.dp.toPx() }
    val x = if (dragX.isNaN()) settings.toolbarX * fullW else dragX
    val y = if (dragY.isNaN()) settings.toolbarY * fullH else dragY
    val ox = x.coerceIn(margin, (fullW - toolbarPx - margin).coerceAtLeast(margin))
    val oy = y.coerceIn(margin, (fullH - with(density) { 120.dp.toPx() }).coerceAtLeast(margin))
    Box(Modifier.offset { IntOffset(ox.roundToInt(), oy.roundToInt()) }) {
        if (settings.toolbarCollapsed) {
            CollapsedToolbar(settings, onExpand = { viewModel.updateSettings { it.copy(toolbarCollapsed = false) } })
        } else {
            Row(verticalAlignment = Alignment.Top) {
                InkToolbar(
                    settings = settings,
                    onPickTool = { onShowSettings(false); onToolPicked(); viewModel.pickTool(it) },
                    onStyle = { tool, style -> viewModel.updateSettings { it.withStyle(tool, style) } },
                    onOpenSettings = { onShowSettings(!showSettings) },
                    onDrag = { dx, dy ->
                        dragX = (if (dragX.isNaN()) ox else dragX) + dx
                        dragY = (if (dragY.isNaN()) oy else dragY) + dy
                    },
                    onDragEnd = {
                        val fx = (dragX / fullW).coerceIn(0f, 1f)
                        val fy = (dragY / fullH).coerceIn(0f, 1f)
                        viewModel.updateSettings { it.copy(toolbarX = fx, toolbarY = fy) }
                        dragX = Float.NaN; dragY = Float.NaN
                    },
                    onCollapse = { onShowSettings(false); viewModel.updateSettings { it.copy(toolbarCollapsed = true) } },
                    onSettings = viewModel::updateSettings,
                )
                if (showSettings) {
                    ToolSettingsPanel(
                        settings = settings,
                        onStyle = { tool, style -> viewModel.updateSettings { it.withStyle(tool, style) } },
                        onChange = viewModel::updateSettings,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

/**
 * The menu under the lasso's selection (F12), as icons (PHASE-4.md 4.4): Resize, Crop (one
 * picture), Bookmark, Duplicate, Copy, Cut, Delete. There's no Done: touching the page outside
 * the selection lets it go (F15).
 */
@Composable
private fun LassoMenu(
    view: EditorViewState,
    strip: PageStrip,
    onResize: (LassoState.Selected) -> Unit,
    onCrop: (LassoState.Selected) -> Unit,
    onDuplicate: (LassoState.Selected) -> Unit,
    onCopy: (LassoState.Selected) -> Unit,
    onCut: (LassoState.Selected) -> Unit,
    onDelete: (LassoState.Selected) -> Unit,
    /** Bookmark the selection's spot for a task (Phase 4), or null when bookmarks aren't available. */
    onBookmark: ((LassoState.Selected) -> Unit)? = null,
) {
    val sel = view.lasso as? LassoState.Selected ?: return
    val cam = view.camera ?: return
    val gap = with(LocalDensity.current) { 12.dp.toPx() }
    var menuWidth by remember { mutableIntStateOf(0) }
    val box = sel.box()
    val bx = cam.toScreenX(strip.pageLeft(sel.pageIndex) + box[0])
        .coerceIn(8f, (view.viewSize.width - menuWidth - 8f).coerceAtLeast(8f))
    val by = (cam.toScreenY(strip.pageTop(sel.pageIndex) + box[3]) + gap).coerceAtMost(view.viewSize.height - 160f)
    val items = buildList {
        add(IconMenuItem(R.drawable.ic_open_in_full, if (sel.handles) "Resize (on)" else "Resize", "lasso-resize", selected = sel.handles) { onResize(sel) })
        // One picture on its own can be cropped (F33).
        if (sel.strokes.isEmpty() && sel.texts.isEmpty() && sel.images.size == 1) add(IconMenuItem(R.drawable.ic_crop, "Crop", "lasso-crop") { onCrop(sel) })
        if (onBookmark != null) add(IconMenuItem(R.drawable.ic_bookmark_add, "Bookmark for a task", "lasso-bookmark") { onBookmark(sel) })
        add(IconMenuItem(R.drawable.ic_library_add, "Duplicate", "lasso-duplicate") { onDuplicate(sel) })
        add(IconMenuItem(R.drawable.ic_content_copy, "Copy", "lasso-copy") { onCopy(sel) })
        add(IconMenuItem(R.drawable.ic_content_cut, "Cut", "lasso-cut") { onCut(sel) })
        add(IconMenuItem(R.drawable.ic_delete, "Delete", "lasso-delete", destructive = true) { onDelete(sel) })
    }
    IconMenu(
        items,
        Modifier
            .offset { IntOffset(bx.roundToInt(), by.roundToInt()) }
            .onSizeChanged { menuWidth = it.width }
            .testTag("lasso-menu"),
    )
}

/**
 * The menu for selected PDF text (PHASE-4.md 4.7), as icons: Copy, Highlight, Underline, Bookmark.
 * It sits above the selection, or below it when there's no room above.
 */
@Composable
private fun TextMenu(
    view: EditorViewState,
    strip: PageStrip,
    onCopy: (TextSel) -> Unit,
    onHighlight: (TextSel) -> Unit,
    onUnderline: (TextSel) -> Unit,
    onBookmark: ((TextSel) -> Unit)?,
) {
    val sel = view.textSel ?: return
    val cam = view.camera ?: return
    val density = LocalDensity.current
    val gap = with(density) { 12.dp.toPx() }
    val menuHeight = with(density) { 48.dp.toPx() }
    var menuWidth by remember { mutableIntStateOf(0) }
    val rects = TextSelection.lineRects(sel.text, sel.range)
    val pl = strip.pageLeft(sel.pageIndex)
    val pt = strip.pageTop(sel.pageIndex)
    // Centred on the part of the selection that's on screen.
    val screenW = view.viewSize.width.toFloat()
    val left = cam.toScreenX(pl + rects.minOf { it[0] }).coerceIn(0f, screenW)
    val right = cam.toScreenX(pl + rects.maxOf { it[2] }).coerceIn(0f, screenW)
    val top = cam.toScreenY(pt + rects.first()[1])
    val bottom = cam.toScreenY(pt + rects.last()[3]) + TEXT_KNOB_RADIUS * 2 + 4f
    val mx = ((left + right) / 2 - menuWidth / 2f).coerceIn(8f, (view.viewSize.width - menuWidth - 8f).coerceAtLeast(8f))
    // Above the text, clear of the floating buttons; else below the handles.
    val above = top - gap - menuHeight
    val my = if (above > with(density) { 72.dp.toPx() }) above else (bottom + gap).coerceAtMost(view.viewSize.height - menuHeight - 8f)
    val items = buildList {
        add(IconMenuItem(R.drawable.ic_content_copy, "Copy", "text-copy") { onCopy(sel) })
        add(IconMenuItem(R.drawable.ic_highlighter, "Highlight", "text-highlight") { onHighlight(sel) })
        add(IconMenuItem(R.drawable.ic_format_underlined, "Underline", "text-underline") { onUnderline(sel) })
        if (onBookmark != null) add(IconMenuItem(R.drawable.ic_bookmark_add, "Bookmark for a task", "text-bookmark") { onBookmark(sel) })
    }
    IconMenu(
        items,
        Modifier
            .offset { IntOffset(mx.roundToInt(), my.roundToInt()) }
            .onSizeChanged { menuWidth = it.width }
            .testTag("text-menu"),
    )
}

/** Underlines made from selected text: a neat steady line this wide (page units). */
private const val UNDERLINE_WIDTH = 1.2f

/** The Paste bubble where a finger was held (F12). */
@Composable
private fun PasteBubble(view: EditorViewState, onPaste: (androidx.compose.ui.geometry.Offset) -> Unit) {
    val at = view.pasteAt ?: return
    val lift = with(LocalDensity.current) { 56.dp.toPx() }
    IconMenu(
        listOf(IconMenuItem(R.drawable.ic_content_paste, "Paste", "paste-button") { onPaste(at) }),
        Modifier
            .offset { IntOffset((at.x - 28f).roundToInt(), (at.y - lift).roundToInt().coerceAtLeast(0)) }
            .testTag("paste-bubble"),
    )
}

/**
 * The page scroller (F20): a "3 / 12" handle on a track along the bottom, placed by how far
 * through the notebook you are. Hold it and slide to scroll through every page. It shows while
 * the pages move (and when the notebook opens), then fades away after [SCROLLER_FADE_MS]; while
 * hidden it doesn't take touches, so writing near the bottom isn't blocked.
 */
@Composable
private fun BoxScope.PageScroller(view: EditorViewState, strip: PageStrip, pageCount: Int) {
    if (pageCount == 0) return
    // Only changes (and re-lays-out the label) when the middle page does, not on every scroll step.
    val current by remember(view, strip) {
        derivedStateOf { view.camera?.let { strip.currentPage(it, view.viewSize.width.toFloat()) } ?: 0 }
    }
    var dragging by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf(false) }
    LaunchedEffect(view) {
        snapshotFlow { view.camera?.offsetX }.collectLatest {
            moving = true
            delay(SCROLLER_FADE_MS)
            moving = false
        }
    }
    val shown = moving || dragging
    val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(if (shown) 120 else 500), label = "scroller")
    var trackWidth by remember { mutableIntStateOf(0) }
    var thumbWidth by remember { mutableIntStateOf(0) }
    var thumbCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val inkStrip by rememberUpdatedState(strip)

    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            // Above the system's bottom swipe zone (home gesture), or a hold there leaves the app.
            .windowInsetsPadding(
                WindowInsets.mandatorySystemGestures.only(WindowInsetsSides.Bottom)
                    .union(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)),
            )
            .padding(horizontal = 24.dp, vertical = 6.dp)
            .height(40.dp)
            .onSizeChanged { trackWidth = it.width }
            .graphicsLayer { this.alpha = alpha },
    ) {
        // The track: a thin bar the handle slides along.
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .height(4.dp)
                .background(MaterialTheme.colorScheme.inverseSurface.copy(alpha = if (dragging) 0.45f else 0.25f), RoundedCornerShape(50)),
        )
        Surface(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset {
                    val cam = view.camera
                    val f = if (cam == null) 0f else inkStrip.scrollFraction(cam, view.viewSize.width.toFloat())
                    IntOffset((f * (trackWidth - thumbWidth).coerceAtLeast(0)).roundToInt(), 0)
                }
                .onSizeChanged { thumbWidth = it.width }
                .onGloballyPositioned { thumbCoords = it }
                .testTag("page-badge")
                .then(
                    if (!shown) Modifier else Modifier.pointerInput(view) {
                        // Hold and slide: the handle follows the finger (or pen), and the pages follow it.
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            dragging = true
                            view.stopMotion()
                            val startX = thumbCoords?.localToRoot(down.position)?.x ?: 0f
                            val startFraction = view.camera?.let { inkStrip.scrollFraction(it, view.viewSize.width.toFloat()) } ?: 0f
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val c = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!c.pressed) { c.consume(); break }
                                    c.consume()
                                    val x = thumbCoords?.localToRoot(c.position)?.x ?: continue
                                    val range = (trackWidth - thumbWidth).coerceAtLeast(1)
                                    val cam = view.camera ?: continue
                                    val w = view.viewSize.width.toFloat()
                                    val f = startFraction + (x - startX) / range
                                    view.fastScroll = true // pages flying past show their quick preview
                                    view.camera = inkStrip.atScrollFraction(cam, f, w, view.viewSize.height.toFloat())
                                }
                            } finally {
                                dragging = false
                                view.fastScroll = false
                            }
                        }
                    },
                ),
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = if (dragging) 0.95f else 0.8f),
            shadowElevation = if (dragging) 4.dp else 0.dp,
        ) {
            Text(
                "${current + 1} / $pageCount",
                color = MaterialTheme.colorScheme.inverseOnSurface,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
    }
}

/** The page scroller fades this long after the pages stop moving (ms). */
private const val SCROLLER_FADE_MS = 1_500L
