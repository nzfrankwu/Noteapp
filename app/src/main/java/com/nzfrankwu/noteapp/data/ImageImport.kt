package com.nzfrankwu.noteapp.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Build
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.roundToInt

/** A picture saved into a notebook's folder: its file name and its size in pixels. */
data class ImportedImage(val asset: String, val widthPx: Int, val heightPx: Int)

/**
 * Brings pictures into a notebook (PHASE-3.md 3.5): decodes them (JPEG, PNG, WebP, HEIC…),
 * turns them upright as the camera recorded (EXIF), shrinks them so the longest side is at most
 * [MAX_SIDE_PX], and saves a copy in the notebook's folder: JPEG 90, or PNG when the picture has
 * see-through parts. The original file is never linked to, so the picture stays even if it's
 * deleted from the gallery.
 */
class ImageImport(
    private val files: NotebookFiles,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    /** Saves the picture [open] reads into notebook [notebookId]. Null if it can't be read. */
    suspend fun import(notebookId: String, open: () -> InputStream?): ImportedImage? = withContext(io) {
        val bytes = try {
            open()?.use { input -> readLimited(input) }
        } catch (e: Exception) {
            null
        } ?: return@withContext null
        val bitmap = decode(bytes) ?: return@withContext null
        try {
            save(notebookId, bitmap)
        } catch (e: Exception) {
            null
        } finally {
            bitmap.recycle()
        }
    }

    /** Saves an already-made picture (e.g. a screenshot snip) into the notebook. */
    suspend fun importBitmap(notebookId: String, bitmap: Bitmap): ImportedImage? = withContext(io) {
        val scaled = shrink(bitmap)
        try {
            save(notebookId, scaled)
        } catch (e: Exception) {
            null
        } finally {
            if (scaled !== bitmap) scaled.recycle()
        }
    }

    private fun save(notebookId: String, bitmap: Bitmap): ImportedImage {
        val png = bitmap.hasAlpha() && hasSeeThroughPixels(bitmap)
        val name = files.newImageName(if (png) "png" else "jpg")
        val out = ByteArrayOutputStream()
        if (png) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        } else {
            // JPEG can't hold see-through parts; draw onto white first if the bitmap has alpha.
            val opaque = if (bitmap.hasAlpha()) onWhite(bitmap) else bitmap
            opaque.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            if (opaque !== bitmap) opaque.recycle()
        }
        files.writeAsset(notebookId, name, ByteArrayInputStream(out.toByteArray()))
        return ImportedImage(name, bitmap.width, bitmap.height)
    }

    companion object {
        /** The longest side of a saved picture, in pixels (sharp on a tablet, small on disk). */
        const val MAX_SIDE_PX = 2560

        /** The largest file read (a huge file is more likely a mistake than a photo). */
        private const val MAX_BYTES = 80L * 1024 * 1024
        private const val JPEG_QUALITY = 90

        private fun readLimited(input: InputStream): ByteArray {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                total += n
                if (total > MAX_BYTES) throw java.io.IOException("Picture too large")
                out.write(buffer, 0, n)
            }
            return out.toByteArray()
        }

        /** Decodes [bytes] upright and no bigger than [MAX_SIDE_PX], or null if it isn't a picture. */
        fun decode(bytes: ByteArray): Bitmap? = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) decodeWithImageDecoder(bytes) else decodeWithBitmapFactory(bytes)
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }

        @androidx.annotation.RequiresApi(Build.VERSION_CODES.P)
        private fun decodeWithImageDecoder(bytes: ByteArray): Bitmap {
            // ImageDecoder turns the picture upright itself (EXIF) and reads HEIC too.
            val source = ImageDecoder.createSource(ByteBuffer.wrap(bytes))
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE // so it can be saved
                val w = info.size.width
                val h = info.size.height
                val longest = max(w, h)
                if (longest > MAX_SIDE_PX) {
                    val f = MAX_SIDE_PX.toFloat() / longest
                    decoder.setTargetSize((w * f).roundToInt().coerceAtLeast(1), (h * f).roundToInt().coerceAtLeast(1))
                }
            }
        }

        private fun decodeWithBitmapFactory(bytes: ByteArray): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE_PX) sample *= 2
            val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
            val upright = uprightByExif(raw, bytes)
            return shrink(upright).also { if (it !== upright && upright !== raw) upright.recycle() }
        }

        private fun uprightByExif(bitmap: Bitmap, bytes: ByteArray): Bitmap {
            val orientation = try {
                ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } catch (e: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }
            val m = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
                else -> return bitmap
            }
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true).also { if (it !== bitmap) bitmap.recycle() }
        }

        /** [bitmap] made smaller if its longest side is over [MAX_SIDE_PX] (else the same bitmap). */
        fun shrink(bitmap: Bitmap): Bitmap {
            val longest = max(bitmap.width, bitmap.height)
            if (longest <= MAX_SIDE_PX) return bitmap
            val f = MAX_SIDE_PX.toFloat() / longest
            return Bitmap.createScaledBitmap(bitmap, (bitmap.width * f).roundToInt().coerceAtLeast(1), (bitmap.height * f).roundToInt().coerceAtLeast(1), true)
        }

        /** True if any pixel is (partly) see-through. Checks a grid of pixels for speed. */
        private fun hasSeeThroughPixels(bitmap: Bitmap): Boolean {
            val stepX = max(1, bitmap.width / 200)
            val stepY = max(1, bitmap.height / 200)
            var y = 0
            while (y < bitmap.height) {
                var x = 0
                while (x < bitmap.width) {
                    if ((bitmap.getPixel(x, y) ushr 24) < 0xFF) return true
                    x += stepX
                }
                y += stepY
            }
            return false
        }

        private fun onWhite(bitmap: Bitmap): Bitmap {
            val out = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            android.graphics.Canvas(out).apply {
                drawColor(android.graphics.Color.WHITE)
                drawBitmap(bitmap, 0f, 0f, null)
            }
            return out
        }

        /**
         * Reads a saved picture for the screen, shrunk (by whole powers of two) so its longest side
         * is about [maxSidePx]. Null if it can't be read.
         */
        fun loadForScreen(file: java.io.File, maxSidePx: Int): Bitmap? = try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSidePx) sample *= 2
            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }
}
