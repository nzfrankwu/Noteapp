package com.nzfrankwu.noteapp.ui.notebook

import androidx.compose.foundation.layout.Arrangement
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
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * An open notebook (PHASE-2.md 2.6): a slim top bar, the pages side by side, the floating
 * toolbar, and a page-number badge. Changes save themselves.
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
    var menuOpen by remember { mutableStateOf(false) }

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
    val strip = remember(nb?.pageWidth, nb?.pageHeight, pages.size) {
        nb?.let { PageStrip(it.pageWidth, it.pageHeight, pages.size) }
    }
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

    Column(modifier.fillMaxSize()) {
        // ---- Top bar (dark, buttons only, like Notein: F5) ----
        EditorTopBar(
            canUndo = viewModel.canUndo,
            canRedo = viewModel.canRedo,
            onBack = ::leave,
            onUndo = { view.lasso = null; viewModel.undo() },
            onRedo = { view.lasso = null; viewModel.redo() },
            onAddPage = {
                view.lasso = null // a selection remembers its page's place, which moves
                scope.launch {
                    val at = pageNow() + 1
                    if (viewModel.addPage(at) != null && strip != null) {
                        val grown = PageStrip(strip.pageWidth, strip.pageHeight, pages.size + 1)
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

        // ---- Pages, toolbar and overlays ----
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (nb != null && pages.isNotEmpty()) {
                NotebookCanvas(viewModel = viewModel, notebook = nb, pages = pages, settings = settings, view = view)
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
                        val copies = viewModel.duplicate(sel.pageId, sel.strokes)
                        view.lasso = LassoState.Selected.of(sel.pageIndex, sel.pageId, copies, sel.handles) ?: sel
                    },
                    onCopy = { sel ->
                        viewModel.copy(sel.strokes)
                        scope.launch { snackbar.showSnackbar("Copied. Hold a finger on a page to paste.") }
                    },
                    onCut = { sel ->
                        viewModel.cut(sel.pageId, sel.strokes)
                        view.lasso = null
                        scope.launch { snackbar.showSnackbar("Cut. Hold a finger on a page to paste.") }
                    },
                    onDelete = { sel ->
                        viewModel.deleteStrokes(sel.pageId, sel.strokes.map { it.id }.toSet())
                        view.lasso = null
                    },
                )
                PasteBubble(view) { at ->
                    view.pasteAt = null
                    val cam = view.camera ?: return@PasteBubble
                    val wx = cam.toWorldX(at.x)
                    val index = strip.pageAt(wx)
                    val page = pages.getOrNull(index) ?: return@PasteBubble
                    val pasted = viewModel.paste(page.id, wx - strip.pageLeft(index), cam.toWorldY(at.y))
                    if (pasted.isNotEmpty()) {
                        // Pasted strokes come selected, ready to move (that needs the lasso).
                        if (settings.tool != EditorTool.LASSO) viewModel.pickTool(EditorTool.LASSO)
                        view.lasso = LassoState.Selected.of(index, page.id, pasted)
                    }
                }
                PageBadge(view, strip, pages.size)
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }
}

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
    val by = (cam.toScreenY(box[3]) + gap).coerceAtMost(view.viewSize.height - 160f)
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

/** "3 / 12" at the bottom left: the page in the middle of the screen. */
@Composable
private fun BoxScope.PageBadge(view: EditorViewState, strip: PageStrip, pageCount: Int) {
    if (pageCount == 0) return
    // Only changes (and re-lays-out the badge) when the middle page does, not on every scroll step.
    val current by remember(view, strip) {
        derivedStateOf { view.camera?.let { strip.currentPage(it, view.viewSize.width.toFloat()) } ?: 0 }
    }
    Surface(
        modifier = Modifier.align(Alignment.BottomStart).padding(16.dp).testTag("page-badge"),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.8f),
    ) {
        Text(
            "${current + 1} / $pageCount",
            color = MaterialTheme.colorScheme.inverseOnSurface,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
