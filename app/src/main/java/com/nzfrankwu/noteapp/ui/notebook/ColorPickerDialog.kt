package com.nzfrankwu.noteapp.ui.notebook

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.nzfrankwu.noteapp.domain.ink.ColorHex
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * Pick any pen colour (PHASE-2.md F10): a colour wheel (around = hue, outwards = how strong), a
 * brightness slider, and a hex box (#RRGGBB) that follows the wheel and can be typed into.
 */
@Composable
fun ColorPickerDialog(initial: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val start = remember(initial) { FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) } }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var sat by remember { mutableFloatStateOf(start[1]) }
    var value by remember { mutableFloatStateOf(start[2]) }
    val color = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))
    var hexText by remember { mutableStateOf(ColorHex.format(initial)) }
    var hexError by remember { mutableStateOf(false) }

    fun fromWheel(h: Float, s: Float, v: Float = value) {
        hue = h; sat = s; value = v
        hexText = ColorHex.format(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)))
        hexError = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pick a colour") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                // Picking a colour on the wheel while it's nearly black brings the brightness back up,
                // so the pick is actually visible.
                HueSatWheel(hue, sat, Modifier.size(220.dp).testTag("colour-wheel")) { h, s ->
                    fromWheel(h, s, if (value < 0.25f) 1f else value)
                }
                Column(Modifier.width(260.dp)) {
                    Text("Brightness", style = MaterialTheme.typography.bodyMedium)
                    Slider(value = value, onValueChange = { fromWheel(hue, sat, it) }, modifier = Modifier.height(32.dp))
                }
                Row(Modifier.width(260.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = hexText,
                        onValueChange = { text ->
                            hexText = text
                            val parsed = ColorHex.parse(text)
                            hexError = parsed == null
                            if (parsed != null) {
                                val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(parsed, it) }
                                hue = hsv[0]; sat = hsv[1]; value = hsv[2]
                            }
                        },
                        label = { Text("Hex") },
                        isError = hexError,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier.weight(1f).testTag("colour-hex"),
                    )
                    // Before → after.
                    Row(Modifier.border(1.dp, Color.Black.copy(alpha = 0.15f), RoundedCornerShape(8.dp))) {
                        Box(Modifier.size(28.dp, 40.dp).background(Color(initial), RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)))
                        Box(Modifier.size(28.dp, 40.dp).background(Color(color), RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(color) }, enabled = !hexError) { Text("Use colour") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * The colour wheel: hue around the circle, saturation from the middle out. Always drawn at full
 * brightness so the colours can be seen; the Brightness slider sets the final shade.
 */
@Composable
private fun HueSatWheel(hue: Float, sat: Float, modifier: Modifier, onChange: (Float, Float) -> Unit) {
    fun pick(p: Offset, w: Float, h: Float) {
        val cx = w / 2; val cy = h / 2; val r = min(w, h) / 2
        val dx = p.x - cx; val dy = p.y - cy
        val angle = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
        onChange((angle + 360f) % 360f, (hypot(dx, dy) / r).coerceIn(0f, 1f))
    }
    val hues = remember { (0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) } }
    Canvas(
        modifier
            .pointerInput(Unit) { detectTapGestures { pick(it, size.width.toFloat(), size.height.toFloat()) } }
            .pointerInput(Unit) {
                detectDragGestures { change, _ -> pick(change.position, size.width.toFloat(), size.height.toFloat()) }
            },
    ) {
        val r = min(size.width, size.height) / 2
        // Colours around (a sweep), fading to white in the middle.
        drawCircle(Brush.sweepGradient(hues, center), r)
        drawCircle(Brush.radialGradient(listOf(Color.White, Color.White.copy(alpha = 0f)), center, r), r)
        // Where the current colour sits.
        val rad = Math.toRadians(hue.toDouble())
        val at = Offset(center.x + (sat * r * cos(rad)).toFloat(), center.y + (sat * r * sin(rad)).toFloat())
        drawCircle(Color.White, 11.dp.toPx(), at, style = Stroke(3.dp.toPx()))
        drawCircle(Color.Black.copy(alpha = 0.5f), 12.5.dp.toPx(), at, style = Stroke(1.dp.toPx()))
    }
}
