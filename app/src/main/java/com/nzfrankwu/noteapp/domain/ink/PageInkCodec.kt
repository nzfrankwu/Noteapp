package com.nzfrankwu.noteapp.domain.ink

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipException

/** A page file that can't be read. It's never overwritten, so nothing is lost. */
class PageFileException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** Everything on one page: its strokes, (from format 2) its pictures and (from format 3) its text boxes. */
data class PageContent(
    val strokes: List<InkStroke>,
    val images: List<InkImage> = emptyList(),
    val texts: List<InkText> = emptyList(),
)

/**
 * The page file format: how one page's strokes are saved (IDEA.md §5a, "store a format/version
 * number with saved data"). It's our own simple format, not Jetpack Ink's, so saved notes stay
 * readable whatever drawing library the app uses later.
 *
 * Layout:
 *  - 4 bytes "NAPG" (marks a Noteapp page file)
 *  - 2 bytes: format version ([VERSION])
 *  - the rest is gzip-compressed, which also adds a checksum: a damaged file is detected, never
 *    half-read. Inside: the stroke count, then each stroke (ID, tool key, colour, size, input
 *    tool, point count, which optional values it has, then its point arrays).
 *  - Version 2 (Phase 3) adds, after the strokes, the picture count and each picture (ID, file
 *    name, left, top, width, height, rotation). It's written ONLY when a page has pictures, so a
 *    page of handwriting alone is still saved exactly as version 1, byte for byte.
 *  - Version 3 (Phase 5) adds, after the pictures (their count may be 0), the text box count and
 *    each box (ID, the text as a length + UTF-8 bytes, left, top, width, height, size, colour,
 *    flags: 1 = bold). It's written ONLY when a page has text boxes, so every other page is saved
 *    exactly as before.
 *
 * Changing the layout means a NEW version number, with [decode] still reading every older one.
 */
object PageInkCodec {
    /** The newest format this app reads and writes. */
    const val VERSION = 3

    /** The format of a page without pictures or text boxes. */
    const val VERSION_STROKES_ONLY = 1

    /** The format of a page with pictures but no text boxes. */
    const val VERSION_PICTURES = 2
    private val MAGIC = byteArrayOf('N'.code.toByte(), 'A'.code.toByte(), 'P'.code.toByte(), 'G'.code.toByte())

    private const val HAS_PRESSURE = 1
    private const val HAS_TILT = 2
    private const val HAS_ORIENTATION = 4

    fun encode(content: PageContent): ByteArray = encode(content.strokes, content.images, content.texts)

    fun encode(strokes: List<InkStroke>, images: List<InkImage> = emptyList(), texts: List<InkText> = emptyList()): ByteArray {
        val version = when {
            texts.isNotEmpty() -> VERSION
            images.isNotEmpty() -> VERSION_PICTURES
            else -> VERSION_STROKES_ONLY
        }
        val bytes = ByteArrayOutputStream()
        bytes.write(MAGIC)
        DataOutputStream(bytes).writeShort(version)
        // Buffered: writing floats one byte at a time straight into gzip is very slow.
        DataOutputStream(BufferedOutputStream(GZIPOutputStream(bytes, BUFFER), BUFFER)).use { out ->
            out.writeInt(strokes.size)
            for (s in strokes) {
                out.writeUTF(s.id)
                out.writeUTF(s.tool.key)
                out.writeInt(s.colorArgb)
                out.writeFloat(s.size)
                out.writeByte(s.inputTool)
                val n = s.pointCount
                out.writeInt(n)
                val flags = (if (s.pressure.any { it != InkStroke.NONE }) HAS_PRESSURE else 0) or
                    (if (s.tilt.any { it != InkStroke.NONE }) HAS_TILT else 0) or
                    (if (s.orientation.any { it != InkStroke.NONE }) HAS_ORIENTATION else 0)
                out.writeByte(flags)
                for (i in 0 until n) out.writeFloat(s.xs[i])
                for (i in 0 until n) out.writeFloat(s.ys[i])
                for (i in 0 until n) out.writeInt(s.times[i])
                if (flags and HAS_PRESSURE != 0) for (i in 0 until n) out.writeFloat(s.pressure[i])
                if (flags and HAS_TILT != 0) for (i in 0 until n) out.writeFloat(s.tilt[i])
                if (flags and HAS_ORIENTATION != 0) for (i in 0 until n) out.writeFloat(s.orientation[i])
            }
            if (version >= 2) {
                out.writeInt(images.size)
                for (img in images) {
                    out.writeUTF(img.id)
                    out.writeUTF(img.asset)
                    out.writeFloat(img.left)
                    out.writeFloat(img.top)
                    out.writeFloat(img.width)
                    out.writeFloat(img.height)
                    out.writeFloat(img.rotation)
                }
            }
            if (version >= 3) {
                out.writeInt(texts.size)
                for (t in texts) {
                    out.writeUTF(t.id)
                    val utf8 = t.text.toByteArray(Charsets.UTF_8)
                    out.writeInt(utf8.size)
                    out.write(utf8)
                    out.writeFloat(t.left)
                    out.writeFloat(t.top)
                    out.writeFloat(t.width)
                    out.writeFloat(t.height)
                    out.writeFloat(t.size)
                    out.writeInt(t.colorArgb)
                    out.writeByte(if (t.bold) TEXT_BOLD else 0)
                }
            }
        }
        return bytes.toByteArray()
    }

    /** Reads just the strokes of a page file (see [decodePage]). */
    fun decode(data: ByteArray): List<InkStroke> = decodePage(data).strokes

    /**
     * Reads a page file: strokes and pictures. Throws [PageFileException] if it isn't a page file,
     * was written by a newer app version, or is damaged (cut short or changed).
     */
    fun decodePage(data: ByteArray): PageContent {
        if (data.size < MAGIC.size + 2 || !data.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            throw PageFileException("Not a Noteapp page file")
        }
        val version = ((data[4].toInt() and 0xFF) shl 8) or (data[5].toInt() and 0xFF)
        if (version > VERSION) throw PageFileException("Saved by a newer version of Noteapp (page format $version)")
        if (version < 1) throw PageFileException("Unknown page format $version")
        try {
            DataInputStream(BufferedInputStream(GZIPInputStream(ByteArrayInputStream(data, 6, data.size - 6), BUFFER), BUFFER)).use { input ->
                val count = input.readInt()
                if (count < 0 || count > MAX_STROKES) throw PageFileException("Bad stroke count $count")
                val strokes = ArrayList<InkStroke>(count)
                repeat(count) {
                    val id = input.readUTF()
                    val tool = InkTool.fromKey(input.readUTF())
                    val color = input.readInt()
                    val size = input.readFloat()
                    val inputTool = input.readUnsignedByte()
                    val n = input.readInt()
                    if (n < 0 || n > MAX_POINTS) throw PageFileException("Bad point count $n")
                    val flags = input.readUnsignedByte()
                    val xs = FloatArray(n) { input.readFloat() }
                    val ys = FloatArray(n) { input.readFloat() }
                    val times = IntArray(n) { input.readInt() }
                    val pressure = if (flags and HAS_PRESSURE != 0) FloatArray(n) { input.readFloat() } else FloatArray(n) { InkStroke.NONE }
                    val tilt = if (flags and HAS_TILT != 0) FloatArray(n) { input.readFloat() } else FloatArray(n) { InkStroke.NONE }
                    val orientation = if (flags and HAS_ORIENTATION != 0) FloatArray(n) { input.readFloat() } else FloatArray(n) { InkStroke.NONE }
                    strokes.add(InkStroke(id, tool, color, size, xs, ys, times, pressure, tilt, orientation, inputTool))
                }
                val images = if (version >= 2) {
                    val imageCount = input.readInt()
                    if (imageCount < 0 || imageCount > MAX_IMAGES) throw PageFileException("Bad picture count $imageCount")
                    List(imageCount) {
                        InkImage(
                            id = input.readUTF(), asset = input.readUTF(),
                            left = input.readFloat(), top = input.readFloat(),
                            width = input.readFloat(), height = input.readFloat(), rotation = input.readFloat(),
                        )
                    }
                } else {
                    emptyList()
                }
                val texts = if (version >= 3) {
                    val textCount = input.readInt()
                    if (textCount < 0 || textCount > MAX_TEXTS) throw PageFileException("Bad text box count $textCount")
                    List(textCount) {
                        val id = input.readUTF()
                        val length = input.readInt()
                        if (length < 0 || length > MAX_TEXT_BYTES) throw PageFileException("Bad text length $length")
                        val bytes = ByteArray(length).also { input.readFully(it) }
                        InkText(
                            id = id, text = String(bytes, Charsets.UTF_8),
                            left = input.readFloat(), top = input.readFloat(),
                            width = input.readFloat(), height = input.readFloat(),
                            size = input.readFloat(), colorArgb = input.readInt(),
                            bold = input.readUnsignedByte() and TEXT_BOLD != 0,
                        )
                    }
                } else {
                    emptyList()
                }
                // Reading to the end makes gzip check its checksum, so a changed byte is caught.
                if (input.read() != -1) throw PageFileException("Unexpected data after the page")
                return PageContent(strokes, images, texts)
            }
        } catch (e: PageFileException) {
            throw e
        } catch (e: EOFException) {
            throw PageFileException("Page file is cut short", e)
        } catch (e: ZipException) {
            throw PageFileException("Page file is damaged", e)
        } catch (e: IOException) {
            throw PageFileException("Page file can't be read: ${e.message}", e)
        }
    }

    private const val BUFFER = 64 * 1024

    /** Sanity limits, so a damaged count can't make the app try to allocate gigabytes. */
    private const val MAX_STROKES = 1_000_000
    private const val MAX_POINTS = 10_000_000
    private const val MAX_IMAGES = 100_000
    private const val MAX_TEXTS = 100_000

    /** Room for [InkText.MAX_LENGTH] characters at up to 4 UTF-8 bytes each. */
    private const val MAX_TEXT_BYTES = 4 * InkText.MAX_LENGTH

    private const val TEXT_BOLD = 1
}
