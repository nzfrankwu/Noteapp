package com.nzfrankwu.noteapp.ui.notebook

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.nzfrankwu.noteapp.domain.ink.BoxHandle
import com.nzfrankwu.noteapp.domain.ink.LassoBox
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The screenshot tool's crop screen (PHASE-3.md 3.9, like Notein's,
 * `design-reference/screenshot selection.jpg`): the still [picture] fills the screen, dimmed
 * outside a box with 8 handles. Drag a handle to resize, drag inside to move. Cancel / Confirm
 * sit under the box. [onConfirm] gets the box in the picture's own pixels (left, top, right, bottom).
 */
@Composable
fun SnipOverlay(picture: ImageBitmap, onCancel: () -> Unit, onConfirm: (IntArray) -> Unit, startWhole: Boolean = false) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black).testTag("snip-overlay")) {
        val density = LocalDensity.current
        val viewW = constraints.maxWidth.toFloat()
        val viewH = constraints.maxHeight.toFloat()
        // Where the picture sits: fitted and centred, with a margin all round (so every handle can
        // be reached, clear of the system's gesture areas) and room for Cancel / Confirm below.
        val margin = with(density) { 32.dp.toPx() }
        val buttonRoom = with(density) { 72.dp.toPx() }
        val areaW = (viewW - 2 * margin).coerceAtLeast(1f)
        val areaH = (viewH - 2 * margin - buttonRoom).coerceAtLeast(1f)
        val scale = min(areaW / picture.width, areaH / picture.height)
        val dstW = picture.width * scale
        val dstH = picture.height * scale
        val dstX = (viewW - dstW) / 2
        val dstY = margin + (areaH - dstH) / 2
        val minSide = with(density) { 48.dp.toPx() }
        // The crop box in screen pixels; it starts as the middle half of the picture (or all of it, to crop a picture).
        var box by remember(picture) {
            mutableStateOf(
                if (startWhole) floatArrayOf(dstX, dstY, dstX + dstW, dstY + dstH)
                else floatArrayOf(dstX + dstW / 4, dstY + dstH / 4, dstX + dstW * 3 / 4, dstY + dstH * 3 / 4),
            )
        }
        fun clamped(b: FloatArray): FloatArray {
            var (l, t, r, bt) = b
            if (r - l < minSide) r = l + minSide
            if (bt - t < minSide) bt = t + minSide
            val w = r - l
            val h = bt - t
            l = l.coerceIn(dstX, dstX + dstW - w); t = t.coerceIn(dstY, dstY + dstH - h)
            return floatArrayOf(l, t, l + w.coerceAtMost(dstW), t + h.coerceAtMost(dstH))
        }
        val handleRadius = with(density) { 9.dp.toPx() }
        val grabRadius = with(density) { 28.dp.toPx() }

        Canvas(
            Modifier.fillMaxSize().pointerInput(picture) {
                var grabbed: BoxHandle? = null
                var moving = false
                detectDragGestures(
                    onDragStart = { at ->
                        grabbed = BoxHandle.entries
                            .map { it to it.at(box) }
                            .filter { (_, p) -> hypot(at.x - p.first, at.y - p.second) <= grabRadius }
                            .minByOrNull { (_, p) -> hypot(at.x - p.first, at.y - p.second) }?.first
                        moving = grabbed == null && at.x in box[0]..box[2] && at.y in box[1]..box[3]
                    },
                    onDrag = { change, drag ->
                        change.consume()
                        val h = grabbed
                        box = when {
                            h != null -> clamped(
                                LassoBox.dragged(box, h, change.position.x.coerceIn(dstX, dstX + dstW), change.position.y.coerceIn(dstY, dstY + dstH)),
                            )
                            moving -> clamped(LassoBox.moved(box, drag.x, drag.y))
                            else -> box
                        }
                    },
                    onDragEnd = { grabbed = null; moving = false },
                )
            },
        ) {
            drawImage(
                picture,
                dstOffset = IntOffset(dstX.roundToInt(), dstY.roundToInt()),
                dstSize = IntSize(dstW.roundToInt(), dstH.roundToInt()),
                filterQuality = FilterQuality.Medium,
            )
            // Dim everything outside the box.
            val dim = Color(0x8C000000)
            val (l, t, r, b) = box
            drawRect(dim, Offset(0f, 0f), Size(size.width, t))
            drawRect(dim, Offset(0f, b), Size(size.width, size.height - b))
            drawRect(dim, Offset(0f, t), Size(l, b - t))
            drawRect(dim, Offset(r, t), Size(size.width - r, b - t))
            drawRect(SnipBlue, Offset(l, t), Size(r - l, b - t), style = Stroke(width = 2f, pathEffect = PathEffect.cornerPathEffect(0f)))
            for (handle in BoxHandle.entries) {
                val (hx, hy) = handle.at(box)
                drawCircle(Color.White, handleRadius, Offset(hx, hy))
                drawCircle(SnipBlue, handleRadius - 3f, Offset(hx, hy))
            }
        }

        // Cancel / Confirm under the box (above it when there's no room below).
        var buttonsSize by remember { mutableStateOf(IntSize.Zero) }
        val gap = with(density) { 16.dp.toPx() }
        val below = box[3] + gap
        val y = if (below + buttonsSize.height < viewH - gap) below else (box[1] - gap - buttonsSize.height).coerceAtLeast(gap)
        val x = ((box[0] + box[2]) / 2 - buttonsSize.width / 2).coerceIn(gap, (viewW - buttonsSize.width - gap).coerceAtLeast(gap))
        Row(
            Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }.onSizeChanged { buttonsSize = it },
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onCancel,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3E4354), contentColor = Color.White),
                modifier = Modifier.testTag("snip-cancel"),
            ) { Text("Cancel") }
            Button(
                onClick = {
                    val px = IntArray(4)
                    px[0] = ((box[0] - dstX) / scale).roundToInt().coerceIn(0, picture.width - 1)
                    px[1] = ((box[1] - dstY) / scale).roundToInt().coerceIn(0, picture.height - 1)
                    px[2] = ((box[2] - dstX) / scale).roundToInt().coerceIn(px[0] + 1, picture.width)
                    px[3] = ((box[3] - dstY) / scale).roundToInt().coerceIn(px[1] + 1, picture.height)
                    onConfirm(px)
                },
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = SnipBlue, contentColor = Color(0xFF10162E)),
                modifier = Modifier.testTag("snip-confirm"),
            ) { Text("Confirm") }
        }
    }
}

private val SnipBlue = Color(0xFF8FA8F8)
