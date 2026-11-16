package com.nzfrankwu.noteapp.domain.ink

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
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
     * one way; corners stretch freely, or keep the shape when [keepShape] (the selection's corners
     * always do, feedback F35: a picture only grows or shrinks; the crop box's corners don't).
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

    /**
     * [stroke] (inside [from]) placed into [to]: moved and stretched to match, then turned by
     * [angle] (radians, clockwise on screen) around [to]'s centre (F14).
     */
    fun map(stroke: InkStroke, from: FloatArray, to: FloatArray, angle: Float = 0f): InkStroke {
        val sx = scaleX(from, to)
        val sy = scaleY(from, to)
        val fcx = (from[0] + from[2]) / 2; val fcy = (from[1] + from[3]) / 2
        val tcx = (to[0] + to[2]) / 2; val tcy = (to[1] + to[3]) / 2
        val cos = cos(angle); val sin = sin(angle)
        val n = stroke.pointCount
        val xs = FloatArray(n)
        val ys = FloatArray(n)
        for (i in 0 until n) {
            val dx = (stroke.xs[i] - fcx) * sx
            val dy = (stroke.ys[i] - fcy) * sy
            xs[i] = tcx + dx * cos - dy * sin
            ys[i] = tcy + dx * sin + dy * cos
        }
        // The pen's direction turns with the writing (it's saved in radians, or NONE).
        val orientation = if (angle == 0f) stroke.orientation.copyOf() else FloatArray(n) {
            val o = stroke.orientation[it]
            if (o < 0f) o else ((o + angle) % TWO_PI + TWO_PI) % TWO_PI
        }
        return InkStroke(
            stroke.id, stroke.tool, stroke.colorArgb, stroke.size * sqrt(sx * sy), xs, ys,
            stroke.times.copyOf(), stroke.pressure.copyOf(), stroke.tilt.copyOf(), orientation, stroke.inputTool,
        )
    }

    /**
     * [image] (inside [from]) placed into [to] like [map] places strokes: its centre moves with
     * the box, its size stretches with it, and it turns by [angle] about [to]'s centre.
     */
    fun mapImage(image: InkImage, from: FloatArray, to: FloatArray, angle: Float = 0f): InkImage {
        val sx = scaleX(from, to)
        val sy = scaleY(from, to)
        val fcx = (from[0] + from[2]) / 2; val fcy = (from[1] + from[3]) / 2
        val tcx = (to[0] + to[2]) / 2; val tcy = (to[1] + to[3]) / 2
        val dx = (image.centreX - fcx) * sx
        val dy = (image.centreY - fcy) * sy
        val c = cos(angle); val s = sin(angle)
        val cx = tcx + dx * c - dy * s
        val cy = tcy + dx * s + dy * c
        val w = image.width * sx
        val h = image.height * sy
        return image.copy(left = cx - w / 2, top = cy - h / 2, width = w, height = h, rotation = image.rotation + angle)
    }

    /**
     * The angle to turn a selection by when the rotate handle is dragged from ([sx], [sy]) to
     * ([x], [y]) around ([cx], [cy]): radians, snapped to the nearest quarter turn when within
     * [SNAP_RADIANS] of it, so straightening writing back up is easy.
     */
    fun rotation(cx: Float, cy: Float, sx: Float, sy: Float, x: Float, y: Float): Float {
        val raw = atan2(y - cy, x - cx) - atan2(sy - cy, sx - cx)
        var a = ((raw % TWO_PI) + TWO_PI) % TWO_PI // 0..2π
        if (a > PI.toFloat()) a -= TWO_PI // -π..π
        val quarter = (PI / 2).toFloat()
        val nearest = kotlin.math.round(a / quarter) * quarter
        return if (abs(a - nearest) <= SNAP_RADIANS) nearest else a
    }

    /** ([x], [y]) turned by [angle] around ([cx], [cy]). */
    fun rotatePoint(x: Float, y: Float, cx: Float, cy: Float, angle: Float): Pair<Float, Float> {
        val c = cos(angle); val s = sin(angle)
        val dx = x - cx; val dy = y - cy
        return (cx + dx * c - dy * s) to (cy + dx * s + dy * c)
    }

    fun scaleX(from: FloatArray, to: FloatArray): Float = (to[2] - to[0]) / max(from[2] - from[0], 1e-3f)
    fun scaleY(from: FloatArray, to: FloatArray): Float = (to[3] - to[1]) / max(from[3] - from[1], 1e-3f)

    fun same(a: FloatArray, b: FloatArray): Boolean = a.indices.all { abs(a[it] - b[it]) < 0.01f }

    /** Within this of a quarter turn (about 4°), rotating snaps to it. */
    const val SNAP_RADIANS = 0.07f

    private const val TWO_PI = (2 * PI).toFloat()
    private const val MIN_SCALE = 0.1f
    private const val MAX_SCALE = 10f
}
