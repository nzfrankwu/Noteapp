package com.nzfrankwu.noteapp.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.nzfrankwu.noteapp.domain.PagePattern
import com.nzfrankwu.noteapp.domain.PageTemplate

/**
 * Draws a page's paper pattern (lines, grid or dots) over the whole draw area, which shows a page
 * of [pageWidth] × [pageHeight] page units. Lines get at least 1 pixel, so they stay visible on
 * small thumbnails.
 */
fun DrawScope.drawPaperPattern(
    template: PageTemplate,
    pageWidth: Float,
    pageHeight: Float,
    lineColor: Color,
) = drawPaperPattern(template, pageWidth, pageHeight, lineColor, left = 0f, top = 0f, scale = size.width / pageWidth)

/**
 * Draws the pattern of a page whose top-left corner is at ([left], [top]) pixels, shown at
 * [scale] pixels per page unit (the notebook editor, at any zoom). Only the part on screen is drawn.
 */
fun DrawScope.drawPaperPattern(
    template: PageTemplate,
    pageWidth: Float,
    pageHeight: Float,
    lineColor: Color,
    left: Float,
    top: Float,
    scale: Float,
) {
    val stroke = (0.6f * scale).coerceAtLeast(1f)
    val right = left + pageWidth * scale
    val bottom = top + pageHeight * scale
    fun onScreenY(y: Float) = y >= 0f && y <= size.height
    fun onScreenX(x: Float) = x >= 0f && x <= size.width
    when (template) {
        PageTemplate.BLANK -> Unit
        PageTemplate.LINED -> {
            val l = left + PagePattern.LINED_SIDE_MARGIN * scale
            val r = right - PagePattern.LINED_SIDE_MARGIN * scale
            for (y in PagePattern.linedRows(pageHeight)) {
                val sy = top + y * scale
                if (onScreenY(sy)) drawLine(lineColor, Offset(l, sy), Offset(r, sy), stroke)
            }
        }
        PageTemplate.GRID -> {
            for (x in PagePattern.gridStops(pageWidth)) {
                val sx = left + x * scale
                if (onScreenX(sx)) drawLine(lineColor, Offset(sx, maxOf(top, 0f)), Offset(sx, minOf(bottom, size.height)), stroke)
            }
            for (y in PagePattern.gridStops(pageHeight)) {
                val sy = top + y * scale
                if (onScreenY(sy)) drawLine(lineColor, Offset(maxOf(left, 0f), sy), Offset(minOf(right, size.width), sy), stroke)
            }
        }
        PageTemplate.DOTS -> {
            val xs = PagePattern.gridStops(pageWidth).map { left + it * scale }.filter(::onScreenX)
            val points = PagePattern.gridStops(pageHeight).map { top + it * scale }.filter(::onScreenY)
                .flatMap { y -> xs.map { x -> Offset(x, y) } }
            drawPoints(points, PointMode.Points, lineColor, strokeWidth = (1.6f * scale).coerceAtLeast(1.5f), cap = StrokeCap.Round)
        }
    }
}
