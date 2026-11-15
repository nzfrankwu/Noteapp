package com.nzfrankwu.noteapp.domain.ink

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
 *
 * Changing the layout means a NEW version number, with [decode] still reading every older one.
 */
object PageInkCodec {
    const val VERSION = 1
    private val MAGIC = byteArrayOf('N'.code.toByte(), 'A'.code.toByte(), 'P'.code.toByte(), 'G'.code.toByte())

    private const val HAS_PRESSURE = 1
    private const val HAS_TILT = 2
    private const val HAS_ORIENTATION = 4

    fun encode(strokes: List<InkStroke>): ByteArray {
        val bytes = ByteArrayOutputStream()
        bytes.write(MAGIC)
        DataOutputStream(bytes).writeShort(VERSION)
        DataOutputStream(GZIPOutputStream(bytes)).use { out ->
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
        }
        return bytes.toByteArray()
    }

    /**
     * Reads a page file. Throws [PageFileException] if it isn't a page file, was written by a newer
     * app version, or is damaged (cut short or changed).
     */
    fun decode(data: ByteArray): List<InkStroke> {
        if (data.size < MAGIC.size + 2 || !data.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            throw PageFileException("Not a Noteapp page file")
        }
        val version = ((data[4].toInt() and 0xFF) shl 8) or (data[5].toInt() and 0xFF)
        if (version > VERSION) throw PageFileException("Saved by a newer version of Noteapp (page format $version)")
        if (version < 1) throw PageFileException("Unknown page format $version")
        try {
            DataInputStream(GZIPInputStream(ByteArrayInputStream(data, 6, data.size - 6))).use { input ->
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
                // Reading to the end makes gzip check its checksum, so a changed byte is caught.
                if (input.read() != -1) throw PageFileException("Unexpected data after the strokes")
                return strokes
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

    /** Sanity limits, so a damaged count can't make the app try to allocate gigabytes. */
    private const val MAX_STROKES = 1_000_000
    private const val MAX_POINTS = 10_000_000
}
