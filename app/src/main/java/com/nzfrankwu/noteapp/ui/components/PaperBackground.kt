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
) {
    val scale = size.width / pageWidth
    val stroke = (0.6f * scale).coerceAtLeast(1f)
    when (template) {
        PageTemplate.BLANK -> Unit
        PageTemplate.LINED -> {
            val left = PagePattern.LINED_SIDE_MARGIN * scale
            val right = size.width - left
            for (y in PagePattern.linedRows(pageHeight)) {
                drawLine(lineColor, Offset(left, y * scale), Offset(right, y * scale), stroke)
            }
        }
        PageTemplate.GRID -> {
            for (x in PagePattern.gridStops(pageWidth)) {
                drawLine(lineColor, Offset(x * scale, 0f), Offset(x * scale, size.height), stroke)
            }
            for (y in PagePattern.gridStops(pageHeight)) {
                drawLine(lineColor, Offset(0f, y * scale), Offset(size.width, y * scale), stroke)
            }
        }
        PageTemplate.DOTS -> {
            val xs = PagePattern.gridStops(pageWidth)
            val points = PagePattern.gridStops(pageHeight).flatMap { y -> xs.map { x -> Offset(x * scale, y * scale) } }
            drawPoints(points, PointMode.Points, lineColor, strokeWidth = (1.6f * scale).coerceAtLeast(1.5f), cap = StrokeCap.Round)
        }
    }
}
