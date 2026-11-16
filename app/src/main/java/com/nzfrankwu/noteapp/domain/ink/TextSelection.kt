package com.nzfrankwu.noteapp.domain.ink

import com.nzfrankwu.noteapp.domain.PageSpot
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * One word of a PDF page's text, with its box in page units (1/72 inch from the top-left of the
 * page as shown). [baseline] is where the letters sit; [line] numbers the page's lines in reading
 * order (0 = the first).
 */
data class PdfWord(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val baseline: Float,
    val line: Int,
) {
    val centreX: Float get() = (left + right) / 2
    val centreY: Float get() = (top + bottom) / 2
}

/** A page's words in reading order (line by line). Scanned pages have none. */
class PageText(val words: List<PdfWord>) {
    val isEmpty: Boolean get() = words.isEmpty()

    companion object {
        val EMPTY = PageText(emptyList())
    }
}

/** Selected words [first]..[last] (indexes into [PageText.words], both included, first ≤ last). */
data class TextRange(val first: Int, val last: Int) {
    init {
        require(first <= last) { "first must not be after last" }
    }

    companion object {
        /** The range between two word indexes, in whichever order they come. */
        fun between(a: Int, b: Int): TextRange = TextRange(min(a, b), max(a, b))
    }
}

/** A straight mark across one selected line: from ([x1], [y]) to ([x2], [y]), [thickness] thick. */
data class LineMark(val x1: Float, val x2: Float, val y: Float, val thickness: Float)

/**
 * Selecting PDF text with a finger (PHASE-4.md 4.6–4.7), whole words at a time. Plain Kotlin,
 * unit-tested: finding the word under a finger, moving a handle, and turning a selection into
 * rectangles, text to copy, highlighter marks and underlines.
 */
object TextSelection {
    /** How far outside a word's box (page units) a held finger still picks it. */
    const val HOLD_SLOP = 4f

    /** How much of a line's height a highlight's ends are pulled in (the flat tip overhangs). */
    const val HIGHLIGHT_END_INSET = 0.2f

    /** How far below the baseline an underline sits (page units). */
    const val UNDERLINE_GAP = 1.5f

    /** The word under ([x], [y]), or within [slop] of it (the nearest if several); null if none. */
    fun wordAt(text: PageText, x: Float, y: Float, slop: Float = HOLD_SLOP): Int? {
        var best: Int? = null
        var bestDistance = Float.MAX_VALUE
        text.words.forEachIndexed { i, w ->
            val dx = max(0f, max(w.left - x, x - w.right))
            val dy = max(0f, max(w.top - y, y - w.bottom))
            if (dx <= slop && dy <= slop) {
                val d = dx + dy + abs(x - w.centreX) * 0.001f // inside both: the closer centre wins
                if (d < bestDistance) {
                    bestDistance = d
                    best = i
                }
            }
        }
        return best
    }

    /**
     * The word a dragged handle at ([x], [y]) points at: the line nearest the point (by its
     * height), then the word on that line nearest across. Null only for a page with no text.
     */
    fun closestWord(text: PageText, x: Float, y: Float): Int? {
        if (text.isEmpty) return null
        val lines = text.words.indices.groupBy { text.words[it].line }
        val line = lines.values.minBy { idx ->
            val top = idx.minOf { text.words[it].top }
            val bottom = idx.maxOf { text.words[it].bottom }
            when {
                y < top -> top - y
                y > bottom -> y - bottom
                else -> 0f
            }
        }
        return line.minBy { i ->
            val w = text.words[i]
            max(0f, max(w.left - x, x - w.right)) + abs(x - w.centreX) * 0.001f
        }
    }

    /** The selected words, line by line (each line's indexes in order). */
    private fun linesOf(text: PageText, range: TextRange): List<List<PdfWord>> {
        val chosen = text.words.subList(range.first.coerceAtLeast(0), (range.last + 1).coerceAtMost(text.words.size))
        return chosen.groupBy { it.line }.toSortedMap().values.toList()
    }

    /** One rectangle per selected line, [left, top, right, bottom] in page units. */
    fun lineRects(text: PageText, range: TextRange): List<FloatArray> =
        linesOf(text, range).map { words ->
            floatArrayOf(words.minOf { it.left }, words.minOf { it.top }, words.maxOf { it.right }, words.maxOf { it.bottom })
        }

    /** The selected text: words joined by spaces, lines by line breaks. */
    fun copyText(text: PageText, range: TextRange): String =
        linesOf(text, range).joinToString("\n") { words -> words.joinToString(" ") { it.text } }

    /**
     * Highlighter marks: one per selected line, across its words, as tall as the line. The ends
     * are pulled in by [HIGHLIGHT_END_INSET] of the height, since the chisel tip reaches past them.
     */
    fun highlightMarks(text: PageText, range: TextRange): List<LineMark> =
        lineRects(text, range).map { r ->
            val h = r[3] - r[1]
            val inset = (h * HIGHLIGHT_END_INSET).coerceAtMost((r[2] - r[0]) / 4)
            LineMark(r[0] + inset, r[2] - inset, (r[1] + r[3]) / 2, h)
        }

    /** Underlines: one per selected line, just under its baseline, [thickness] thick. */
    fun underlineMarks(text: PageText, range: TextRange, thickness: Float): List<LineMark> =
        linesOf(text, range).map { words ->
            LineMark(words.minOf { it.left }, words.maxOf { it.right }, words.maxOf { it.baseline } + UNDERLINE_GAP + thickness / 2, thickness)
        }

    /**
     * Straight strokes for [marks] (one per line), in [tool], [colorArgb] and either each mark's
     * thickness (highlighter) or [size] (pen). [newId] names each stroke.
     */
    fun strokes(marks: List<LineMark>, tool: InkTool, colorArgb: Int, size: Float?, newId: () -> String): List<InkStroke> =
        marks.filter { it.x2 > it.x1 }.map { m ->
            Straighten.line(newId(), tool, colorArgb, size ?: m.thickness, m.x1, m.y, m.x2, m.y, 1f, InkStroke.TOOL_TOUCH)
        }

    /** The box around the whole selection (for a bookmark's spot). */
    fun spotOf(text: PageText, range: TextRange): PageSpot {
        val rects = lineRects(text, range)
        val l = rects.minOf { it[0] }
        val t = rects.minOf { it[1] }
        return PageSpot(l, t, rects.maxOf { it[2] } - l, rects.maxOf { it[3] } - t)
    }

    /**
     * Where the two handles sit: the start handle at the bottom-left of the first word, the end
     * handle at the bottom-right of the last. Returns (startX, startY, endX, endY).
     */
    fun handles(text: PageText, range: TextRange): FloatArray {
        val a = text.words[range.first]
        val b = text.words[range.last]
        return floatArrayOf(a.left, a.bottom, b.right, b.bottom)
    }

    /**
     * Groups words read off a page into lines (PdfText gives them in reading order, with a new
     * line flagged by [startsLine]) and numbers them. Words with no text are dropped.
     */
    fun build(words: List<Pair<PdfWord, Boolean>>): PageText {
        var line = -1
        var newLine = true
        val out = ArrayList<PdfWord>(words.size)
        for ((w, startsLine) in words) {
            if (startsLine) newLine = true
            if (w.text.isBlank()) continue
            if (newLine) {
                line++
                newLine = false
            }
            out += w.copy(text = w.text.trim(), line = line)
        }
        return PageText(out)
    }
}
