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
import androidx.compose.material3.SnackbarHostState
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
import com.nzfrankwu.noteapp.domain.PageTemplate
import com.nzfrankwu.noteapp.domain.ink.EditorTool
import com.nzfrankwu.noteapp.domain.ink.InkToolSettings
import com.nzfrankwu.noteapp.domain.ink.PageStrip
import com.nzfrankwu.noteapp.ui.components.RoundIconButton
import com.nzfrankwu.noteapp.ui.theme.DeskDark
import com.nzfrankwu.noteapp.ui.theme.DeskLight
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
    val hasSelection by remember(view) { derivedStateOf { view.lasso != null } }
    BackHandler(enabled = showSettings || hasSelection) {
        if (showSettings) showSettings = false else view.lasso = null
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

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.saveNow() }
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); viewModel.messageShown() }
    }
    val damaged = viewModel.pageStatus.values.count { it is PageStatus.Damaged }
    LaunchedEffect(damaged) {
        if (damaged > 0) snackbar.showSnackbar("A page couldn't be read. Its file is kept safe and won't be changed.")
    }
    LaunchedEffect(settings.tool) { if (settings.tool != EditorTool.LASSO) view.lasso = null }
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
    LaunchedEffect(viewSize, strip != null && pages.isNotEmpty()) {
        if (strip == null || pages.isEmpty() || viewSize.width == 0 || fittedFor == viewSize) return@LaunchedEffect
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

    fun leave() {
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
            )
            if (strip != null) {
                LassoMenu(
                    view, strip,
                    onResize = { sel -> view.lasso = sel.copy(handles = !sel.handles) },
                    onDuplicate = { sel ->
                        val copies = viewModel.duplicate(sel.pageId, sel.strokes, sel.images)
                        view.lasso = LassoState.Selected.of(sel.pageIndex, sel.pageId, copies.strokes, copies.images, handles = sel.handles) ?: sel
                    },
                    onCopy = { sel ->
                        viewModel.copy(sel.strokes, sel.images)
                        scope.launch { snackbar.showSnackbar("Copied. Hold a finger on a page to paste.") }
                    },
                    onCut = { sel ->
                        viewModel.cut(sel.pageId, sel.strokes, sel.images)
                        view.lasso = null
                        scope.launch { snackbar.showSnackbar("Cut. Hold a finger on a page to paste.") }
                    },
                    onDelete = { sel ->
                        viewModel.deleteSelection(sel.pageId, sel.strokes.mapTo(HashSet()) { it.id }, sel.images.mapTo(HashSet()) { it.id })
                        view.lasso = null
                    },
                )
                PasteBubble(view) { at ->
                    view.pasteAt = null
                    val cam = view.camera ?: return@PasteBubble
                    val wx = cam.toWorldX(at.x)
                    val index = strip.pageAt(wx)
                    val page = pages.getOrNull(index) ?: return@PasteBubble
                    val pasted = viewModel.paste(page.id, wx - strip.pageLeft(index), cam.toWorldY(at.y) - strip.pageTop(index))
                    if (pasted.strokes.isNotEmpty() || pasted.images.isNotEmpty()) {
                        // Pasted things come selected, ready to move (that needs the lasso).
                        if (settings.tool != EditorTool.LASSO) viewModel.pickTool(EditorTool.LASSO)
                        view.lasso = LassoState.Selected.of(index, page.id, pasted.strokes, pasted.images)
                    }
                }
                PageScroller(view, strip, pages.size)
            }
            // ---- Floating buttons (F17) ----
            EditorTopBar(
                canUndo = viewModel.canUndo,
                canRedo = viewModel.canRedo,
                onBack = ::leave,
                onUndo = { view.lasso = null; viewModel.undo() },
                onRedo = { view.lasso = null; viewModel.redo() },
                onImage = { imageMenuOpen = !imageMenuOpen; menuOpen = false },
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
            ) {
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
                    onPickTool = { onShowSettings(false); viewModel.pickTool(it) },
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
 * The menu under the lasso's selection (F12): Resize, Duplicate, Copy, Cut, Delete. There's no
 * Done: touching the page outside the selection lets it go (F15).
 */
@Composable
private fun LassoMenu(
    view: EditorViewState,
    strip: PageStrip,
    onResize: (LassoState.Selected) -> Unit,
    onDuplicate: (LassoState.Selected) -> Unit,
    onCopy: (LassoState.Selected) -> Unit,
    onCut: (LassoState.Selected) -> Unit,
    onDelete: (LassoState.Selected) -> Unit,
) {
    val sel = view.lasso as? LassoState.Selected ?: return
    val cam = view.camera ?: return
    val gap = with(LocalDensity.current) { 12.dp.toPx() }
    var menuWidth by remember { mutableIntStateOf(0) }
    val box = sel.box()
    val bx = cam.toScreenX(strip.pageLeft(sel.pageIndex) + box[0])
        .coerceIn(8f, (view.viewSize.width - menuWidth - 8f).coerceAtLeast(8f))
    val by = (cam.toScreenY(strip.pageTop(sel.pageIndex) + box[3]) + gap).coerceAtMost(view.viewSize.height - 160f)
    Surface(
        modifier = Modifier
            .offset { IntOffset(bx.roundToInt(), by.roundToInt()) }
            .onSizeChanged { menuWidth = it.width }
            .testTag("lasso-menu"),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
    ) {
        Row {
            TextButton(onClick = { onResize(sel) }) { Text(if (sel.handles) "Resize ✓" else "Resize") }
            TextButton(onClick = { onDuplicate(sel) }) { Text("Duplicate") }
            TextButton(onClick = { onCopy(sel) }) { Text("Copy") }
            TextButton(onClick = { onCut(sel) }) { Text("Cut") }
            TextButton(onClick = { onDelete(sel) }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        }
    }
}

/** The Paste bubble where a finger was held (F12). */
@Composable
private fun PasteBubble(view: EditorViewState, onPaste: (androidx.compose.ui.geometry.Offset) -> Unit) {
    val at = view.pasteAt ?: return
    val lift = with(LocalDensity.current) { 56.dp.toPx() }
    Surface(
        modifier = Modifier
            .offset { IntOffset((at.x - 40f).roundToInt(), (at.y - lift).roundToInt().coerceAtLeast(0)) }
            .testTag("paste-bubble"),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        shadowElevation = 6.dp,
    ) {
        TextButton(onClick = { onPaste(at) }) { Text("Paste", color = MaterialTheme.colorScheme.inverseOnSurface) }
    }
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
