package com.nzfrankwu.noteapp.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nzfrankwu.noteapp.data.backup.drive.AppPdfSource
import com.nzfrankwu.noteapp.data.db.NoteAppDatabase
import com.nzfrankwu.noteapp.domain.ink.InkStroke
import com.nzfrankwu.noteapp.domain.ink.InkTool
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** PHASE-6.md 6.12: the notebooks the PDF copies are made from, with folder paths and versions. */
@RunWith(AndroidJUnit4::class)
class AppPdfSourceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(context.cacheDir, "pdf-source-test")
    private lateinit var db: NoteAppDatabase
    private lateinit var notebooks: NotebookRepository
    private lateinit var files: NotebookFiles
    private lateinit var source: AppPdfSource
    private lateinit var nb: String

    @Before
    fun setUp() {
        runBlocking {
            root.deleteRecursively()
            db = Room.inMemoryDatabaseBuilder(context, NoteAppDatabase::class.java).build()
            val folders = FolderRepository(db)
            val top = (folders.createFolder("Electromagnetics", null) as FolderResult.Success).folderId
            val labs = (folders.createSubfolder(top, "Labs") as FolderResult.Success).folderId
            notebooks = NotebookRepository(db)
            files = NotebookFiles(root)
            source = AppPdfSource(db, files)
            nb = (notebooks.createNotebook(labs, "Lab 1") as NotebookResult.Success).id
            com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context)
        }
    }

    @After
    fun tearDown() {
        db.close()
        root.deleteRecursively()
    }

    private fun stroke() = InkStroke(
        "s1", InkTool.PEN, 0xFF000000.toInt(), 3f,
        floatArrayOf(10f, 50f, 90f), floatArrayOf(10f, 40f, 20f), intArrayOf(0, 10, 20),
        floatArrayOf(0.5f, 0.5f, 0.5f), FloatArray(3) { InkStroke.NONE }, FloatArray(3) { InkStroke.NONE },
    )

    @Test
    fun aNotebook_isListedWithItsFolderPath_andExportsAPdf() = runBlocking {
        val w = source.wanted().single()
        assertEquals("Lab 1", w.title)
        assertEquals(listOf("Electromagnetics", "Labs"), w.folderPath)
        val out = File(root, "out.pdf")
        source.export(nb, out)
        assertTrue(out.readBytes().decodeToString(0, 5) == "%PDF-")
    }

    @Test
    fun theVersion_changesWithInk_butNotWithTheTitle() = runBlocking {
        val v1 = source.wanted().single().version
        notebooks.rename(nb, "Lab 1: oscilloscope")
        assertEquals(v1, source.wanted().single().version)
        val page = notebooks.getPages(nb).first().id
        files.write(nb, page, listOf(stroke()))
        notebooks.markPageEdited(page)
        assertNotEquals(v1, source.wanted().single().version)
    }

    @Test
    fun aDeletedNotebook_isntWanted_butIsStillKnown() = runBlocking {
        notebooks.delete(nb)
        assertTrue(source.wanted().isEmpty())
        assertTrue(nb in source.allIds())
    }
}
