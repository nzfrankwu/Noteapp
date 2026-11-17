package com.nzfrankwu.noteapp.data.backup.drive

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** PHASE-6.md 6.13: Word/PowerPoint → PDF through Drive, and the Drive copy never stays. */
class OfficeConvertTest {
    @get:Rule val temp = TemporaryFolder()

    private val drive = FakeDrive()
    private var connected = true
    private val convert by lazy { OfficeConvert(drive, { connected }, temp.newFolder("scratch")) }
    private val input by lazy { temp.newFile("slides.pptx").apply { writeText("pptx bytes") } }
    private val target by lazy { temp.root.resolve("out.pdf") }

    private fun googleFilesLeft() = drive.items.values.count { it.mimeType.startsWith("application/vnd.google-apps.") && it.mimeType != DriveClient.FOLDER }

    @Test
    fun aPresentation_becomesAPdf_andTheDriveCopyIsDeleted() {
        runBlocking { convert.toPdf(input, OfficeConvert.PPTX, target) }
        assertTrue(target.readText().startsWith("%PDF-"))
        assertEquals(0, googleFilesLeft())
        assertTrue(drive.folderId("Noteapp", "Converting") != null)
    }

    @Test
    fun notConnected_explainsHow_andSendsNothing() {
        connected = false
        val e = assertThrows(ConvertException::class.java) { runBlocking { convert.toPdf(input, OfficeConvert.PPTX, target) } }
        assertTrue(e.message!!, e.message!!.contains("Settings → Backup"))
        assertTrue(e.message!!, e.message!!.contains("Print → Save as PDF"))
        assertTrue(drive.items.isEmpty())
    }

    @Test
    fun aFileTooBigToExport_saysSo_andTheDriveCopyStillGoes() {
        drive.failConversion = DriveException(DriveException.Kind.OTHER, "Google Drive refused the request (403, exportSizeLimitExceeded).", reason = "exportSizeLimitExceeded")
        val e = assertThrows(ConvertException::class.java) { runBlocking { convert.toPdf(input, OfficeConvert.PPTX, target) } }
        assertTrue(e.message!!, e.message!!.contains("too big"))
        assertEquals(0, googleFilesLeft())
        assertFalse(target.exists())
    }

    @Test
    fun offline_saysItNeedsTheInternet() {
        drive.failConversion = DriveException(DriveException.Kind.OFFLINE, "Couldn't reach Google Drive.")
        val e = assertThrows(ConvertException::class.java) { runBlocking { convert.toPdf(input, OfficeConvert.DOCX, target) } }
        assertTrue(e.message!!, e.message!!.contains("needs the internet"))
    }

    @Test
    fun officeType_fromTheTypeOrTheName() {
        assertEquals(OfficeConvert.DOCX, OfficeConvert.officeType(OfficeConvert.DOCX, null))
        assertEquals(OfficeConvert.PPTX, OfficeConvert.officeType("application/octet-stream", "Week 3.PPTX"))
        assertEquals(OfficeConvert.DOC, OfficeConvert.officeType(null, "essay.doc"))
        assertNull(OfficeConvert.officeType("application/pdf", "Lecture.pdf"))
        assertNull(OfficeConvert.officeType(null, null))
        assertEquals("pptx", OfficeConvert.extensionFor(OfficeConvert.PPTX))
    }

    @Test
    fun titles_dropTheExtension() {
        assertEquals("Week 3 slides", OfficeConvert.titleFrom("Week 3 slides.pptx"))
        assertEquals("Essay draft", OfficeConvert.titleFrom("Essay draft.DOCX"))
    }
}
