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
    private fun outlineStrokes(word: String, size: Float = 60f, baseline: Float = 120f, idStart: Int = 0): List<InkStroke> {
        val path = Path()
        Paint().apply { textSize = size }.getTextPath(word, 0, word.length, 50f, baseline, path)
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
                "s${idStart + strokes.size}", InkTool.PEN, 0xFF000000.toInt(), 2f, xs, ys, IntArray(n) { it * 8 },
                FloatArray(n) { 0.5f }, FloatArray(n) { -1f }, FloatArray(n) { -1f },
            )
        } while (measure.nextContour())
        return strokes
    }

    private suspend fun readyReader(): MlKitHandwriting {
        val reader = MlKitHandwriting()
        val ready = kotlinx.coroutines.withTimeoutOrNull(120_000) {
            while (!reader.prepare()) kotlinx.coroutines.delay(2_000)
            true
        } == true
        assumeTrue("The handwriting model couldn't be downloaded here (no Wi-Fi or Play services)", ready)
        return reader
    }

    /**
     * A full page at the size real notes are (page units: letters ~14 tall, lines 24 apart):
     * three lines found (not one big blob), each read on its own.
     */
    @Test
    fun aMultiLinePage_atRealSize_isReadLineByLine() = runBlocking {
        val reader = readyReader()
        val words = listOf("hello world", "smith chart", "fourier series")
        val strokes = words.flatMapIndexed { i, w -> outlineStrokes(w, size = 14f, baseline = 100f + 24f * i, idStart = i * 1000) }
        val lines = HandwritingLines.group(strokes)
        assertTrue("3 lines, got ${lines.size}", lines.size == 3)
        // Font outlines aren't handwriting, so what's read isn't checked; only that each line is read.
        var before = ""
        for ((i, line) in lines.withIndex()) {
            val readings = reader.read(line, before)
            before = readings.firstOrNull().orEmpty()
            Log.i("MlKitHandwritingTest", "line $i: $readings")
            assertTrue("line $i read", readings.isNotEmpty())
        }
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
