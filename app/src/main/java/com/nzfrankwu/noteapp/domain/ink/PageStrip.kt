package com.nzfrankwu.noteapp.domain.ink

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** How a double-tap fits the page to the screen (Settings → Notebooks). Saved by [key]. */
enum class FitMode(val key: String, val label: String) {
    HEIGHT("height", "Fit height"),
    WIDTH("width", "Fit width"),
    PAGE("page", "Whole page");

    companion object {
        /** The user's choice (PHASE-2.md): fit the page's height. */
        val DEFAULT = HEIGHT
        fun fromKey(key: String?): FitMode = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/** What the screen shows: screen = page units × [scale] + offset. */
data class Camera(val scale: Float, val offsetX: Float, val offsetY: Float) {
    fun toScreenX(worldX: Float) = worldX * scale + offsetX
    fun toScreenY(worldY: Float) = worldY * scale + offsetY
    fun toWorldX(screenX: Float) = (screenX - offsetX) / scale
    fun toWorldY(screenY: Float) = (screenY - offsetY) / scale
}

/**
 * A notebook's pages laid side by side on a "desk", left to right, [gap] apart (PHASE-2.md:
 * pages scroll sideways, like Notein). By default they touch, with a thin line drawn between
 * them (PHASE-2.md F4). Positions are in page units; page `i` starts at
 * [pageLeft]. Works out fitting, zooming and how far you can scroll.
 */
class PageStrip(val pageWidth: Float, val pageHeight: Float, val pageCount: Int, val gap: Float = GAP) {
    val totalWidth: Float get() = max(1, pageCount) * pageWidth + (max(1, pageCount) - 1) * gap

    fun pageLeft(index: Int): Float = index * (pageWidth + gap)

    /** The page at desk position [worldX] (the nearest one when it's in a gap or past the ends). */
    fun pageAt(worldX: Float): Int {
        if (pageCount <= 1) return 0
        val i = floor((worldX + gap / 2) / (pageWidth + gap)).toInt()
        return i.coerceIn(0, pageCount - 1)
    }

    /** The pages at least partly on a screen [viewWidth] wide. */
    fun visiblePages(camera: Camera, viewWidth: Float): IntRange {
        if (pageCount == 0) return IntRange.EMPTY
        val first = pageAt(camera.toWorldX(0f))
        val last = pageAt(camera.toWorldX(viewWidth))
        return max(0, first - 0)..min(pageCount - 1, last)
    }

    /** The page nearest the middle of the screen (for the page number badge). */
    fun currentPage(camera: Camera, viewWidth: Float): Int = pageAt(camera.toWorldX(viewWidth / 2))

    /**
     * The camera that fits page [index] to a [viewWidth] × [viewHeight] screen, [margin] px from
     * the sides. Top to bottom it uses the whole height (F17, like Notein), [marginY] px.
     */
    fun fit(mode: FitMode, index: Int, viewWidth: Float, viewHeight: Float, margin: Float = MARGIN, marginY: Float = MARGIN_Y): Camera {
        val byHeight = (viewHeight - 2 * marginY) / pageHeight
        val byWidth = (viewWidth - 2 * margin) / pageWidth
        val scale = when (mode) {
            FitMode.HEIGHT -> byHeight
            FitMode.WIDTH -> byWidth
            FitMode.PAGE -> min(byHeight, byWidth)
        }.coerceAtLeast(0.01f)
        val centreX = pageLeft(index.coerceIn(0, max(0, pageCount - 1))) + pageWidth / 2
        val offsetX = viewWidth / 2 - centreX * scale
        val offsetY = if (pageHeight * scale + 2 * marginY > viewHeight) marginY else (viewHeight - pageHeight * scale) / 2
        return Camera(scale, offsetX, offsetY)
    }

    /** Smallest and largest zoom for this screen. */
    fun scaleRange(viewWidth: Float, viewHeight: Float): ClosedFloatingPointRange<Float> {
        val whole = fit(FitMode.PAGE, 0, viewWidth, viewHeight).scale
        val width = fit(FitMode.WIDTH, 0, viewWidth, viewHeight).scale
        return (whole * MIN_ZOOM_OF_WHOLE_PAGE)..max(width * MAX_ZOOM_OF_WIDTH, whole * 2)
    }

    /** Zooms by [factor] keeping the point under ([focusX], [focusY]) in place, within [scaleRange]. */
    fun zoom(camera: Camera, factor: Float, focusX: Float, focusY: Float, viewWidth: Float, viewHeight: Float): Camera {
        val range = scaleRange(viewWidth, viewHeight)
        val scale = (camera.scale * factor).coerceIn(range.start, range.endInclusive)
        val wx = camera.toWorldX(focusX)
        val wy = camera.toWorldY(focusY)
        return clamp(Camera(scale, focusX - wx * scale, focusY - wy * scale), viewWidth, viewHeight)
    }

    /**
     * Keeps the pages on screen: the middle of the screen can travel from the middle of the first
     * page to the middle of the last; a page shorter than the screen stays centred top to bottom.
     */
    fun clamp(camera: Camera, viewWidth: Float, viewHeight: Float, margin: Float = MARGIN_Y): Camera {
        val s = camera.scale
        val firstCentre = pageWidth / 2
        val lastCentre = totalWidth - pageWidth / 2
        val maxX = viewWidth / 2 - firstCentre * s
        val minX = viewWidth / 2 - lastCentre * s
        val x = camera.offsetX.coerceIn(min(minX, maxX), max(minX, maxX))
        val contentHeight = pageHeight * s
        val y = if (contentHeight + 2 * margin <= viewHeight) {
            (viewHeight - contentHeight) / 2
        } else {
            camera.offsetY.coerceIn(viewHeight - contentHeight - margin, margin)
        }
        return Camera(s, x, y)
    }

    /** True when two cameras show nearly the same thing (for "is it already fitted?"). */
    fun same(a: Camera, b: Camera): Boolean =
        abs(a.scale - b.scale) < a.scale * 0.01f && abs(a.offsetX - b.offsetX) < 2f && abs(a.offsetY - b.offsetY) < 2f

    companion object {
        /** Gap between pages on the desk, in page units: none, the pages touch (F4). */
        const val GAP = 0f

        /** Space each side of a fitted page, in screen pixels. */
        const val MARGIN = 24f

        /** Space above and below a fitted page, and past its top and bottom when zoomed in: none (F17). */
        const val MARGIN_Y = 0f

        private const val MIN_ZOOM_OF_WHOLE_PAGE = 0.5f
        private const val MAX_ZOOM_OF_WIDTH = 4f
    }
}
