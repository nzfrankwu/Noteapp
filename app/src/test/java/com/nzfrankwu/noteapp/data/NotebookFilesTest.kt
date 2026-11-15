package com.nzfrankwu.noteapp.data

import com.nzfrankwu.noteapp.domain.ink.PageInkCodec
import com.nzfrankwu.noteapp.domain.ink.testStroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class NotebookFilesTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val files by lazy { NotebookFiles(tmp.root) }
    private val strokes = listOf(testStroke("s1", 1f to 1f, 2f to 2f), testStroke("s2", 5f to 5f, 9f to 9f))

    @Test
    fun aPageNeverWrittenOn_isMissing() {
        assertEquals(PageRead.Missing, files.read("nb", "p1"))
    }

    @Test
    fun writtenStrokes_readBack() {
        files.write("nb", "p1", strokes)
        assertEquals(PageRead.Ok(strokes), files.read("nb", "p1"))
        assertEquals(File(tmp.root, "notebooks/nb/p1.ink"), files.pageFile("nb", "p1"))
    }

    @Test
    fun rewriting_replacesThePage_andLeavesNoTemporaryFile() {
        files.write("nb", "p1", strokes)
        files.write("nb", "p1", strokes.take(1))
        assertEquals(PageRead.Ok(strokes.take(1)), files.read("nb", "p1"))
        assertEquals(listOf("p1.ink"), File(tmp.root, "notebooks/nb").list()!!.toList())
    }

    @Test
    fun aSaveCutOffMidway_leavesTheOldPageReadable() {
        files.write("nb", "p1", strokes)
        // What a crash during the next save leaves behind: a half-written temporary file.
        val half = PageInkCodec.encode(strokes.take(1)).let { it.copyOf(it.size / 2) }
        File(tmp.root, "notebooks/nb/p1.ink.saving").writeBytes(half)

        assertEquals(PageRead.Ok(strokes), files.read("nb", "p1"))
        files.cleanUpUnfinishedSaves("nb")
        assertFalse(File(tmp.root, "notebooks/nb/p1.ink.saving").exists())
        assertEquals(PageRead.Ok(strokes), files.read("nb", "p1"))
    }

    @Test
    fun aDamagedFile_isReportedAndLeftAlone() {
        files.write("nb", "p1", strokes)
        val file = files.pageFile("nb", "p1")
        val bytes = file.readBytes()
        file.writeBytes(bytes.copyOf(bytes.size - 5))

        val read = files.read("nb", "p1")
        assertTrue(read is PageRead.Damaged)
        assertEquals("The damaged file is kept as it is", bytes.size - 5, file.length().toInt())
    }

    @Test
    fun pagesOfDifferentNotebooks_areKeptApart() {
        files.write("nb1", "p", strokes)
        files.write("nb2", "p", strokes.take(1))
        assertEquals(PageRead.Ok(strokes), files.read("nb1", "p"))
        assertEquals(PageRead.Ok(strokes.take(1)), files.read("nb2", "p"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun idsThatCouldEscapeTheFolder_areRefused() {
        files.write("../evil", "p", strokes)
    }
}
