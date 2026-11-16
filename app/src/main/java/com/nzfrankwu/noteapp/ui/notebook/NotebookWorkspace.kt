package com.nzfrankwu.noteapp.ui.notebook

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.nzfrankwu.noteapp.R
import com.nzfrankwu.noteapp.data.EditorViewStore
import com.nzfrankwu.noteapp.data.db.NotebookListItem
import com.nzfrankwu.noteapp.domain.DividerRelease
import com.nzfrankwu.noteapp.domain.PageSpot
import com.nzfrankwu.noteapp.domain.PaneSide
import com.nzfrankwu.noteapp.domain.SplitLayout
import com.nzfrankwu.noteapp.domain.SplitMode
import com.nzfrankwu.noteapp.ui.theme.EditorBarButton
import com.nzfrankwu.noteapp.ui.theme.EditorBarIcon
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The notebook editor with the recent notebooks panel and split view (PHASE-5.md 5.7–5.9): one
 * notebook, or two side by side or stacked, with a divider between them. The top bar and toolbar
 * work on the active pane (blue outline), the one last touched.
 *
 * [firstId] is the notebook the screen was opened with ([openAtPage]/[openAtSpot] apply to it).
 * [viewModelFor] gives each notebook its view model (kept for the screen's life); [onReplace]
 * opens another notebook in place of a single one.
 */
@Composable
fun NotebookWorkspace(
    firstId: String,
    viewModelFor: @Composable (String) -> NotebookViewModel,
    viewStore: EditorViewStore,
    allNotebooks: Flow<List<NotebookListItem>>,
    onBack: () -> Unit,
    onReplace: (String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenTask: (String) -> Unit = {},
    openAtPage: String? = null,
    openAtSpot: PageSpot? = null,
) {
    // The notebooks open: [first], and [second] in split view. Saved, so rotating keeps them.
    var first by rememberSaveable { mutableStateOf(firstId) }
    var second by rememberSaveable { mutableStateOf<String?>(null) }
    var activeIndex by rememberSaveable { mutableIntStateOf(0) }
    var panelOpen by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val firstPane = paneFor(viewModelFor(first))
    val secondId = second
    val secondPane = secondId?.let { paneFor(viewModelFor(it)) }
    val panes = listOfNotNull(firstPane, secondPane)
    val active = if (activeIndex == 1 && secondPane != null) secondPane else firstPane
    // A text box being typed in is finished when the other pane becomes the active one.
    LaunchedEffect(active) { panes.filter { it !== active }.forEach { it.finishTyping() } }

    val savedMode by viewStore.splitMode.collectAsState(initial = null)
    val ratio by viewStore.splitRatio.collectAsState(initial = SplitLayout.DEFAULT_RATIO)
    val recentIds by viewStore.recentNotebooks.collectAsState(initial = emptyList())
    // Only while the panel is open: the query re-counts every notebook's pages after each save.
    val all by remember(panelOpen, allNotebooks) {
        if (panelOpen) allNotebooks else kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())
    val recent = remember(recentIds, all) {
        val byId = all.associateBy { it.notebook.id }
        recentIds.mapNotNull { byId[it] }
    }
    LaunchedEffect(first, secondId) {
        // The active one last, so it's the newest.
        val opened = listOfNotNull(first, secondId)
        (opened - active.viewModel.notebookId + active.viewModel.notebookId).forEach { viewStore.notebookOpened(it) }
    }

    // Where the panes are on screen (root coordinates) and whether two fit, for split and drops.
    var area by remember { mutableStateOf(Rect.Zero) }
    var canSplit by remember { mutableStateOf(true) }
    val mode = savedMode ?: SplitMode.forShape(area.width, area.height)
    // A notebook being dragged out of the panel (5.9): its title and where the finger is.
    var dragging by remember { mutableStateOf<Pair<NotebookListItem, Offset>?>(null) }

    fun openBeside(id: String, side: PaneSide) {
        if (id == first || id == secondId) return
        if (savedMode == null) scope.launch { viewStore.setSplitMode(mode) }
        val current = first
        if (side == PaneSide.FIRST) {
            first = id
            second = current
            activeIndex = 0
        } else {
            second = id
            activeIndex = 1
        }
    }

    fun replacePane(side: PaneSide, id: String) {
        if (id == first || id == secondId) {
            activeIndex = if (id == first) 0 else 1
            return
        }
        panes.getOrNull(if (side == PaneSide.FIRST) 0 else 1)?.let { it.finishTyping(); it.viewModel.saveNow() }
        if (side == PaneSide.FIRST) first = id else second = id
        activeIndex = if (side == PaneSide.FIRST) 0 else 1
    }

    fun closePane(side: PaneSide) {
        val keep = if (side == PaneSide.FIRST) secondId ?: return else first
        panes.getOrNull(if (side == PaneSide.FIRST) 0 else 1)?.let { it.finishTyping(); it.viewModel.saveNow() }
        first = keep
        second = null
        activeIndex = 0
    }

    fun swap() {
        val s = secondId ?: return
        second = first
        first = s
        activeIndex = 1 - activeIndex
    }

    val panelPx = with(LocalDensity.current) { RecentPanelWidth.toPx() }
    fun drop(item: NotebookListItem, at: Offset) {
        // Let go outside the pages, or back over the panel: nothing happens.
        if (!area.contains(at) || !canSplit || (panelOpen && at.x < area.left + panelPx)) return
        val side = SplitLayout.dropSide(at.x - area.left, at.y - area.top, area.width, area.height, mode)
        val id = item.notebook.id
        if (secondId == null) openBeside(id, side) else replacePane(side, id)
        panelOpen = false
    }

    // A layer the panes area records into for split view's screenshot (5.10).
    val areaLayer = androidx.compose.ui.graphics.rememberGraphicsLayer()
    var capturingArea by remember { mutableStateOf(false) }

    NotebookEditor(
        // In a window too small for two, only the active notebook shows: the editor works on it alone.
        panes = if (canSplit) panes else listOf(active),
        active = active,
        captureArea = if (secondPane == null || !canSplit) null else {
            {
                capturingArea = true
                try {
                    androidx.compose.runtime.withFrameNanos { }
                    androidx.compose.runtime.withFrameNanos { }
                    runCatching { areaLayer.toImageBitmap().asAndroidBitmap() }.getOrNull()
                } finally {
                    capturingArea = false
                }
            }
        },
        onBack = onBack,
        modifier = modifier,
        onOpenTask = onOpenTask,
        leftButton = {
            TopBarIconButton(
                R.drawable.ic_library_books, if (panelOpen) "Close the recent notebooks" else "Recent notebooks",
                { panelOpen = !panelOpen }, Modifier.testTag("recent-button"), selected = panelOpen,
            )
        },
        extraMenu = { close ->
            if (secondId != null) {
                DropdownMenuItem(
                    text = { Text("Close split screen") },
                    onClick = { close(); closePane(if (activeIndex == 0) PaneSide.SECOND else PaneSide.FIRST) },
                    modifier = Modifier.testTag("menu-close-split"),
                )
                HorizontalDivider()
            }
        },
        overlay = {
            if (panelOpen) {
                RecentPanel(
                    items = recent,
                    openIds = setOfNotNull(first, secondId),
                    canSplit = canSplit,
                    onOpen = { id ->
                        panelOpen = false
                        when {
                            secondId != null -> replacePane(if (activeIndex == 0) PaneSide.FIRST else PaneSide.SECOND, id)
                            id != first -> { firstPane.finishTyping(); firstPane.viewModel.saveNow(); onReplace(id) }
                        }
                    },
                    onOpenBeside = { id ->
                        panelOpen = false
                        if (secondId == null) openBeside(id, PaneSide.SECOND) else replacePane(if (activeIndex == 0) PaneSide.SECOND else PaneSide.FIRST, id)
                    },
                    onClose = { panelOpen = false },
                    onDragStart = { item, at -> dragging = item to at },
                    onDrag = { at -> dragging = dragging?.let { it.first to at } },
                    onDragEnd = {
                        dragging?.let { (item, at) -> drop(item, at) }
                        dragging = null
                    },
                    modifier = Modifier.padding(top = 64.dp),
                )
            }
            dragging?.let { (item, at) -> DragGhost(item, at, area, mode, secondId != null) }
        },
    ) { show ->
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { area = it.boundsInRoot() }
                .drawWithContent {
                    if (capturingArea) {
                        areaLayer.record { this@drawWithContent.drawContent() }
                        drawLayer(areaLayer)
                    } else {
                        drawContent()
                    }
                },
        ) {
            canSplit = SplitLayout.canSplit(maxWidth.value, maxHeight.value)
            if (secondPane == null || !canSplit) {
                // One notebook (or a window too small for two: just the active one).
                val only = if (secondPane != null) active else firstPane
                key(only.viewModel.notebookId) {
                    show(only, Modifier.fillMaxSize(), openAtPage.takeIf { only.viewModel.notebookId == firstId }, openAtSpot)
                }
            } else {
                SplitPanes(
                    first = firstPane,
                    second = secondPane,
                    activeIndex = activeIndex,
                    onActivate = { activeIndex = it },
                    mode = mode,
                    ratio = ratio,
                    onRatio = { scope.launch { viewStore.setSplitRatio(it) } },
                    onClose = ::closePane,
                    onSwap = ::swap,
                    onMode = { scope.launch { viewStore.setSplitMode(it) } },
                    show = { p, m -> show(p, m, openAtPage.takeIf { p.viewModel.notebookId == firstId }, openAtSpot) },
                )
            }
        }
    }
}

@Composable
private fun paneFor(viewModel: NotebookViewModel): PaneState = remember(viewModel) { PaneState(viewModel) }

/**
 * Two panes and the divider between them (PHASE-5.md 5.8). The divider's ⋮ handle drags to
 * resize (very near an edge closes that pane; past 75% it springs back) and, tapped, offers Swap
 * sides, the other layout, and Close split screen.
 */
@Composable
private fun SplitPanes(
    first: PaneState,
    second: PaneState,
    activeIndex: Int,
    onActivate: (Int) -> Unit,
    mode: SplitMode,
    ratio: Float,
    onRatio: (Float) -> Unit,
    onClose: (PaneSide) -> Unit,
    onSwap: () -> Unit,
    onMode: (SplitMode) -> Unit,
    show: @Composable (PaneState, Modifier) -> Unit,
) {
    val density = LocalDensity.current
    val sideBySide = mode == SplitMode.SIDE_BY_SIDE
    var areaCoords by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
    var handleCoords by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { areaCoords = it }.testTag("split-panes")) {
        val total = with(density) { (if (sideBySide) maxWidth else maxHeight).toPx() }
        val gap = with(density) { DividerThickness.toPx() }
        var dragRatio by remember { mutableStateOf<Float?>(null) }
        val shown = SplitLayout.dragged(dragRatio ?: ratio)
        val firstPx = ((total - gap) * shown).roundToInt()
        val secondPx = (total - gap).roundToInt() - firstPx
        val closing = dragRatio?.let { r -> if (SplitLayout.closing(r)) (if (r < 0.5f) PaneSide.FIRST else PaneSide.SECOND) else null }

        @Composable
        fun pane(p: PaneState, index: Int, offsetPx: Int, sizePx: Int) {
            val sizeDp = with(density) { sizePx.toDp() }
            val outline = MaterialTheme.colorScheme.primary
            Box(
                Modifier
                    .offset { if (sideBySide) IntOffset(offsetPx, 0) else IntOffset(0, offsetPx) }
                    .then(if (sideBySide) Modifier.size(sizeDp, maxHeight) else Modifier.size(maxWidth, sizeDp))
                    // A touch anywhere in a pane makes it the active one (without taking the touch).
                    .pointerInput(index) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            onActivate(index)
                        }
                    }
                    .drawWithContent {
                        drawContent()
                        if (index == activeIndex) drawRect(outline, style = Stroke(width = 2.dp.toPx() * 2))
                    }
                    .testTag("pane-$index-${p.viewModel.notebookId}"),
            ) {
                key(p.viewModel.notebookId) { show(p, Modifier.fillMaxSize()) }
                if ((closing == PaneSide.FIRST && index == 0) || (closing == PaneSide.SECOND && index == 1)) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                        Text("Release to close", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
        pane(first, 0, 0, firstPx)
        pane(second, 1, firstPx + gap.roundToInt(), secondPx)

        // The divider: a dark line with the ⋮ handle in the middle.
        val dividerOffset = firstPx
        Box(
            Modifier
                .offset { if (sideBySide) IntOffset(dividerOffset, 0) else IntOffset(0, dividerOffset) }
                .then(if (sideBySide) Modifier.size(DividerThickness, maxHeight) else Modifier.size(maxWidth, DividerThickness))
                .background(DividerColor),
            contentAlignment = Alignment.Center,
        ) {
            var menu by remember { mutableStateOf(false) }
            Box {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = EditorBarButton,
                    modifier = Modifier
                        .then(if (sideBySide) Modifier.size(24.dp, 56.dp) else Modifier.size(56.dp, 24.dp))
                        .shadow(3.dp, RoundedCornerShape(50))
                        .onGloballyPositioned { handleCoords = it }
                        .pointerInput(sideBySide, total) {
                            detectDragGestures(
                                onDragStart = { dragRatio = ratio },
                                onDragEnd = {
                                    val r = dragRatio
                                    dragRatio = null
                                    when (val release = r?.let { SplitLayout.release(it) }) {
                                        is DividerRelease.Keep -> onRatio(release.ratio)
                                        is DividerRelease.Close -> onClose(release.side)
                                        null -> Unit
                                    }
                                },
                                onDragCancel = { dragRatio = null },
                            ) { change, _ ->
                                change.consume()
                                // Where the finger is in the whole split area (the handle itself
                                // moves with the finger, so its own coordinates can't be used).
                                val a = areaCoords
                                val h = handleCoords
                                if (a != null && h != null && a.isAttached && h.isAttached) {
                                    val at = a.localPositionOf(h, change.position)
                                    val along = if (sideBySide) at.x else at.y
                                    dragRatio = ((along - gap / 2) / (total - gap)).coerceIn(0f, 1f)
                                }
                            }
                        }
                        .clickable { menu = true }
                        .semantics { contentDescription = "Split screen options; drag to resize" }
                        .testTag("split-handle"),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painterResource(R.drawable.ic_more_vert),
                            contentDescription = null,
                            tint = EditorBarIcon,
                            modifier = Modifier.size(20.dp).then(if (sideBySide) Modifier else Modifier.rotate(90f)),
                        )
                    }
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Swap sides") }, onClick = { menu = false; onSwap() }, modifier = Modifier.testTag("split-swap"))
                    DropdownMenuItem(
                        text = { Text(if (sideBySide) "Top and bottom" else "Side by side") },
                        onClick = { menu = false; onMode(if (sideBySide) SplitMode.TOP_BOTTOM else SplitMode.SIDE_BY_SIDE) },
                        modifier = Modifier.testTag("split-mode"),
                    )
                    DropdownMenuItem(
                        text = { Text("Close split screen") },
                        // The active notebook stays.
                        onClick = { menu = false; onClose(if (activeIndex == 0) PaneSide.SECOND else PaneSide.FIRST) },
                        modifier = Modifier.testTag("split-close"),
                    )
                }
            }
        }
    }
}

/**
 * The notebook being dragged out of the panel (5.9): a small card under the finger, and the half
 * (or pane) it would open in lit up.
 */
@Composable
private fun DragGhost(item: NotebookListItem, at: Offset, area: Rect, mode: SplitMode, split: Boolean) {
    val density = LocalDensity.current
    if (area.contains(at)) {
        val side = SplitLayout.dropSide(at.x - area.left, at.y - area.top, area.width, area.height, mode)
        val half = when (mode) {
            SplitMode.SIDE_BY_SIDE -> if (side == PaneSide.FIRST) Rect(area.left, area.top, area.center.x, area.bottom) else Rect(area.center.x, area.top, area.right, area.bottom)
            SplitMode.TOP_BOTTOM -> if (side == PaneSide.FIRST) Rect(area.left, area.top, area.right, area.center.y) else Rect(area.left, area.center.y, area.right, area.bottom)
        }
        // In root coordinates; this overlay sits in the same box as the panes.
        Box(
            Modifier
                .offset { IntOffset((half.left - area.left).roundToInt(), (half.top - area.top).roundToInt()) }
                .size(with(density) { half.width.toDp() }, with(density) { half.height.toDp() })
                .background(DropTint)
                .border(3.dp, MaterialTheme.colorScheme.primary)
                .testTag("drop-zone"),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (split) "Open here instead" else "Open here",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shadowElevation = 8.dp,
        modifier = Modifier.offset { IntOffset((at.x - area.left - 60.dp.toPx()).roundToInt(), (at.y - area.top - 30.dp.toPx()).roundToInt()) },
    ) {
        Text(item.notebook.title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
    }
}

private val DividerThickness = 14.dp
private val DividerColor = Color(0xFF1E1F22)
private val DropTint = Color(0x332F6BDB)
