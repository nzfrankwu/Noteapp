package com.nzfrankwu.noteapp.domain.ink

import com.nzfrankwu.noteapp.domain.PageSize
import kotlin.math.abs
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
 * them (PHASE-2.md F4). Positions are in page units.
 *
 * Pages can differ in size (Phase 3: a PDF's pages, or a page shaped like an image). Page `i`
 * starts at [pageLeft] and is [pageWidth] × [pageHeight]. Pages are centred top to bottom in a
 * band as tall as the tallest page ([bandHeight]), so page `i`'s top edge is at [pageTop]: 0 when
 * every page is the same height, as in a plain notebook, which then lays out exactly as before.
 * Works out fitting, zooming and how far you can scroll.
 */
class PageStrip(val sizes: List<PageSize>, val gap: Float = GAP) {
    /** Every page [pageWidth] × [pageHeight] (a plain notebook). */
    constructor(pageWidth: Float, pageHeight: Float, pageCount: Int, gap: Float = GAP) :
        this(List(pageCount) { PageSize(pageWidth, pageHeight) }, gap)

    val pageCount: Int get() = sizes.size

    /** Each page's left edge, running along the desk. */
    private val lefts: FloatArray = FloatArray(sizes.size).also { arr ->
        var x = 0f
        for (i in sizes.indices) {
            arr[i] = x
            x += sizes[i].width + gap
        }
    }

    /** The tallest page: the height of the band the pages are centred in. */
    val bandHeight: Float = sizes.maxOfOrNull { it.height } ?: DEFAULT_SIZE.height

    /** From the first page's left edge to the last page's right edge. */
    val totalWidth: Float = if (sizes.isEmpty()) DEFAULT_SIZE.width else lefts[sizes.size - 1] + sizes[sizes.size - 1].width

    private fun sizeOf(index: Int): PageSize =
        if (sizes.isEmpty()) DEFAULT_SIZE else sizes[index.coerceIn(0, sizes.size - 1)]

    /** Page [index]'s width (the nearest page's, past the ends). */
    fun pageWidth(index: Int): Float = sizeOf(index).width

    /** Page [index]'s height (the nearest page's, past the ends). */
    fun pageHeight(index: Int): Float = sizeOf(index).height

    /** Page [index]'s left edge. Past the last page: where more pages like the last would start. */
    fun pageLeft(index: Int): Float = when {
        sizes.isEmpty() || index <= 0 -> 0f
        index < sizes.size -> lefts[index]
        else -> totalWidth + gap + (index - sizes.size) * (pageWidth(sizes.size - 1) + gap)
    }

    /** Page [index]'s top edge: pages are centred top to bottom in the band. */
    fun pageTop(index: Int): Float = (bandHeight - pageHeight(index)) / 2

    /** The middle of page [index], left to right (the nearest page, past the ends). */
    fun pageCentreX(index: Int): Float {
        if (sizes.isEmpty()) return DEFAULT_SIZE.width / 2
        val i = index.coerceIn(0, sizes.size - 1)
        return lefts[i] + sizes[i].width / 2
    }

    /** The page at desk position [worldX] (the nearest one when it's in a gap or past the ends). */
    fun pageAt(worldX: Float): Int {
        if (sizes.size <= 1) return 0
        // The last page whose left edge, less half a gap, is at or before worldX (binary search).
        var lo = 0
        var hi = sizes.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (lefts[mid] - gap / 2 <= worldX) lo = mid else hi = mid - 1
        }
        return lo
    }

    /** The pages at least partly on a screen [viewWidth] wide. */
    fun visiblePages(camera: Camera, viewWidth: Float): IntRange {
        if (sizes.isEmpty()) return IntRange.EMPTY
        val first = pageAt(camera.toWorldX(0f))
        val last = pageAt(camera.toWorldX(viewWidth))
        return max(0, first)..min(sizes.size - 1, last)
    }

    /** The page nearest the middle of the screen (for the page number badge). */
    fun currentPage(camera: Camera, viewWidth: Float): Int = pageAt(camera.toWorldX(viewWidth / 2))

    /**
     * This strip with a page added at [index], the same size as the page before it (or, at the
     * front, the page after it): the same rule as NotebookRepository.addPage.
     */
    fun withPageAdded(index: Int): PageStrip {
        val at = index.coerceIn(0, sizes.size)
        val size = sizes.getOrNull(at - 1) ?: sizes.getOrNull(at) ?: DEFAULT_SIZE
        return PageStrip(sizes.toMutableList().apply { add(at, size) }, gap)
    }

    /**
     * The camera that fits page [index] to a [viewWidth] × [viewHeight] screen, [margin] px from
     * the sides. Top to bottom it uses the whole height (F17, like Notein), [marginY] px.
     */
    fun fit(mode: FitMode, index: Int, viewWidth: Float, viewHeight: Float, margin: Float = MARGIN, marginY: Float = MARGIN_Y): Camera {
        val w = pageWidth(index)
        val h = pageHeight(index)
        val byHeight = (viewHeight - 2 * marginY) / h
        val byWidth = (viewWidth - 2 * margin) / w
        val scale = when (mode) {
            FitMode.HEIGHT -> byHeight
            FitMode.WIDTH -> byWidth
            FitMode.PAGE -> min(byHeight, byWidth)
        }.coerceAtLeast(0.01f)
        val offsetX = viewWidth / 2 - pageCentreX(index) * scale
        val top = pageTop(index)
        val offsetY = if (h * scale + 2 * marginY > viewHeight) {
            marginY - top * scale // the page's top edge at the top of the screen
        } else {
            (viewHeight - h * scale) / 2 - top * scale // the page centred top to bottom
        }
        return Camera(scale, offsetX, offsetY)
    }

    /** Smallest and largest zoom for this screen. */
    fun scaleRange(viewWidth: Float, viewHeight: Float): ClosedFloatingPointRange<Float> {
        // The whole band can shrink to half; the narrowest page can be zoomed into the most.
        val biggest = PageStrip(listOf(PageSize(sizes.maxOfOrNull { it.width } ?: DEFAULT_SIZE.width, bandHeight)), gap)
        val narrowest = PageStrip(listOf(sizes.minByOrNull { it.width } ?: DEFAULT_SIZE), gap)
        val whole = biggest.fit(FitMode.PAGE, 0, viewWidth, viewHeight).scale
        val width = narrowest.fit(FitMode.WIDTH, 0, viewWidth, viewHeight).scale
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
     * page to the middle of the last. Top to bottom, when the band (as tall as the tallest page)
     * is shorter than the screen it stays centred; otherwise it can't leave a gap above or below.
     */
    fun clamp(camera: Camera, viewWidth: Float, viewHeight: Float, margin: Float = MARGIN_Y): Camera {
        val s = camera.scale
        val maxX = viewWidth / 2 - pageCentreX(0) * s
        val minX = viewWidth / 2 - pageCentreX(sizes.size - 1) * s
        val x = camera.offsetX.coerceIn(min(minX, maxX), max(minX, maxX))
        val contentHeight = bandHeight * s
        val y = if (contentHeight + 2 * margin <= viewHeight) {
            (viewHeight - contentHeight) / 2
        } else {
            camera.offsetY.coerceIn(viewHeight - contentHeight - margin, margin)
        }
        return Camera(s, x, y)
    }

    /**
     * How far through the notebook the screen is, 0 (middle of the first page in the middle of
     * the screen) to 1 (the last page's middle): where the page scroller's handle sits (F20).
     */
    fun scrollFraction(camera: Camera, viewWidth: Float): Float {
        val first = pageCentreX(0)
        val last = pageCentreX(sizes.size - 1)
        if (last <= first) return 0f
        return ((camera.toWorldX(viewWidth / 2) - first) / (last - first)).coerceIn(0f, 1f)
    }

    /** [camera] moved sideways (same zoom and height) to [fraction] of the way through (F20). */
    fun atScrollFraction(camera: Camera, fraction: Float, viewWidth: Float, viewHeight: Float): Camera {
        val first = pageCentreX(0)
        val last = pageCentreX(sizes.size - 1)
        val centre = first + (last - first).coerceAtLeast(0f) * fraction.coerceIn(0f, 1f)
        return clamp(camera.copy(offsetX = viewWidth / 2 - centre * camera.scale), viewWidth, viewHeight)
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

        /** The size used while there are no pages: A4 portrait. */
        val DEFAULT_SIZE = PageSize(595f, 842f)

        private const val MIN_ZOOM_OF_WHOLE_PAGE = 0.5f
        private const val MAX_ZOOM_OF_WIDTH = 4f
    }
}
