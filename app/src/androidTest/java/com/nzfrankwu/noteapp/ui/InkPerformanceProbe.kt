package com.nzfrankwu.noteapp.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.util.Log
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nzfrankwu.noteapp.domain.ink.InkStroke
import com.nzfrankwu.noteapp.domain.ink.InkTool
import com.nzfrankwu.noteapp.domain.ink.PageInkCodec
import com.nzfrankwu.noteapp.ui.notebook.InkBrushes
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Rough timings for a densely written page (logged as "InkPerf"; no pass/fail thresholds). */
@RunWith(AndroidJUnit4::class)
class InkPerformanceProbe {
    private fun densePage(strokes: Int, points: Int): List<InkStroke> {
        val random = Random(42)
        return List(strokes) { s ->
            val cx = 40f + random.nextFloat() * 500f
            val cy = 80f + random.nextFloat() * 700f
            InkStroke(
                "s$s", InkTool.PEN, 0xFF1B1C1E.toInt(), 1.5f,
                FloatArray(points) { cx + 8f * cos(it * 0.4f) + it * 0.3f },
                FloatArray(points) { cy + 8f * sin(it * 0.4f) },
                IntArray(points) { it * 8 },
                FloatArray(points) { 0.3f + 0.4f * random.nextFloat() },
                FloatArray(points) { InkStroke.NONE }, FloatArray(points) { InkStroke.NONE },
            )
        }
    }

    @Test
    fun timings() {
        val page = densePage(strokes = 1200, points = 40)
        var t = System.nanoTime()
        val bytes = PageInkCodec.encode(page)
        Log.w("InkPerf", "encode 1200 strokes: ${(System.nanoTime() - t) / 1_000_000} ms, ${bytes.size / 1024} KB")
        t = System.nanoTime()
        PageInkCodec.decode(bytes)
        Log.w("InkPerf", "decode: ${(System.nanoTime() - t) / 1_000_000} ms")
        t = System.nanoTime()
        val drawables = page.mapNotNull { InkBrushes.toDrawable(it) }
        Log.w("InkPerf", "build ${drawables.size} drawables: ${(System.nanoTime() - t) / 1_000_000} ms")

        // Hardware recording (what the screen does), off the main thread, twice.
        val hw = CanvasStrokeRenderer.create()
        val hint = Matrix().apply { setScale(2f, 2f) }
        repeat(3) { round ->
            val node = android.graphics.RenderNode("probe")
            node.setPosition(0, 0, 1200, 1700)
            t = System.nanoTime()
            val rc = node.beginRecording()
            drawables.forEach { hw.draw(rc, it, hint) }
            node.endRecording()
            Log.w("InkPerf", "hardware record round $round: ${(System.nanoTime() - t) / 1_000_000} ms (hw=${rc.isHardwareAccelerated})")
        }

        val bitmap = Bitmap.createBitmap(1200, 1700, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val renderer = CanvasStrokeRenderer.create()
        val m = Matrix().apply { setScale(2f, 2f) }
        canvas.concat(m)
        repeat(3) { round ->
            t = System.nanoTime()
            drawables.forEach { renderer.draw(canvas, it, m) }
            Log.w("InkPerf", "software draw round $round: ${(System.nanoTime() - t) / 1_000_000} ms")
        }
    }
}
