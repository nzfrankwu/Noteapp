package com.nzfrankwu.noteapp.data

import com.nzfrankwu.noteapp.domain.ink.InkImage
import com.nzfrankwu.noteapp.domain.ink.testStroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Files kept next to a notebook's pages: imported PDFs and pictures (Phase 3). */
class NotebookAssetsTest {
    @get:Rule val tmp = TemporaryFolder()
    private val files by lazy { NotebookFiles(tmp.root) }

    @Test
    fun aPageWithPictures_savesAndReadsBack() {
        val strokes = listOf(testStroke("a", 1f to 2f, 3f to 4f))
        val images = listOf(InkImage("i", "images/x.jpg", 10f, 20f, 30f, 40f, 0.25f))
        files.write("nb", "p", strokes, images)
        assertEquals(PageRead.Ok(strokes, images), files.read("nb", "p"))
        // Saving again without pictures (they were deleted) leaves none.
        files.write("nb", "p", strokes)
        assertEquals(PageRead.Ok(strokes), files.read("nb", "p"))
    }

    @Test
    fun anImportedPdf_isCopiedInWhole_intoTheNotebooksFolder() {
        val bytes = ByteArray(300_000) { (it % 251).toByte() }
        val name = files.importPdf("nb", bytes.inputStream())
        val file = files.assetFile("nb", name)
        assertEquals(File(File(tmp.root, NotebookFiles.DIR), "nb"), file.parentFile)
        assertTrue(file.readBytes().contentEquals(bytes))
        assertFalse("No temporary file left", file.parentFile!!.listFiles()!!.any { it.name.endsWith(".saving") })
    }

    @Test
    fun aCopyThatFails_leavesNothing() {
        val failing = object : java.io.InputStream() {
            var n = 0
            override fun read(): Int = if (n++ < 1000) 7 else throw java.io.IOException("cut off")
        }
        try {
            files.importPdf("nb", failing)
            fail("The failure must be reported")
        } catch (e: java.io.IOException) {
            // expected
        }
        assertEquals(emptyList<String>(), File(File(tmp.root, NotebookFiles.DIR), "nb").list()?.toList().orEmpty())
    }

    @Test
    fun assetNames_cannotLeaveTheNotebooksFolder() {
        for (bad in listOf("../x.pdf", "images/../../x", "/abs.pdf", "", "a b.pdf", "images//x.jpg")) {
            try {
                files.assetFile("nb", bad)
                fail("\"$bad\" must be refused")
            } catch (e: IllegalArgumentException) {
                // expected
            }
        }
        assertTrue(files.newImageName("jpg").matches(Regex("images/[0-9a-f-]{36}\\.jpg")))
    }

    @Test
    fun anUnusedImportFolder_canBeRemoved() {
        files.importPdf("nb-unused", ByteArray(10).inputStream())
        files.deleteUnusedNotebookFolder("nb-unused")
        assertFalse(File(File(tmp.root, NotebookFiles.DIR), "nb-unused").exists())
    }
}
