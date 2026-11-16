package com.nzfrankwu.noteapp.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfCoordsTest {
    private fun assertPoint(expected: Pair<Float, Float>, actual: Pair<Float, Float>) {
        assertEquals(expected.first, actual.first, 0.001f)
        assertEquals(expected.second, actual.second, 0.001f)
    }

    @Test
    fun unturnedPage_flipsUpAndDown() {
        val m = PdfCoords.pageToPdf(0f, 0f, 612f, 792f, 0)
        assertPoint(0f to 792f, PdfCoords.apply(m, 0f, 0f)) // our top-left = PDF's top-left
        assertPoint(612f to 0f, PdfCoords.apply(m, 612f, 792f))
    }

    @Test
    fun cropBoxOffset_isAdded() {
        val m = PdfCoords.pageToPdf(10f, 20f, 500f, 700f, 0)
        assertPoint(10f to 720f, PdfCoords.apply(m, 0f, 0f))
    }

    @Test
    fun turnedPages_putOurTopLeftWherePdfRendererShowsIt() {
        // A page turned 90° clockwise shows the unturned page's bottom-left at its top-left.
        assertPoint(0f to 0f, PdfCoords.apply(PdfCoords.pageToPdf(0f, 0f, 612f, 792f, 90), 0f, 0f))
        assertPoint(612f to 0f, PdfCoords.apply(PdfCoords.pageToPdf(0f, 0f, 612f, 792f, 180), 0f, 0f))
        assertPoint(612f to 792f, PdfCoords.apply(PdfCoords.pageToPdf(0f, 0f, 612f, 792f, 270), 0f, 0f))
        // -90 is the same as 270.
        assertPoint(612f to 792f, PdfCoords.apply(PdfCoords.pageToPdf(0f, 0f, 612f, 792f, -90), 0f, 0f))
    }

    @Test
    fun pdfToPage_undoesPageToPdf_forEveryTurn() {
        for (rotation in listOf(0, 90, 180, 270)) {
            val there = PdfCoords.pageToPdf(15f, 25f, 600f, 800f, rotation)
            val back = PdfCoords.pdfToPage(15f, 25f, 600f, 800f, rotation)
            for ((u, v) in listOf(0f to 0f, 100f to 50f, 333f to 777f)) {
                val (x, y) = PdfCoords.apply(there, u, v)
                assertPoint(u to v, PdfCoords.apply(back, x, y))
            }
        }
    }
}
