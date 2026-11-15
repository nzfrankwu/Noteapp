package com.nzfrankwu.noteapp.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File

/** Bringing pictures into a notebook (PHASE-3.md 3.5), with real pictures made on the device. */
@RunWith(AndroidJUnit4::class)
class ImageImportTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(context.cacheDir, "image-import-test").apply { deleteRecursively(); mkdirs() }
    private val files = NotebookFiles(root)
    private val importer = ImageImport(files)

    @After
    fun tearDown() { root.deleteRecursively() }

    private fun bytes(bitmap: Bitmap, format: Bitmap.CompressFormat) =
        ByteArrayOutputStream().also { bitmap.compress(format, 95, it) }.toByteArray()

    private fun saved(image: ImportedImage) = BitmapFactory.decodeFile(files.assetFile("nb", image.asset).path)

    @Test
    fun aPictureWithSeeThroughParts_staysAPng() = runBlocking {
        val disc = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888) // all see-through
        android.graphics.Canvas(disc).drawCircle(150f, 100f, 80f, android.graphics.Paint().apply { color = Color.RED })
        val image = importer.import("nb") { bytes(disc, Bitmap.CompressFormat.PNG).inputStream() }!!
        assertTrue(image.asset, image.asset.startsWith("images/") && image.asset.endsWith(".png"))
        assertEquals(300, image.widthPx)
        assertEquals(200, image.heightPx)
        assertEquals(0, Color.alpha(saved(image).getPixel(5, 5)))
    }

    @Test
    fun aBigPhoto_isShrunk_andSavedAsJpeg() = runBlocking {
        val photo = Bitmap.createBitmap(4000, 3000, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        val image = importer.import("nb") { bytes(photo, Bitmap.CompressFormat.JPEG).inputStream() }!!
        assertTrue(image.asset.endsWith(".jpg"))
        assertEquals(ImageImport.MAX_SIDE_PX, image.widthPx)
        assertEquals(1920, image.heightPx)
        val back = saved(image)
        assertEquals(ImageImport.MAX_SIDE_PX, back.width)
    }

    @Test
    fun aSidewaysCameraPhoto_isTurnedUpright() = runBlocking {
        // 400 wide × 200 high as stored, with EXIF saying "turn 90°": upright it's 200 × 400.
        val stored = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        val file = File(root, "sideways.jpg").apply { writeBytes(bytes(stored, Bitmap.CompressFormat.JPEG)) }
        ExifInterface(file.path).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        val image = importer.import("nb") { file.inputStream() }!!
        assertEquals(200, image.widthPx)
        assertEquals(400, image.heightPx)
    }

    @Test
    fun somethingThatIsntAPicture_isRefused_andLeavesNothing() = runBlocking {
        assertNull(importer.import("nb") { "not a picture".byteInputStream() })
        assertNull(importer.import("nb") { null })
        val dir = File(File(File(root, NotebookFiles.DIR), "nb"), "images")
        assertTrue(dir.list().isNullOrEmpty())
    }

    @Test
    fun aSavedPicture_loadsShrunkForTheScreen() = runBlocking {
        val photo = Bitmap.createBitmap(2400, 1200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.YELLOW) }
        val image = importer.import("nb") { bytes(photo, Bitmap.CompressFormat.JPEG).inputStream() }!!
        val small = ImageImport.loadForScreen(files.assetFile("nb", image.asset), 600)
        assertNotNull(small)
        assertTrue(small!!.width in 600..1199)
    }
}
