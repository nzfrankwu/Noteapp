package com.nzfrankwu.noteapp.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nzfrankwu.noteapp.domain.ink.InkStroke
import com.nzfrankwu.noteapp.domain.ink.InkTool
import com.nzfrankwu.noteapp.ui.notebook.InkBrushes
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 7.12 (the user, 18 Nov: Full and Medium "slightly more emphasised, like a real pen"): the v2
 * pens are thinner than the v1 pens when pressing lightly and thicker when pressing hard. The
 * emulator's pen only presses at full pressure, so this measures strokes with set pressures.
 */
@RunWith(AndroidJUnit4::class)
class PenPressureTest {
    private fun line(tool: InkTool, y: Float, pressure: (Float) -> Float): InkStroke {
        val n = 40
        val xs = FloatArray(n) { 20f + it * 10f }
        val ys = FloatArray(n) { y }
        val ts = IntArray(n) { it * 8 }
        val ps = FloatArray(n) { pressure(it / (n - 1f)) }
        val none = FloatArray(n) { InkStroke.NONE }
        return InkStroke("s-$tool-$y", tool, Color.BLACK, 4f, xs, ys, ts, ps, none, none.copyOf())
    }

    /** How thick the stroke is (its shape's height; the line is horizontal). */
    private fun thickness(tool: InkTool, pressure: Float): Float {
        val box = InkBrushes.toDrawable(line(tool, 50f) { pressure })!!.shape.computeBoundingBox()!!
        return box.yMax - box.yMin
    }

    @Test
    fun v2Pens_areThinnerWhenLight_andThickerWhenHard() {
        for ((old, new) in listOf(InkTool.PEN to InkTool.PEN_PRESSURE_FULL_V2, InkTool.PEN_PRESSURE_MID to InkTool.PEN_PRESSURE_MID_V2)) {
            val light = 0.15f
            val hard = 0.95f
            assertTrue("$new thinner than $old when light", thickness(new, light) < thickness(old, light))
            assertTrue("$new thicker than $old when hard", thickness(new, hard) > thickness(old, hard))
        }
        // Full responds to pressure at least as much as Medium.
        val fullRange = thickness(InkTool.PEN_PRESSURE_FULL_V2, 0.95f) / thickness(InkTool.PEN_PRESSURE_FULL_V2, 0.15f)
        val midRange = thickness(InkTool.PEN_PRESSURE_MID_V2, 0.95f) / thickness(InkTool.PEN_PRESSURE_MID_V2, 0.15f)
        assertTrue("Full ($fullRange) responds at least as much as Medium ($midRange)", fullRange >= midRange)
        drawComparison()
    }

    /** A picture for the check-in: each pen pressing lightly → hard along the line. */
    private fun drawComparison() {
        val tools = listOf(InkTool.PEN_PRESSURE_MID, InkTool.PEN_PRESSURE_MID_V2, InkTool.PEN, InkTool.PEN_PRESSURE_FULL_V2)
        val names = listOf("Medium (before)", "Medium (new)", "Full (before)", "Full (new)")
        val zoom = 3f
        val bitmap = Bitmap.createBitmap((440 * zoom).toInt(), ((30 + tools.size * 50) * zoom).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap).apply { drawColor(Color.WHITE) }
        val renderer = CanvasStrokeRenderer.create()
        val scale = Matrix().apply { setScale(zoom, zoom) }
        val label = android.graphics.Paint().apply { color = Color.DKGRAY; textSize = 11f * zoom; isAntiAlias = true }
        tools.forEachIndexed { i, tool ->
            val y = 40f + i * 50f
            canvas.drawText("${names[i]}: light → hard", 20f * zoom, (y - 14f) * zoom, label)
            canvas.save()
            canvas.scale(zoom, zoom)
            renderer.draw(canvas, InkBrushes.toDrawable(line(tool, y) { it })!!, scale)
            canvas.restore()
        }
        val dir = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null) ?: return
        File(dir, "pen-pressure.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
