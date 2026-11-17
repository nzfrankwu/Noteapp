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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.drawscope.clipRect
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
import com.nzfrankwu.noteapp.data.TextBoxLayout
import com.nzfrankwu.noteapp.domain.ink.BookmarkPen
import com.nzfrankwu.noteapp.data.db.Notebook
import com.nzfrankwu.noteapp.data.db.Page
import com.nzfrankwu.noteapp.domain.ink.Camera
import com.nzfrankwu.noteapp.domain.ink.EditorTool
import com.nzfrankwu.noteapp.domain.ink.InkGeometry
import com.nzfrankwu.noteapp.domain.ink.BoxHandle
import com.nzfrankwu.noteapp.domain.ink.LassoBox
import com.nzfrankwu.noteapp.domain.ink.InkStroke
import com.nzfrankwu.noteapp.domain.ink.PageSplit
import com.nzfrankwu.noteapp.domain.ink.InkTool
import com.nzfrankwu.noteapp.domain.ink.InkToolSettings
import com.nzfrankwu.noteapp.domain.ink.PageStrip
import com.nzfrankwu.noteapp.domain.ink.PressureErase
import com.nzfrankwu.noteapp.domain.ink.PressHold
import com.nzfrankwu.noteapp.domain.ink.StillTimer
import com.nzfrankwu.noteapp.domain.ink.Straighten
import com.nzfrankwu.noteapp.domain.ink.PageText
import com.nzfrankwu.noteapp.domain.ink.TextRange
import com.nzfrankwu.noteapp.domain.ink.TextSelection
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
        /** Pictures in the selection (Phase 3), moved and resized with the strokes. */
        val images: List<com.nzfrankwu.noteapp.domain.ink.InkImage> = emptyList(),
        /** Text boxes in the selection (Phase 5), moved and scaled with the rest. */
        val texts: List<com.nzfrankwu.noteapp.domain.ink.InkText> = emptyList(),
    ) : LassoState {
        /** The selection box after the move/resize so far (page units), before any [angle]. */
        fun box(): FloatArray = target

        val changed: Boolean get() = !LassoBox.same(bounds, target) || angle != 0f

        fun transformed(): List<InkStroke> = strokes.map { LassoBox.map(it, bounds, target, angle) }

        fun transformedImages(): List<com.nzfrankwu.noteapp.domain.ink.InkImage> = images.map { LassoBox.mapImage(it, bounds, target, angle) }

        fun transformedTexts(): List<com.nzfrankwu.noteapp.domain.ink.InkText> = texts.map { LassoBox.mapText(it, bounds, target, angle) }

        /** The handles shown: just the bottom-right corner, or all eight after Resize. */
        fun shownHandles(): List<BoxHandle> = when {
            !handles -> listOf(BoxHandle.BOTTOM_RIGHT)
            // Text scales evenly, so only the corners; and a short box's middle stays free to drag (Phase 5).
            texts.isNotEmpty() -> BoxHandle.entries.filter { (it.left || it.right) && (it.top || it.bottom) }
            else -> BoxHandle.entries
        }

        companion object {
            /** A fresh selection of [strokes], [images] and [texts] (none moved yet); null if all are empty. */
            fun of(
                pageIndex: Int, pageId: String, strokes: List<InkStroke>,
                images: List<com.nzfrankwu.noteapp.domain.ink.InkImage> = emptyList(), handles: Boolean = false,
                texts: List<com.nzfrankwu.noteapp.domain.ink.InkText> = emptyList(),
            ): Selected? = InkGeometry.boundsOf(strokes, images, texts)?.let {
                // Pictures or text boxes alone open in resize mode (F32): all eight handles and the rotate handle.
                Selected(
                    pageIndex, pageId, strokes, it,
                    handles = handles || (strokes.isEmpty() && (images.isNotEmpty() || texts.isNotEmpty())),
                    images = images, texts = texts,
                )
            }
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

    /** Counts touches on the page (pen or finger); the tool's settings panel closes on the next one (F27, F29). */
    var pageTouches by mutableIntStateOf(0)

    /** Hands finished strokes from the pen layer to the page layers without a flash (F30). */
    val handoff = InkHandoff()

    /** Selected PDF text (PHASE-4.md 4.7), or null. */
    var textSel by mutableStateOf<TextSel?>(null)

    /**
     * Text boxes not drawn on the page right now (Phase 5): the one being typed in (the editor
     * shows it) and any the lasso holds (the overlay shows them where they're dragged).
     */
    var hiddenTexts by mutableStateOf<Set<String>>(emptySet())

    /** The T button is on (Phase 5): a tap on the page (pen or finger) places or edits a text box. */
    var textMode by mutableStateOf(false)

    /** The text box being typed in, or null (see TextBoxEditing.kt). */
    var editingText by mutableStateOf<TextEditing?>(null)

    /** The bookmark pen's loop or rectangle being drawn (desk units), or null (PHASE-5.md FB5). */
    var bookmarkLoop by mutableStateOf<BookmarkLoop?>(null)

    /** The part of a page the bookmark pen just picked, waiting for its picture and the bookmark sheet. */
    var bookmarkArea by mutableStateOf<BookmarkArea?>(null)

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

/** Selected words of a PDF page (PHASE-4.md 4.7): page [pageIndex] ([pageId]), its [text], and the [range]. */
data class TextSel(val pageIndex: Int, val pageId: String, val text: PageText, val range: TextRange)

/** The two handles of a text selection. */
enum class TextHandle { START, END }

/** Where a text handle's knob is drawn: [x], [y] in screen pixels (the knob hangs below the text). */
fun textHandleKnob(sel: TextSel, handle: TextHandle, strip: PageStrip, cam: Camera): Offset {
    val h = TextSelection.handles(sel.text, sel.range)
    val x = if (handle == TextHandle.START) h[0] else h[2]
    val y = if (handle == TextHandle.START) h[1] else h[3]
    return Offset(cam.toScreenX(strip.pageLeft(sel.pageIndex) + x), cam.toScreenY(strip.pageTop(sel.pageIndex) + y) + TEXT_KNOB_RADIUS + 2f)
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
    val sizes = pages.map { it.size(notebook) }
    val strip = remember(sizes) { PageStrip(sizes) }
    val renderer = remember { CanvasStrokeRenderer.create() }
    val motionScope = rememberCoroutineScope()
    val inputs = remember { InkLayerInputs() }
    val dark = com.nzfrankwu.noteapp.ui.theme.LocalDarkTheme.current

    // The pen layer's brush (read at the start of each stroke; its transform is set by the gestures).
    inputs.brush = settings.penStyle(settings.tool)?.let { style ->
        InkBrushes.brush(settings.inkTool() ?: InkTool.PEN, style.colorArgb, style.size)
    }

    // Latest values for the long-running gesture handler.
    val current by rememberUpdatedState(Snapshot(strip, pages, settings))
    var eraserAt by remember { mutableStateOf<Offset?>(null) }

    val onStrokesFinished: (Map<androidx.ink.authoring.InProgressStrokeId, Stroke>) -> Unit = remember(viewModel, view) {
        { finished ->
            val snap = current
            val byPage = LinkedHashMap<Int, MutableList<InkStroke>>()
            for ((wetId, stroke) in finished) {
                // In desk units first, then cut where it crosses from page to page (F36): each page
                // keeps its own piece, moved into its own units.
                val desk = InkBrushes.toSaved(stroke, 0f, 0f)
                val pieces = PageSplit.split(
                    desk,
                    pageAt = snap.strip::pageAt,
                    edgeBetween = { a, b -> if (b > a) snap.strip.pageLeft(b) - snap.strip.gap / 2 else snap.strip.pageLeft(a) - snap.strip.gap / 2 },
                    newId = { java.util.UUID.randomUUID().toString() },
                ).filter { (index, _) -> snap.pages.getOrNull(index) != null }
                // The pen layer keeps drawing it until every piece is drawn by its page (F30: no flash).
                view.handoff.expect(pieces.map { it.second.id }, wetId)
                for ((index, piece) in pieces) {
                    byPage.getOrPut(index) { ArrayList() } +=
                        piece.mapped(1f, 0f, 0f, -snap.strip.pageLeft(index), -snap.strip.pageTop(index), scaleSize = false)
                }
            }
            byPage.keys.forEach { viewModel.prepareToDraw(snap.pages[it].id) } // so it can draw them (and the pen layer let go)
            viewModel.addStrokesAcross(byPage.entries.associate { (index, strokes) -> snap.pages[index].id to strokes })
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
    // Imported PDF pages near the screen, drawn at the settled zoom (see NotebookViewModel.preparePdf).
    LaunchedEffect(visible, pages, view.inkScale) {
        if (visible.isEmpty() || view.inkScale <= 0f) return@LaunchedEffect
        val order = ((visible.first - 1)..(visible.last + 1))
            .sortedBy { if (it in visible) 0 else 1 }
            .mapNotNull { pages.getOrNull(it) }
        viewModel.preparePdf(order, view.inkScale)
    }
    // Pictures on the pages near the screen.
    LaunchedEffect(visible, pages, viewModel.version) {
        if (visible.isEmpty()) return@LaunchedEffect
        viewModel.prepareImages(((visible.first - 1)..(visible.last + 1)).mapNotNull { pages.getOrNull(it) })
    }
    // Zoomed in very far on a PDF page: once the view rests, draw just the part on screen, sharp.
    LaunchedEffect(view, strip, pages) {
        snapshotFlow { view.camera }.collectLatest { cam ->
            if (cam == null || pages.none { it.isPdf }) return@collectLatest
            delay(PDF_DETAIL_DELAY_MS)
            val vw = view.viewSize.width.toFloat()
            val vh = view.viewSize.height.toFloat()
            val regions = HashMap<Page, FloatArray>()
            for (i in strip.visiblePages(cam, vw)) {
                val page = pages.getOrNull(i)?.takeIf { it.isPdf } ?: continue
                val pl = strip.pageLeft(i)
                val pt = strip.pageTop(i)
                // The part of the page on screen, plus a margin for small moves, in page units.
                val mx = vw / cam.scale * 0.15f
                val my = vh / cam.scale * 0.15f
                val l = (cam.toWorldX(0f) - pl - mx).coerceAtLeast(0f)
                val t = (cam.toWorldY(0f) - pt - my).coerceAtLeast(0f)
                val r = (cam.toWorldX(vw) - pl + mx).coerceAtMost(strip.pageWidth(i))
                val b = (cam.toWorldY(vh) - pt + my).coerceAtMost(strip.pageHeight(i))
                if (r > l && b > t) regions[page] = floatArrayOf(l, t, r, b)
            }
            viewModel.preparePdfDetail(regions, cam.scale)
        }
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
            val shown = strip.visiblePages(cam, size.width)
            for (i in shown) {
                val page = pages.getOrNull(i) ?: continue
                val pw = strip.pageWidth(i)
                val ph = strip.pageHeight(i)
                val w = pw * s
                val h = ph * s
                val left = cam.toScreenX(strip.pageLeft(i))
                val top = cam.toScreenY(strip.pageTop(i))
                drawRect(PaperWhite, Offset(left, top), Size(w, h))
                if (page.isPdf) {
                    // An imported PDF's page (no paper pattern), then a sharper part when zoomed in far.
                    viewModel.pdfPictures[page.id]?.let { drawPdfLayer(it, left, top, s) }
                    viewModel.pdfDetail[page.id]?.let { drawPdfLayer(it, left, top, s) }
                } else {
                    drawPaperPattern(page.template ?: notebook.template, pw, ph, PaperLine, left, top, s)
                }
                // Pictures, under the ink (read version so they redraw when they change).
                @Suppress("UNUSED_VARIABLE") val edits = viewModel.version
                val held = (view.lasso as? LassoState.Selected)?.takeIf { it.pageId == page.id }?.images?.mapTo(HashSet()) { it.id }
                // Clipped to the page, like the ink: a picture never shows on the next page.
                clipRect(left, top, left + w, top + h) {
                    for (image in viewModel.ink.images(page.id)) {
                        if (held == null || image.id !in held) drawPicture(image, viewModel.imageBitmaps[image.asset], left, top, s)
                    }
                    // Text boxes, above the pictures and under the ink (Phase 5). A box being
                    // typed in or held by the lasso is drawn by the editor/overlay instead.
                    val boxes = viewModel.ink.texts(page.id)
                    if (boxes.isNotEmpty()) {
                        val heldTexts = (view.lasso as? LassoState.Selected)?.takeIf { it.pageId == page.id }?.texts.orEmpty()
                        val hidden = if (heldTexts.isEmpty()) view.hiddenTexts else view.hiddenTexts + heldTexts.map { it.id }
                        val shownBoxes = if (hidden.isEmpty()) boxes else boxes.filter { it.id !in hidden }
                        drawIntoCanvas { c ->
                            val nc = c.nativeCanvas
                            nc.save()
                            nc.translate(left, top)
                            TextBoxLayout.drawAll(nc, shownBoxes, s)
                            nc.restore()
                        }
                    }
                }
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
            // Where two pages meet, the line is as tall as the taller of the two.
            if (!shown.isEmpty()) {
                for (i in shown.first..shown.last + 1) {
                    val x = cam.toScreenX(strip.pageLeft(i) - if (i > 0) strip.gap / 2 else 0f)
                    val tall = listOfNotNull(i - 1, i).filter { it in 0 until strip.pageCount }
                        .maxByOrNull { strip.pageHeight(it) } ?: continue
                    val top = cam.toScreenY(strip.pageTop(tall))
                    drawLine(PageEdge, Offset(x, top), Offset(x, top + strip.pageHeight(tall) * s), strokeWidth = PAGE_EDGE_PX)
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
                PageInkLayer(viewModel, page.id, i, strip.pageWidth(i), strip.pageHeight(i), strip, view, renderer)
            }
        }

        // 3. The lasso's loop and box, the bookmark pen's loop, and the eraser's ring.
        Canvas(Modifier.fillMaxSize()) {
            val cam = view.camera ?: return@Canvas
            view.bookmarkLoop?.let { loop ->
                val style = DrawStroke(width = 2.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 8f)))
                if (loop.rect && loop.xs.size >= 2) {
                    val x0 = cam.toScreenX(loop.xs[0]); val y0 = cam.toScreenY(loop.ys[0])
                    val x1 = cam.toScreenX(loop.xs[1]); val y1 = cam.toScreenY(loop.ys[1])
                    drawRect(BookmarkOrange.copy(alpha = 0.10f), Offset(minOf(x0, x1), minOf(y0, y1)), Size(abs(x1 - x0), abs(y1 - y0)))
                    drawRect(BookmarkOrange, Offset(minOf(x0, x1), minOf(y0, y1)), Size(abs(x1 - x0), abs(y1 - y0)), style = style)
                } else if (loop.xs.size > 1) {
                    val path = androidx.compose.ui.graphics.Path()
                    loop.xs.indices.forEach { k ->
                        val x = cam.toScreenX(loop.xs[k]); val y = cam.toScreenY(loop.ys[k])
                        if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, BookmarkOrange, style = style)
                }
            }
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
                    for (image in lasso.transformedImages()) {
                        drawPicture(image, viewModel.imageBitmaps[image.asset], cam.toScreenX(strip.pageLeft(lasso.pageIndex)), cam.toScreenY(strip.pageTop(lasso.pageIndex)), cam.scale)
                    }
                    if (lasso.texts.isNotEmpty()) {
                        drawIntoCanvas { c ->
                            val nc = c.nativeCanvas
                            nc.save()
                            nc.translate(cam.toScreenX(strip.pageLeft(lasso.pageIndex)), cam.toScreenY(strip.pageTop(lasso.pageIndex)))
                            TextBoxLayout.drawAll(nc, lasso.transformedTexts(), cam.scale)
                            nc.restore()
                        }
                    }
                    val pl = strip.pageLeft(lasso.pageIndex)
                    val pt = strip.pageTop(lasso.pageIndex)
                    val l = cam.toScreenX(pl + box[0]) - 6f; val t = cam.toScreenY(pt + box[1]) - 6f
                    val r = cam.toScreenX(pl + box[2]) + 6f; val b = cam.toScreenY(pt + box[3]) + 6f
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
            view.textSel?.let { sel ->
                val pl = strip.pageLeft(sel.pageIndex)
                val pt = strip.pageTop(sel.pageIndex)
                for (r in TextSelection.lineRects(sel.text, sel.range)) {
                    val l = cam.toScreenX(pl + r[0]); val t = cam.toScreenY(pt + r[1])
                    drawRect(TextTint, Offset(l, t), Size(cam.toScreenX(pl + r[2]) - l, cam.toScreenY(pt + r[3]) - t))
                }
                for (handle in TextHandle.entries) {
                    val knob = textHandleKnob(sel, handle, strip, cam)
                    val words = sel.text.words
                    val word = if (handle == TextHandle.START) words[sel.range.first] else words[sel.range.last]
                    val top = cam.toScreenY(pt + word.top)
                    drawLine(SelectionBlue, Offset(knob.x, top), Offset(knob.x, knob.y), strokeWidth = 3f)
                    drawCircle(SelectionBlue, TEXT_KNOB_RADIUS, knob)
                }
            }
            view.straight?.let { line ->
                val alpha = if (line.tool.isHighlighter) com.nzfrankwu.noteapp.domain.ink.HighlightOpacity.of(line.colorArgb) else 1f
                drawLine(
                    Color(line.colorArgb or 0xFF000000.toInt()).copy(alpha = alpha),
                    Offset(cam.toScreenX(line.sx), cam.toScreenY(line.sy)),
                    Offset(cam.toScreenX(line.ex), cam.toScreenY(line.ey)),
                    strokeWidth = line.size * cam.scale * if (line.tool.isHighlighter || line.tool == InkTool.PEN_STEADY) 1f else 0.8f,
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
        InkLayer(inputs, view.handoff, onStrokesFinished)
    }
}

/** The pen layer. Its inputs never change identity, so it isn't rebuilt (which would cancel a stroke). */
@Composable
private fun InkLayer(
    inputs: InkLayerInputs,
    handoff: InkHandoff,
    onStrokesFinished: (Map<androidx.ink.authoring.InProgressStrokeId, Stroke>) -> Unit,
) {
    val nextBrush = remember(inputs) { { inputs.brush } }
    WetInkLayer(
        nextBrush = nextBrush,
        pointerEventToWorldTransform = inputs.transform,
        handoff = handoff,
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

/** A text handle's knob, and how far from it a touch still grabs it (screen pixels). */
const val TEXT_KNOB_RADIUS = 13f
private const val TEXT_KNOB_GRAB = 44f
private val TextTint = Color(0x552F6BDB)

/** How far above the box the rotate handle sits (screen pixels, F14). */
private const val ROTATE_HANDLE_GAP = 56f

private val SelectionBlue = Color(0xFF2F6BDB)

/** Where the rotate handle sits for a (padded, screen) box: above its top middle. */
private fun rotateHandleAt(box: FloatArray): Pair<Float, Float> = (box[0] + box[2]) / 2 to box[1] - ROTATE_HANDLE_GAP

/** Hold a finger this long to paste (ms). */
/** The bookmark pen's loop (FB5): orange, so it isn't mistaken for the lasso's blue. */
private val BookmarkOrange = Color(0xFFEF6C00)

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
private enum class PenMode { INK, STRAIGHT, ERASE, LASSO_LOOP, LASSO_MOVE, LASSO_RESIZE, LASSO_ROTATE, TEXT_HANDLE, TEXT_TAP, BOOKMARK_LOOP, BLOCKED }

/** The bookmark pen's loop or rectangle being drawn, in desk units (PHASE-5.md FB5). */
data class BookmarkLoop(val pageIndex: Int, val xs: List<Float>, val ys: List<Float>, val rect: Boolean)

/** The part of page [pageIndex] the bookmark pen picked: [left, top, right, bottom] in page units. */
data class BookmarkArea(val pageIndex: Int, val pageId: String, val box: FloatArray)

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
    // Press and hold a finger to paste (F12), or to pick the picture under it (F31).
    var longPress: Job? = null
    var penDownAt = 0L
    // A finger dragging the lasso's selection (F32): which handle, or null to move it.
    var fingerDrag: FingerDrag? = null
    // The pen's touch finished typing in a text box (Phase 5): lifting it then makes no new box.
    var penFinishedTyping = false

    /** Saves a moved, resized or turned selection and selects the result. */
    fun commitSelection(sel: LassoState.Selected) {
        if (!sel.changed) return
        val moved = sel.transformed()
        val movedImages = sel.transformedImages()
        val movedTexts = viewModel.replaceSelection(sel.pageId, moved, movedImages, sel.transformedTexts())
        onLasso(LassoState.Selected.of(sel.pageIndex, sel.pageId, moved, movedImages, handles = sel.handles, texts = movedTexts))
    }

    // Selecting PDF text (PHASE-4.md 4.7): which handle a finger or the pen is dragging, and
    // whether a held finger's page text is being read (lifting the finger then doesn't cancel it).
    var textDrag: TextHandle? = null
    var readingText = false

    /** The text handle whose knob is under screen point [p], if any. */
    fun textHandleAt(p: Offset, snap: Snapshot, cam: Camera): TextHandle? {
        val sel = view.textSel ?: return null
        return TextHandle.entries
            .map { it to (textHandleKnob(sel, it, snap.strip, cam) - p).getDistance() }
            .filter { it.second < TEXT_KNOB_GRAB }
            .minByOrNull { it.second }?.first
    }

    /** Moves [handle] to the word nearest screen point [p]; returns the handle now held (they swap when they cross). */
    fun dragTextHandle(handle: TextHandle, p: Offset, snap: Snapshot, cam: Camera): TextHandle {
        val sel = view.textSel ?: return handle
        // The finger is on the knob, below the text: aim a little above it.
        val x = cam.toWorldX(p.x) - snap.strip.pageLeft(sel.pageIndex)
        val y = cam.toWorldY(p.y - TEXT_KNOB_RADIUS * 2) - snap.strip.pageTop(sel.pageIndex)
        val i = TextSelection.closestWord(sel.text, x, y) ?: return handle
        val r = sel.range
        val (range, held) = when (handle) {
            TextHandle.START -> if (i <= r.last) TextRange(i, r.last) to TextHandle.START else TextRange(r.last, i) to TextHandle.END
            TextHandle.END -> if (i >= r.first) TextRange(r.first, i) to TextHandle.END else TextRange(i, r.first) to TextHandle.START
        }
        if (range != r) view.textSel = sel.copy(range = range)
        return held
    }

    /** Selects the PDF word under screen point [at] (reading the page's text if needed). True if there was one. */
    suspend fun selectTextAt(at: Offset, snap: Snapshot, cam: Camera): Boolean {
        val index = snap.strip.pageAt(cam.toWorldX(at.x))
        val page = snap.pages.getOrNull(index)?.takeIf { it.isPdf } ?: return false
        val text = viewModel.pageText(page)
        val x = cam.toWorldX(at.x) - snap.strip.pageLeft(index)
        val y = cam.toWorldY(at.y) - snap.strip.pageTop(index)
        val word = TextSelection.wordAt(text, x, y, TextSelection.HOLD_SLOP / cam.scale.coerceAtMost(1f)) ?: return false
        onLasso(null)
        view.textSel = TextSel(index, page.id, text, TextRange(word, word))
        return true
    }

    /** Selects the topmost picture under screen point [at], switching to the lasso. True if there was one. */
    fun selectPictureAt(at: Offset, snap: Snapshot, cam: Camera): Boolean {
        val index = snap.strip.pageAt(cam.toWorldX(at.x))
        val page = snap.pages.getOrNull(index) ?: return false
        val x = cam.toWorldX(at.x) - snap.strip.pageLeft(index)
        val y = cam.toWorldY(at.y) - snap.strip.pageTop(index)
        // A text box lies above the pictures, so it's picked first (Phase 5).
        val text = viewModel.textAt(page.id, x, y)
        val image = if (text == null) viewModel.imageAt(page.id, x, y) ?: return false else null
        if (snap.settings.tool != EditorTool.LASSO) viewModel.pickTool(EditorTool.LASSO)
        view.textMode = false
        onLasso(
            if (text != null) LassoState.Selected.of(index, page.id, emptyList(), texts = listOf(text))
            else LassoState.Selected.of(index, page.id, emptyList(), listOf(image!!)),
        )
        return true
    }

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
            view.pageTouches++ // closes the tool's settings panel (pen F27, finger F29)
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
                    penDownAt = change.uptimeMillis
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
                    // Where the pen is, from its very first sample: a hard press that never moves
                    // sends nothing more, and the hold watcher erases (and shows the ring) here (F26).
                    lastScreen = change.position
                    lastWorld = Offset(cam.toWorldX(change.position.x), cam.toWorldY(change.position.y))
                    pressureVaries = PressureErase.reportsPressure(firstPressure, change.pressure)
                    var hardAtOnce = false
                    if (snap.settings.pressureErase && isPen) {
                        val level = snap.settings.pressureEraseLevel
                        lastHard = PressureErase.isHard(change.pressure, level, pressureVaries)
                        hardAtOnce = pressHold.feed(change.pressure, level, pressureVaries, lastWorld.x, lastWorld.y, change.uptimeMillis)
                    }
                    holdWatch?.cancel()
                    val watchedPen = change.id
                    holdWatch = motionScope.launch {
                        while (true) {
                            delay(HOLD_CHECK_MS)
                            if (penId != watchedPen || penMode != PenMode.INK) break
                            val held = android.os.SystemClock.uptimeMillis() - penDownAt
                            if (held >= LONG_PRESS_MS && (lastScreen - penStart).getDistance() < TAP_SLOP) {
                                val c0 = view.camera ?: break
                                if (selectPictureAt(penStart, snapshot(), c0)) {
                                    penMode = PenMode.LASSO_MOVE // the next sample is consumed, which cancels the dot
                                    break
                                }
                            }
                            val quiet = android.os.SystemClock.uptimeMillis() - lastPenTime
                            if (quiet < HOLD_CHECK_MS) continue // samples are still arriving
                            val now = snapshot()
                            val c = view.camera ?: break
                            if (pressHold.doneAfterQuiet(lastHard, quiet)) {
                                penMode = PenMode.ERASE // the next sample is consumed, which cancels the wet stroke
                                eraseStep(viewModel, now, c, lastScreen, lastScreen, onEraser, penDownAt)
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
                    // The pen can drag a text selection's handles; touching anywhere else lets the selection go.
                    val textHandle = textHandleAt(change.position, snap, cam)
                    if (textHandle == null) view.textSel = null
                    // Touching the page outside the text box being typed in finishes it (Phase 5).
                    penFinishedTyping = view.editingText != null
                    if (penFinishedTyping) finishTextEditing(view, viewModel)
                    penMode = when {
                        textHandle != null -> PenMode.TEXT_HANDLE.also { textDrag = textHandle }
                        page == null || !viewModel.isEditable(page.id) -> PenMode.BLOCKED
                        // The T button is on: the pen places or picks text boxes (its eraser end still erases).
                        view.textMode && change.type != PointerType.Eraser -> PenMode.TEXT_TAP
                        change.type == PointerType.Eraser || tool == EditorTool.ERASER -> PenMode.ERASE
                        tool == EditorTool.LASSO && selected != null && onRotateHandle(change.position, selected, snap, cam) -> PenMode.LASSO_ROTATE
                        tool == EditorTool.LASSO && selected != null && handleAt(change.position, selected, snap, cam)?.also { grabbed = it } != null -> PenMode.LASSO_RESIZE
                        tool == EditorTool.LASSO && selected != null && insideSelection(change.position, selected, snap, cam) -> PenMode.LASSO_MOVE
                        tool == EditorTool.LASSO -> {
                            onLasso(LassoState.Drawing(pageIndex, listOf(cam.toWorldX(change.position.x)), listOf(cam.toWorldY(change.position.y))))
                            PenMode.LASSO_LOOP
                        }
                        tool == EditorTool.BOOKMARK -> {
                            view.bookmarkLoop = BookmarkLoop(
                                pageIndex, listOf(cam.toWorldX(change.position.x)), listOf(cam.toWorldY(change.position.y)), snap.settings.bookmarkRect,
                            )
                            PenMode.BOOKMARK_LOOP
                        }
                        else -> PenMode.INK
                    }
                    if (penMode == PenMode.INK && hardAtOnce) penMode = PenMode.ERASE // "Hold for: no wait"
                    if (penMode != PenMode.INK) change.consume()
                    if (penMode == PenMode.ERASE) eraseStep(viewModel, snap, cam, change.position, change.position, onEraser, penDownAt)
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
                            if (PressureErase.reportsPressure(firstPressure, change.pressure)) pressureVaries = true
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
                                eraseStep(viewModel, snap, cam, from, change.position, onEraser, penDownAt)
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
                            eraseStep(viewModel, snap, cam, from, change.position, onEraser, penDownAt)
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
                                val y = cam.toWorldY(change.position.y) - snap.strip.pageTop(sel.pageIndex)
                                // Corners always keep the shape (F35); the side handles stretch one way.
                                onLasso(sel.copy(target = LassoBox.dragged(sel.target, grabbed, x, y, keepShape = keepsShape(sel))))
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
                        PenMode.TEXT_HANDLE -> {
                            change.consume()
                            textDrag?.let { textDrag = dragTextHandle(it, change.position, snap, cam) }
                        }
                        PenMode.BOOKMARK_LOOP -> {
                            change.consume()
                            view.bookmarkLoop?.let { loop ->
                                val (xs, ys) = BookmarkPen.extend(loop.xs, loop.ys, cam.toWorldX(change.position.x), cam.toWorldY(change.position.y), loop.rect)
                                view.bookmarkLoop = loop.copy(xs = xs, ys = ys)
                            }
                        }
                        PenMode.TEXT_TAP, PenMode.BLOCKED -> change.consume()
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
                            if (loop != null && page != null) {
                                val pl = snap.strip.pageLeft(loop.pageIndex)
                                val pt = snap.strip.pageTop(loop.pageIndex)
                                val xs = FloatArray(loop.xs.size) { loop.xs[it] - pl }
                                val ys = FloatArray(loop.ys.size) { loop.ys[it] - pt }
                                val extent = (xs.max() - xs.min()) + (ys.max() - ys.min())
                                if (extent * cam.scale < LASSO_TAP_PX) {
                                    // A lasso tap: the text box under the pen (they're above pictures),
                                    // else the topmost picture (PHASE-3.md 3.6).
                                    val text = viewModel.textAt(page.id, xs.first(), ys.first())
                                    val image = if (text == null) viewModel.imageAt(page.id, xs.first(), ys.first()) else null
                                    onLasso(
                                        when {
                                            text != null -> LassoState.Selected.of(loop.pageIndex, page.id, emptyList(), texts = listOf(text))
                                            image != null -> LassoState.Selected.of(loop.pageIndex, page.id, emptyList(), listOf(image))
                                            else -> null
                                        },
                                    )
                                } else {
                                    val picked = viewModel.strokesInLoop(page.id, xs, ys)
                                    val images = viewModel.imagesInLoop(page.id, xs, ys)
                                    val texts = viewModel.textsInLoop(page.id, xs, ys)
                                    onLasso(LassoState.Selected.of(loop.pageIndex, page.id, picked, images, texts = texts))
                                }
                            } else {
                                onLasso(null)
                            }
                        }
                        PenMode.LASSO_MOVE, PenMode.LASSO_RESIZE, PenMode.LASSO_ROTATE -> {
                            (view.lasso as? LassoState.Selected)?.let { commitSelection(it) }
                        }
                        PenMode.BOOKMARK_LOOP -> {
                            val loop = view.bookmarkLoop
                            view.bookmarkLoop = null
                            val page = loop?.let { snap.pages.getOrNull(it.pageIndex) }
                            if (loop != null && page != null) {
                                val pl = snap.strip.pageLeft(loop.pageIndex)
                                val pt = snap.strip.pageTop(loop.pageIndex)
                                val box = BookmarkPen.area(
                                    FloatArray(loop.xs.size) { loop.xs[it] - pl }, FloatArray(loop.ys.size) { loop.ys[it] - pt },
                                    snap.strip.pageWidth(loop.pageIndex), snap.strip.pageHeight(loop.pageIndex),
                                )
                                if (box != null) view.bookmarkArea = BookmarkArea(loop.pageIndex, page.id, box)
                            }
                        }
                        PenMode.TEXT_TAP -> if ((change.position - penStart).getDistance() < TAP_SLOP * 2) {
                            startTextEditing(view, viewModel, snap.strip, snap.pages, change.position, allowNew = !penFinishedTyping)
                        }
                        else -> Unit
                    }
                    if (penMode != PenMode.INK) change.consume()
                    if (penMode == PenMode.TEXT_HANDLE) textDrag = null
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
                    textDrag = textHandleAt(change.position, snap, cam)
                    fingerDrag = if (textDrag != null) null else (view.lasso as? LassoState.Selected)?.let { sel ->
                        when {
                            sel.handles && handleAt(change.position, sel, snap, cam) != null -> FingerDrag(handleAt(change.position, sel, snap, cam))
                            insideSelection(change.position, sel, snap, cam) -> FingerDrag(null)
                            else -> null
                        }
                    }
                    val at = change.position
                    longPress = motionScope.launch {
                        delay(LONG_PRESS_MS)
                        if (touches.size != 1 || tapMoved || penId != null || fingerDrag != null || textDrag != null) return@launch
                        val c = view.camera ?: return@launch
                        // A finger held on a picture picks it (F31); on PDF text it selects the word,
                        // and keeps dragging its end (PHASE-4.md 4.7); elsewhere it offers Paste (F12).
                        val now = snapshot()
                        when {
                            // A finger held on a text box types in it (PHASE-5.md FB2).
                            view.lasso == null && startTextEditing(view, viewModel, now.strip, now.pages, at, allowNew = false) -> Unit
                            selectPictureAt(at, now, c) -> Unit
                            run {
                                // The first hold on a big PDF reads its text, which can take a moment.
                                readingText = true
                                try {
                                    selectTextAt(at, snapshot(), c)
                                } finally {
                                    readingText = false
                                }
                            } -> if (touches.size == 1 && !tapMoved) textDrag = TextHandle.END
                            viewModel.clipboard != null -> view.pasteAt = at
                        }
                    }
                } else {
                    tapMoved = true // a pinch is not a tap
                    pinched = true
                    // Two fingers zoom, even over a selection: keep any move made so far.
                    if (fingerDrag != null) (view.lasso as? LassoState.Selected)?.let { commitSelection(it) }
                    fingerDrag = null
                    textDrag = null
                }
                velocity.addPosition(change.uptimeMillis, change.position)
                continue
            }
            if (!change.pressed) {
                if (!readingText) longPress?.cancel()
                if (textDrag != null && touches.size == 1) {
                    textDrag = null
                    touches.remove(change.id)
                    continue
                }
                if (fingerDrag != null && touches.size == 1) {
                    fingerDrag = null
                    touches.remove(change.id)
                    (view.lasso as? LassoState.Selected)?.let { commitSelection(it) }
                    continue
                }
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
                    view.textSel = null // a tap lets selected text go
                    // A tap outside the lasso's selection lets it go (F15; the pen does too).
                    (view.lasso as? LassoState.Selected)?.let { sel ->
                        if (!insideSelection(change.position, sel, snap, cam) && handleAt(change.position, sel, snap, cam) == null &&
                            !onRotateHandle(change.position, sel, snap, cam)
                        ) onLasso(null)
                    }
                    // Text boxes (Phase 5): a tap finishes the one being typed in; a tap on a box
                    // types in it; with the T button on, a tap on the paper makes a new one.
                    val typing = view.editingText != null
                    if (typing) finishTextEditing(view, viewModel)
                    if (view.lasso == null && startTextEditing(view, viewModel, snap.strip, snap.pages, change.position, allowNew = view.textMode && !typing)) {
                        lastTapUpAt = 0L
                        continue
                    }
                    if (typing) {
                        lastTapUpAt = 0L
                        continue
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

        // ---- A finger dragging a text handle (PHASE-4.md 4.7) ----
        val held = textDrag
        if (held != null && touches.size == 1 && penId == null) {
            val id = touches.keys.first()
            val now = event.changes.firstOrNull { it.id == id }?.position
            if (now != null && now != touches.getValue(id)) {
                touches[id] = now
                if ((now - tapDownPos).getDistance() > TAP_SLOP) {
                    tapMoved = true
                    longPress?.cancel()
                }
                textDrag = dragTextHandle(held, now, snap, cam)
            }
            continue
        }

        // ---- A finger dragging the selection (F32) ----
        val drag = fingerDrag
        if (drag != null && touches.size == 1 && penId == null) {
            val id = touches.keys.first()
            val now = event.changes.firstOrNull { it.id == id }?.position
            val before = touches.getValue(id)
            val sel = view.lasso as? LassoState.Selected
            if (now != null && sel != null && now != before) {
                touches[id] = now
                if ((now - tapDownPos).getDistance() > TAP_SLOP) tapMoved = true
                val handle = drag.handle
                onLasso(
                    if (handle == null) {
                        sel.copy(target = LassoBox.moved(sel.target, (now.x - before.x) / cam.scale, (now.y - before.y) / cam.scale))
                    } else {
                        val x = cam.toWorldX(now.x) - snap.strip.pageLeft(sel.pageIndex)
                        val y = cam.toWorldY(now.y) - snap.strip.pageTop(sel.pageIndex)
                        sel.copy(target = LassoBox.dragged(sel.target, handle, x, y, keepShape = keepsShape(sel)))
                    },
                )
            }
            continue
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

/**
 * Whether a corner handle keeps the selection's shape: always for pictures (F35: they only grow
 * or shrink) and outside Resize mode; Resize's corners stretch writing freely (Phase 2).
 */
private fun keepsShape(sel: LassoState.Selected): Boolean = !sel.handles || sel.images.isNotEmpty() || sel.texts.isNotEmpty()

/** A finger dragging the lasso's selection: by [handle] (resize), or by its middle (move) when null. */
private class FingerDrag(val handle: BoxHandle?)

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
    return StraightLine(sx, sy, x, y, snap.settings.inkTool(tool) ?: InkTool.PEN, style.colorArgb, style.size, pressure, byFinger)
}

/** Saves a finished straight line onto the page where it started. */
private fun finishStraightLine(line: StraightLine, snap: Snapshot, viewModel: NotebookViewModel) {
    val index = snap.strip.pageAt(line.sx)
    val page = snap.pages.getOrNull(index) ?: return
    val left = snap.strip.pageLeft(index)
    val top = snap.strip.pageTop(index)
    val stroke = Straighten.line(
        java.util.UUID.randomUUID().toString(), line.tool, line.colorArgb, line.size,
        line.sx - left, line.sy - top, line.ex - left, line.ey - top, line.pressure,
        if (line.byFinger) InkStroke.TOOL_TOUCH else InkStroke.TOOL_STYLUS,
    )
    viewModel.addStrokes(page.id, listOf(stroke))
}

private fun centroid(points: List<Offset>): Offset =
    Offset(points.sumOf { it.x.toDouble() }.toFloat() / points.size, points.sumOf { it.y.toDouble() }.toFloat() / points.size)

private fun spread(points: List<Offset>, c: Offset): Float = points.map { (it - c).getDistance() }.average().toFloat()

private fun eraseStep(viewModel: NotebookViewModel, snap: Snapshot, cam: Camera, from: Offset, to: Offset, onEraser: (Offset?) -> Unit, drag: Long) {
    onEraser(to)
    val index = snap.strip.pageAt(cam.toWorldX(to.x))
    val page = snap.pages.getOrNull(index) ?: return
    val pl = snap.strip.pageLeft(index)
    val pt = snap.strip.pageTop(index)
    viewModel.eraseAlong(
        page.id,
        cam.toWorldX(from.x) - pl, cam.toWorldY(from.y) - pt,
        cam.toWorldX(to.x) - pl, cam.toWorldY(to.y) - pt,
        snap.settings.eraserRadius,
        drag = drag,
        partial = snap.settings.eraserPartial,
    )
}

private fun selectionScreenBox(sel: LassoState.Selected, snap: Snapshot, cam: Camera): FloatArray {
    val box = sel.box()
    val pl = snap.strip.pageLeft(sel.pageIndex)
    val pt = snap.strip.pageTop(sel.pageIndex)
    return floatArrayOf(cam.toScreenX(pl + box[0]), cam.toScreenY(pt + box[1]), cam.toScreenX(pl + box[2]), cam.toScreenY(pt + box[3]))
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

/**
 * Draws a picture on a page whose top-left corner is at ([pageLeft], [pageTop]) px, at [scale]
 * px per page unit, turned about its centre. Until its [bitmap] has loaded, a pale box stands in.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPicture(
    image: com.nzfrankwu.noteapp.domain.ink.InkImage,
    bitmap: androidx.compose.ui.graphics.ImageBitmap?,
    pageLeft: Float, pageTop: Float, scale: Float,
) {
    val x = pageLeft + image.left * scale
    val y = pageTop + image.top * scale
    val w = image.width * scale
    val h = image.height * scale
    rotate(Math.toDegrees(image.rotation.toDouble()).toFloat(), pivot = Offset(x + w / 2, y + h / 2)) {
        if (bitmap == null) {
            drawRect(Color(0x1F000000), Offset(x, y), Size(w, h))
        } else {
            drawImage(
                bitmap,
                dstOffset = IntOffset(x.roundToInt(), y.roundToInt()),
                dstSize = IntSize(w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1)),
                filterQuality = FilterQuality.Medium,
            )
        }
    }
}

/** A lasso loop smaller than this (screen px, width + height) is a tap: it picks the picture under the pen. */
private const val LASSO_TAP_PX = 24f

/** How long the view must rest before a sharp part of a deeply zoomed PDF page is drawn. */
private const val PDF_DETAIL_DELAY_MS = 180L

/** Draws a part of a PDF page on the page whose top-left corner is at ([pageLeft], [pageTop]) px, at [scale] px per page unit. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPdfLayer(
    layer: NotebookViewModel.PdfLayer, pageLeft: Float, pageTop: Float, scale: Float,
) {
    val x0 = pageLeft + layer.left * scale
    val y0 = pageTop + layer.top * scale
    val x1 = pageLeft + layer.right * scale
    val y1 = pageTop + layer.bottom * scale
    drawImage(
        layer.image,
        dstOffset = IntOffset(x0.roundToInt(), y0.roundToInt()),
        dstSize = IntSize((x1 - x0).roundToInt().coerceAtLeast(1), (y1 - y0).roundToInt().coerceAtLeast(1)),
        filterQuality = FilterQuality.Medium,
    )
}

