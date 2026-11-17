package com.nzfrankwu.noteapp.data.backup.drive

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** PHASE-6.md 6.12: readable PDF copies mirror the folders, and only changed notebooks are sent again. */
class PdfCopiesTest {
    @get:Rule val temp = TemporaryFolder()

    private val drive = FakeDrive()
    private val notebooks = LinkedHashMap<String, PdfCopies.Wanted>()
    private val deleted = HashSet<String>() // in Recently deleted
    private val exported = mutableListOf<String>()
    private val broken = HashSet<String>()

    private val source = object : PdfCopies.Source {
        override suspend fun wanted() = notebooks.values.toList()
        override suspend fun allIds() = notebooks.keys + deleted
        override suspend fun export(notebookId: String, out: File) {
            if (notebookId in broken) error("can't draw this one")
            exported += notebookId
            out.writeText("%PDF ${notebooks.getValue(notebookId).version}")
        }
    }
    private val copies by lazy { PdfCopies(drive, source, temp.newFolder("scratch")) }

    private fun sync() = runBlocking { copies.sync() }
    private fun add(id: String, title: String, vararg path: String, version: String = "v1") {
        notebooks[id] = PdfCopies.Wanted(id, title, path.toList(), version)
    }

    /** Where each copy is, as "Folder/Sub/Title.pdf". */
    private fun layout(): Set<String> {
        val pdfRoot = drive.folderId("Noteapp", "Notes (PDF)")!!
        fun pathOf(item: FakeDrive.Item): String {
            val parts = ArrayList<String>()
            var parent = item.parent
            while (parent != null && parent != pdfRoot) {
                val folder = drive.items.getValue(parent)
                parts.add(0, folder.name)
                parent = folder.parent
            }
            return (parts + item.name).joinToString("/")
        }
        return drive.items.values.filter { it.mimeType == PdfCopies.PDF_MIME }.map(::pathOf).toSet()
    }

    private fun folderExists(vararg path: String) = drive.folderId("Noteapp", "Notes (PDF)", *path) != null

    @Test
    fun firstSync_mirrorsTheFolders() {
        add("n1", "Lecture 1", "Electromagnetics")
        add("n2", "Lab 2", "Electromagnetics", "Labs")
        add("n3", "Shopping list")
        val result = sync()
        assertEquals(3, result.uploaded)
        assertEquals(setOf("Electromagnetics/Lecture 1.pdf", "Electromagnetics/Labs/Lab 2.pdf", "Shopping list.pdf"), layout())
    }

    @Test
    fun nothingChanged_nothingIsSent() {
        add("n1", "Lecture 1", "Electromagnetics")
        sync()
        exported.clear()
        drive.uploads.clear()
        val result = sync()
        assertEquals(PdfCopies.Result(0, 0, 0, 0), result)
        assertTrue(exported.isEmpty())
    }

    @Test
    fun anEditedNotebook_replacesItsCopy() {
        add("n1", "Lecture 1", "Electromagnetics")
        sync()
        val id = drive.items.values.single { it.mimeType == PdfCopies.PDF_MIME }.id
        add("n1", "Lecture 1", "Electromagnetics", version = "v2")
        sync()
        val copy = drive.items.values.single { it.mimeType == PdfCopies.PDF_MIME }
        assertEquals("same Drive file (links to it keep working)", id, copy.id)
        assertEquals("%PDF v2", copy.bytes.decodeToString())
        assertEquals("Drive's old versions go (they'd count against storage)", listOf(id), drive.revisionCleanups)
    }

    @Test
    fun aRenamedOrMovedNotebook_isMoved_notExportedAgain() {
        add("n1", "Lecture 1", "Electromagnetics")
        add("n2", "Lecture 2", "Electromagnetics")
        sync()
        exported.clear()
        add("n1", "Lecture 1 (Maxwell)", "Electromagnetics")
        add("n2", "Lecture 2", "Circuits")
        val result = sync()
        assertEquals(2, result.moved)
        assertTrue(exported.isEmpty())
        assertEquals(setOf("Electromagnetics/Lecture 1 (Maxwell).pdf", "Circuits/Lecture 2.pdf"), layout())
    }

    @Test
    fun aRenamedFolder_movesItsCopies_andTheOldFolderGoes() {
        add("n1", "Lecture 1", "ELECTENG 204", "Week 1")
        sync()
        add("n1", "Lecture 1", "Electromagnetics", "Week 1")
        sync()
        assertEquals(setOf("Electromagnetics/Week 1/Lecture 1.pdf"), layout())
        assertTrue(!folderExists("ELECTENG 204"))
    }

    @Test
    fun recentlyDeleted_keepsItsCopy_untilDeletedForGood() {
        add("n1", "Lecture 1", "Electromagnetics")
        sync()
        notebooks.remove("n1")
        deleted += "n1"
        sync()
        assertEquals(setOf("Electromagnetics/Lecture 1.pdf"), layout())
        deleted.clear()
        sync()
        assertTrue(layout().isEmpty())
        assertTrue(!folderExists("Electromagnetics"))
    }

    @Test
    fun aNotebookThatCantBeExported_doesntStopTheOthers() {
        add("n1", "Broken", "A")
        add("n2", "Fine", "A")
        broken += "n1"
        val result = sync()
        assertEquals(1, result.failed)
        assertEquals(setOf("A/Fine.pdf"), layout())
        assertNull(temp.root.resolve("scratch").listFiles()?.firstOrNull())
    }

    @Test
    fun aSecondCopyOfOneNotebook_isRemoved() {
        add("n1", "Lecture 1")
        sync()
        val pdfRoot = drive.folderId("Noteapp", "Notes (PDF)")!!
        val extra = temp.newFile("extra.pdf").apply { writeText("%PDF old") }
        runBlocking {
            drive.upload(extra, "Lecture 1.pdf", pdfRoot, PdfCopies.PDF_MIME, mapOf(PdfCopies.NOTEBOOK_ID to "n1", PdfCopies.VERSION to "v0"))
        }
        sync()
        assertEquals(1, drive.items.values.count { it.mimeType == PdfCopies.PDF_MIME })
    }
}
