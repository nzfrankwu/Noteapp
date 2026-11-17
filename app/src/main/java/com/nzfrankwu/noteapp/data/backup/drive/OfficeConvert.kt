package com.nzfrankwu.noteapp.data.backup.drive

import com.nzfrankwu.noteapp.data.PdfImport
import com.nzfrankwu.noteapp.data.PdfImportResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.UUID

/** A Word or PowerPoint file that couldn't be turned into a PDF. The message is for the user. */
class ConvertException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Word and PowerPoint → PDF through the person's own Google Drive (PHASE-6.md 6.13, IDEA.md §2.9):
 * the file goes to `Noteapp/Converting/`, Drive turns it into Docs/Slides, Noteapp downloads that as
 * a PDF and deletes the Drive copy straight away. Then it's imported like any PDF.
 */
class OfficeConvert(
    private val drive: DriveApi,
    /** True when Google Drive is connected (Settings → Backup). */
    private val connected: suspend () -> Boolean,
    /** A scratch folder (in the cache). */
    private val scratch: File,
) {
    /** Turns [input] (a Word/PowerPoint file of type [sourceMime]) into the PDF [target]. Throws [ConvertException]. */
    suspend fun toPdf(input: File, sourceMime: String, target: File) {
        val googleMime = GOOGLE_TYPES[sourceMime] ?: throw ConvertException("Noteapp can't turn this kind of file into a PDF.")
        if (!connected()) {
            throw ConvertException("Noteapp turns Word and PowerPoint files into PDFs with your Google Drive. Connect it in Settings → Backup. $TIP")
        }
        var uploaded: DriveFile? = null
        try {
            val root = drive.folder(DriveBackup.ROOT_FOLDER, null)
            val temp = drive.folder(FOLDER, root.id)
            uploaded = drive.uploadToConvert(input, "Converting ${UUID.randomUUID()}", temp.id, sourceMime, googleMime)
            drive.exportPdf(uploaded.id, target)
            val start = withContext(Dispatchers.IO) {
                ByteArray(5).also { head -> target.inputStream().use { DataInputStreamCompat.readUpTo(it, head) } }
            }
            if (!start.contentEquals("%PDF-".toByteArray())) throw ConvertException("Google Drive couldn't turn this file into a PDF. $TIP")
        } catch (e: DriveException) {
            target.delete()
            throw ConvertException(messageFor(e), e)
        } catch (e: Exception) {
            target.delete()
            throw e
        } finally {
            // The copy in Drive was only for converting: it goes even if the person left the screen.
            uploaded?.let { file -> withContext(NonCancellable) { runCatching { drive.delete(file.id) } } }
        }
    }

    /**
     * Converts the file [open] gives and imports the PDF into [folderId] as a new notebook, named
     * after [displayName] without its extension.
     */
    suspend fun importInto(
        pdfImport: PdfImport,
        folderId: String,
        displayName: String?,
        sourceMime: String,
        open: () -> InputStream?,
    ): PdfImportResult {
        val dir = File(scratch, UUID.randomUUID().toString())
        return try {
            val input = File(dir, "input")
            withContext(Dispatchers.IO) {
                dir.mkdirs()
                val stream = open() ?: throw ConvertException("Noteapp couldn't read that file.")
                stream.use { s -> input.outputStream().use { s.copyTo(it) } }
            }
            val pdf = File(dir, "converted.pdf")
            toPdf(input, sourceMime, pdf)
            pdfImport.import(folderId, titleFrom(displayName)) { pdf.inputStream() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ConvertException) {
            PdfImportResult.Failure(e.message.orEmpty())
        } catch (e: java.io.IOException) {
            PdfImportResult.Failure("Noteapp couldn't read that file.")
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { dir.deleteRecursively() }
        }
    }

    private fun messageFor(e: DriveException): String = when {
        e.kind == DriveException.Kind.OFFLINE -> "Turning this file into a PDF needs the internet. $TIP"
        e.kind == DriveException.Kind.AUTH -> "Sign in to Google again (Settings → Backup) to turn this file into a PDF. $TIP"
        e.kind == DriveException.Kind.QUOTA -> "Your Google Drive is full, so this file couldn't be turned into a PDF. $TIP"
        e.reason == "exportSizeLimitExceeded" -> "This file is too big for Google Drive to turn into a PDF. $TIP"
        else -> "Google Drive couldn't turn this file into a PDF. $TIP"
    }

    companion object {
        /** Drive's temporary folder for conversions (inside My Drive/Noteapp). */
        const val FOLDER = "Converting"

        const val TIP = "Or open it in Word or PowerPoint, use Print → Save as PDF, and share the PDF to Noteapp."

        const val DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        const val DOC = "application/msword"
        const val PPTX = "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        const val PPT = "application/vnd.ms-powerpoint"

        private val GOOGLE_TYPES = mapOf(
            DOCX to "application/vnd.google-apps.document",
            DOC to "application/vnd.google-apps.document",
            PPTX to "application/vnd.google-apps.presentation",
            PPT to "application/vnd.google-apps.presentation",
        )
        private val EXTENSIONS = mapOf("docx" to DOCX, "doc" to DOC, "pptx" to PPTX, "ppt" to PPT)

        /** Every type that can be converted (for file pickers and "Open with"). */
        val MIME_TYPES: List<String> = GOOGLE_TYPES.keys.toList()

        /**
         * The Word/PowerPoint type of a file, from what the other app says it is or, failing that
         * (many say "application/octet-stream"), its name; null if it's neither.
         */
        fun officeType(mime: String?, displayName: String?): String? {
            if (mime != null && mime in GOOGLE_TYPES) return mime
            val ext = displayName?.substringAfterLast('.', "")?.lowercase() ?: return null
            return EXTENSIONS[ext]
        }

        /** The usual file extension for a Word/PowerPoint type ("docx", …). */
        fun extensionFor(officeType: String): String = EXTENSIONS.entries.first { it.value == officeType }.key

        /** "Week 3 slides.pptx" → "Week 3 slides". */
        fun titleFrom(displayName: String?): String {
            val name = displayName?.trim().orEmpty()
            val ext = name.substringAfterLast('.', "").lowercase()
            val bare = if (ext in EXTENSIONS) name.dropLast(ext.length + 1).trim() else name
            return PdfImport.titleFrom(bare)
        }
    }
}

/** Reads as much of [into] as the stream has (InputStream.readNBytes needs Android 13). */
private object DataInputStreamCompat {
    fun readUpTo(input: InputStream, into: ByteArray) {
        var off = 0
        while (off < into.size) {
            val n = input.read(into, off, into.size - off)
            if (n < 0) break
            off += n
        }
    }
}
