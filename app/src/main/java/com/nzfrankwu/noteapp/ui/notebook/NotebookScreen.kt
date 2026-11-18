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
    val pane = remember(viewModel) { PaneState(viewModel) }
    NotebookEditor(
        panes = listOf(pane),
        active = pane,
        onBack = onBack,
        modifier = modifier,
        onOpenTask = onOpenTask,
    ) { show -> show(pane, Modifier.fillMaxSize(), openAtPage, openAtSpot) }
}

/**
 * The editor around one or two notebooks ([panes], PHASE-5.md 5.6): the floating buttons, the
 * toolbar, the menus, sheets and dialogs, all working on the [active] pane. [panesArea] lays the
 * panes out (one filling the screen, or two split); it's given a function that draws one pane.
 */
@Composable
internal fun NotebookEditor(
    panes: List<PaneState>,
    active: PaneState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenTask: (String) -> Unit = {},
    /** Extra items at the top of the ⋮ menu (split view's own). */
    extraMenu: @Composable androidx.compose.foundation.layout.ColumnScope.(close: () -> Unit) -> Unit = {},
    /** A button next to Back (the recent notebooks panel, 5.7). */
    leftButton: @Composable () -> Unit = {},
    /** Things drawn over the panes, under the floating buttons (the recent panel, 5.7). */
    overlay: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit = {},
    /** A picture of every pane at once (split view's screenshot, 5.10); null = the active pane's. */
    captureArea: (suspend () -> android.graphics.Bitmap?)? = null,
    panesArea: @Composable (show: @Composable (PaneState, Modifier, String?, com.nzfrankwu.noteapp.domain.PageSpot?) -> Unit) -> Unit,
) {
    val viewModel = active.viewModel
    val view = active.view
    val notebook by viewModel.notebook.collectAsStateWithLifecycle()
    val pages by viewModel.pages.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
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
    // Touching any page (pen or finger) closes the tool's settings panel (F27, F29).
    for (p in panes) {
        LaunchedEffect(p) {
            var seen = p.view.pageTouches
            snapshotFlow { p.view.pageTouches }.collect { if (it != seen) { seen = it; showSettings = false } }
        }
    }
    var menuOpen by remember { mutableStateOf(false) }
    var imageMenuOpen by remember { mutableStateOf(false) }
    // The screenshot tool (PHASE-3.md 3.9): the still picture being cropped.
    var snip by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    // A cropped screenshot waiting for "Insert into" (split view, PHASE-5.md 5.10).
    var snipChoice by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    // The "Add Page" menu (F34), and a PDF whose pages are being chosen.
    var addMenuOpen by remember { mutableStateOf(false) }
    var choosingPdf by remember { mutableStateOf<NotebookViewModel.CopiedPdf?>(null) }
    var pdfWhole by remember { mutableStateOf(false) }
    var addAfter by remember { mutableIntStateOf(0) }
    // A picture being cropped (F33), in the pane it's in.
    var cropping by remember { mutableStateOf<Pair<PaneState, CropTarget>?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    // The launchers below answer later: they work on the pane that's active then.
    val activeNow by rememberUpdatedState(active)
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
    val zone = remember { java.time.ZoneId.systemDefault() }
    fun nowInstant(): java.time.Instant = java.time.Instant.now()
    // A bookmark being saved (PHASE-4.md 4.5): its pane, page, spot and prefilled note.
    var bookmarking by remember { mutableStateOf<Pair<PaneState, BookmarkRequest>?>(null) }

    // The pen's side button, when it arrives as a key (the OPPO Pencil 2's does).
    DisposableEffect(Unit) {
        val click: () -> Unit = { activeNow.viewModel.penButtonClicked() }
        PenButtonKeyEvents.onClick = click
        onDispose { if (PenButtonKeyEvents.onClick === click) PenButtonKeyEvents.onClick = null }
    }

    val nb = notebook

    // "Add Page" (F34): photos and camera pictures become pages of their own; PDFs add their pages.
    fun addPicturePages(uris: List<android.net.Uri>) {
        val resolver = context.contentResolver
        val p = activeNow
        scope.launch {
            var at = addAfter
            var failed = 0
            for (uri in uris) {
                if (p.viewModel.insertImageAsPage(at) { openPicture(resolver, uri) } != null) at++ else failed++
            }
            uris.filter { it.scheme == "file" }.forEach { runCatching { java.io.File(it.path!!).delete() } }
            if (at > addAfter) p.showPage(scope, addAfter + 1, settings)
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
        val p = activeNow
        scope.launch {
            val pdf = p.viewModel.copyPdfIn({ resolver.openInputStream(uri) }) { message -> scope.launch { snackbar.showSnackbar(message) } }
                ?: return@launch
            if (pdfWhole) {
                // "Add the entire PDF": every page, after the page on screen.
                if (p.viewModel.insertPdfPages(addAfter, pdf, pdf.sizes.indices.toList()) > 0) p.showPage(scope, addAfter + 1, settings)
            } else {
                choosingPdf = pdf
            }
        }
    }

    // A screenshot's cropped part goes on the page in the middle of [p], selected (PHASE-3.md 3.9).
    fun insertSnipInto(p: PaneState, cropped: android.graphics.Bitmap) {
        val at = p.pageNow()
        scope.launch {
            val placed = p.viewModel.insertSnip(at, cropped)
            val pageId = p.pages.getOrNull(at)?.id
            if (placed.isNotEmpty() && pageId != null) {
                // The snip comes selected, ready to drag.
                if (settings.tool != EditorTool.LASSO) p.viewModel.pickTool(EditorTool.LASSO)
                p.view.lasso = LassoState.Selected.of(at, pageId, emptyList(), placed)
            } else {
                snackbar.showSnackbar("The screenshot couldn't be added.")
            }
        }
    }

    fun leave() {
        for (p in panes) {
            p.finishTyping()
            p.viewModel.saveNow()
        }
        onBack()
    }

    val dark = com.nzfrankwu.noteapp.ui.theme.LocalDarkTheme.current
    // No top bar (F17, like Notein): the pages fill the screen up to the status bar, and the
    // buttons float over them.
    Box(modifier.fillMaxSize().background(if (dark) DeskDark else DeskLight).statusBarsPadding()) {
        var areaSize by remember { mutableStateOf(IntSize.Zero) }
        Box(Modifier.fillMaxSize().onSizeChanged { areaSize = it }) {
            panesArea { p, m, openAtPage, openAtSpot ->
                NotebookPane(
                    pane = p,
                    settings = settings,
                    snackbar = snackbar,
                    onBookmark = if (p.viewModel.links == null) null else { request -> bookmarking = p to request },
                    onCrop = { target -> cropping = p to target },
                    modifier = m,
                    openAtPage = openAtPage,
                    openAtSpot = openAtSpot,
                )
            }
            FloatingToolbar(
                settings = settings,
                viewSize = areaSize,
                showSettings = showSettings,
                onShowSettings = { showSettings = it },
                viewModel = viewModel,
                onToolPicked = {
                    for (p in panes) {
                        p.finishTyping()
                        p.view.textMode = false
                    }
                },
            )
            // Over the toolbar, under the top bar: the recent panel and a notebook being dragged (5.7).
            overlay()
            // ---- Floating buttons (F17) ----
            val findInNotebook = LocalFindInNotebook.current
            EditorTopBar(
                // Its own button, top right (the user's wish, 18 Nov).
                onFind = findInNotebook?.let { find -> { menuOpen = false; viewModel.saveNow(); find(viewModel.notebookId) } },
                canUndo = viewModel.canUndo,
                canRedo = viewModel.canRedo,
                onBack = ::leave,
                onUndo = { active.finishTyping(); view.lasso = null; viewModel.undo() },
                onRedo = { active.finishTyping(); view.lasso = null; viewModel.redo() },
                textMode = view.textMode,
                onText = {
                    active.finishTyping()
                    view.lasso = null
                    view.textMode = !view.textMode
                },
                onImage = { imageMenuOpen = !imageMenuOpen; menuOpen = false },
                onAddMenu = { view.lasso = null; imageMenuOpen = false; menuOpen = false; addAfter = active.pageNow(); addMenuOpen = true },
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
                            val picture = captureArea?.invoke() ?: active.capture()
                            snip = picture?.let { runCatching { com.nzfrankwu.noteapp.capture.ScreenCapture.readable(it) }.getOrNull() }
                            if (snip == null) snackbar.showSnackbar("The screenshot couldn't be taken.")
                        }
                    }
                },
                imageMenuOpen = imageMenuOpen,
                onAddPage = {
                    view.lasso = null // a selection remembers its page's place, which moves
                    val p = active
                    scope.launch {
                        val at = p.pageNow() + 1
                        val s = p.strip
                        if (p.viewModel.addPage(at) != null && s != null) {
                            val grown = s.withPageAdded(at)
                            p.view.animateTo(scope, grown.fit(settings.fitMode, at, p.view.viewSize.width.toFloat(), p.view.viewSize.height.toFloat()))
                        }
                    }
                },
                menuOpen = menuOpen,
                onMenuOpen = { menuOpen = it },
                onBookmark = if (links == null) null else {
                    {
                        val index = active.pageNow()
                        pages.getOrNull(index)?.let { bookmarking = active to BookmarkRequest(it.id, index + 1, spot = null, note = "") }
                    }
                },
                leftButton = leftButton,
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
                extraMenu { menuOpen = false }
                if (links != null && linkedTasks.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("Link to a task…") },
                        onClick = { menuOpen = false; linkingTask = true },
                        modifier = Modifier.testTag("menu-link-task"),
                    )
                    HorizontalDivider()
                }
                // Phase 7 (7.3): this page's paper, or every page's. A PDF page has no paper of its own.
                val pageNow = pages.getOrNull(active.pageNow())
                if (pageNow != null && !pageNow.isPdf) {
                    Text(
                        "This page's paper",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                    val pagePaper = pageNow.template ?: nb?.template
                    PageTemplate.entries.forEach { t ->
                        DropdownMenuItem(
                            text = { Text(if (pagePaper == t) "✓  ${t.label}" else "    ${t.label}") },
                            onClick = { menuOpen = false; viewModel.setPageTemplate(pageNow.id, t) },
                            modifier = Modifier.testTag("page-paper-${t.key}"),
                        )
                    }
                    HorizontalDivider()
                }
                Text(
                    "Paper for all pages",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
                // Ticked only when every page uses it.
                val allSame = nb?.template?.takeIf { t -> pages.none { !it.isPdf && it.template != null && it.template != t } }
                PageTemplate.entries.forEach { t ->
                    DropdownMenuItem(
                        text = { Text(if (allSame == t) "✓  ${t.label}" else "    ${t.label}") },
                        onClick = { menuOpen = false; viewModel.setTemplate(t) },
                        modifier = Modifier.testTag("notebook-paper-${t.key}"),
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
                        pages.getOrNull(active.pageNow())?.let { viewModel.deletePage(it.id) }
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
                        val p = active
                        val at = p.pageNow()
                        scope.launch {
                            val sources = uris.map { uri -> { openPicture(resolver, uri) } }
                            if (asNewPage) {
                                val pageId = p.viewModel.insertImageAsPage(at, sources.first())
                                val s = p.strip
                                if (pageId == null) snackbar.showSnackbar("That picture couldn't be read.")
                                else if (s != null) {
                                    val grown = s.withPageAdded(at + 1)
                                    p.view.animateTo(scope, grown.fit(settings.fitMode, at + 1, p.view.viewSize.width.toFloat(), p.view.viewSize.height.toFloat()))
                                }
                            } else {
                                val placed = p.viewModel.insertImages(at, sources)
                                val pageId = p.pages.getOrNull(at)?.id
                                if (placed.isNotEmpty() && pageId != null) {
                                    // New pictures come selected, ready to move or resize (PHASE-3.md).
                                    if (settings.tool != EditorTool.LASSO) p.viewModel.pickTool(EditorTool.LASSO)
                                    p.view.lasso = LassoState.Selected.of(at, pageId, emptyList(), placed)
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
            for (p in panes) ExportDialogs(p.viewModel, p.viewModel.notebook.value?.title.orEmpty())
            bookmarking?.let { (p, request) ->
                val paneLinks = p.viewModel.links ?: return@let
                val linked by paneLinks.tasks.collectAsStateWithLifecycle()
                val all by paneLinks.allChoices.collectAsStateWithLifecycle()
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
                        scope.launch {
                            val image = request.picture?.let { p.viewModel.saveBookmarkPicture(it) }
                            paneLinks.addBookmark(task, request.pageId, note, request.spot, image)
                        }
                    },
                    onCancel = { bookmarking = null },
                    picture = remember(request.picture) { request.picture?.asImageBitmap() },
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
                            is AddPageChoice.Blank -> {
                                val p = active
                                scope.launch {
                                    if (p.viewModel.addPage(addAfter + 1, template = choice.template) != null) p.showPage(scope, addAfter + 1, settings)
                                }
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
                val p = active
                PdfPagePicker(
                    pageCount = pdf.sizes.size,
                    aspect = { pdf.sizes[it].width / pdf.sizes[it].height },
                    thumbnail = { i, px -> p.viewModel.pdfThumbnail(pdf, i, px) },
                    onDismiss = { choosingPdf = null; p.viewModel.discardPdf(pdf) },
                    onAdd = { chosen ->
                        choosingPdf = null
                        scope.launch {
                            val added = p.viewModel.insertPdfPages(addAfter, pdf, chosen)
                            if (added > 0) p.showPage(scope, addAfter + 1, settings)
                        }
                    },
                )
            }
            cropping?.let { (p, target) ->
                val image = remember(target) { target.picture.asImageBitmap() }
                SnipOverlay(
                    picture = image,
                    startWhole = true,
                    onCancel = { cropping = null },
                    onConfirm = { box ->
                        cropping = null
                        scope.launch {
                            val cropped = p.viewModel.cropImage(target.pageId, target.image, target.picture, box)
                            if (cropped == null) {
                                snackbar.showSnackbar("The picture couldn't be cropped.")
                            } else {
                                p.view.lasso = LassoState.Selected.of(target.pageIndex, target.pageId, emptyList(), listOf(cropped))
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
                        val cropped = android.graphics.Bitmap.createBitmap(picture, box[0], box[1], box[2] - box[0], box[3] - box[1])
                        if (com.nzfrankwu.noteapp.capture.ScreenCapture.isAllBlack(cropped)) {
                            scope.launch { snackbar.showSnackbar("That app doesn't allow screenshots, so this part came out black.") }
                        } else if (panes.size > 1) {
                            snipChoice = cropped // which notebook? (5.10)
                        } else {
                            insertSnipInto(active, cropped)
                        }
                    },
                )
            }
            snipChoice?.let { cropped ->
                InsertIntoDialog(
                    panes = panes,
                    active = active,
                    onPick = { p -> snipChoice = null; insertSnipInto(p, cropped) },
                    onCancel = { snipChoice = null },
                )
            }
            if (panes.any { it.viewModel.insertingImages }) {
                androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter).testTag("inserting-images"))
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** A bookmark waiting for its task and note: page [pageId] (number [pageNumber]), at [spot] or the whole page. */
data class BookmarkRequest(
    val pageId: String,
    val pageNumber: Int,
    val spot: com.nzfrankwu.noteapp.domain.PageSpot?,
    val note: String,
    /** The bookmark pen's picture of the spot (PHASE-5.md FB5), saved only if the bookmark is. */
    val picture: android.graphics.Bitmap? = null,
)

/** A picture being cropped: where it is, and its full picture file. */
class CropTarget(val pageIndex: Int, val pageId: String, val image: com.nzfrankwu.noteapp.domain.ink.InkImage, val picture: android.graphics.Bitmap)

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
internal fun LassoMenu(
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
internal fun TextMenu(
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
internal const val UNDERLINE_WIDTH = 1.2f

/** The Paste bubble where a finger was held (F12). */
@Composable
internal fun PasteBubble(view: EditorViewState, onPaste: (androidx.compose.ui.geometry.Offset) -> Unit) {
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
internal fun BoxScope.PageScroller(view: EditorViewState, strip: PageStrip, pageCount: Int) {
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

/**
 * The part of the screen picture [full] between ([left], [top]) and ([right], [bottom]) (screen
 * pixels), cut to the picture, at most [com.nzfrankwu.noteapp.domain.ink.BookmarkPen.MAX_PICTURE_SIDE]
 * pixels on its longer side. Null if none of it is on screen.
 */
internal fun cropForBookmark(full: android.graphics.Bitmap, left: Float, top: Float, right: Float, bottom: Float): android.graphics.Bitmap? {
    val l = left.toInt().coerceIn(0, full.width)
    val t = top.toInt().coerceIn(0, full.height)
    val r = kotlin.math.ceil(right).toInt().coerceIn(0, full.width)
    val b = kotlin.math.ceil(bottom).toInt().coerceIn(0, full.height)
    if (r - l < 2 || b - t < 2) return null
    val soft = full.config == android.graphics.Bitmap.Config.HARDWARE
    val source = if (soft) full.copy(android.graphics.Bitmap.Config.ARGB_8888, false) else full
    val cut = android.graphics.Bitmap.createBitmap(source, l, t, r - l, b - t)
    val max = com.nzfrankwu.noteapp.domain.ink.BookmarkPen.MAX_PICTURE_SIDE
    val longest = maxOf(cut.width, cut.height)
    return if (longest <= max) cut else {
        val k = max.toFloat() / longest
        android.graphics.Bitmap.createScaledBitmap(cut, (cut.width * k).toInt().coerceAtLeast(1), (cut.height * k).toInt().coerceAtLeast(1), true)
    }
}

/**
 * "Insert into" (PHASE-5.md 5.10, IDEA.md §2.7): with two notebooks open, which one a screenshot
 * goes into. The one last written in ([active]) is first and marked.
 */
@Composable
private fun InsertIntoDialog(panes: List<PaneState>, active: PaneState, onPick: (PaneState) -> Unit, onCancel: () -> Unit) {
    val ordered = listOf(active) + panes.filter { it !== active }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Insert into") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ordered.forEachIndexed { i, p ->
                    val title = p.viewModel.notebook.value?.title ?: "Notebook"
                    val label = if (i == 0) "$title (last used)" else title
                    if (i == 0) {
                        androidx.compose.material3.Button(
                            onClick = { onPick(p) },
                            modifier = Modifier.fillMaxWidth().testTag("insert-into-$i"),
                        ) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    } else {
                        androidx.compose.material3.OutlinedButton(
                            onClick = { onPick(p) },
                            modifier = Modifier.fillMaxWidth().testTag("insert-into-$i"),
                        ) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}
