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

    // ---- 7.10: handwriting ----

    /** Reads each line as "line@<top>" (and counts calls); [ready] says whether it can read at all. */
    private class FakeReader(var ready: Boolean = true) : HandwritingReader {
        var reads = 0
        override suspend fun prepare() = ready
        override suspend fun read(line: com.nzfrankwu.noteapp.domain.ink.HandwritingLines.Line): String {
            reads++
            return if (line.top < 150f) "Smith chart notes" else "Fourier series"
        }
    }

    private fun letter(id: String, x: Float, y: Float) = com.nzfrankwu.noteapp.domain.ink.InkStroke(
        id, com.nzfrankwu.noteapp.domain.ink.InkTool.PEN, 0xFF000000.toInt(), 2f,
        floatArrayOf(x, x + 6f, x + 12f), floatArrayOf(y, y + 20f, y), intArrayOf(0, 8, 16),
        floatArrayOf(0.5f, 0.5f, 0.5f), floatArrayOf(-1f, -1f, -1f), floatArrayOf(-1f, -1f, -1f),
    )

    @Test
    fun handwriting_isReadLineByLine_keptWithItsBand_andReadAgainOnlyWhenThePageChanges() = runBlocking {
        val reader = FakeReader()
        val withHand = NoteTextIndexer(db, files, store, reader)
        val nb = (notebooks.createNotebook(folderId, "Lecture 8") as NotebookResult.Success).id
        val page = notebooks.getPages(nb).first().id
        files.write(nb, page, listOf(letter("a", 100f, 100f), letter("b", 120f, 100f), letter("c", 100f, 200f)), texts = listOf(box("typed")))

        assertEquals(1, withHand.run().handwritten)
        val hit = store.search(listOf("fourier")).single()
        assertEquals(page, hit.pageId)
        assertEquals("Smith chart notes\nFourier series", hit.handText)
        assertEquals("100.0,120.0;200.0,220.0", hit.handLines)
        assertEquals("typed", hit.boxText) // the text box is kept alongside
        assertEquals(2, reader.reads)

        // Nothing changed: nothing is read again.
        assertEquals(0, withHand.run().handwritten)
        assertEquals(2, reader.reads)

        // The page changes: its text boxes and handwriting are read again, the old words go.
        Thread.sleep(20)
        files.write(nb, page, listOf(letter("d", 100f, 300f)))
        withHand.run()
        assertTrue(store.search(listOf("smith")).isEmpty())
        assertTrue(store.search(listOf("typed")).isEmpty())
        assertEquals(1, store.search(listOf("fourier")).size)
    }

    @Test
    fun handwriting_waitsUntilTheReaderIsReady() = runBlocking {
        val reader = FakeReader(ready = false)
        val withHand = NoteTextIndexer(db, files, store, reader)
        val nb = (notebooks.createNotebook(folderId, "Lecture 9") as NotebookResult.Success).id
        files.write(nb, notebooks.getPages(nb).first().id, listOf(letter("a", 100f, 100f)))
        assertEquals(0, withHand.run().handwritten)
        assertTrue(store.search(listOf("smith")).isEmpty())

        reader.ready = true // e.g. the model finished downloading
        assertEquals(1, withHand.run().handwritten)
        assertEquals(1, store.search(listOf("smith")).size)
    }

    @Test
    fun theSearchStore_isNeverInABackup() {
        // It lives in databases/search.db; backups take only noteapp.db and the notebook files.
        assertEquals("search.db", NoteTextStore.FILE_NAME)
        assertFalse(BackupPaths.isRestorable("db/search.db"))
        assertFalse(BackupPaths.isRestorable("databases/search.db"))
    }
}
