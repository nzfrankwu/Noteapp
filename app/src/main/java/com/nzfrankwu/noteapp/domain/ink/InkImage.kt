package com.nzfrankwu.noteapp.domain.ink

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * A picture placed on a page (Phase 3): drawn under the ink, moved and resized with the lasso.
 *
 * [asset] is the image file's name inside the notebook's folder (e.g. "images/<uuid>.jpg", see
 * NotebookFiles). The box is in page units: [left], [top], [width] × [height] before turning, and
 * [rotation] (radians, clockwise on screen) turns it about its centre.
 */
data class InkImage(
    val id: String,
    val asset: String,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val rotation: Float = 0f,
) {
    val centreX: Float get() = left + width / 2
    val centreY: Float get() = top + height / 2

    /** The four corners after turning, as x0, y0, x1, y1, … (top-left, top-right, bottom-right, bottom-left). */
    fun corners(): FloatArray {
        val c = cos(rotation)
        val s = sin(rotation)
        val hw = width / 2
        val hh = height / 2
        val out = FloatArray(8)
        val dx = floatArrayOf(-hw, hw, hw, -hw)
        val dy = floatArrayOf(-hh, -hh, hh, hh)
        for (i in 0 until 4) {
            out[2 * i] = centreX + dx[i] * c - dy[i] * s
            out[2 * i + 1] = centreY + dx[i] * s + dy[i] * c
        }
        return out
    }

    /** The smallest upright box around it (after turning): left, top, right, bottom. */
    fun bounds(): FloatArray {
        if (rotation == 0f) return floatArrayOf(left, top, left + width, top + height)
        val c = corners()
        return floatArrayOf(
            minOf(c[0], c[2], c[4], c[6]), minOf(c[1], c[3], c[5], c[7]),
            maxOf(c[0], c[2], c[4], c[6]), maxOf(c[1], c[3], c[5], c[7]),
        )
    }

    /** True if page point ([x], [y]) is on the picture. */
    fun contains(x: Float, y: Float): Boolean {
        // Turn the point back into the picture's own (upright) frame.
        val c = cos(-rotation)
        val s = sin(-rotation)
        val px = x - centreX
        val py = y - centreY
        val ux = px * c - py * s
        val uy = px * s + py * c
        return abs(ux) <= width / 2 && abs(uy) <= height / 2
    }

    /**
     * This picture cut down to the part ([left], [top], [right], [bottom] in the picture's own
     * pixels, of a [pixelWidth] × [pixelHeight] file) and showing [newAsset]: the kept part stays
     * exactly where it was on the page, at the same scale and turn (feedback F33).
     */
    fun cropped(newAsset: String, pixelWidth: Int, pixelHeight: Int, left: Int, top: Int, right: Int, bottom: Int): InkImage {
        val sx = width / pixelWidth
        val sy = height / pixelHeight
        val w = (right - left) * sx
        val h = (bottom - top) * sy
        // The kept part's centre, relative to the whole picture's centre, in its own (unturned) frame.
        val dx = left * sx + w / 2 - width / 2
        val dy = top * sy + h / 2 - height / 2
        val c = cos(rotation)
        val s = sin(rotation)
        val cx = centreX + dx * c - dy * s
        val cy = centreY + dx * s + dy * c
        return copy(asset = newAsset, left = cx - w / 2, top = cy - h / 2, width = w, height = h)
    }

    companion object {
        /**
         * A picture [pixelWidth] × [pixelHeight] placed in the middle of a [pageWidth] × [pageHeight]
         * page, [fraction] of the page wide (or less, so it also fits the page's height).
         */
        fun centred(id: String, asset: String, pixelWidth: Int, pixelHeight: Int, pageWidth: Float, pageHeight: Float, fraction: Float = 0.6f): InkImage {
            val aspect = pixelHeight.toFloat() / pixelWidth.coerceAtLeast(1)
            var w = pageWidth * fraction
            var h = w * aspect
            val maxH = pageHeight * 0.8f
            if (h > maxH) {
                h = maxH
                w = h / aspect
            }
            return InkImage(id, asset, (pageWidth - w) / 2, (pageHeight - h) / 2, w, h)
        }
    }
}
