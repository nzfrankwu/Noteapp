package com.nzfrankwu.noteapp.domain.ink

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Hit-testing for the eraser and the lasso, on the saved stroke points (page units). Plain
 * Kotlin, so it's unit-tested without the drawing library.
 */
object InkGeometry {
    /**
     * True when an eraser moving from (ax, ay) to (bx, by), [radius] units wide on each side,
     * touches [stroke] (counting the stroke's own width).
     */
    fun eraserTouches(stroke: InkStroke, ax: Float, ay: Float, bx: Float, by: Float, radius: Float): Boolean {
        val reach = radius + stroke.size / 2f
        val box = stroke.bounds()
        if (max(ax, bx) < box[0] - reach || min(ax, bx) > box[2] + reach ||
            max(ay, by) < box[1] - reach || min(ay, by) > box[3] + reach
        ) return false
        val n = stroke.pointCount
        if (n == 1) return segmentDistance(ax, ay, bx, by, stroke.xs[0], stroke.ys[0], stroke.xs[0], stroke.ys[0]) <= reach
        for (i in 0 until n - 1) {
            val d = segmentDistance(ax, ay, bx, by, stroke.xs[i], stroke.ys[i], stroke.xs[i + 1], stroke.ys[i + 1])
            if (d <= reach) return true
        }
        return false
    }

    /**
     * True when [stroke] counts as inside a lasso loop: more than half of its points are inside
     * the polygon ([loopXs], [loopYs]; the loop closes itself). This picks what you circled even if
     * a stroke pokes out a little.
     */
    fun insideLasso(stroke: InkStroke, loopXs: FloatArray, loopYs: FloatArray): Boolean {
        if (loopXs.size < 3 || stroke.pointCount == 0) return false
        // Any part inside picks the whole stroke (F19): you needn't circle all of it.
        for (i in 0 until stroke.pointCount) {
            if (pointInPolygon(stroke.xs[i], stroke.ys[i], loopXs, loopYs)) return true
        }
        // A stroke crossing the loop with no point inside (e.g. a long straight line, 2 points).
        for (i in 1 until stroke.pointCount) {
            if (crossesLoop(stroke.xs[i - 1], stroke.ys[i - 1], stroke.xs[i], stroke.ys[i], loopXs, loopYs)) return true
        }
        return false
    }

    /** Does the segment (ax, ay)–(bx, by) cross any side of the closed loop? */
    private fun crossesLoop(ax: Float, ay: Float, bx: Float, by: Float, xs: FloatArray, ys: FloatArray): Boolean {
        var j = xs.size - 1
        for (i in xs.indices) {
            if (segmentsCross(ax, ay, bx, by, xs[j], ys[j], xs[i], ys[i])) return true
            j = i
        }
        return false
    }

    /** Even-odd rule: is (x, y) inside the polygon? */
    fun pointInPolygon(x: Float, y: Float, xs: FloatArray, ys: FloatArray): Boolean {
        var inside = false
        var j = xs.size - 1
        for (i in xs.indices) {
            if ((ys[i] > y) != (ys[j] > y) && x < (xs[j] - xs[i]) * (y - ys[i]) / (ys[j] - ys[i]) + xs[i]) inside = !inside
            j = i
        }
        return inside
    }

    /** The box around several strokes including their width, as [left, top, right, bottom], or null if none. */
    fun boundsOf(strokes: Collection<InkStroke>): FloatArray? {
        if (strokes.isEmpty()) return null
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        for (s in strokes) {
            val box = s.bounds()
            val half = s.size / 2f
            l = min(l, box[0] - half); t = min(t, box[1] - half)
            r = max(r, box[2] + half); b = max(b, box[3] + half)
        }
        return floatArrayOf(l, t, r, b)
    }

    /** Shortest distance between segments P1–P2 and Q1–Q2. */
    fun segmentDistance(
        p1x: Float, p1y: Float, p2x: Float, p2y: Float,
        q1x: Float, q1y: Float, q2x: Float, q2y: Float,
    ): Float {
        if (segmentsCross(p1x, p1y, p2x, p2y, q1x, q1y, q2x, q2y)) return 0f
        return min(
            min(pointSegment(p1x, p1y, q1x, q1y, q2x, q2y), pointSegment(p2x, p2y, q1x, q1y, q2x, q2y)),
            min(pointSegment(q1x, q1y, p1x, p1y, p2x, p2y), pointSegment(q2x, q2y, p1x, p1y, p2x, p2y)),
        )
    }

    /** Distance from point P to segment A–B. */
    fun pointSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        if (len2 == 0f) return hypot(px - ax, py - ay)
        val t = (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0f, 1f)
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }

    private fun segmentsCross(
        p1x: Float, p1y: Float, p2x: Float, p2y: Float,
        q1x: Float, q1y: Float, q2x: Float, q2y: Float,
    ): Boolean {
        val d1 = cross(q2x - q1x, q2y - q1y, p1x - q1x, p1y - q1y)
        val d2 = cross(q2x - q1x, q2y - q1y, p2x - q1x, p2y - q1y)
        val d3 = cross(p2x - p1x, p2y - p1y, q1x - p1x, q1y - p1y)
        val d4 = cross(p2x - p1x, p2y - p1y, q2x - p1x, q2y - p1y)
        return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))
    }

    private fun cross(ax: Float, ay: Float, bx: Float, by: Float) = ax * by - ay * bx
}
