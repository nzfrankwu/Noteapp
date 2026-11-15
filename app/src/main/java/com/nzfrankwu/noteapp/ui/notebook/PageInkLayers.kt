package com.nzfrankwu.noteapp.ui.notebook

import android.graphics.Matrix
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import com.nzfrankwu.noteapp.domain.ink.InkChunk
import com.nzfrankwu.noteapp.domain.ink.InkChunks
import com.nzfrankwu.noteapp.domain.ink.InkStroke
import com.nzfrankwu.noteapp.domain.ink.InkTool
import com.nzfrankwu.noteapp.domain.ink.PageStrip

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
 * The page is a graphics layer positioned by the camera: scrolling and zooming only move the
 * layer, nothing is redrawn. Inside it the strokes are split into chunks ([InkChunks]), each its
 * own layer that's drawn once: writing or erasing redraws only the chunk it touches.
 * Highlighter chunks sit under pen chunks, like on paper. The lasso's selection is its own layer
 * on top, moved and resized by the layer (again without redrawing).
 *
 * A full page appears a few chunks per frame when it first shows, so it never freezes the screen.
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
    @Suppress("UNUSED_VARIABLE") val version = viewModel.version // re-split the chunks on every edit
    val ready = viewModel.drawables.isReady(pageId)

    // The strokes held by the lasso (hidden from the chunks, drawn by the selection layer). The
    // list is the same object while the selection is dragged, so dragging doesn't recompose this.
    val selected by remember(view, pageId) {
        derivedStateOf(referentialEqualityPolicy()) {
            (view.lasso as? LassoState.Selected)?.takeIf { it.pageId == pageId }?.strokes
        }
    }
    val hidden = remember(selected) { selected?.mapTo(HashSet()) { it.id } }

    val chunks = remember(pageId) { PageChunks() }
    if (ready) chunks.update(viewModel.ink.strokes(pageId), hidden)
    val total = chunks.highlights.size + chunks.pens.size

    // A page showing for the first time reveals its chunks a few per frame.
    var revealed by remember(pageId) { mutableIntStateOf(REVEAL_PER_FRAME) }
    val totalNow by rememberUpdatedState(total)
    LaunchedEffect(pageId, ready) {
        if (!ready) return@LaunchedEffect
        while (revealed < totalNow) {
            withFrameNanos { }
            revealed += REVEAL_PER_FRAME
        }
        revealed = Int.MAX_VALUE // from now on, edits show at once
    }

    Box(
        Modifier.fillMaxSize().graphicsLayer {
            val cam = view.camera ?: return@graphicsLayer
            transformOrigin = TransformOrigin(0f, 0f)
            scaleX = cam.scale
            scaleY = cam.scale
            translationX = cam.toScreenX(strip.pageLeft(pageIndex))
            translationY = cam.toScreenY(0f)
        },
    ) {
        if (!ready) return@Box
        var shown = 0
        for (chunk in chunks.highlights) {
            if (shown++ >= revealed) break
            key(chunk.key) { ChunkLayer(chunk.strokes, pageId, pageWidth, pageHeight, viewModel.drawables, renderer, view) }
        }
        for (chunk in chunks.pens) {
            if (shown++ >= revealed) break
            key(chunk.key) { ChunkLayer(chunk.strokes, pageId, pageWidth, pageHeight, viewModel.drawables, renderer, view) }
        }
        selected?.let { strokes ->
            key(System.identityHashCode(strokes)) {
                Spacer(
                    Modifier.fillMaxSize()
                        .graphicsLayer {
                            val sel = view.lasso as? LassoState.Selected ?: return@graphicsLayer
                            // Resize around the selection box's top-left corner, then move.
                            transformOrigin = TransformOrigin(sel.bounds[0] / size.width, sel.bounds[1] / size.height)
                            scaleX = sel.scale
                            scaleY = sel.scale
                            translationX = sel.dx
                            translationY = sel.dy
                        }
                        .drawBehind { drawStrokes(strokes, pageId, viewModel.drawables, renderer, view.inkScale) },
                )
            }
        }
    }
}

/** One chunk: drawn once into its own layer, then reused until the chunk is replaced. */
@Composable
private fun ChunkLayer(
    strokes: List<InkStroke>,
    pageId: String,
    pageWidth: Float,
    pageHeight: Float,
    drawables: StrokeDrawables,
    renderer: CanvasStrokeRenderer,
    view: EditorViewState,
) {
    Spacer(
        Modifier.fillMaxSize().graphicsLayer().drawBehind {
            // Reading inkScale here redraws the chunk when the zoom has settled at a new level.
            val scale = view.inkScale
            clipRect(0f, 0f, pageWidth, pageHeight) { drawStrokes(strokes, pageId, drawables, renderer, scale) }
        },
    )
}

/** Draws strokes in page units (the layers above scale them to the screen). */
private fun DrawScope.drawStrokes(
    strokes: List<InkStroke>,
    pageId: String,
    drawables: StrokeDrawables,
    renderer: CanvasStrokeRenderer,
    scale: Float,
) {
    // The renderer's matrix doesn't move anything: it tells it how big a pixel is, for smooth edges.
    val hint = Matrix().apply { setScale(scale, scale) }
    drawIntoCanvas { canvas ->
        for (pass in 0..1) {
            for (stroke in strokes) {
                if ((stroke.tool == InkTool.HIGHLIGHTER) != (pass == 0)) continue
                drawables.get(pageId, stroke)?.let { renderer.draw(canvas.nativeCanvas, it, hint) }
            }
        }
    }
}

/** How many chunks of a page appear per frame when it first shows. */
private const val REVEAL_PER_FRAME = 2
