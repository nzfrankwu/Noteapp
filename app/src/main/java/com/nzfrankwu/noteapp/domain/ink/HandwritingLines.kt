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
 * - Strokes are grouped by their MIDDLE height: going down the page, a stroke joins the current
 *   line while its middle is within [JOIN] × a typical stroke's height of the line's average
 *   middle; otherwise it starts the next line. (Grouping by the strokes' full height merged a
 *   whole page into one "line": ascenders and descenders make neighbouring lines overlap.)
 * - A "line" of one or two strokes just above another (an i's dot, a lone accent) joins it.
 * - Lines come out top to bottom; strokes keep the order they were written in.
 */
object HandwritingLines {
    /** One line of writing: its strokes (in writing order) and its band on the page. */
    data class Line(val strokes: List<InkStroke>, val top: Float, val bottom: Float, val left: Float, val right: Float)

    /** A stroke this many times taller than the typical one is a drawing. */
    const val TALL = 4f

    /** How far (in typical stroke heights) a stroke's middle may be from its line's middle. */
    const val JOIN = 0.8f

    private class Building(val strokes: MutableList<InkStroke>) {
        var sumMid = 0f
        val mid: Float get() = sumMid / strokes.size
        fun add(s: InkStroke, m: Float) { strokes += s; sumMid += m }
    }

    fun group(strokes: List<InkStroke>): List<Line> {
        val writing = strokes.filter { !it.tool.isHighlighter && it.pointCount >= 2 }
        if (writing.isEmpty()) return emptyList()
        val heights = writing.map { val b = it.bounds(); b[3] - b[1] }.sorted()
        val typical = heights[heights.size / 2].coerceAtLeast(1f)
        val letters = writing.filter { val b = it.bounds(); !(b[3] - b[1] > TALL * typical * 1.5f && b[3] - b[1] > 40f) }

        // Down the page by each stroke's middle.
        fun midOf(s: InkStroke): Float { val b = s.bounds(); return (b[1] + b[3]) / 2f }
        val lines = ArrayList<Building>()
        for (s in letters.sortedBy(::midOf)) {
            val m = midOf(s)
            val last = lines.lastOrNull()
            if (last != null && m - last.mid <= JOIN * typical) last.add(s, m) else lines += Building(mutableListOf()).also { it.add(s, m) }
        }

        // A tiny "line" (an i's dot, an accent) just above the next one belongs to it.
        val merged = ArrayList<Building>()
        var i = 0
        while (i < lines.size) {
            val l = lines[i]
            val next = lines.getOrNull(i + 1)
            if (l.strokes.size <= 2 && next != null && next.mid - l.mid <= 1.5f * typical) {
                val combined = Building(mutableListOf())
                (l.strokes + next.strokes).forEach { combined.add(it, midOf(it)) }
                lines[i + 1] = combined
            } else {
                merged += l
            }
            i++
        }

        val order = writing.withIndex().associate { (n, s) -> s.id to n }
        return merged.map { b ->
            var top = Float.MAX_VALUE; var bottom = -Float.MAX_VALUE; var left = Float.MAX_VALUE; var right = -Float.MAX_VALUE
            for (s in b.strokes) {
                val r = s.bounds()
                left = min(left, r[0]); top = min(top, r[1]); right = max(right, r[2]); bottom = max(bottom, r[3])
            }
            Line(b.strokes.sortedBy { order[it.id] }, top, bottom, left, right)
        }
    }
}
