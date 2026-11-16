package com.nzfrankwu.noteapp.ui.notebook

import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.MotionEvent
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.setFrom
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.ink.authoring.InProgressStrokeId
import androidx.ink.authoring.InProgressStrokesFinishedListener
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.brush.Brush
import androidx.ink.strokes.Stroke
import androidx.input.motionprediction.MotionEventPredictor

/**
 * Hands finished strokes from the pen layer to the page layers WITHOUT a flash (feedback F30).
 *
 * The pen layer draws a stroke while it's written. When the pen lifts, the stroke is saved and
 * drawn by its page layer, which happens a frame or two later. Jetpack Ink's ready-made Compose
 * pen layer stops drawing the stroke at once, so for those frames nobody drew it: a flash. Here
 * the pen layer keeps drawing it until the page layer has drawn it, then one frame more.
 */
class InkHandoff {
    private val handler = Handler(Looper.getMainLooper())

    /** Which saved pieces each pen-layer stroke still waits for (main thread only; tested in HandoffBookTest). */
    private val book = com.nzfrankwu.noteapp.domain.ink.HandoffBook<InProgressStrokeId>()

    val hasWaiting: Boolean get() = book.hasWaiting

    /** The pen layer to tell (set by [WetInkLayer]). */
    internal var view: InProgressStrokesView? = null

    /**
     * The pen layer's stroke [wetId] is now saved as [savedIds] (one, or a piece per page when it
     * crossed a page edge): stop drawing it once every piece has been drawn.
     */
    fun expect(savedIds: List<String>, wetId: InProgressStrokeId) {
        if (!book.expect(savedIds, wetId)) {
            runCatching { view?.removeFinishedStrokes(setOf(wetId)) }
            return
        }
        // In case one is never drawn (e.g. its page couldn't take it), stop anyway after a moment.
        handler.postDelayed({ releaseStroke(wetId) }, TIMEOUT_MS)
    }

    /** A page layer just drew the stroke (or piece) [savedId]. Called while drawing. */
    fun drawn(savedId: String) {
        val wetId = book.drawn(savedId) ?: return
        // One more frame, so the page layer's drawing is on screen before the pen layer lets go.
        Choreographer.getInstance().postFrameCallback { releaseStroke(wetId) }
    }

    private fun releaseStroke(wetId: InProgressStrokeId) {
        if (!book.release(wetId)) return
        runCatching { view?.removeFinishedStrokes(setOf(wetId)) }
    }

    private companion object {
        /** A page that wasn't ready to draw may take a moment to get ready (it's asked to). */
        const val TIMEOUT_MS = 2_000L
    }
}

/**
 * The pen layer (low-latency wet ink), on Jetpack Ink's [InProgressStrokesView].
 *
 * The pointer handling follows Jetpack Ink's own Compose `InProgressStrokes` (androidx.ink
 * 1.0.0, Apache License 2.0, Copyright The Android Open Source Project), with one change: finished
 * strokes stay drawn until [handoff] says the page has drawn them, instead of being dropped at once.
 *
 * As there: a pointer that goes down unconsumed starts a stroke; a pointer that the gesture handler
 * consumes later cancels its stroke (palm rejection, eraser, lasso).
 */
@Composable
fun WetInkLayer(
    nextBrush: () -> Brush?,
    pointerEventToWorldTransform: Matrix,
    handoff: InkHandoff,
    onStrokesFinished: (Map<InProgressStrokeId, Stroke>) -> Unit,
) {
    var strokesView by remember { mutableStateOf<InProgressStrokesView?>(null) }
    var predictor by remember { mutableStateOf<MotionEventPredictor?>(null) }
    val containingView = LocalView.current
    val pointerIds = remember { HashMap<PointerId, Int>() }
    val inProgress = remember { HashMap<PointerId, Pair<InProgressStrokeId, Int>>() }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(pointerEventToWorldTransform) {
                awaitEachGesture {
                    do {
                        val view = strokesView ?: return@awaitEachGesture
                        val event = awaitPointerEvent()
                        // Ignore hover; still let up events through.
                        if (pointerIds.isEmpty() && event.changes.none { it.pressed }) return@awaitEachGesture

                        if (event.type == PointerEventType.Press) {
                            val raw = event.motionEvent ?: return@awaitEachGesture
                            for (change in event.changes) {
                                if (change.changedToDownIgnoreConsumed()) {
                                    pointerIds[change.id] = raw.getPointerId(raw.actionIndex)
                                    break // only one pointer goes down per event
                                }
                            }
                        }
                        val local = event.obtainLocalMotionEvent(pointerIds)
                        pointerIds.keys.retainAll(event.changes.filter { it.pressed }.map { it.id }.toSet())
                        if (local != null) predictor?.record(local)
                        val predicted = if (event.type == PointerEventType.Move) predictor?.predict() else null
                        try {
                            for (change in event.changes) {
                                val current = inProgress[change.id]
                                when {
                                    current != null && change.isConsumed -> {
                                        view.cancelStroke(current.first, local)
                                        inProgress.remove(change.id)
                                    }
                                    current == null && change.changedToDown() && local != null -> {
                                        val pointerId = pointerIds[change.id] ?: continue
                                        val brush = nextBrush() ?: continue
                                        val id = view.startStroke(
                                            local, pointerId, brush,
                                            android.graphics.Matrix().apply { setFrom(pointerEventToWorldTransform) },
                                        )
                                        inProgress[change.id] = id to pointerId
                                        change.consume()
                                    }
                                    current != null && change.changedToUp() && local != null -> {
                                        inProgress.remove(change.id)
                                        view.finishStroke(local, current.second, current.first)
                                        change.consume()
                                    }
                                    current != null && change.positionChanged() && local != null -> {
                                        view.addToStroke(local, current.second, current.first, predicted)
                                        change.consume()
                                    }
                                }
                            }
                        } finally {
                            local?.recycle()
                            predicted?.recycle()
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            // Ask Android to deliver pen events as they come (not once per frame), for low latency.
            .pointerInteropFilter {
                containingView.requestUnbufferedDispatch(it)
                false
            },
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                InProgressStrokesView(context).also { v ->
                    v.eagerInit()
                    strokesView = v
                    handoff.view = v
                    predictor = MotionEventPredictor.newInstance(v)
                }
            },
            update = { v ->
                v.clearFinishedStrokesListeners()
                v.addFinishedStrokesListener(
                    object : InProgressStrokesFinishedListener {
                        override fun onStrokesFinished(strokes: Map<InProgressStrokeId, Stroke>) {
                            // NOT removed here: InkHandoff removes each once its page has drawn it.
                            onStrokesFinished(strokes)
                        }
                    },
                )
            },
        )
    }
}

/**
 * The [PointerEvent] as a [MotionEvent] in this layer's own coordinates (the raw one is in the
 * window's). Follows Jetpack Ink's `obtainLocalMotionEvent` (Apache 2.0). Recycle the result.
 */
private fun PointerEvent.obtainLocalMotionEvent(pointerIds: Map<PointerId, Int>): MotionEvent? {
    val raw = motionEvent ?: return null
    val count = raw.pointerCount
    val properties = Array(count) { MotionEvent.PointerProperties() }
    val coords = Array(count) { MotionEvent.PointerCoords() }
    for (i in 0 until count) {
        raw.getPointerProperties(i, properties[i])
        raw.getPointerCoords(i, coords[i])
        val id = raw.getPointerId(i)
        val change = changes.firstOrNull { pointerIds[it.id] == id } ?: continue
        coords[i].x = change.position.x
        coords[i].y = change.position.y
    }
    val event = MotionEvent.obtain(
        raw.downTime, raw.eventTime, raw.action, count, properties, coords, raw.metaState, raw.buttonState,
        raw.xPrecision, raw.yPrecision, raw.deviceId, raw.edgeFlags, raw.source, raw.flags,
    )
    val first = changes[0]
    if (changes.any { it.historical.size != first.historical.size }) return event // can't line up history: skip it
    for (h in first.historical.indices) {
        for (i in 0 until count) {
            raw.getHistoricalPointerCoords(i, h, coords[i])
            val id = raw.getPointerId(i)
            val change = changes.firstOrNull { pointerIds[it.id] == id } ?: continue
            val past = change.historical[h]
            coords[i].x = past.position.x
            coords[i].y = past.position.y
        }
        event.addBatch(first.historical[h].uptimeMillis, coords, raw.metaState)
    }
    return event
}
