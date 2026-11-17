package com.nzfrankwu.noteapp.data.search

import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nzfrankwu.noteapp.domain.ink.HandwritingLines
import com.nzfrankwu.noteapp.domain.ink.InkStroke
import com.nzfrankwu.noteapp.domain.ink.InkTool
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PHASE-7.md 7.10: the real ML Kit recogniser on the device. Needs the English model, which it
 * downloads (Wi-Fi) the first time; skipped (not failed) when that isn't possible.
 *
 * The "writing" is a word's letter outlines turned into pen strokes, so recognition isn't
 * expected to be perfect; this checks the whole path works and gives text back.
 */
@RunWith(AndroidJUnit4::class)
class MlKitHandwritingTest {
    private fun outlineStrokes(word: String): List<InkStroke> {
        val path = Path()
        Paint().apply { textSize = 60f }.getTextPath(word, 0, word.length, 50f, 120f, path)
        val measure = PathMeasure(path, false)
        val strokes = ArrayList<InkStroke>()
        val pos = FloatArray(2)
        do {
            val length = measure.length
            if (length < 2f) continue
            val n = (length / 3f).toInt().coerceIn(2, 200)
            val xs = FloatArray(n)
            val ys = FloatArray(n)
            for (i in 0 until n) {
                measure.getPosTan(length * i / (n - 1), pos, null)
                xs[i] = pos[0]
                ys[i] = pos[1]
            }
            strokes += InkStroke(
                "s${strokes.size}", InkTool.PEN, 0xFF000000.toInt(), 2f, xs, ys, IntArray(n) { it * 8 },
                FloatArray(n) { 0.5f }, FloatArray(n) { -1f }, FloatArray(n) { -1f },
            )
        } while (measure.nextContour())
        return strokes
    }

    @Test
    fun theRecogniser_downloadsItsModel_andReadsALine() = runBlocking {
        val reader = MlKitHandwriting()
        // prepare() asks for the download and returns at once: wait up to 2 minutes for it.
        val ready = kotlinx.coroutines.withTimeoutOrNull(120_000) {
            while (!reader.prepare()) kotlinx.coroutines.delay(2_000)
            true
        } == true
        assumeTrue("The handwriting model couldn't be downloaded here (no Wi-Fi or Play services)", ready)
        val line = HandwritingLines.group(outlineStrokes("hello")).single()
        val readings = reader.read(line)
        Log.i("MlKitHandwritingTest", "read: $readings")
        assertTrue("some readings", readings.isNotEmpty())
    }
}
