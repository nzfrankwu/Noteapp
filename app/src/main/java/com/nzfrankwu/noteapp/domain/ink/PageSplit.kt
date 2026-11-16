package com.nzfrankwu.noteapp.domain.ink

/**
 * A stroke written across the edge between pages is split there, one piece per page
 * (feedback F36). Each page keeps (and draws) only its own strokes, so without this the part on
 * the next page vanished when the pen lifted. The pieces meet exactly at the edge, so the line
 * still looks whole.
 */
object PageSplit {
    /**
     * Splits [stroke] (in DESK coordinates, all pages side by side) into runs that each stay on
     * one page. [pageAt] gives the page under a desk x; [edgeBetween] gives the desk x where you
     * cross from page `a` to page `b`. Returns (page index, piece in desk coordinates); the first
     * piece keeps [stroke]'s ID, the others get IDs from [newId]. A stroke on one page comes back
     * as it is.
     */
    fun split(
        stroke: InkStroke,
        pageAt: (Float) -> Int,
        edgeBetween: (from: Int, to: Int) -> Float,
        newId: () -> String,
    ): List<Pair<Int, InkStroke>> {
        val n = stroke.pointCount
        if (n == 0) return emptyList()
        val pageOf = IntArray(n) { pageAt(stroke.xs[it]) }
        if (pageOf.all { it == pageOf[0] }) return listOf(pageOf[0] to stroke)

        val out = ArrayList<Pair<Int, InkStroke>>()
        val run = Run()
        var page = pageOf[0]
        for (i in 0 until n) {
            if (i > 0 && pageOf[i] != page) {
                // Crossing: end this run at the edge, and start the next one there.
                val edge = edgeBetween(page, pageOf[i])
                val x0 = stroke.xs[i - 1]
                val x1 = stroke.xs[i]
                val t = if (x1 == x0) 0f else ((edge - x0) / (x1 - x0)).coerceIn(0f, 1f)
                run.addBetween(stroke, i - 1, i, t, edge)
                out += page to run.build(stroke, if (out.isEmpty()) stroke.id else newId())
                run.clear()
                run.addBetween(stroke, i - 1, i, t, edge)
                page = pageOf[i]
            }
            run.add(stroke, i)
        }
        out += page to run.build(stroke, if (out.isEmpty()) stroke.id else newId())
        return out
    }

    /** The points of one piece, collected as the stroke is walked. */
    private class Run {
        val xs = ArrayList<Float>()
        val ys = ArrayList<Float>()
        val times = ArrayList<Int>()
        val pressure = ArrayList<Float>()
        val tilt = ArrayList<Float>()
        val orientation = ArrayList<Float>()

        fun add(s: InkStroke, i: Int) {
            xs += s.xs[i]; ys += s.ys[i]; times += s.times[i]
            pressure += s.pressure[i]; tilt += s.tilt[i]; orientation += s.orientation[i]
        }

        /** A point a fraction [t] of the way from point [a] to point [b], at desk x [x]. */
        fun addBetween(s: InkStroke, a: Int, b: Int, t: Float, x: Float) {
            xs += x
            ys += s.ys[a] + (s.ys[b] - s.ys[a]) * t
            times += s.times[a] + ((s.times[b] - s.times[a]) * t).toInt()
            pressure += mix(s.pressure[a], s.pressure[b], t)
            tilt += mix(s.tilt[a], s.tilt[b], t)
            orientation += if (t < 0.5f) s.orientation[a] else s.orientation[b]
        }

        fun clear() {
            xs.clear(); ys.clear(); times.clear(); pressure.clear(); tilt.clear(); orientation.clear()
        }

        fun build(s: InkStroke, id: String) = InkStroke(
            id, s.tool, s.colorArgb, s.size,
            xs.toFloatArray(), ys.toFloatArray(), times.toIntArray(),
            pressure.toFloatArray(), tilt.toFloatArray(), orientation.toFloatArray(), s.inputTool,
        )

        private fun mix(a: Float, b: Float, t: Float): Float =
            if (a == InkStroke.NONE || b == InkStroke.NONE) (if (t < 0.5f) a else b) else a + (b - a) * t
    }
}
