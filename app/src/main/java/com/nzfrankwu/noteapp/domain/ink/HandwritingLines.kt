package com.nzfrankwu.noteapp.domain.ink

import kotlin.math.max
import kotlin.math.min

/**
 * Splits a page's handwriting into lines for handwriting search (PHASE-7.md 7.10), so each line
 * can be recognised on its own (recognisers read one line at a time best). Plain Kotlin, tested.
 *
 * - Highlighter strokes and dots are left out (they aren't writing).
 * - Very tall strokes (taller than [TALL] × the page's typical stroke) are drawings, not letters,
 *   and are left out too.
 * - A stroke joins the line its height overlaps by at least half (a flat stroke, like a "T"'s bar
 *   or a "-", counts as a typical letter's height around its middle); otherwise it starts a new
 *   line. Lines come out top to bottom; strokes keep the order they were written in.
 */
object HandwritingLines {
    /** One line of writing: its strokes (in writing order) and its band on the page. */
    data class Line(val strokes: List<InkStroke>, val top: Float, val bottom: Float, val left: Float, val right: Float)

    /** A stroke this many times taller than the typical one is a drawing. */
    const val TALL = 4f

    fun group(strokes: List<InkStroke>): List<Line> {
        val writing = strokes.filter { !it.tool.isHighlighter && it.pointCount >= 2 }
        if (writing.isEmpty()) return emptyList()
        val heights = writing.map { val b = it.bounds(); b[3] - b[1] }.sorted()
        val typical = heights[heights.size / 2].coerceAtLeast(1f)

        class Building(var top: Float, var bottom: Float, var left: Float, var right: Float, val strokes: MutableList<InkStroke>)
        val lines = ArrayList<Building>()
        for (s in writing) {
            val b = s.bounds()
            if (b[3] - b[1] > TALL * typical * 1.5f && b[3] - b[1] > 40f) continue
            val mid = (b[1] + b[3]) / 2f
            val top = min(b[1], mid - typical / 2f)
            val bottom = max(b[3], mid + typical / 2f)
            val line = lines.firstOrNull { l ->
                val lineTop = min(l.top, (l.top + l.bottom) / 2f - typical / 2f)
                val lineBottom = max(l.bottom, (l.top + l.bottom) / 2f + typical / 2f)
                val overlap = min(bottom, lineBottom) - max(top, lineTop)
                overlap >= 0.5f * min(bottom - top, lineBottom - lineTop)
            }
            if (line == null) {
                lines += Building(b[1], b[3], b[0], b[2], mutableListOf(s))
            } else {
                line.strokes += s
                line.top = min(line.top, b[1])
                line.bottom = max(line.bottom, b[3])
                line.left = min(line.left, b[0])
                line.right = max(line.right, b[2])
            }
        }
        // Two bands that grew into each other are one line.
        val merged = ArrayList<Building>()
        for (l in lines.sortedBy { it.top }) {
            val last = merged.lastOrNull()
            val overlap = if (last == null) 0f else min(last.bottom, l.bottom) - max(last.top, l.top)
            if (last != null && overlap > 0.5f * min(last.bottom - last.top, l.bottom - l.top)) {
                last.strokes += l.strokes
                last.top = min(last.top, l.top); last.bottom = max(last.bottom, l.bottom)
                last.left = min(last.left, l.left); last.right = max(last.right, l.right)
            } else {
                merged += l
            }
        }
        val order = writing.withIndex().associate { (i, s) -> s.id to i }
        return merged.map { b -> Line(b.strokes.sortedBy { order[it.id] }, b.top, b.bottom, b.left, b.right) }
    }
}
