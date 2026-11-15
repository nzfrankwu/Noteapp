package com.nzfrankwu.noteapp.ui.notebook

import android.graphics.Matrix
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.ink.authoring.compose.InProgressStrokes
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import androidx.ink.brush.Brush
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInput
import com.nzfrankwu.noteapp.data.db.Notebook
import com.nzfrankwu.noteapp.data.db.Page
import com.nzfrankwu.noteapp.domain.ink.Camera
import com.nzfrankwu.noteapp.domain.ink.EditorTool
import com.nzfrankwu.noteapp.domain.ink.InkGeometry
import com.nzfrankwu.noteapp.domain.ink.InkStroke
import com.nzfrankwu.noteapp.domain.ink.InkTool
import com.nzfrankwu.noteapp.domain.ink.InkToolSettings
import com.nzfrankwu.noteapp.domain.ink.PageStrip
import com.nzfrankwu.noteapp.domain.ink.PressureErase
import com.nzfrankwu.noteapp.ui.components.drawPaperPattern
import com.nzfrankwu.noteapp.ui.theme.DeskDark
import com.nzfrankwu.noteapp.ui.theme.DeskLight
import com.nzfrankwu.noteapp.ui.theme.PaperLine
import com.nzfrankwu.noteapp.ui.theme.PaperWhite
import kotlin.math.hypot
import kotlin.math.max

/**
 * What the lasso has: the loop being drawn, or the strokes it picked on one page with how far
 * they've been moved ([dx], [dy] page units) and resized ([scale], around the box's top-left).
 */
sealed interface LassoState {
    data class Drawing(val pageIndex: Int, val xs: List<Float>, val ys: List<Float>) : LassoState
    data class Selected(
        val pageIndex: Int,
        val pageId: String,
        val strokes: List<InkStroke>,
        val bounds: FloatArray,
        val dx: Float = 0f,
        val dy: Float = 0f,
        val scale: Float = 1f,
    ) : LassoState {
        /** The selection box after the move/resize so far (page units). */
        fun box(): FloatArray {
            val l = bounds[0]; val t = bounds[1]
            return floatArrayOf(l + dx, t + dy, l + (bounds[2] - l) * scale + dx, t + (bounds[3] - t) * scale + dy)
        }

        fun transformed(): List<InkStroke> = strokes.map { it.mapped(scale, bounds[0], bounds[1], dx, dy) }
    }
}

/**
 * What the editor is showing: where the pages are on screen ([camera]), the screen size, and the
 * lasso. Kept in one holder that the canvas reads only while DRAWING, so panning, zooming and
 * dragging a selection redraw the canvas without re-laying-out the whole screen.
 */
@Stable
class EditorViewState {
    var camera by mutableStateOf<Camera?>(null)
    var viewSize by mutableStateOf(IntSize.Zero)
    var lasso by mutableStateOf<LassoState?>(null)

    /**
     * The zoom the ink was last drawn for (it tunes how smooth stroke edges are). It follows the
     * camera's zoom only once a zoom has settled, so pinching doesn't redraw every stroke.
     */
    var inkScale by mutableFloatStateOf(0f)
}

/** The pen layer's matrix and brush, kept in one stable object so the pen layer isn't rebuilt mid-stroke. */
@Stable
private class InkLayerInputs {
    /** Screen → page-desk coordinates for the pen layer. */
    val transform = androidx.compose.ui.graphics.Matrix()
    var brush: Brush? = null

    /** world = (screen − offset) / scale, written straight into the matrix's values. */
    fun setCamera(camera: Camera) {
        transform.reset()
        val v = transform.values
        v[androidx.compose.ui.graphics.Matrix.ScaleX] = 1f / camera.scale
        v[androidx.compose.ui.graphics.Matrix.ScaleY] = 1f / camera.scale
        v[androidx.compose.ui.graphics.Matrix.TranslateX] = -camera.offsetX / camera.scale
        v[androidx.compose.ui.graphics.Matrix.TranslateY] = -camera.offsetY / camera.scale
    }
}

/**
 * The notebook's pages side by side on a desk, with writing (PHASE-2.md 2.4–2.10).
 *
 * Input:
 *  - **Stylus**: draws with the pen layer (Jetpack Ink, low latency), or erases / lassos, by tool.
 *    Its eraser end always erases. Pressing hard erases (when on). The side button toggles lasso.
 *  - **Finger**: never draws (palm rejection): one finger pans, two pinch-zoom, a double-tap
 *    fits the page. While the stylus is down, fingers are ignored entirely.
 *    (Debug "Draw with finger" lets a single finger act as the stylus, for the emulator.)
 */
@Composable
fun NotebookCanvas(
    viewModel: NotebookViewModel,
    notebook: Notebook,
    pages: List<Page>,
    settings: InkToolSettings,
    view: EditorViewState,
    modifier: Modifier = Modifier,
) {
    val strip = remember(notebook.pageWidth, notebook.pageHeight, pages.size) {
        PageStrip(notebook.pageWidth, notebook.pageHeight, pages.size)
    }
    val renderer = remember { CanvasStrokeRenderer.create() }
    val inputs = remember { InkLayerInputs() }
    val dark = isSystemInDarkTheme()

    // The pen layer's brush (read at the start of each stroke; its transform is set by the gestures).
    inputs.brush = settings.penStyle(settings.tool)?.let { style ->
        InkBrushes.brush(if (settings.tool == EditorTool.HIGHLIGHTER) InkTool.HIGHLIGHTER else InkTool.PEN, style.colorArgb, style.size)
    }

    // Latest values for the long-running gesture handler.
    val current by rememberUpdatedState(Snapshot(strip, pages, settings))
    var eraserAt by remember { mutableStateOf<Offset?>(null) }

    val onStrokesFinished: (List<Stroke>) -> Unit = remember(viewModel) {
        { strokes ->
            val snap = current
            strokes.groupBy { stroke ->
                val first = StrokeInput().also { stroke.inputs.populate(0, it) }
                snap.strip.pageAt(first.x)
            }.forEach { (index, group) ->
                val page = snap.pages.getOrNull(index) ?: return@forEach
                viewModel.addStrokes(page.id, group.map { InkBrushes.toSaved(it, snap.strip.pageLeft(index), 0f) })
            }
        }
    }

    // Which pages are on screen. This only changes when a page comes or goes, not on every scroll step.
    val visible by remember(strip, view) {
        derivedStateOf {
            view.camera?.let { strip.visiblePages(it, view.viewSize.width.toFloat()) } ?: IntRange.EMPTY
        }
    }
    // Get the pages on screen and their neighbours ready to draw; forget the far ones.
    LaunchedEffect(visible, pages, viewModel.version) {
        if (visible.isEmpty() || pages.isEmpty()) return@LaunchedEffect
        val near = (visible.first - 1).coerceAtLeast(0)..(visible.last + 1).coerceAtMost(pages.lastIndex)
        for (i in near) pages.getOrNull(i)?.let { viewModel.prepareToDraw(it.id) }
        val keep = (near.first - 1).coerceAtLeast(0)..(near.last + 1).coerceAtMost(pages.lastIndex)
        viewModel.keepReadyOnly(keep.mapNotNullTo(HashSet()) { pages.getOrNull(it)?.id })
    }
    // Follow the zoom once it has settled (see EditorViewState.inkScale).
    LaunchedEffect(view) {
        snapshotFlow { view.camera?.scale }.collectLatest { scale ->
            if (scale == null) return@collectLatest
            if (view.inkScale == 0f) { view.inkScale = scale; return@collectLatest }
            val ratio = if (scale > view.inkScale) scale / view.inkScale else view.inkScale / scale
            if (ratio > 1.4f) {
                delay(300)
                view.inkScale = scale
            }
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .clipToBounds() // zoomed-in pages must never draw over the top bar
            .testTag("notebook-canvas")
            .onSizeChanged { view.viewSize = it }
            .pointerInput(viewModel, view) {
                handleGestures(
                    snapshot = { current },
                    view = view,
                    inputs = inputs,
                    viewModel = viewModel,
                    onEraser = { eraserAt = it },
                )
            },
    ) {
        // 1. The desk and the paper (cheap, redrawn as you scroll).
        Canvas(Modifier.fillMaxSize()) {
            drawRect(if (dark) DeskDark else DeskLight)
            val cam = view.camera ?: return@Canvas
            val s = cam.scale
            val w = notebook.pageWidth * s
            val h = notebook.pageHeight * s
            for (i in strip.visiblePages(cam, size.width)) {
                val page = pages.getOrNull(i) ?: continue
                val left = cam.toScreenX(strip.pageLeft(i))
                val top = cam.toScreenY(0f)
                drawRect(Color.Black.copy(alpha = 0.12f), Offset(left + 2f, top + 3f), Size(w, h))
                drawRect(PaperWhite, Offset(left, top), Size(w, h))
                drawPaperPattern(page.template ?: notebook.template, notebook.pageWidth, notebook.pageHeight, PaperLine, left, top, s)
                if (viewModel.pageStatus[page.id] is PageStatus.Damaged) {
                    drawRect(Color(0x22D32F2F), Offset(left, top), Size(w, h))
                }
            }
        }

        // 2. The ink: one layer per page on screen (see PageInkLayer).
        for (i in visible) {
            val page = pages.getOrNull(i) ?: continue
            key(page.id) {
                PageInkLayer(viewModel, page.id, i, notebook.pageWidth, notebook.pageHeight, strip, view, renderer)
            }
        }

        // 3. The lasso's loop and box, and the eraser's ring.
        Canvas(Modifier.fillMaxSize()) {
            val cam = view.camera ?: return@Canvas
            when (val lasso = view.lasso) {
                is LassoState.Drawing -> if (lasso.xs.size > 1) {
                    val path = androidx.compose.ui.graphics.Path()
                    lasso.xs.indices.forEach { k ->
                        val x = cam.toScreenX(lasso.xs[k]); val y = cam.toScreenY(lasso.ys[k])
                        if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, Color(0xFF2F6BDB), style = DrawStroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))))
                }
                is LassoState.Selected -> {
                    val box = lasso.box()
                    val pl = strip.pageLeft(lasso.pageIndex)
                    val l = cam.toScreenX(pl + box[0]) - 6f; val t = cam.toScreenY(box[1]) - 6f
                    val r = cam.toScreenX(pl + box[2]) + 6f; val b = cam.toScreenY(box[3]) + 6f
                    drawRect(Color(0xFF2F6BDB), Offset(l, t), Size(r - l, b - t), style = DrawStroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))))
                    drawCircle(Color.White, HANDLE_RADIUS, Offset(r, b))
                    drawCircle(Color(0xFF2F6BDB), HANDLE_RADIUS, Offset(r, b), style = DrawStroke(width = 3f))
                }
                null -> Unit
            }
            eraserAt?.let { at ->
                val radius = settings.eraserRadius * cam.scale
                drawCircle(Color.White.copy(alpha = 0.6f), radius, at)
                drawCircle(Color(0xFF5D6066), radius, at, style = DrawStroke(width = 2f))
            }
        }
        // 4. The stroke being written (Jetpack Ink's low-latency layer).
        InkLayer(inputs, onStrokesFinished)
    }
}

/** The pen layer. Its inputs never change identity, so it isn't rebuilt (which would cancel a stroke). */
@Composable
private fun InkLayer(inputs: InkLayerInputs, onStrokesFinished: (List<Stroke>) -> Unit) {
    val nextBrush = remember(inputs) { { inputs.brush } }
    InProgressStrokes(
        defaultBrush = null,
        nextBrush = nextBrush,
        pointerEventToWorldTransform = inputs.transform,
        onStrokesFinished = onStrokesFinished,
    )
}

/** Everything the gesture handler needs, as of the last composition. */
private data class Snapshot(
    val strip: PageStrip,
    val pages: List<Page>,
    val settings: InkToolSettings,
)

private const val HANDLE_RADIUS = 14f
private const val TAP_SLOP = 24f
private const val TAP_MS = 300L
private const val DOUBLE_TAP_MS = 320L

/** What a stylus (or the debug finger) is doing between down and up. */
private enum class PenMode { INK, ERASE, LASSO_LOOP, LASSO_MOVE, LASSO_RESIZE, BLOCKED }

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.handleGestures(
    snapshot: () -> Snapshot,
    view: EditorViewState,
    inputs: InkLayerInputs,
    viewModel: NotebookViewModel,
    onEraser: (Offset?) -> Unit,
) = awaitPointerEventScope {
    val onCamera: (Camera) -> Unit = { view.camera = it }
    val onLasso: (LassoState?) -> Unit = { view.lasso = it }
    val touches = LinkedHashMap<PointerId, Offset>()
    var penId: PointerId? = null
    var penMode = PenMode.INK
    var penLast: Offset? = null
    var penStart = Offset.Zero
    var penIsFinger = false
    // Press-hard-to-erase only trusts pens whose pressure really varies (see PressureErase).
    var firstPressure = 0f
    var pressureVaries = false
    var buttonWasDown = false
    // Double-tap tracking (fingers).
    var tapDownAt = 0L
    var tapDownPos = Offset.Zero
    var tapMoved = false
    var lastTapUpAt = 0L
    var lastTapPos = Offset.Zero

    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val snap = snapshot()
        val cam = view.camera ?: continue
        // The pen layer runs after this handler: give it the current view before it starts a stroke.
        inputs.setCamera(cam)
        val viewW = size.width.toFloat()
        val viewH = size.height.toFloat()

        // ---- Stylus side button: a click toggles lasso (hovering or touching). ----
        val stylusInEvent = event.changes.any { it.type == PointerType.Stylus || it.type == PointerType.Eraser }
        if (stylusInEvent) {
            val down = event.buttons.isSecondaryPressed
            if (down && !buttonWasDown) viewModel.penButtonClicked()
            buttonWasDown = down
        }

        for (change in event.changes) {
            val isPen = change.type == PointerType.Stylus || change.type == PointerType.Eraser
            val fingerAsPen = !isPen && snap.settings.drawWithFinger && (penId == change.id || (penId == null && touches.isEmpty() && change.changedToDownIgnoreConsumed()))
            if (isPen || fingerAsPen) {
                // ---- Pen ----
                if (change.changedToDownIgnoreConsumed() && penId == null) {
                    penId = change.id
                    penStart = change.position
                    penLast = change.position
                    penIsFinger = !isPen
                    firstPressure = change.pressure
                    pressureVaries = false
                    val pageIndex = snap.strip.pageAt(cam.toWorldX(change.position.x))
                    val page = snap.pages.getOrNull(pageIndex)
                    val tool = snap.settings.tool
                    val selected = view.lasso as? LassoState.Selected
                    penMode = when {
                        page == null || !viewModel.isEditable(page.id) -> PenMode.BLOCKED
                        change.type == PointerType.Eraser || tool == EditorTool.ERASER -> PenMode.ERASE
                        tool == EditorTool.LASSO && selected != null && nearHandle(change.position, selected, snap, cam) -> PenMode.LASSO_RESIZE
                        tool == EditorTool.LASSO && selected != null && insideSelection(change.position, selected, snap, cam) -> PenMode.LASSO_MOVE
                        tool == EditorTool.LASSO -> {
                            onLasso(LassoState.Drawing(pageIndex, listOf(cam.toWorldX(change.position.x)), listOf(cam.toWorldY(change.position.y))))
                            PenMode.LASSO_LOOP
                        }
                        else -> PenMode.INK
                    }
                    if (penMode != PenMode.INK) change.consume()
                    if (penMode == PenMode.ERASE) eraseStep(viewModel, snap, cam, change.position, change.position, onEraser)
                    // Any other tool, or the pen's eraser end, ends the lasso's selection (it would
                    // otherwise keep showing strokes that were just erased).
                    if ((tool != EditorTool.LASSO || penMode == PenMode.ERASE) && view.lasso != null) onLasso(null)
                    continue
                }
                if (change.id != penId) { change.consume(); continue }
                if (change.pressed) {
                    val from = penLast ?: change.position
                    when (penMode) {
                        PenMode.INK -> {
                            if (change.pressure != firstPressure) pressureVaries = true
                            val hard = snap.settings.pressureErase && isPen &&
                                PressureErase.isHard(change.pressure, snap.settings.pressureEraseLevel, pressureVaries)
                            if (hard) {
                                penMode = PenMode.ERASE // consuming from now on cancels the wet stroke
                                change.consume()
                                eraseStep(viewModel, snap, cam, from, change.position, onEraser)
                            }
                        }
                        PenMode.ERASE -> {
                            change.consume()
                            eraseStep(viewModel, snap, cam, from, change.position, onEraser)
                        }
                        PenMode.LASSO_LOOP -> {
                            change.consume()
                            (view.lasso as? LassoState.Drawing)?.let {
                                onLasso(it.copy(xs = it.xs + cam.toWorldX(change.position.x), ys = it.ys + cam.toWorldY(change.position.y)))
                            }
                        }
                        PenMode.LASSO_MOVE -> {
                            change.consume()
                            (view.lasso as? LassoState.Selected)?.let {
                                onLasso(it.copy(dx = it.dx + (change.position.x - from.x) / cam.scale, dy = it.dy + (change.position.y - from.y) / cam.scale))
                            }
                        }
                        PenMode.LASSO_RESIZE -> {
                            change.consume()
                            (view.lasso as? LassoState.Selected)?.let { sel ->
                                val b = sel.bounds
                                val pl = snap.strip.pageLeft(sel.pageIndex)
                                val px = cam.toWorldX(change.position.x) - pl - (b[0] + sel.dx)
                                val py = cam.toWorldY(change.position.y) - (b[1] + sel.dy)
                                val scale = max(px / max(b[2] - b[0], 1f), py / max(b[3] - b[1], 1f)).coerceIn(0.1f, 10f)
                                onLasso(sel.copy(scale = scale))
                            }
                        }
                        PenMode.BLOCKED -> change.consume()
                    }
                    penLast = change.position
                } else {
                    // Pen lifted.
                    when (penMode) {
                        PenMode.LASSO_LOOP -> {
                            val loop = view.lasso as? LassoState.Drawing
                            val page = loop?.let { snap.pages.getOrNull(it.pageIndex) }
                            if (loop != null && page != null && loop.xs.size > 2) {
                                val pl = snap.strip.pageLeft(loop.pageIndex)
                                val xs = FloatArray(loop.xs.size) { loop.xs[it] - pl }
                                val ys = loop.ys.toFloatArray()
                                val picked = viewModel.strokesInLoop(page.id, xs, ys)
                                onLasso(
                                    if (picked.isEmpty()) null
                                    else LassoState.Selected(loop.pageIndex, page.id, picked, InkGeometry.boundsOf(picked)!!),
                                )
                            } else {
                                onLasso(null)
                            }
                        }
                        PenMode.LASSO_MOVE, PenMode.LASSO_RESIZE -> {
                            (view.lasso as? LassoState.Selected)?.let { sel ->
                                if (sel.dx != 0f || sel.dy != 0f || sel.scale != 1f) {
                                    val moved = sel.transformed()
                                    viewModel.replaceStrokes(sel.pageId, moved)
                                    onLasso(LassoState.Selected(sel.pageIndex, sel.pageId, moved, InkGeometry.boundsOf(moved)!!))
                                }
                            }
                        }
                        else -> Unit
                    }
                    if (penMode != PenMode.INK) change.consume()
                    penId = null
                    penLast = null
                    onEraser(null)
                }
                continue
            }

            // ---- Finger ----
            change.consume() // fingers never reach the pen layer (palm rejection)
            val pen = penId
            if (pen != null) {
                if (penIsFinger && penMode == PenMode.INK && change.changedToDownIgnoreConsumed()) {
                    // Debug "Draw with finger": a second finger turns writing into a two-finger
                    // scroll/zoom. The line being drawn is dropped (its next events arrive consumed).
                    touches[pen] = penLast ?: change.position
                    penId = null
                    penLast = null
                } else {
                    touches.remove(change.id) // palm while writing: ignore
                    continue
                }
            }
            if (change.changedToDownIgnoreConsumed()) {
                touches[change.id] = change.position
                if (touches.size == 1) {
                    tapDownAt = change.uptimeMillis
                    tapDownPos = change.position
                    tapMoved = false
                } else {
                    tapMoved = true // a pinch is not a tap
                }
                continue
            }
            if (!change.pressed) {
                touches.remove(change.id)
                val isTap = !tapMoved && touches.isEmpty() && change.uptimeMillis - tapDownAt < TAP_MS
                if (isTap) {
                    if (change.uptimeMillis - lastTapUpAt < DOUBLE_TAP_MS && (change.position - lastTapPos).getDistance() < TAP_SLOP * 3) {
                        val index = snap.strip.pageAt(cam.toWorldX(change.position.x))
                        onCamera(snap.strip.fit(snap.settings.fitMode, index, viewW, viewH))
                        lastTapUpAt = 0L
                    } else {
                        lastTapUpAt = change.uptimeMillis
                        lastTapPos = change.position
                    }
                }
                continue
            }
            if ((change.position - tapDownPos).getDistance() > TAP_SLOP) tapMoved = true
        }

        // ---- Pan and pinch-zoom with the fingers that moved ----
        if (touches.isNotEmpty() && penId == null) {
            val before = touches.values.toList()
            val now = touches.keys.map { id -> event.changes.firstOrNull { it.id == id }?.position ?: touches.getValue(id) }
            touches.keys.zip(now).forEach { (id, pos) -> touches[id] = pos }
            if (before != now) {
                val c0 = centroid(before); val c1 = centroid(now)
                var next = cam
                if (before.size >= 2) {
                    val s0 = spread(before, c0); val s1 = spread(now, c1)
                    if (s0 > 1f) next = snap.strip.zoom(next, s1 / s0, c1.x, c1.y, viewW, viewH)
                }
                next = snap.strip.clamp(next.copy(offsetX = next.offsetX + (c1.x - c0.x), offsetY = next.offsetY + (c1.y - c0.y)), viewW, viewH)
                onCamera(next)
            }
        }
    }
}

private fun centroid(points: List<Offset>): Offset =
    Offset(points.sumOf { it.x.toDouble() }.toFloat() / points.size, points.sumOf { it.y.toDouble() }.toFloat() / points.size)

private fun spread(points: List<Offset>, c: Offset): Float = points.map { (it - c).getDistance() }.average().toFloat()

private fun eraseStep(viewModel: NotebookViewModel, snap: Snapshot, cam: Camera, from: Offset, to: Offset, onEraser: (Offset?) -> Unit) {
    onEraser(to)
    val index = snap.strip.pageAt(cam.toWorldX(to.x))
    val page = snap.pages.getOrNull(index) ?: return
    val pl = snap.strip.pageLeft(index)
    viewModel.eraseAlong(
        page.id,
        cam.toWorldX(from.x) - pl, cam.toWorldY(from.y),
        cam.toWorldX(to.x) - pl, cam.toWorldY(to.y),
        snap.settings.eraserRadius,
    )
}

private fun selectionScreenBox(sel: LassoState.Selected, snap: Snapshot, cam: Camera): FloatArray {
    val box = sel.box()
    val pl = snap.strip.pageLeft(sel.pageIndex)
    return floatArrayOf(cam.toScreenX(pl + box[0]), cam.toScreenY(box[1]), cam.toScreenX(pl + box[2]), cam.toScreenY(box[3]))
}

private fun insideSelection(p: Offset, sel: LassoState.Selected, snap: Snapshot, cam: Camera): Boolean {
    val b = selectionScreenBox(sel, snap, cam)
    val pad = 16f
    return p.x >= b[0] - pad && p.x <= b[2] + pad && p.y >= b[1] - pad && p.y <= b[3] + pad
}

private fun nearHandle(p: Offset, sel: LassoState.Selected, snap: Snapshot, cam: Camera): Boolean {
    val b = selectionScreenBox(sel, snap, cam)
    return hypot(p.x - (b[2] + 6f), p.y - (b[3] + 6f)) <= HANDLE_RADIUS * 2.5f
}

private fun androidx.compose.ui.input.pointer.PointerInputChange.changedToDownIgnoreConsumed() = !previousPressed && pressed
