package com.nzfrankwu.noteapp.domain.ink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PageInkCodecTest {
    private val page = listOf(
        testStroke("a", 10f to 20f, 11f to 21.5f, 12.25f to 23f),
        testStroke("b", 100f to 100f, 140f to 100f, tool = InkTool.HIGHLIGHTER, size = 12f, withPressure = false),
        testStroke("dot", 50f to 50f),
    )

    @Test
    fun savedStrokes_readBackExactly() {
        assertEquals(page, PageInkCodec.decode(PageInkCodec.encode(page)))
    }

    @Test
    fun anEmptyPage_readsBackEmpty() {
        assertEquals(emptyList<InkStroke>(), PageInkCodec.decode(PageInkCodec.encode(emptyList())))
    }

    @Test
    fun strokesWithoutPressure_keepNone() {
        val back = PageInkCodec.decode(PageInkCodec.encode(page))
        assertTrue(back[1].pressure.all { it == InkStroke.NONE })
    }

    @Test
    fun theFile_startsWithItsMarkAndVersion() {
        val bytes = PageInkCodec.encode(page)
        assertEquals("NAPG", String(bytes.copyOfRange(0, 4), Charsets.US_ASCII))
        // Handwriting alone is still saved as version 1 (Phase 3: version 2 only when a page has pictures).
        assertEquals(PageInkCodec.VERSION_STROKES_ONLY, ((bytes[4].toInt() and 0xFF) shl 8) or (bytes[5].toInt() and 0xFF))
        assertEquals(1, PageInkCodec.VERSION_STROKES_ONLY)
    }

    @Test
    fun aFileFromANewerApp_isRefused_notMisread() {
        val bytes = PageInkCodec.encode(page)
        bytes[5] = (PageInkCodec.VERSION + 1).toByte()
        assertRefused(bytes, "newer")
    }

    @Test
    fun aFileCutShort_isRefused() {
        val bytes = PageInkCodec.encode(page)
        assertRefused(bytes.copyOf(bytes.size - 10), null)
    }

    @Test
    fun aChangedByte_isCaughtByTheChecksum() {
        val bytes = PageInkCodec.encode(page)
        // Change one byte of the compressed data (after the 6-byte mark+version and the 10-byte gzip
        // header, whose timestamp isn't part of the data); gzip's checksum must catch it.
        var caught = 0
        for (i in 16 until bytes.size - 8 step 7) {
            val copy = bytes.copyOf()
            copy[i] = (copy[i].toInt() xor 0x5A).toByte()
            try {
                PageInkCodec.decode(copy)
            } catch (e: PageFileException) {
                caught++
                continue
            }
            fail("A damaged byte at $i was not noticed")
        }
        assertTrue(caught > 0)
    }

    @Test
    fun somethingElse_isNotAPageFile() {
        assertRefused("hello world".toByteArray(), "Not a Noteapp page file")
        assertRefused(ByteArray(0), "Not a Noteapp page file")
    }

    @Test
    fun unknownToolKeys_fallBackToThePen() {
        assertEquals(InkTool.PEN, InkTool.fromKey("calligraphy-v9"))
    }

    @Test
    fun toolKeys_neverChange() {
        // New kinds may be added (F18: the round highlighter); existing keys never change.
        assertEquals(
            setOf(
                "pen-v1", "highlighter-v1", "highlighter-round-v1", "pen-steady-v1", "pen-pressure-low-v1", "pen-pressure-mid-v1",
                "pen-pressure-mid-v2", "pen-pressure-full-v2", // 7.12
            ),
            InkTool.entries.map { it.key }.toSet(),
        )
        assertTrue(InkTool.HIGHLIGHTER.isHighlighter && InkTool.HIGHLIGHTER_ROUND.isHighlighter && !InkTool.PEN.isHighlighter)
    }

    private fun assertRefused(bytes: ByteArray, messagePart: String?) {
        try {
            PageInkCodec.decode(bytes)
            fail("Expected the page file to be refused")
        } catch (e: PageFileException) {
            if (messagePart != null) assertTrue(e.message, e.message!!.contains(messagePart))
        }
    }
}
