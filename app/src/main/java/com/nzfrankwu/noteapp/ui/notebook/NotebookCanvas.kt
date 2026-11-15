package com.nzfrankwu.noteapp.ui.notebook

import android.graphics.Matrix
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.runtime.rememberCoroutineScope
import com.nzfrankwu.noteapp.domain.ink.AxisLock
import com.nzfrankwu.noteapp.domain.ink.Fling
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
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
import com.nzfrankwu.noteapp.domain.ink.BoxHandle
import com.nzfrankwu.noteapp.domain.ink.LassoBox
import com.nzfrankwu.noteapp.domain.ink.InkStroke
import com.nzfrankwu.noteapp.domain.ink.InkTool
import com.nzfrankwu.noteapp.domain.ink.InkToolSettings
import com.nzfrankwu.noteapp.domain.ink.PageStrip
import com.nzfrankwu.noteapp.domain.ink.PressureErase
import com.nzfrankwu.noteapp.domain.ink.PressHold
import com.nzfrankwu.noteapp.domain.ink.StillTimer
import com.nzfrankwu.noteapp.domain.ink.Straighten
import com.nzfrankwu.noteapp.ui.components.drawPaperPattern
import com.nzfrankwu.noteapp.ui.theme.DeskDark
import com.nzfrankwu.noteapp.ui.theme.DeskLight
import com.nzfrankwu.noteapp.ui.theme.PaperLine
import com.nzfrankwu.noteapp.ui.theme.PaperWhite
import kotlin.math.hypot
import kotlin.math.max

/**
 * What the lasso has: the loop being drawn, or the strokes it picked on one page. A selection
 * keeps the box its strokes were picked in ([bounds]) and the box they're being moved or
 * stretched to ([target]), both [left, top, right, bottom] in page units (see [LassoBox]).
 * [handles] shows the handles on every corner and edge, plus the rotate handle above the box
 * (the menu's Resize: F12, F14). [angle] is how far the rotate handle has turned the selection so
 * far (radians, around the box's centre); it's applied to the strokes when the pen lifts.
 */
sealed interface LassoState {
    data class Drawing(val pageIndex: Int, val xs: List<Float>, val ys: List<Float>) : LassoState
    data class Selected(
        val pageIndex: Int,
        val pageId: String,
        val strokes: List<InkStroke>,
        val bounds: FloatArray,
        val target: FloatArray = bounds,
        val handles: Boolean = false,
        val angle: Float = 0f,
    ) : LassoState {
        /** The selection box after the move/resize so far (page units), before any [angle]. */
        fun box(): FloatArray = target

        val changed: Boolean get() = !LassoBox.same(bounds, target) || angle != 0f

        fun transformed(): List<InkStroke> = strokes.map { LassoBox.map(it, bounds, target, angle) }

        /** The handles shown: just the bottom-right corner, or all eight after Resize. */
        fun shownHandles(): List<BoxHandle> = if (handles) BoxHandle.entries else listOf(BoxHandle.BOTTOM_RIGHT)

        companion object {
            /** A fresh selection of [strokes] (none moved yet). */
            fun of(pageIndex: Int, pageId: String, strokes: List<InkStroke>, handles: Boolean = false): Selected? =
                InkGeometry.boundsOf(strokes)?.let { Selected(pageIndex, pageId, strokes, it, handles = handles) }
        }
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

    /**
     * True while the pages fly past quickly (a fast flick or drag): far pages then show only
     * their quick preview instead of being drawn in full (PHASE-2.md F8).
     */
    var fastScroll by mutableStateOf(false)

    /** Where a finger was held to paste (screen pixels): shows the Paste bubble (F12). */
    var pasteAt by mutableStateOf<Offset?>(null)

    /** A straight line being placed (hold to straighten, F11), drawn on the overlay. */
    var straight by mutableStateOf<StraightLine?>(null)

    /** Pages whose ink is drawn in full right now; the others show their quick preview (F8). */
    val fullyDrawn = mutableStateMapOf<String, Boolean>()

    private var motion: Job? = null

    /** Stops an animation or flick in progress (e.g. a finger or the pen touched down). */
    fun stopMotion() {
        motion?.cancel()
        motion = null
        fastScroll = false
    }

    /** Glides the camera to [target] (double-tap to fit, a new page: PHASE-2.md F7). */
    fun animateTo(scope: CoroutineScope, target: Camera, durationMs: Int = FIT_ANIMATION_MS) {
        stopMotion()
        val from = camera ?: run { camera = target; return }
        motion = scope.launch {
            animate(0f, 1f, animationSpec = tween(durationMs, easing = FastOutSlowInEasing)) { t, _ ->
                camera = Camera(
                    scale = from.scale + (target.scale - from.scale) * t,
                    offsetX = from.offsetX + (target.offsetX - from.offsetX) * t,
                    offsetY = from.offsetY + (target.offsetY - from.offsetY) * t,
                )
            }
        }
    }

    /**
     * Keeps the pages moving after a flick at ([vx], [vy]) pixels per second, slowing down
     * (see [Fling]). [clamp] keeps the camera within the pages; hitting an end stops that way.
     */
    fun fling(scope: CoroutineScope, vx: Float, vy: Float, clamp: (Camera) -> Camera) {
        stopMotion()
        motion = scope.launch {
            var velX = vx
            var velY = vy
            var last = withFrameNanos { it }
            try {
                while (hypot(velX, velY) > Fling.STOP_SPEED) {
                    val now = withFrameNanos { it }
                    val dt = ((now - last) / 1e9f).coerceIn(0f, 0.05f)
                    last = now
                    val cam = camera ?: break
                    val wantX = Fling.distance(velX, dt)
                    val wantY = Fling.distance(velY, dt)
                    val next = clamp(cam.copy(offsetX = cam.offsetX + wantX, offsetY = cam.offsetY + wantY))
                    if (abs(next.offsetX - (cam.offsetX + wantX)) > 0.5f) velX = 0f // reached an end
                    if (abs(next.offsetY - (cam.offsetY + wantY)) > 0.5f) velY = 0f
                    camera = next
                    velX = Fling.velocityAfter(velX, dt)
                    velY = Fling.velocityAfter(velY, dt)
                    fastScroll = abs(velX) > FAST_SCROLL_SPEED
                }
            } finally {
                fastScroll = false
            }
        }
    }

    companion object {
        /** Double-tap-to-fit animation length: smooth but short (F7). */
        const val FIT_ANIMATION_MS = 260

        /** Faster than this (pixels per second), pages flying past show only their preview (F8). */
        const val FAST_SCROLL_SPEED = 2_500f
    }
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
    val motionScope = rememberCoroutineScope()
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
    LaunchedEffect(visible, pages, viewModel.version, view.fastScroll) {
        if (visible.isEmpty() || pages.isEmpty()) return@LaunchedEffect
        viewModel.requestPreviews(visible.first)
        if (view.fastScroll) return@LaunchedEffect // pages flying past show only their preview
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
                    motionScope = motionScope,
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
            val shown = strip.visiblePages(cam, size.width)
            val top = cam.toScreenY(0f)
            for (i in shown) {
                val page = pages.getOrNull(i) ?: continue
                val left = cam.toScreenX(strip.pageLeft(i))
                drawRect(PaperWhite, Offset(left, top), Size(w, h))
                drawPaperPattern(page.template ?: notebook.template, notebook.pageWidth, notebook.pageHeight, PaperLine, left, top, s)
                // Until the page's ink is drawn in full, its quick preview stands in (soft, F8).
                if (view.fullyDrawn[page.id] != true) {
                    viewModel.previews[page.id]?.let { img ->
                        drawImage(
                            img,
                            dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                            dstSize = IntSize(w.roundToInt(), h.roundToInt()),
                            filterQuality = FilterQuality.Low,
                        )
                    }
                }
                if (viewModel.pageStatus[page.id] is PageStatus.Damaged) {
                    drawRect(Color(0x22D32F2F), Offset(left, top), Size(w, h))
                }
            }
            // The pages touch (F4): a thin dark line marks each edge, including the outer ones.
            if (!shown.isEmpty()) {
                for (i in shown.first..shown.last + 1) {
                    val x = cam.toScreenX(strip.pageLeft(i) - if (i > 0) strip.gap / 2 else 0f)
                    drawLine(PageEdge, Offset(x, top), Offset(x, top + h), strokeWidth = PAGE_EDGE_PX)
                }
            }
        }

        // 2. The ink: one layer per page on screen (see PageInkLayer).
        val flying = view.fastScroll
        for (i in visible) {
            val page = pages.getOrNull(i) ?: continue
            // While pages fly past, only those already drawn stay; the rest show their preview.
            if (flying && view.fullyDrawn[page.id] != true) continue
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
                    // While the rotate handle is dragged, the box turns with the writing (F14).
                    rotate(Math.toDegrees(lasso.angle.toDouble()).toFloat(), pivot = Offset((l + r) / 2, (t + b) / 2)) {
                        drawRect(SelectionBlue, Offset(l, t), Size(r - l, b - t), style = DrawStroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))))
                        val padded = floatArrayOf(l, t, r, b)
                        if (lasso.handles) {
                            val (rx, ry) = rotateHandleAt(padded)
                            drawLine(SelectionBlue, Offset(rx, t), Offset(rx, ry), strokeWidth = 2f)
                            drawCircle(SelectionBlue, HANDLE_RADIUS, Offset(rx, ry))
                            // A small turning arrow on it.
                            drawArc(
                                Color.White, -60f, 270f, useCenter = false,
                                topLeft = Offset(rx - HANDLE_RADIUS * 0.55f, ry - HANDLE_RADIUS * 0.55f),
                                size = Size(HANDLE_RADIUS * 1.1f, HANDLE_RADIUS * 1.1f),
                                style = DrawStroke(width = 2.5f, cap = StrokeCap.Round),
                            )
                        }
                        if (lasso.angle == 0f) {
                            for (handle in lasso.shownHandles()) {
                                val (hx, hy) = handle.at(padded)
                                drawCircle(Color.White, HANDLE_RADIUS, Offset(hx, hy))
                                drawCircle(SelectionBlue, HANDLE_RADIUS, Offset(hx, hy), style = DrawStroke(width = 3f))
                            }
                        }
                    }
                }
                null -> Unit
            }
            view.straight?.let { line ->
                val alpha = if (line.tool == InkTool.HIGHLIGHTER) 0.4f else 1f
                drawLine(
                    Color(line.colorArgb).copy(alpha = alpha),
                    Offset(cam.toScreenX(line.sx), cam.toScreenY(line.sy)),
                    Offset(cam.toScreenX(line.ex), cam.toScreenY(line.ey)),
                    strokeWidth = line.size * cam.scale * if (line.tool == InkTool.HIGHLIGHTER) 1f else 0.8f,
                    cap = if (line.tool == InkTool.HIGHLIGHTER) StrokeCap.Butt else StrokeCap.Round,
                )
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

/** How far above the box the rotate handle sits (screen pixels, F14). */
private const val ROTATE_HANDLE_GAP = 56f

private val SelectionBlue = Color(0xFF2F6BDB)

/** Where the rotate handle sits for a (padded, screen) box: above its top middle. */
private fun rotateHandleAt(box: FloatArray): Pair<Float, Float> = (box[0] + box[2]) / 2 to box[1] - ROTATE_HANDLE_GAP

/** Hold a finger this long to paste (ms). */
private const val LONG_PRESS_MS = 500L

/** How often a held pen is checked when it sends nothing new (ms). */
private const val HOLD_CHECK_MS = 40L

/** The line between touching pages (F4). */
private val PageEdge = Color(0xFF2B2B2B)
private const val PAGE_EDGE_PX = 1.5f
private const val TAP_SLOP = 24f
private const val TAP_MS = 300L
private const val DOUBLE_TAP_MS = 320L

/** What a stylus (or the debug finger) is doing between down and up. */
private enum class PenMode { INK, STRAIGHT, ERASE, LASSO_LOOP, LASSO_MOVE, LASSO_RESIZE, LASSO_ROTATE, BLOCKED }

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.handleGestures(
    snapshot: () -> Snapshot,
    view: EditorViewState,
    motionScope: CoroutineScope,
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
    var grabbed = BoxHandle.BOTTOM_RIGHT
    var penIsFinger = false
    // Press-hard-to-erase only trusts pens whose pressure really varies (see PressureErase).
    var firstPressure = 0f
    var pressureVaries = false
    var pressHold = PressHold(PressureErase.DEFAULT_HOLD_MS)
    // Hold to straighten (F11): how long the pen has been still, and its average pressure.
    val straightStill = StillTimer(Straighten.STILL_RADIUS)
    var pressureSum = 0f
    var pressureCount = 0
    // A pen held perfectly still may send nothing new, so a watcher checks the holds every few
    // milliseconds too, from the pen's last sample (F9, F11).
    var holdWatch: Job? = null
    var lastPenTime = 0L
    var lastHard = false
    var lastStraightStill = -1L // -1: not a line that could straighten
    var lastWorld = Offset.Zero
    var lastScreen = Offset.Zero
    var buttonWasDown = false
    // Double-tap tracking (fingers).
    var tapDownAt = 0L
    var tapDownPos = Offset.Zero
    var tapMoved = false
    var lastTapUpAt = 0L
    var lastTapPos = Offset.Zero
    // Finger scrolling: the sideways lock, how fast the finger moves, and whether it pinched.
    var lock = AxisLock()
    val velocity = VelocityTracker()
    var pinched = false
    // Press and hold a finger to paste (F12).
    var longPress: Job? = null

    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val snap = snapshot()
        val cam = view.camera ?: continue
        // The pen layer runs after this handler: give it the current view before it starts a stroke.
        inputs.setCamera(cam)
        val viewW = size.width.toFloat()
        val viewH = size.height.toFloat()
        // Any new touch (finger or pen) stops a flick or animation where it is, and closes Paste.
        if (event.changes.any { it.changedToDownIgnoreConsumed() }) {
            view.stopMotion()
            view.pasteAt = null
            longPress?.cancel()
        }

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
                    pressHold = PressHold(snap.settings.pressureEraseHoldMs)
                    straightStill.reset()
                    pressureSum = 0f
                    pressureCount = 0
                    lastPenTime = change.uptimeMillis
                    lastHard = false
                    lastStraightStill = -1L
                    holdWatch?.cancel()
                    val watchedPen = change.id
                    holdWatch = motionScope.launch {
                        while (true) {
                            delay(HOLD_CHECK_MS)
                            if (penId != watchedPen || penMode != PenMode.INK) break
                            val quiet = android.os.SystemClock.uptimeMillis() - lastPenTime
                            if (quiet < HOLD_CHECK_MS) continue // samples are still arriving
                            val now = snapshot()
                            val c = view.camera ?: break
                            if (pressHold.doneAfterQuiet(lastHard, quiet)) {
                                penMode = PenMode.ERASE // the next sample is consumed, which cancels the wet stroke
                                eraseStep(viewModel, now, c, lastScreen, lastScreen, onEraser)
                                break
                            }
                            if (lastStraightStill >= 0 && lastStraightStill + quiet >= Straighten.HOLD_MS) {
                                penMode = PenMode.STRAIGHT
                                view.straight = straightLine(now, c.toWorldX(penStart.x), c.toWorldY(penStart.y), lastWorld.x, lastWorld.y, pressureSum, pressureCount, penIsFinger)
                                break
                            }
                        }
                    }
                    val pageIndex = snap.strip.pageAt(cam.toWorldX(change.position.x))
                    val page = snap.pages.getOrNull(pageIndex)
                    val tool = snap.settings.tool
                    val selected = view.lasso as? LassoState.Selected
                    penMode = when {
                        page == null || !viewModel.isEditable(page.id) -> PenMode.BLOCKED
                        change.type == PointerType.Eraser || tool == EditorTool.ERASER -> PenMode.ERASE
                        tool == EditorTool.LASSO && selected != null && onRotateHandle(change.position, selected, snap, cam) -> PenMode.LASSO_ROTATE
                        tool == EditorTool.LASSO && selected != null && handleAt(change.position, selected, snap, cam)?.also { grabbed = it } != null -> PenMode.LASSO_RESIZE
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
                            if (change.pressure in 0f..1f) { pressureSum += change.pressure; pressureCount++ }
                            val wx = cam.toWorldX(change.position.x)
                            val wy = cam.toWorldY(change.position.y)
                            val time = change.uptimeMillis
                            val erasing = snap.settings.pressureErase && isPen
                            val level = snap.settings.pressureEraseLevel
                            lastPenTime = time
                            lastWorld = Offset(wx, wy)
                            lastScreen = change.position
                            lastHard = erasing && PressureErase.isHard(change.pressure, level, pressureVaries)
                            lastStraightStill = -1L
                            if (erasing && pressHold.feed(change.pressure, level, pressureVaries, wx, wy, time)) {
                                // Pressed hard and held (F9): erase from here on.
                                penMode = PenMode.ERASE // consuming from now on cancels the wet stroke
                                change.consume()
                                eraseStep(viewModel, snap, cam, from, change.position, onEraser)
                            } else if (snap.settings.holdToStraighten && snap.settings.tool.draws &&
                                !(erasing && PressureErase.isHard(change.pressure, level, pressureVaries))
                            ) {
                                // Held still mid-line (F11): the line snaps straight, and its end follows the pen.
                                val sx = cam.toWorldX(penStart.x)
                                val sy = cam.toWorldY(penStart.y)
                                val still = straightStill.feed(wx, wy, time)
                                val longEnough = hypot(wx - sx, wy - sy) >= Straighten.MIN_LENGTH
                                if (longEnough) lastStraightStill = still
                                if (still >= Straighten.HOLD_MS && longEnough) {
                                    penMode = PenMode.STRAIGHT
                                    change.consume()
                                    view.straight = straightLine(snap, sx, sy, wx, wy, pressureSum, pressureCount, penIsFinger)
                                }
                            }
                        }
                        PenMode.STRAIGHT -> {
                            change.consume()
                            view.straight?.let { line ->
                                val (ex, ey) = Straighten.snappedEnd(line.sx, line.sy, cam.toWorldX(change.position.x), cam.toWorldY(change.position.y))
                                view.straight = line.copy(ex = ex, ey = ey)
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
                                val dx = (change.position.x - from.x) / cam.scale
                                val dy = (change.position.y - from.y) / cam.scale
                                onLasso(it.copy(target = LassoBox.moved(it.target, dx, dy)))
                            }
                        }
                        PenMode.LASSO_RESIZE -> {
                            change.consume()
                            (view.lasso as? LassoState.Selected)?.let { sel ->
                                val pl = snap.strip.pageLeft(sel.pageIndex)
                                val x = cam.toWorldX(change.position.x) - pl
                                val y = cam.toWorldY(change.position.y)
                                // The plain corner handle keeps the shape; Resize's handles stretch freely.
                                onLasso(sel.copy(target = LassoBox.dragged(sel.target, grabbed, x, y, keepShape = !sel.handles)))
                            }
                        }
                        PenMode.LASSO_ROTATE -> {
                            change.consume()
                            (view.lasso as? LassoState.Selected)?.let { sel ->
                                val b = selectionScreenBox(sel, snap, cam)
                                val angle = LassoBox.rotation((b[0] + b[2]) / 2, (b[1] + b[3]) / 2, penStart.x, penStart.y, change.position.x, change.position.y)
                                onLasso(sel.copy(angle = angle))
                            }
                        }
                        PenMode.BLOCKED -> change.consume()
                    }
                    penLast = change.position
                } else {
                    // Pen lifted.
                    holdWatch?.cancel()
                    when (penMode) {
                        PenMode.STRAIGHT -> {
                            view.straight?.let { line -> finishStraightLine(line, snap, viewModel) }
                            view.straight = null
                        }
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
                                    else LassoState.Selected.of(loop.pageIndex, page.id, picked),
                                )
                            } else {
                                onLasso(null)
                            }
                        }
                        PenMode.LASSO_MOVE, PenMode.LASSO_RESIZE, PenMode.LASSO_ROTATE -> {
                            (view.lasso as? LassoState.Selected)?.let { sel ->
                                if (sel.changed) {
                                    val moved = sel.transformed()
                                    viewModel.replaceStrokes(sel.pageId, moved)
                                    onLasso(LassoState.Selected.of(sel.pageIndex, sel.pageId, moved, sel.handles))
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
                    lock = AxisLock()
                    velocity.resetTracking()
                    pinched = false
                    if (viewModel.clipboard != null) {
                        val at = change.position
                        longPress = motionScope.launch {
                            delay(LONG_PRESS_MS)
                            if (touches.size == 1 && !tapMoved && penId == null) view.pasteAt = at
                        }
                    }
                } else {
                    tapMoved = true // a pinch is not a tap
                    pinched = true
                }
                velocity.addPosition(change.uptimeMillis, change.position)
                continue
            }
            if (!change.pressed) {
                longPress?.cancel()
                // The lift can be a little further on than the last move: apply that bit too.
                val lastPos = touches[change.id]
                if (lastPos != null && touches.size == 1 && !pinched && (change.position - lastPos).getDistance() > 0.5f) {
                    val (mx, my) = lock.step(change.position.x - lastPos.x, change.position.y - lastPos.y)
                    onCamera(snap.strip.clamp(cam.copy(offsetX = cam.offsetX + mx, offsetY = cam.offsetY + my), viewW, viewH))
                }
                touches.remove(change.id)
                val isTap = !tapMoved && touches.isEmpty() && change.uptimeMillis - tapDownAt < TAP_MS
                if (!isTap && touches.isEmpty()) {
                    // The last finger lifted: a flick keeps the pages moving (F6). Not after a pinch.
                    view.fastScroll = false
                    if (!pinched) {
                        velocity.addPosition(change.uptimeMillis, change.position)
                        val v = velocity.calculateVelocity()
                        Fling.start(v.x, v.y, lock.state == AxisLock.State.SIDEWAYS)?.let { (fx, fy) ->
                            view.fling(motionScope, fx, fy) { snap.strip.clamp(it, viewW, viewH) }
                        }
                    }
                }
                if (isTap) {
                    // A tap outside the lasso's selection lets it go (F15; the pen does too).
                    (view.lasso as? LassoState.Selected)?.let { sel ->
                        if (!insideSelection(change.position, sel, snap, cam) && handleAt(change.position, sel, snap, cam) == null &&
                            !onRotateHandle(change.position, sel, snap, cam)
                        ) onLasso(null)
                    }
                    if (change.uptimeMillis - lastTapUpAt < DOUBLE_TAP_MS && (change.position - lastTapPos).getDistance() < TAP_SLOP * 3) {
                        val index = snap.strip.pageAt(cam.toWorldX(change.position.x))
                        view.animateTo(motionScope, snap.strip.fit(snap.settings.fitMode, index, viewW, viewH))
                        lastTapUpAt = 0L
                    } else {
                        lastTapUpAt = change.uptimeMillis
                        lastTapPos = change.position
                    }
                }
                continue
            }
            if ((change.position - tapDownPos).getDistance() > TAP_SLOP) {
                tapMoved = true
                longPress?.cancel()
            }
            if (touches.size == 1) velocity.addPosition(change.uptimeMillis, change.position)
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
                // One finger sticks to sideways when it starts sideways (F6); a pinch moves freely.
                val (mx, my) = if (before.size == 1 && !pinched) lock.step(c1.x - c0.x, c1.y - c0.y) else (c1.x - c0.x) to (c1.y - c0.y)
                next = snap.strip.clamp(next.copy(offsetX = next.offsetX + mx, offsetY = next.offsetY + my), viewW, viewH)
                onCamera(next)
                if (before.size == 1 && !pinched) {
                    view.fastScroll = abs(velocity.calculateVelocity().x) > EditorViewState.FAST_SCROLL_SPEED
                }
            }
        }
    }
}

/** A straight line in progress (F11), in desk units, with the pen it's drawn with. */
data class StraightLine(
    val sx: Float, val sy: Float, val ex: Float, val ey: Float,
    val tool: InkTool, val colorArgb: Int, val size: Float, val pressure: Float, val byFinger: Boolean,
)

private fun straightLine(snap: Snapshot, sx: Float, sy: Float, ex: Float, ey: Float, pressureSum: Float, pressureCount: Int, byFinger: Boolean): StraightLine? {
    val tool = snap.settings.tool
    val style = snap.settings.penStyle(tool) ?: return null
    val (x, y) = Straighten.snappedEnd(sx, sy, ex, ey)
    val pressure = if (pressureCount > 0) pressureSum / pressureCount else InkStroke.NONE
    return StraightLine(sx, sy, x, y, if (tool == EditorTool.HIGHLIGHTER) InkTool.HIGHLIGHTER else InkTool.PEN, style.colorArgb, style.size, pressure, byFinger)
}

/** Saves a finished straight line onto the page where it started. */
private fun finishStraightLine(line: StraightLine, snap: Snapshot, viewModel: NotebookViewModel) {
    val index = snap.strip.pageAt(line.sx)
    val page = snap.pages.getOrNull(index) ?: return
    val left = snap.strip.pageLeft(index)
    val stroke = Straighten.line(
        java.util.UUID.randomUUID().toString(), line.tool, line.colorArgb, line.size,
        line.sx - left, line.sy, line.ex - left, line.ey, line.pressure,
        if (line.byFinger) InkStroke.TOOL_TOUCH else InkStroke.TOOL_STYLUS,
    )
    viewModel.addStrokes(page.id, listOf(stroke))
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

/** True when [p] (screen pixels) is on the rotate handle, shown after Resize (F14). */
private fun onRotateHandle(p: Offset, sel: LassoState.Selected, snap: Snapshot, cam: Camera): Boolean {
    if (!sel.handles) return false
    val b = selectionScreenBox(sel, snap, cam)
    val (rx, ry) = rotateHandleAt(floatArrayOf(b[0] - 6f, b[1] - 6f, b[2] + 6f, b[3] + 6f))
    return hypot(p.x - rx, p.y - ry) <= HANDLE_RADIUS * 2.5f
}

/** The selection handle under [p] (screen pixels), if any. */
private fun handleAt(p: Offset, sel: LassoState.Selected, snap: Snapshot, cam: Camera): BoxHandle? {
    val b = selectionScreenBox(sel, snap, cam)
    val padded = floatArrayOf(b[0] - 6f, b[1] - 6f, b[2] + 6f, b[3] + 6f) // the box is drawn 6 px out
    return sel.shownHandles()
        .map { it to it.at(padded) }
        .filter { (_, at) -> hypot(p.x - at.first, p.y - at.second) <= HANDLE_RADIUS * 2.5f }
        .minByOrNull { (_, at) -> hypot(p.x - at.first, p.y - at.second) }
        ?.first
}

private fun androidx.compose.ui.input.pointer.PointerInputChange.changedToDownIgnoreConsumed() = !previousPressed && pressed
