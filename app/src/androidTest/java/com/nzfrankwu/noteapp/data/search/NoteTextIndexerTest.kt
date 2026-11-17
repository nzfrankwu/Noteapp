package com.nzfrankwu.noteapp.data.search

import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nzfrankwu.noteapp.data.FolderRepository
import com.nzfrankwu.noteapp.data.FolderResult
import com.nzfrankwu.noteapp.data.NotebookFiles
import com.nzfrankwu.noteapp.data.NotebookRepository
import com.nzfrankwu.noteapp.data.NotebookResult
import com.nzfrankwu.noteapp.data.PdfImport
import com.nzfrankwu.noteapp.data.PdfImportResult
import com.nzfrankwu.noteapp.data.backup.BackupPaths
import com.nzfrankwu.noteapp.data.db.NoteAppDatabase
import com.nzfrankwu.noteapp.domain.ink.InkText
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File

/** PHASE-7.md 7.9: the search store follows the notes' PDF words and text boxes. */
@RunWith(AndroidJUnit4::class)
class NoteTextIndexerTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: NoteAppDatabase
    private lateinit var root: File
    private lateinit var files: NotebookFiles
    private lateinit var notebooks: NotebookRepository
    private lateinit var store: NoteTextStore
    private lateinit var indexer: NoteTextIndexer
    private lateinit var folderId: String

    @Before
    fun setUp() {
        PDFBoxResourceLoader.init(context)
        db = Room.inMemoryDatabaseBuilder(context, NoteAppDatabase::class.java).build()
        root = File(context.cacheDir, "note-text-test").apply { deleteRecursively(); mkdirs() }
        files = NotebookFiles(root)
        notebooks = NotebookRepository(db)
        store = NoteTextStore(context, name = null) // in memory
        indexer = NoteTextIndexer(db, files, store)
        folderId = runBlocking { (FolderRepository(db).createFolder("Electromagnetics", "ELECTENG 204") as FolderResult.Success).folderId }
    }

    @After
    fun tearDown() {
        store.close()
        db.close()
        root.deleteRecursively()
    }

    private fun box(text: String) = InkText(id = "t-$text", text = text, left = 50f, top = 60f, width = 200f, height = 30f)

    private fun pdf(vararg lines: String): ByteArray {
        val doc = PdfDocument()
        val paint = Paint().apply { textSize = 24f; color = Color.BLACK }
        val page = doc.startPage(PdfDocument.PageInfo.Builder(842, 595, 1).create())
        lines.forEachIndexed { i, line -> page.canvas.drawText(line, 60f, 80f + 40f * i, paint) }
        doc.finishPage(page)
        return ByteArrayOutputStream().also { doc.writeTo(it); doc.close() }.toByteArray()
    }

    @Test
    fun textBoxes_areFound_followEdits_andLeaveWithTheirPage() = runBlocking {
        val nb = (notebooks.createNotebook(folderId, "Lecture 7") as NotebookResult.Success).id
        val page = notebooks.getPages(nb).first().id
        files.write(nb, page, emptyList(), texts = listOf(box("Smith chart: impedance")))
        indexer.run()
        assertEquals(listOf(page), store.search(listOf("smith", "IMPEDANCE")).map { it.pageId })

        // Edited: the old words go, the new ones are found.
        Thread.sleep(20) // a new modified time
        files.write(nb, page, emptyList(), texts = listOf(box("Fourier series")))
        indexer.run()
        assertTrue(store.search(listOf("smith")).isEmpty())
        assertEquals(1, store.search(listOf("fourier")).size)

        // A second run with nothing changed reads nothing.
        assertEquals(0, indexer.run().read)

        // The notebook is deleted (Recently deleted): its pages leave the store.
        notebooks.delete(nb)
        indexer.run()
        assertTrue(store.search(listOf("fourier")).isEmpty())
    }

    @Test
    fun pdfPages_areFound_byTheirWords_andCanBeLimitedToANotebook() = runBlocking {
        val pdfImport = PdfImport(notebooks, files)
        val made = pdfImport.import(folderId, "Slides") { pdf("Transmission lines", "Coaxial cable").inputStream() }
        val nb = (made as PdfImportResult.Success).notebookId
        val other = (notebooks.createNotebook(folderId, "Notes") as NotebookResult.Success).id
        files.write(other, notebooks.getPages(other).first().id, emptyList(), texts = listOf(box("coaxial cable notes")))

        indexer.run()
        val hits = store.search(listOf("coaxial"))
        assertEquals(2, hits.size)
        val pdfHit = hits.single { it.notebookId == nb }
        assertTrue(pdfHit.pdfText!!.contains("Transmission lines"))
        assertEquals(listOf(nb), store.search(listOf("coaxial"), notebookId = nb).map { it.notebookId })
        assertTrue("words in order, any case", store.search(listOf("TRANSMISSION", "cable")).isNotEmpty())
        // Search words are text, never SQL wildcards.
        assertTrue(store.search(listOf("%")).isEmpty())
    }

    @Test
    fun theSearchStore_isNeverInABackup() {
        // It lives in databases/search.db; backups take only noteapp.db and the notebook files.
        assertEquals("search.db", NoteTextStore.FILE_NAME)
        assertFalse(BackupPaths.isRestorable("db/search.db"))
        assertFalse(BackupPaths.isRestorable("databases/search.db"))
    }
}
