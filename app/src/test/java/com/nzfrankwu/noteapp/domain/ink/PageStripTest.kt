package com.nzfrankwu.noteapp.domain.ink

import com.nzfrankwu.noteapp.domain.PageSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageStripTest {
    // A4 in page units; a landscape tablet screen in pixels.
    // With a gap, so the gap rules are tested too (the app's default is no gap).
    private val strip = PageStrip(595f, 842f, pageCount = 3, gap = 24f)
    private val w = 2560f
    private val h = 1500f

    @Test
    fun pagesSitSideBySide_withGaps() {
        assertEquals(0f, strip.pageLeft(0))
        assertEquals(619f, strip.pageLeft(1))
        assertEquals(3 * 595f + 2 * 24f, strip.totalWidth)
    }

    @Test
    fun byDefault_pagesTouch() {
        val touching = PageStrip(595f, 842f, pageCount = 3)
        assertEquals(595f, touching.pageLeft(1))
        assertEquals(3 * 595f, touching.totalWidth)
        assertEquals(0, touching.pageAt(594f))
        assertEquals(1, touching.pageAt(596f))
    }

    @Test
    fun pageAt_findsThePage_andTheNearestOneInAGap() {
        assertEquals(0, strip.pageAt(10f))
        assertEquals(1, strip.pageAt(700f))
        assertEquals(0, strip.pageAt(600f)) // first half of the gap after page 1
        assertEquals(1, strip.pageAt(615f)) // second half
        assertEquals(2, strip.pageAt(99_999f))
        assertEquals(0, strip.pageAt(-50f))
    }

    @Test
    fun fitHeight_fillsTheHeight_andCentresThePage() {
        val cam = strip.fit(FitMode.HEIGHT, 1, w, h)
        // F17: the page fills the whole height, top to bottom, with no margin.
        assertEquals(h / 842f, cam.scale, 0.0001f)
        assertEquals(0f, cam.toScreenY(0f), 0.01f)
        assertEquals(h, cam.toScreenY(842f), 0.01f)
        val centre = strip.pageLeft(1) + 595f / 2
        assertEquals(w / 2, cam.toScreenX(centre), 0.01f)
    }

    @Test
    fun fitWidth_fillsTheWidth_andStartsAtTheTop() {
        val cam = strip.fit(FitMode.WIDTH, 0, w, h)
        assertEquals((w - 48f) / 595f, cam.scale, 0.0001f)
        assertEquals(0f, cam.toScreenY(0f), 0.01f) // starts right at the top (F17)
        assertEquals(24f, cam.toScreenX(0f), 0.01f)
    }

    @Test
    fun wholePage_fitsBothWays() {
        val portrait = strip.fit(FitMode.PAGE, 0, 1600f, 2400f)
        assertTrue(842f * portrait.scale <= 2400f - 48f + 0.01f)
        assertTrue(595f * portrait.scale <= 1600f - 48f + 0.01f)
    }

    @Test
    fun zoom_keepsThePointUnderTheFingers() {
        val cam = strip.fit(FitMode.HEIGHT, 0, w, h)
        val wx = cam.toWorldX(1300f)
        val zoomed = strip.zoom(cam, 2f, 1300f, 700f, w, h)
        assertEquals(cam.scale * 2, zoomed.scale, 0.0001f)
        assertEquals(1300f, zoomed.toScreenX(wx), 0.5f)
    }

    @Test
    fun zoom_staysWithinLimits() {
        val cam = strip.fit(FitMode.HEIGHT, 0, w, h)
        val range = strip.scaleRange(w, h)
        assertEquals(range.endInclusive, strip.zoom(cam, 1000f, 0f, 0f, w, h).scale, 0.0001f)
        assertEquals(range.start, strip.zoom(cam, 0.0001f, 0f, 0f, w, h).scale, 0.0001f)
    }

    @Test
    fun clamp_stopsScrollingPastTheFirstAndLastPage() {
        val cam = strip.fit(FitMode.HEIGHT, 0, w, h)
        val tooFarRight = strip.clamp(cam.copy(offsetX = cam.offsetX + 5000f), w, h)
        assertEquals(w / 2, tooFarRight.toScreenX(595f / 2), 0.5f)
        val tooFarLeft = strip.clamp(cam.copy(offsetX = -999_999f), w, h)
        assertEquals(w / 2, tooFarLeft.toScreenX(strip.totalWidth - 595f / 2), 0.5f)
    }

    @Test
    fun clamp_keepsAShortPageCentredTopToBottom() {
        val small = strip.fit(FitMode.PAGE, 0, w, h).let { it.copy(scale = it.scale / 2) }
        val clamped = strip.clamp(small.copy(offsetY = 0f), w, h)
        assertEquals((h - 842f * small.scale) / 2, clamped.offsetY, 0.01f)
    }

    @Test
    fun currentPage_isTheOneInTheMiddle() {
        assertEquals(2, strip.currentPage(strip.fit(FitMode.HEIGHT, 2, w, h), w))
    }

    @Test
    fun fitModeKeys_neverChange() {
        assertEquals(setOf("height", "width", "page"), FitMode.entries.map { it.key }.toSet())
        assertEquals(FitMode.HEIGHT, FitMode.fromKey(null))
    }

    @Test
    fun theScroller_goesFromTheFirstPageToTheLast_andBack() {
        val cam = strip.fit(FitMode.HEIGHT, 0, w, h)
        assertEquals(0f, strip.scrollFraction(cam, w), 0.001f)
        val end = strip.atScrollFraction(cam, 1f, w, h)
        assertEquals(strip.pageCount - 1, strip.currentPage(end, w))
        assertEquals(1f, strip.scrollFraction(end, w), 0.001f)
        val half = strip.atScrollFraction(cam, 0.5f, w, h)
        assertEquals(0.5f, strip.scrollFraction(half, w), 0.001f)
        assertEquals(cam.scale, half.scale, 0.0001f) // sliding never zooms
        // Past the ends stays at the ends.
        assertEquals(1f, strip.scrollFraction(strip.atScrollFraction(cam, 7f, w, h), w), 0.001f)
    }

    // ---- Pages of different sizes (Phase 3) ----

    // A portrait A4 page, two landscape slides, and a US Letter page, with a gap.
    private val mixed = PageStrip(
        listOf(PageSize(595f, 842f), PageSize(842f, 595f), PageSize(842f, 595f), PageSize(612f, 792f)),
        gap = 20f,
    )

    @Test
    fun mixed_pagesRunOnFromEachOther() {
        assertEquals(0f, mixed.pageLeft(0))
        assertEquals(615f, mixed.pageLeft(1))
        assertEquals(1477f, mixed.pageLeft(2))
        assertEquals(2339f, mixed.pageLeft(3))
        assertEquals(2339f + 612f, mixed.totalWidth)
        assertEquals(842f, mixed.pageWidth(1))
        assertEquals(595f, mixed.pageHeight(1))
    }

    @Test
    fun mixed_pagesAreCentredTopToBottom_inABandAsTallAsTheTallest() {
        assertEquals(842f, mixed.bandHeight)
        assertEquals(0f, mixed.pageTop(0))
        assertEquals((842f - 595f) / 2, mixed.pageTop(1))
        assertEquals((842f - 792f) / 2, mixed.pageTop(3))
    }

    @Test
    fun mixed_pageAt_findsEachPage_andTheNearestInAGap() {
        assertEquals(0, mixed.pageAt(594f))
        assertEquals(0, mixed.pageAt(604f)) // first half of the gap
        assertEquals(1, mixed.pageAt(606f)) // second half
        assertEquals(1, mixed.pageAt(1400f))
        assertEquals(2, mixed.pageAt(1477f))
        assertEquals(3, mixed.pageAt(2400f))
        assertEquals(3, mixed.pageAt(99_999f))
        assertEquals(0, mixed.pageAt(-5f))
        // Against a plain search through every page.
        var x = -50f
        while (x < mixed.totalWidth + 50f) {
            val expected = (0 until mixed.pageCount).last { it == 0 || mixed.pageLeft(it) - 10f <= x }
            assertEquals("at $x", expected, mixed.pageAt(x))
            x += 7.3f
        }
    }

    @Test
    fun mixed_fitHeight_fillsTheScreenWithThatPage() {
        val cam = mixed.fit(FitMode.HEIGHT, 1, w, h)
        assertEquals(h / 595f, cam.scale, 0.0001f)
        assertEquals(0f, cam.toScreenY(mixed.pageTop(1)), 0.01f)
        assertEquals(h, cam.toScreenY(mixed.pageTop(1) + 595f), 0.01f)
        assertEquals(w / 2, cam.toScreenX(mixed.pageLeft(1) + 842f / 2), 0.01f)
        assertEquals(1, mixed.currentPage(cam, w))
        // Fitting doesn't move the page when clamped afterwards (it's a valid place to be).
        assertTrue(mixed.same(cam, mixed.clamp(cam, w, h)))
    }

    @Test
    fun mixed_fitWholePage_centresASmallPage() {
        val cam = mixed.fit(FitMode.PAGE, 3, w, h)
        val top = cam.toScreenY(mixed.pageTop(3))
        val bottom = cam.toScreenY(mixed.pageTop(3) + 792f)
        assertEquals(h - bottom, top, 0.5f)
    }

    @Test
    fun mixed_clamp_travelsFromTheFirstPagesMiddleToTheLastPagesMiddle() {
        val cam = mixed.fit(FitMode.HEIGHT, 0, w, h)
        val start = mixed.clamp(cam.copy(offsetX = 99_999f), w, h)
        assertEquals(w / 2, start.toScreenX(595f / 2), 0.5f)
        val end = mixed.clamp(cam.copy(offsetX = -99_999f), w, h)
        assertEquals(w / 2, end.toScreenX(2339f + 612f / 2), 0.5f)
        assertEquals(1f, mixed.scrollFraction(end, w), 0.001f)
        assertEquals(3, mixed.currentPage(mixed.atScrollFraction(cam, 1f, w, h), w))
    }

    @Test
    fun withPageAdded_copiesThePageBefore_orAfterAtTheFront() {
        assertEquals(PageSize(612f, 792f), mixed.withPageAdded(4).sizes[4])
        assertEquals(PageSize(842f, 595f), mixed.withPageAdded(2).sizes[2])
        assertEquals(PageSize(595f, 842f), mixed.withPageAdded(0).sizes[0])
        assertEquals(5, mixed.withPageAdded(99).pageCount)
        assertEquals(PageStrip.DEFAULT_SIZE, PageStrip(emptyList()).withPageAdded(0).sizes.single())
    }

    @Test
    fun aPlainNotebook_laysOutExactlyAsTheSameSizedList() {
        val plain = PageStrip(595f, 842f, 3, gap = 24f)
        val listed = PageStrip(List(3) { PageSize(595f, 842f) }, gap = 24f)
        for (i in 0..5) assertEquals(i * 619f, plain.pageLeft(i))
        for (i in 0 until 3) assertEquals(0f, plain.pageTop(i))
        val cam = plain.fit(FitMode.HEIGHT, 1, w, h)
        assertEquals(cam, listed.fit(FitMode.HEIGHT, 1, w, h))
        assertEquals(plain.scaleRange(w, h), listed.scaleRange(w, h))
    }

    @Test
    fun theScroller_withOnePage_staysPut() {
        val one = PageStrip(595f, 842f, 1)
        val cam = one.fit(FitMode.HEIGHT, 0, w, h)
        assertEquals(0f, one.scrollFraction(cam, w), 0.001f)
        assertEquals(cam.offsetX, one.atScrollFraction(cam, 0.8f, w, h).offsetX, 0.01f)
    }
}
