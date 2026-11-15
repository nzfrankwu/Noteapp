package com.nzfrankwu.noteapp.domain.ink

import kotlin.math.max
import kotlin.math.sqrt

/** The lasso selection box's drag handles (PHASE-2.md F12). */
enum class BoxHandle(val left: Boolean, val top: Boolean, val right: Boolean, val bottom: Boolean) {
    TOP_LEFT(true, true, false, false),
    TOP(false, true, false, false),
    TOP_RIGHT(false, true, true, false),
    RIGHT(false, false, true, false),
    BOTTOM_RIGHT(false, false, true, true),
    BOTTOM(false, false, false, true),
    BOTTOM_LEFT(true, false, false, true),
    LEFT(true, false, false, false);

    /** Where the handle sits on a box [left, top, right, bottom]. */
    fun at(box: FloatArray): Pair<Float, Float> {
        val x = when { left -> box[0]; right -> box[2]; else -> (box[0] + box[2]) / 2 }
        val y = when { top -> box[1]; bottom -> box[3]; else -> (box[1] + box[3]) / 2 }
        return x to y
    }
}

/**
 * Moving and resizing the lasso's selection (PHASE-2.md 2.9, F12). A selection keeps the box its
 * strokes were picked in ([from]) and the box they're being moved or stretched to ([to]); each
 * box is [left, top, right, bottom] in page units. Plain Kotlin, unit-tested.
 */
object LassoBox {
    /** The smallest a selection can be squeezed to, in page units. */
    const val MIN_SIDE = 4f

    /** [box] moved by ([dx], [dy]). */
    fun moved(box: FloatArray, dx: Float, dy: Float): FloatArray =
        floatArrayOf(box[0] + dx, box[1] + dy, box[2] + dx, box[3] + dy)

    /**
     * [box] with [handle] dragged to ([x], [y]). Its opposite side stays put. Edge handles stretch
     * one way; corners stretch freely, or keep the shape when [keepShape] (the plain corner handle).
     */
    fun dragged(box: FloatArray, handle: BoxHandle, x: Float, y: Float, keepShape: Boolean = false): FloatArray {
        var l = box[0]; var t = box[1]; var r = box[2]; var b = box[3]
        if (handle.left) l = minOf(x, r - MIN_SIDE)
        if (handle.right) r = max(x, l + MIN_SIDE)
        if (handle.top) t = minOf(y, b - MIN_SIDE)
        if (handle.bottom) b = max(y, t + MIN_SIDE)
        val corner = (handle.left || handle.right) && (handle.top || handle.bottom)
        if (keepShape && corner) {
            // Grow by whichever way moved more, keeping the box's shape.
            val w0 = box[2] - box[0]; val h0 = box[3] - box[1]
            val k = max((r - l) / w0, (b - t) / h0).coerceIn(MIN_SCALE, MAX_SCALE)
            if (handle.left) l = box[2] - w0 * k else r = box[0] + w0 * k
            if (handle.top) t = box[3] - h0 * k else b = box[1] + h0 * k
        }
        return floatArrayOf(l, t, r, b)
    }

    /** [stroke] (inside [from]) placed into [to]: moved and stretched to match. */
    fun map(stroke: InkStroke, from: FloatArray, to: FloatArray): InkStroke {
        val sx = scaleX(from, to)
        val sy = scaleY(from, to)
        val n = stroke.pointCount
        val xs = FloatArray(n) { to[0] + (stroke.xs[it] - from[0]) * sx }
        val ys = FloatArray(n) { to[1] + (stroke.ys[it] - from[1]) * sy }
        return InkStroke(
            stroke.id, stroke.tool, stroke.colorArgb, stroke.size * sqrt(sx * sy), xs, ys,
            stroke.times.copyOf(), stroke.pressure.copyOf(), stroke.tilt.copyOf(), stroke.orientation.copyOf(), stroke.inputTool,
        )
    }

    fun scaleX(from: FloatArray, to: FloatArray): Float = (to[2] - to[0]) / max(from[2] - from[0], 1e-3f)
    fun scaleY(from: FloatArray, to: FloatArray): Float = (to[3] - to[1]) / max(from[3] - from[1], 1e-3f)

    fun same(a: FloatArray, b: FloatArray): Boolean = a.indices.all { kotlin.math.abs(a[it] - b[it]) < 0.01f }

    private const val MIN_SCALE = 0.1f
    private const val MAX_SCALE = 10f
}
