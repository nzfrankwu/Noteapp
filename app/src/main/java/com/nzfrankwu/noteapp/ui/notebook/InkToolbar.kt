package com.nzfrankwu.noteapp.ui.notebook

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.nzfrankwu.noteapp.domain.ink.HighlightOpacity
import com.nzfrankwu.noteapp.domain.ink.PaletteEdit
import com.nzfrankwu.noteapp.domain.ink.PenPressure
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material3.TextButton
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nzfrankwu.noteapp.R
import com.nzfrankwu.noteapp.domain.ink.EditorTool
import com.nzfrankwu.noteapp.domain.ink.InkPalette
import com.nzfrankwu.noteapp.domain.ink.InkToolSettings
import com.nzfrankwu.noteapp.domain.ink.PenStyle
import com.nzfrankwu.noteapp.domain.ink.PressureErase
import kotlin.math.roundToInt

/** The toolbar's width: a bit thinner than Notein's (PHASE-2.md). */
val ToolbarWidth = 48.dp

/**
 * The floating notebook toolbar (PHASE-2.md 2.7): drag it by its handle, tap the handle's arrow
 * to tuck it away. Tools, then the current tool's colours and sizes. Tapping the tool that's
 * already chosen opens its settings ([onOpenSettings]).
 */
@Composable
fun InkToolbar(
    settings: InkToolSettings,
    onPickTool: (EditorTool) -> Unit,
    onStyle: (EditorTool, PenStyle) -> Unit,
    onOpenSettings: () -> Unit,
    onDrag: (dx: Float, dy: Float) -> Unit,
    onDragEnd: () -> Unit,
    onCollapse: () -> Unit,
    onSettings: ((InkToolSettings) -> InkToolSettings) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.width(ToolbarWidth).testTag("ink-toolbar"),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
    ) {
        Column(Modifier.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(40.dp, 28.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(onDragEnd = onDragEnd) { change, amount ->
                            change.consume()
                            onDrag(amount.x, amount.y)
                        }
                    }
                    .clickable(onClick = onCollapse)
                    .semantics { contentDescription = "Move or hide the toolbar" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_drag_handle), null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            for (slot in 0 until EditorTool.PEN_COUNT) {
                val tool = EditorTool.pen(slot)
                ToolButton(tool, R.drawable.ic_edit, Color(settings.pens[slot].colorArgb), settings.tool == tool) {
                    if (settings.tool == tool) onOpenSettings() else onPickTool(tool)
                }
            }
            ToolButton(EditorTool.HIGHLIGHTER, R.drawable.ic_highlighter, Color(settings.highlighter.colorArgb or 0xFF000000.toInt()), settings.tool == EditorTool.HIGHLIGHTER) {
                if (settings.tool == EditorTool.HIGHLIGHTER) onOpenSettings() else onPickTool(EditorTool.HIGHLIGHTER)
            }
            ToolButton(EditorTool.ERASER, R.drawable.ic_eraser, null, settings.tool == EditorTool.ERASER) {
                if (settings.tool == EditorTool.ERASER) onOpenSettings() else onPickTool(EditorTool.ERASER)
            }
            ToolButton(EditorTool.LASSO, R.drawable.ic_lasso, null, settings.tool == EditorTool.LASSO) { onPickTool(EditorTool.LASSO) }
            ToolButton(EditorTool.BOOKMARK, R.drawable.ic_bookmark_add, null, settings.tool == EditorTool.BOOKMARK) {
                if (settings.tool == EditorTool.BOOKMARK) onOpenSettings() else onPickTool(EditorTool.BOOKMARK)
            }

            val style = settings.penStyle(settings.tool)
            if (style != null) {
                HorizontalDivider(Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                PaletteSwatches(settings, style, onStyle, onSettings)
                HorizontalDivider(Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                val sizes = if (settings.tool == EditorTool.HIGHLIGHTER) InkPalette.HIGHLIGHTER_SIZES else InkPalette.SIZES
                sizes.forEachIndexed { i, size ->
                    SizeDot(
                        dot = (6 + i * 4),
                        selected = kotlin.math.abs(style.size - size) < 0.01f,
                        description = listOf("Fine", "Medium", "Bold")[i],
                    ) { onStyle(settings.tool, style.copy(size = size)) }
                }
            }
        }
    }
}

/**
 * The toolbar's colours for the current tool (F23, F24), editable:
 *  - tap a colour to use it; tap the one in use to change it with the colour wheel;
 *  - hold a colour for Change, Move earlier / later and Delete;
 *  - ＋ adds a colour from the colour wheel.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PaletteSwatches(
    settings: InkToolSettings,
    style: PenStyle,
    onStyle: (EditorTool, PenStyle) -> Unit,
    onSettings: ((InkToolSettings) -> InkToolSettings) -> Unit,
) {
    val tool = settings.tool
    val highlighter = tool == EditorTool.HIGHLIGHTER
    val palette = settings.palette(tool)
    var menuFor by remember { mutableStateOf<Int?>(null) }
    var editing by remember { mutableStateOf<Int?>(null) } // a palette index, or -1 for a new colour
    palette.withIndex().chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(vertical = 2.dp)) {
            row.forEach { (i, c) ->
                val selected = HighlightOpacity.sameColour(style.colorArgb, c)
                Box {
                    Box(
                        Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(Color(c))
                            .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.15f), CircleShape)
                            .combinedClickable(
                                onClick = { if (selected) editing = i else onStyle(tool, style.withColour(c, highlighter)) },
                                onLongClick = { menuFor = i },
                            )
                            .semantics { contentDescription = "Colour ${"%08X".format(c)}"; this.selected = selected },
                    )
                    DropdownMenu(expanded = menuFor == i, onDismissRequest = { menuFor = null }) {
                        DropdownMenuItem(text = { Text("Change colour") }, onClick = { menuFor = null; editing = i })
                        DropdownMenuItem(
                            text = { Text("Move earlier") }, enabled = i > 0,
                            onClick = { menuFor = null; onSettings { it.withPalette(tool, PaletteEdit.move(it.palette(tool), i, -1)) } },
                        )
                        DropdownMenuItem(
                            text = { Text("Move later") }, enabled = i < palette.lastIndex,
                            onClick = { menuFor = null; onSettings { it.withPalette(tool, PaletteEdit.move(it.palette(tool), i, +1)) } },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, enabled = palette.size > 1,
                            onClick = { menuFor = null; onSettings { it.withPalette(tool, PaletteEdit.delete(it.palette(tool), i)) } },
                        )
                    }
                }
            }
        }
    }
    Box(
        Modifier.padding(top = 2.dp).size(22.dp).clip(CircleShape).clickable { editing = -1 }
            .semantics { contentDescription = "Add a colour" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_add), null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    editing?.let { index ->
        ColorPickerDialog(
            initial = (palette.getOrNull(index) ?: style.colorArgb) or 0xFF000000.toInt(),
            onDismiss = { editing = null },
            onPick = { picked ->
                editing = null
                onSettings { now ->
                    val current = now.palette(tool)
                    val colors = if (index >= 0) PaletteEdit.replace(current, index, picked) else PaletteEdit.add(current, picked)
                    val withColours = now.withPalette(tool, colors)
                    now.penStyle(tool)?.let { withColours.withStyle(tool, it.withColour(picked, highlighter)) } ?: withColours
                }
            },
        )
    }
}

/** The tucked-away toolbar: one round button showing the current tool. */
@Composable
fun CollapsedToolbar(settings: InkToolSettings, onExpand: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(44.dp).testTag("ink-toolbar-collapsed"),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
        onClick = onExpand,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painterResource(iconOf(settings.tool)),
                contentDescription = "Show the toolbar",
                tint = settings.penStyle(settings.tool)?.let { Color(it.colorArgb or 0xFF000000.toInt()) } ?: MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

fun iconOf(tool: EditorTool): Int = when (tool) {
    EditorTool.PEN_1, EditorTool.PEN_2 -> R.drawable.ic_edit
    EditorTool.HIGHLIGHTER -> R.drawable.ic_highlighter
    EditorTool.ERASER -> R.drawable.ic_eraser
    EditorTool.LASSO -> R.drawable.ic_lasso
    EditorTool.BOOKMARK -> R.drawable.ic_bookmark_add
}

@Composable
private fun ToolButton(tool: EditorTool, icon: Int, tint: Color?, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(vertical = 2.dp)
            .size(38.dp)
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .testTag("tool-${tool.key}")
            .semantics {
                contentDescription = tool.label
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), null, Modifier.size(22.dp), tint = tint ?: MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun Swatch(color: Int, selected: Boolean, size: Int, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Color(color))
            .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.15f), CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Colour ${"%08X".format(color)}"; this.selected = selected },
    )
}

/** A colour made with the colour wheel: tap to use it, hold for Add to toolbar / Delete (F28). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CustomSwatch(color: Int, selected: Boolean, onPick: () -> Unit, onAddToToolbar: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(Color(color))
                .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.15f), CircleShape)
                .combinedClickable(onClick = onPick, onLongClick = { menu = true })
                .semantics { contentDescription = "Your colour ${"%08X".format(color)}"; this.selected = selected },
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Add to toolbar") }, onClick = { menu = false; onAddToToolbar() })
            DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; onDelete() })
        }
    }
}

@Composable
private fun SizeDot(dot: Int, selected: Boolean, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description; this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(dot.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface))
    }
}

/**
 * The current tool's settings: thickness, colours, Stability, press-hard-to-erase and
 * hold-to-straighten for pens; size and colours for the highlighter; size for the eraser.
 */
@Composable
fun ToolSettingsPanel(
    settings: InkToolSettings,
    onStyle: (EditorTool, PenStyle) -> Unit,
    onChange: ((InkToolSettings) -> InkToolSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tool = settings.tool
    Surface(
        modifier = modifier.widthIn(max = 300.dp).testTag("tool-settings"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(tool.label, style = MaterialTheme.typography.titleMedium)
            when (tool) {
                EditorTool.ERASER -> {
                    LabeledSlider("Eraser size", "%.0f".format(settings.eraserRadius * 2), settings.eraserRadius, 2f..30f) { r ->
                        onChange { it.copy(eraserRadius = r) }
                    }
                    // Phase 7 (7.4): whole strokes, or only the part the eraser passes over.
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().testTag("eraser-kind")) {
                        listOf(false to "Whole strokes", true to "Part of strokes").forEachIndexed { i, (partial, label) ->
                            SegmentedButton(
                                selected = settings.eraserPartial == partial,
                                onClick = { onChange { it.copy(eraserPartial = partial) } },
                                shape = SegmentedButtonDefaults.itemShape(i, 2),
                                modifier = Modifier.testTag(if (partial) "eraser-partial" else "eraser-whole"),
                            ) { Text(label) }
                        }
                    }
                    Text(
                        if (settings.eraserPartial) "The eraser rubs out only the part of a stroke it passes over."
                        else "The eraser removes whole strokes it touches.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                EditorTool.LASSO -> Text("Circle strokes (or just part of them) to select them, then drag to move, pull the corner to resize, or use the menu.")
                EditorTool.BOOKMARK -> {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().testTag("bookmark-shape")) {
                        listOf(false to "Free shape", true to "Rectangle").forEachIndexed { i, (rect, label) ->
                            SegmentedButton(
                                selected = settings.bookmarkRect == rect,
                                onClick = { onChange { it.copy(bookmarkRect = rect) } },
                                shape = SegmentedButtonDefaults.itemShape(i, 2),
                                modifier = Modifier.testTag(if (rect) "bookmark-shape-rect" else "bookmark-shape-free"),
                            ) { Text(label) }
                        }
                    }
                    Text(
                        "Circle or box part of the page with the pen to bookmark it for a task. A picture of it becomes the bookmark's note.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    val style = settings.penStyle(tool) ?: return@Column
                    val highlighter = tool == EditorTool.HIGHLIGHTER
                    val range = if (highlighter) 3f..30f else InkPalette.MIN_SIZE..InkPalette.MAX_SIZE
                    if (highlighter) {
                        // Flat (chisel) or round tip, like Notein (F18).
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().testTag("highlighter-tip")) {
                            listOf(false to "Flat tip", true to "Round tip").forEachIndexed { i, (round, label) ->
                                SegmentedButton(
                                    selected = settings.highlighterRound == round,
                                    onClick = { onChange { it.copy(highlighterRound = round) } },
                                    shape = SegmentedButtonDefaults.itemShape(i, 2),
                                ) { Text(label) }
                            }
                        }
                    }
                    LabeledSlider("Thickness", "%.1f".format(style.size), style.size, range) { onStyle(tool, style.copy(size = it)) }
                    if (highlighter) {
                        // How see-through the highlighter is (F21), kept in its colour.
                        val opacity = HighlightOpacity.of(style.colorArgb)
                        LabeledSlider("Opacity", "${(opacity * 100).roundToInt()}%", opacity, HighlightOpacity.MIN..HighlightOpacity.MAX) { v ->
                            onStyle(tool, style.copy(colorArgb = HighlightOpacity.applied(style.colorArgb, v)))
                        }
                    } else {
                        // How much the line's width follows pressure (F22); Off draws the same width throughout.
                        Column {
                            Row(Modifier.fillMaxWidth()) {
                                Text("Pressure", style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.weight(1f))
                                Text(PenPressure.LABELS[style.pressure.coerceIn(0, 3)], style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Slider(
                                value = style.pressure.toFloat(),
                                onValueChange = { v -> onStyle(tool, style.copy(pressure = v.roundToInt().coerceIn(PenPressure.OFF, PenPressure.FULL))) },
                                valueRange = 0f..3f,
                                steps = 2,
                                modifier = Modifier.height(32.dp).testTag("pen-pressure"),
                            )
                        }
                    }
                    Text("Colours", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val all = if (highlighter) settings.highlighterPalette else (settings.penPalette + InkPalette.MORE_PEN_COLORS).distinct()
                    all.chunked(8).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { c ->
                                Swatch(c, selected = HighlightOpacity.sameColour(style.colorArgb, c), size = 24) { onStyle(tool, style.withColour(c, highlighter)) }
                            }
                        }
                    }
                    // Your own colours from the colour wheel (F10), and the wheel itself.
                    var picking by remember { mutableStateOf(false) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        // Hold one for Add to toolbar / Delete (F28).
                        settings.customColors.take(6).forEach { c ->
                            CustomSwatch(
                                color = c,
                                selected = HighlightOpacity.sameColour(style.colorArgb, c),
                                onPick = { onStyle(tool, style.withColour(c, highlighter)) },
                                onAddToToolbar = { onChange { it.withPalette(tool, PaletteEdit.add(it.palette(tool), c)) } },
                                onDelete = { onChange { it.withoutCustomColor(c) } },
                            )
                        }
                        TextButton(onClick = { picking = true }, modifier = Modifier.testTag("pick-colour")) { Text("Colour wheel…") }
                    }
                    if (picking) {
                        ColorPickerDialog(
                            initial = style.colorArgb,
                            onDismiss = { picking = false },
                            onPick = { c ->
                                picking = false
                                onStyle(tool, style.withColour(c, highlighter))
                                onChange { it.withCustomColor(c) }
                            },
                        )
                    }
                    if (!highlighter) {
                        LabeledSlider("Stability", "${settings.stability}%", settings.stability.toFloat(), 0f..100f) { v ->
                            onChange { it.copy(stability = v.roundToInt()) }
                        }
                    }
                    SwitchRow("Hold still to snap a straight line", settings.holdToStraighten) { on ->
                        onChange { it.copy(holdToStraighten = on) }
                    }
                    SwitchRow("Press hard to erase", settings.pressureErase) { on -> onChange { it.copy(pressureErase = on) } }
                    if (settings.pressureErase) {
                        LabeledSlider(
                            "How hard",
                            "${(settings.pressureEraseLevel * 100).roundToInt()}%",
                            settings.pressureEraseLevel,
                            PressureErase.MIN_LEVEL..PressureErase.MAX_LEVEL,
                        ) { v -> onChange { it.copy(pressureEraseLevel = v) } }
                        LabeledSlider(
                            "Hold for",
                            if (settings.pressureEraseHoldMs == 0) "No wait" else "%.1f s".format(settings.pressureEraseHoldMs / 1000f),
                            settings.pressureEraseHoldMs.toFloat(),
                            0f..PressureErase.MAX_HOLD_MS.toFloat(),
                        ) { v -> onChange { it.copy(pressureEraseHoldMs = (v / 100f).roundToInt() * 100) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun LabeledSlider(label: String, value: String, current: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = current.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range, modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
