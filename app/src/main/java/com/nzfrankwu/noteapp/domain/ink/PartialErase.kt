package com.nzfrankwu.noteapp.domain.ink

import kotlin.math.hypot

/**
 * The partial eraser (Phase 7, 7.4): cuts away the part of a stroke the eraser passes over and
 * keeps the rest as new strokes ("pieces"). Plain Kotlin, unit-tested.
 *
 * The eraser moving from A to B covers a capsule: every point within [radius] of the segment A–B.
 * A stroke loses the parts of its centre line within `radius + size / 2` of A–B (the same reach
 * as the whole-stroke eraser, so a touch of the ink's edge already counts). Along one segment of
 * the stroke, the distance to A–B is convex, so the part that goes is one interval, found with a
 * ternary search for the closest point and binary searches for its two ends.
 *
 * Pieces are cut at interpolated points: position, time, pressure, tilt and orientation are
 * interpolated between the two neighbouring points ("not reported" stays not reported). Each
 * piece's times start at 0. A piece needs at least two points; anything shorter is dropped.
 */
object PartialErase {
    private const val SEARCH_STEPS = 32
    private const val EDGE = 1e-4f

    /**
     * The pieces of [stroke] left after the eraser moved from (ax, ay) to (bx, by), or null when
     * the eraser doesn't touch it. An empty list means the whole stroke goes.
     */
    fun cut(
        stroke: InkStroke,
        ax: Float, ay: Float, bx: Float, by: Float,
        radius: Float,
        newId: () -> String,
    ): List<InkStroke>? {
        if (!InkGeometry.eraserTouches(stroke, ax, ay, bx, by, radius)) return null
        val reach = radius + stroke.size / 2f
        val n = stroke.pointCount
        if (n <= 1) return emptyList() // a dot it touches goes completely

        fun dist(i: Int, t: Float): Float {
            val x = stroke.xs[i] + (stroke.xs[i + 1] - stroke.xs[i]) * t
            val y = stroke.ys[i] + (stroke.ys[i + 1] - stroke.ys[i]) * t
            return InkGeometry.pointSegment(x, y, ax, ay, bx, by)
        }

        val pieces = ArrayList<InkStroke>()
        val current = ArrayList<Pair<Int, Float>>() // (segment, t): the point on segment i at t
        fun close() {
            if (current.size >= 2) pieces += build(stroke, current, newId())
            current.clear()
        }

        if (InkGeometry.pointSegment(stroke.xs[0], stroke.ys[0], ax, ay, bx, by) > reach) current += 0 to 0f
        var touched = false
        for (i in 0 until n - 1) {
            val gone = interval(i, reach, ::dist)
            if (gone == null) {
                if (current.isEmpty()) current += i to 0f
                current += i to 1f
                continue
            }
            touched = true
            val (e0, e1) = gone
            if (e0 > EDGE) {
                if (current.isEmpty()) current += i to 0f
                current += i to e0
            }
            close()
            if (e1 < 1f - EDGE) {
                current += i to e1
                current += i to 1f
            }
        }
        close()
        return if (touched) pieces else null
    }

    /** The part [t0, t1] of segment [i] within [reach], or null if none of it is. */
    private fun interval(i: Int, reach: Float, dist: (Int, Float) -> Float): Pair<Float, Float>? {
        // Closest point on the segment (the distance along a segment is convex).
        var lo = 0f
        var hi = 1f
        repeat(SEARCH_STEPS) {
            val m1 = lo + (hi - lo) / 3f
            val m2 = hi - (hi - lo) / 3f
            if (dist(i, m1) <= dist(i, m2)) hi = m2 else lo = m1
        }
        val tMin = (lo + hi) / 2f
        val closest = minOf(dist(i, tMin), dist(i, 0f), dist(i, 1f))
        if (closest > reach) return null
        val best = when (closest) {
            dist(i, 0f) -> 0f
            dist(i, 1f) -> 1f
            else -> tMin
        }
        val start = if (dist(i, 0f) <= reach) 0f else edge(i, 0f, best, reach, dist, insideAtHigh = true)
        val end = if (dist(i, 1f) <= reach) 1f else edge(i, best, 1f, reach, dist, insideAtHigh = false)
        return start to end
    }

    /** Where the distance crosses [reach] between [from] and [to] (one end inside, the other outside). */
    private fun edge(i: Int, from: Float, to: Float, reach: Float, dist: (Int, Float) -> Float, insideAtHigh: Boolean): Float {
        var lo = from
        var hi = to
        repeat(SEARCH_STEPS) {
            val m = (lo + hi) / 2f
            val inside = dist(i, m) <= reach
            if (inside == insideAtHigh) hi = m else lo = m
        }
        return (lo + hi) / 2f
    }

    private fun build(stroke: InkStroke, points: List<Pair<Int, Float>>, id: String): InkStroke {
        val m = points.size
        val xs = FloatArray(m)
        val ys = FloatArray(m)
        val times = IntArray(m)
        val pressure = FloatArray(m)
        val tilt = FloatArray(m)
        val orientation = FloatArray(m)
        for ((k, p) in points.withIndex()) {
            val (i, t) = p
            val j = (i + 1).coerceAtMost(stroke.pointCount - 1)
            xs[k] = lerp(stroke.xs[i], stroke.xs[j], t)
            ys[k] = lerp(stroke.ys[i], stroke.ys[j], t)
            times[k] = Math.round(lerp(stroke.times[i].toFloat(), stroke.times[j].toFloat(), t))
            pressure[k] = lerpReported(stroke.pressure[i], stroke.pressure[j], t)
            tilt[k] = lerpReported(stroke.tilt[i], stroke.tilt[j], t)
            orientation[k] = lerpReported(stroke.orientation[i], stroke.orientation[j], t)
        }
        val first = times[0]
        for (k in 0 until m) times[k] = (times[k] - first).coerceAtLeast(if (k == 0) 0 else times[k - 1])
        return InkStroke(id, stroke.tool, stroke.colorArgb, stroke.size, xs, ys, times, pressure, tilt, orientation, stroke.inputTool)
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    private fun lerpReported(a: Float, b: Float, t: Float): Float = when {
        a == InkStroke.NONE && b == InkStroke.NONE -> InkStroke.NONE
        a == InkStroke.NONE -> b
        b == InkStroke.NONE -> a
        else -> lerp(a, b, t)
    }

    /** Length of a stroke's centre line (tests). */
    fun length(stroke: InkStroke): Float {
        var total = 0f
        for (i in 1 until stroke.pointCount) total += hypot(stroke.xs[i] - stroke.xs[i - 1], stroke.ys[i] - stroke.ys[i - 1])
        return total
    }
}
