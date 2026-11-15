package com.nzfrankwu.noteapp.domain.ink

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
        assertEquals((h - 48f) / 842f, cam.scale, 0.0001f)
        assertEquals(24f, cam.toScreenY(0f), 0.01f)
        val centre = strip.pageLeft(1) + 595f / 2
        assertEquals(w / 2, cam.toScreenX(centre), 0.01f)
    }

    @Test
    fun fitWidth_fillsTheWidth_andStartsAtTheTop() {
        val cam = strip.fit(FitMode.WIDTH, 0, w, h)
        assertEquals((w - 48f) / 595f, cam.scale, 0.0001f)
        assertEquals(24f, cam.toScreenY(0f), 0.01f)
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
}
