package com.nzfrankwu.noteapp.data

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import com.nzfrankwu.noteapp.domain.PageSize
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** What opening a PDF found. */
sealed interface PdfInfo {
    /** It opened: one size per page, in points (1/72 inch, the same as page units). */
    data class Ok(val pageSizes: List<PageSize>) : PdfInfo
    data object PasswordProtected : PdfInfo
    /** Not a PDF, damaged, or without pages. */
    data object Broken : PdfInfo
}

/**
 * A part of a PDF page drawn as a picture: [left], [top], [right], [bottom] are in page units
 * (the whole page is 0, 0, width, height), at [scale] pixels per page unit.
 */
class PdfPicture(val bitmap: Bitmap, val left: Float, val top: Float, val right: Float, val bottom: Float, val scale: Float) {
    val isWholePage: Boolean get() = left == 0f && top == 0f
}

/**
 * Draws pages of imported PDFs with Android's built-in PdfRenderer (PHASE-3.md: no library).
 *
 * PdfRenderer isn't thread-safe and shows one page at a time, so everything runs on one
 * background thread of its own. A few files are kept open (the most recently used), and whole-page
 * pictures are kept in a small memory cache. [close] when the notebook closes.
 */
class PdfPages(cacheBytes: Int = DEFAULT_CACHE_BYTES) : Closeable {
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "pdf-pages").apply { isDaemon = true } }
    private val thread = executor.asCoroutineDispatcher()

    private class Opened(val fd: ParcelFileDescriptor, val renderer: PdfRenderer) : Closeable {
        override fun close() {
            runCatching { renderer.close() }
            runCatching { fd.close() }
        }
    }

    /** Open files, least recently used first (only touched on [thread]). */
    private val open = LinkedHashMap<String, Opened>(4, 0.75f, true)

    private val cache = object : LruCache<String, PdfPicture>(cacheBytes) {
        override fun sizeOf(key: String, value: PdfPicture) = value.bitmap.byteCount
    }

    @Volatile private var closed = false

    private fun opened(file: File): Opened {
        open[file.path]?.let { return it }
        val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = try {
            PdfRenderer(fd)
        } catch (e: Exception) {
            fd.close()
            throw e
        }
        val o = Opened(fd, renderer)
        open[file.path] = o
        while (open.size > MAX_OPEN_FILES) {
            val eldest = open.entries.first()
            open.remove(eldest.key)
            eldest.value.close()
        }
        return o
    }

    /**
     * The whole of page [pageIndex] of [file] at about [scale] pixels per page unit, made smaller
     * if needed so it stays within [MAX_SIDE_PX] and [MAX_PIXELS]. Null if it can't be drawn.
     */
    suspend fun wholePage(file: File, pageIndex: Int, size: PageSize, scale: Float): PdfPicture? {
        val s = wholePageScale(size, scale)
        val w = ceil(size.width * s).toInt().coerceAtLeast(1)
        val h = ceil(size.height * s).toInt().coerceAtLeast(1)
        val key = "${file.path}#$pageIndex#$w"
        cache.get(key)?.let { return it }
        return withContext(thread) {
            cache.get(key)?.let { return@withContext it }
            draw(file, pageIndex, w, h, s, 0f, 0f)?.let { bitmap ->
                PdfPicture(bitmap, 0f, 0f, size.width, size.height, s).also { cache.put(key, it) }
            }
        }
    }

    /** Just the part [left]..[right] × [top]..[bottom] (page units) of a page, at [scale]: for deep zoom. */
    suspend fun region(file: File, pageIndex: Int, left: Float, top: Float, right: Float, bottom: Float, scale: Float): PdfPicture? {
        val w = ceil((right - left) * scale).toInt()
        val h = ceil((bottom - top) * scale).toInt()
        if (w <= 0 || h <= 0 || w.toLong() * h > MAX_PIXELS * 2) return null
        return withContext(thread) {
            draw(file, pageIndex, w, h, scale, left, top)?.let { PdfPicture(it, left, top, right, bottom, scale) }
        }
    }

    /** Draws onto a new [w] × [h] bitmap, page point (x, y) landing at ((x − [left]) × [scale], (y − [top]) × [scale]). On [thread]. */
    private fun draw(file: File, pageIndex: Int, w: Int, h: Int, scale: Float, left: Float, top: Float): Bitmap? {
        if (closed) return null
        return try {
            val renderer = opened(file).renderer
            if (pageIndex !in 0 until renderer.pageCount) return null
            renderer.openPage(pageIndex).use { page ->
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                // The matrix maps the page's points to the bitmap's pixels.
                val matrix = Matrix().apply {
                    setScale(scale, scale)
                    postTranslate(-left * scale, -top * scale)
                }
                page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        } catch (e: Exception) {
            // A missing or damaged file, or out of memory: the page shows without its PDF.
            null
        } catch (e: OutOfMemoryError) {
            cache.evictAll()
            null
        }
    }

    /** Forgets the cached pictures (e.g. when memory is low). */
    fun trimMemory() = cache.evictAll()

    override fun close() {
        closed = true
        cache.evictAll()
        executor.execute {
            open.values.forEach { it.close() }
            open.clear()
        }
        executor.shutdown()
    }

    companion object {
        /** Whole-page pictures kept in memory. */
        const val DEFAULT_CACHE_BYTES = 64 * 1024 * 1024

        /** Largest whole-page picture: beyond this, deep zoom draws just the part on screen. */
        const val MAX_SIDE_PX = 3000
        const val MAX_PIXELS = 6_000_000L

        private const val MAX_OPEN_FILES = 3

        /** The scale a whole-page picture is drawn at for a wanted [scale] (capped by size). */
        fun wholePageScale(size: PageSize, scale: Float): Float {
            val bySide = MAX_SIDE_PX / max(size.width, size.height)
            val byPixels = sqrt(MAX_PIXELS / (size.width.toDouble() * size.height)).toFloat()
            return min(scale, min(bySide, byPixels)).coerceAtLeast(0.05f)
        }

        /**
         * Opens [file] and reads its page sizes. Blocking: call it off the main thread. Never
         * throws: a password-protected or broken file says so instead.
         */
        fun readInfo(file: File): PdfInfo = try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    if (renderer.pageCount == 0) return PdfInfo.Broken
                    PdfInfo.Ok(
                        List(renderer.pageCount) { i ->
                            renderer.openPage(i).use { PageSize(it.width.toFloat(), it.height.toFloat()) }
                        },
                    )
                }
            }
        } catch (e: SecurityException) {
            PdfInfo.PasswordProtected
        } catch (e: IOException) {
            PdfInfo.Broken
        } catch (e: RuntimeException) {
            PdfInfo.Broken
        }

        /** Draws one whole page right away, for tile pictures. Blocking; null if it can't. */
        fun renderOnce(file: File, pageIndex: Int, widthPx: Int, heightPx: Int): Bitmap? = try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    if (pageIndex !in 0 until renderer.pageCount) return null
                    renderer.openPage(pageIndex).use { page ->
                        Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888).also {
                            page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
