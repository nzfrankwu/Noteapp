package com.nzfrankwu.noteapp.ui.notebook

import android.graphics.Matrix
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import com.nzfrankwu.noteapp.domain.ink.InkChunk
import com.nzfrankwu.noteapp.domain.ink.InkChunks
import com.nzfrankwu.noteapp.domain.ink.InkStroke
import com.nzfrankwu.noteapp.domain.ink.InkTool
import com.nzfrankwu.noteapp.domain.ink.LassoBox
import com.nzfrankwu.noteapp.domain.ink.PageStrip
import kotlin.math.ceil

/** A page's chunks, remembered between edits so untouched ones are reused. */
private class PageChunks {
    var highlights: List<InkChunk> = emptyList()
    var pens: List<InkChunk> = emptyList()

    fun update(strokes: List<InkStroke>, hidden: Set<String>?) {
        val shown = if (hidden == null) strokes else strokes.filter { it.id !in hidden }
        highlights = InkChunks.reconcile(highlights, shown.filter { it.tool == InkTool.HIGHLIGHTER })
        pens = InkChunks.reconcile(pens, shown.filter { it.tool != InkTool.HIGHLIGHTER })
    }
}

/**
 * One page's ink, drawn fast however full the page is (PHASE-2.md 2.12).
 *
 * How it stays fast:
 *  - The strokes are split into chunks ([InkChunks]), each drawn once into its own layer.
 *    Writing or erasing redraws only the chunk it touches.
 *  - The settled chunks sit in one page layer that the graphics card keeps as a ready-made
 *    picture, so scrolling and pinching just move that picture: nothing is redrawn. The picture
 *    is made at the zoom the view last settled at ([raster]) and remade when that changes.
 *  - The chunk being written into (the last one) and the lasso's selection sit in a layer on
 *    top, so writing a stroke doesn't remake the page's picture. The newest highlighter chunk
 *    likewise sits in its own layer, under the picture (highlighter is always under the pen).
 *  - Highlighter chunks are under pen chunks, like on paper.
 *  - A full page appears a few chunks per frame when it first shows, so it never freezes.
 */
@Composable
fun PageInkLayer(
    viewModel: NotebookViewModel,
    pageId: String,
    pageIndex: Int,
    pageWidth: Float,
    pageHeight: Float,
    strip: PageStrip,
    view: EditorViewState,
    renderer: CanvasStrokeRenderer,
) {
    // While this page is on screen, say whether it's drawn in full (else its preview shows: F8).
    DisposableEffect(view, pageId) { onDispose { view.fullyDrawn.remove(pageId) } }
    @Suppress("UNUSED_VARIABLE") val version = viewModel.version // re-split the chunks on every edit
    val raster = view.inkScale
    if (raster <= 0f || !viewModel.drawables.isReady(pageId)) return

    // The strokes held by the lasso (hidden from the chunks, drawn by the selection layer). The
    // list is the same object while the selection is dragged, so dragging doesn't recompose this.
    val selected by remember(view, pageId) {
        derivedStateOf(referentialEqualityPolicy()) {
            (view.lasso as? LassoState.Selected)?.takeIf { it.pageId == pageId }?.strokes
        }
    }
    val hidden = remember(selected) { selected?.mapTo(HashSet()) { it.id } }

    val chunks = remember(pageId) { PageChunks() }
    chunks.update(viewModel.ink.strokes(pageId), hidden)
    val settled = chunks.highlights.dropLast(1) + chunks.pens.dropLast(1)
    val tail = chunks.pens.lastOrNull()
    val highlightTail = chunks.highlights.lastOrNull()

    // A page showing for the first time reveals its chunks a few per frame.
    var revealed by remember(pageId) { mutableIntStateOf(REVEAL_PER_FRAME) }
    val settledCount by rememberUpdatedState(settled.size)
    LaunchedEffect(pageId) {
        while (revealed < settledCount) {
            withFrameNanos { }
            revealed += REVEAL_PER_FRAME
        }
        revealed = Int.MAX_VALUE // from now on, edits show at once
        withFrameNanos { } // let the last chunks draw before the preview goes
        view.fullyDrawn[pageId] = true
    }

    // The page in screen pixels at the settled zoom.
    val widthPx = ceil(pageWidth * raster).toInt()
    val heightPx = ceil(pageHeight * raster).toInt()
    // Keeping a picture of the page only works up to the graphics card's picture size limits;
    // zoomed in further, the chunks are drawn as they are (still without redrawing on scroll).
    val asPicture = widthPx <= MAX_PICTURE_SIDE && heightPx <= MAX_PICTURE_SIDE &&
        widthPx.toLong() * heightPx <= MAX_PICTURE_PIXELS
    val place: GraphicsLayerScope.() -> Unit = {
        val cam = view.camera
        if (cam != null) {
            transformOrigin = TransformOrigin(0f, 0f)
            scaleX = cam.scale / raster
            scaleY = cam.scale / raster
            translationX = cam.toScreenX(strip.pageLeft(pageIndex))
            translationY = cam.toScreenY(0f)
        }
    }

    // The newest highlighter chunk, under the page picture (so highlighting doesn't remake it).
    PageSizedLayer(widthPx, heightPx, Modifier.graphicsLayer { place() }) {
        if (highlightTail != null) key(highlightTail.key) { ChunkLayer(highlightTail.strokes, pageId, raster, viewModel.drawables, renderer) }
    }
    PageSizedLayer(
        widthPx, heightPx,
        Modifier.graphicsLayer {
            place()
            compositingStrategy = if (asPicture) CompositingStrategy.Offscreen else CompositingStrategy.Auto
        },
    ) {
        for ((index, chunk) in settled.withIndex()) {
            if (index >= revealed) break
            key(chunk.key) { ChunkLayer(chunk.strokes, pageId, raster, viewModel.drawables, renderer) }
        }
    }
    PageSizedLayer(widthPx, heightPx, Modifier.graphicsLayer { place() }) {
        if (tail != null) key(tail.key) { ChunkLayer(tail.strokes, pageId, raster, viewModel.drawables, renderer) }
        selected?.let { strokes ->
            key(System.identityHashCode(strokes)) {
                Spacer(
                    Modifier.fillMaxSize()
                        .graphicsLayer {
                            val sel = view.lasso as? LassoState.Selected ?: return@graphicsLayer
                            // Stretch around the box's top-left corner, then move (F12: free stretching).
                            transformOrigin = TransformOrigin(sel.bounds[0] * raster / size.width, sel.bounds[1] * raster / size.height)
                            scaleX = LassoBox.scaleX(sel.bounds, sel.target)
                            scaleY = LassoBox.scaleY(sel.bounds, sel.target)
                            translationX = (sel.target[0] - sel.bounds[0]) * raster
                            translationY = (sel.target[1] - sel.bounds[1]) * raster
                        }
                        .drawBehind { drawStrokes(strokes, pageId, raster, viewModel.drawables, renderer) },
                )
            }
        }
    }
}

/** A layer exactly [widthPx] × [heightPx], placed at the top-left however big its parent is. */
@Composable
private fun PageSizedLayer(widthPx: Int, heightPx: Int, modifier: Modifier, content: @Composable () -> Unit) {
    Layout(content, Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).then(modifier)) { measurables, _ ->
        val fixed = Constraints.fixed(widthPx, heightPx)
        val placeables = measurables.map { it.measure(fixed) }
        layout(widthPx, heightPx) { placeables.forEach { it.place(0, 0) } }
    }
}

/** One chunk: drawn once into its own layer, then reused until the chunk is replaced. */
@Composable
private fun ChunkLayer(
    strokes: List<InkStroke>,
    pageId: String,
    raster: Float,
    drawables: StrokeDrawables,
    renderer: CanvasStrokeRenderer,
) {
    Spacer(
        Modifier.fillMaxSize().graphicsLayer().drawBehind {
            clipRect { drawStrokes(strokes, pageId, raster, drawables, renderer) }
        },
    )
}

/** Draws strokes (saved in page units) at [raster] pixels per page unit. */
private fun DrawScope.drawStrokes(
    strokes: List<InkStroke>,
    pageId: String,
    raster: Float,
    drawables: StrokeDrawables,
    renderer: CanvasStrokeRenderer,
) {
    // The renderer's matrix doesn't move anything: it tells it how big a pixel is, for smooth edges.
    val hint = Matrix().apply { setScale(raster, raster) }
    scale(raster, raster, pivot = Offset.Zero) {
        drawIntoCanvas { canvas ->
            for (pass in 0..1) {
                for (stroke in strokes) {
                    if ((stroke.tool == InkTool.HIGHLIGHTER) != (pass == 0)) continue
                    drawables.get(pageId, stroke)?.let { renderer.draw(canvas.nativeCanvas, it, hint) }
                }
            }
        }
    }
}

/** How many chunks of a page appear per frame when it first shows. */
private const val REVEAL_PER_FRAME = 8

/** The biggest page picture the app asks the graphics card to keep (see [PageInkLayer]). */
private const val MAX_PICTURE_SIDE = 4096
private const val MAX_PICTURE_PIXELS = 12_000_000L
