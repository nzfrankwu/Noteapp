package com.nzfrankwu.noteapp.ui.notebook

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.nzfrankwu.noteapp.R
import com.nzfrankwu.noteapp.data.TextBoxLayout
import com.nzfrankwu.noteapp.data.db.Page
import com.nzfrankwu.noteapp.domain.ink.InkPalette
import com.nzfrankwu.noteapp.domain.ink.InkText
import com.nzfrankwu.noteapp.domain.ink.PageStrip
import com.nzfrankwu.noteapp.ui.theme.EditorBarButton
import com.nzfrankwu.noteapp.ui.theme.EditorBarIcon
import kotlin.math.roundToInt

/**
 * A text box being typed in (PHASE-5.md 5.3): which page it's on, the box as it was ([original],
 * null for a new one), and the box as it is now ([draft]). Finishing records one undo step.
 */
data class TextEditing(val pageIndex: Int, val pageId: String, val original: InkText?, val draft: InkText)

/**
 * Starts typing in the text box under screen point [at], or, when the T button is on and
 * [allowNew], in a new box there. Returns true if a box is being typed in now.
 */
fun startTextEditing(
    view: EditorViewState,
    viewModel: NotebookViewModel,
    strip: PageStrip,
    pages: List<Page>,
    at: Offset,
    allowNew: Boolean = view.textMode,
): Boolean {
    // Another box being typed in is finished (saved) first, never dropped.
    if (view.editingText != null) finishTextEditing(view, viewModel)
    val cam = view.camera ?: return false
    val wx = cam.toWorldX(at.x)
    val index = strip.pageAt(wx)
    val page = pages.getOrNull(index) ?: return false
    if (!viewModel.isEditable(page.id)) return false
    val pw = strip.pageWidth(index)
    val ph = strip.pageHeight(index)
    val x = wx - strip.pageLeft(index)
    val y = cam.toWorldY(at.y) - strip.pageTop(index)
    if (x < 0f || y < 0f || x > pw || y > ph) return false
    viewModel.textAt(page.id, x, y)?.let { box ->
        begin(view, TextEditing(index, page.id, box, box))
        return true
    }
    if (!allowNew) return false
    // A new box: the look of the last one, about 45% of the page wide, its first line centred on the tap.
    val style = viewModel.lastTextStyle
    val size = style?.size ?: InkText.DEFAULT_SIZE
    val width = (pw * NEW_BOX_WIDTH).coerceIn(InkText.MIN_WIDTH, (pw - 2 * PAGE_MARGIN).coerceAtLeast(InkText.MIN_WIDTH))
    val line = TextBoxLayout.lineHeight(size)
    val left = x.coerceAtMost(pw - width - PAGE_MARGIN).coerceAtLeast(0f)
    val top = (y - line / 2).coerceIn(0f, (ph - line).coerceAtLeast(0f))
    val draft = InkText(
        id = java.util.UUID.randomUUID().toString(), text = "", left = left, top = top, width = width, height = line,
        size = size, colorArgb = style?.colorArgb ?: InkText.DEFAULT_COLOR, bold = style?.bold ?: false,
    )
    begin(view, TextEditing(index, page.id, null, draft))
    return true
}

private fun begin(view: EditorViewState, editing: TextEditing) {
    view.lasso = null
    view.textSel = null
    view.editingText = editing
    view.hiddenTexts = view.hiddenTexts + editing.draft.id
}

/** Finishes typing (if a box is being typed in): saves it as one undo step. Empty boxes disappear. */
fun finishTextEditing(view: EditorViewState, viewModel: NotebookViewModel) {
    val editing = view.editingText ?: return
    view.editingText = null
    viewModel.commitText(editing.pageId, editing.original, editing.draft)
    view.hiddenTexts = view.hiddenTexts - editing.draft.id
}

/**
 * The text box being typed in, over the page where it will be drawn, with a handle on its right
 * edge to change its width, and the text bar (size, colour, bold, delete, done) above it.
 */
@Composable
fun TextBoxEditor(view: EditorViewState, strip: PageStrip, viewModel: NotebookViewModel) {
    val editing = view.editingText ?: return
    key(editing.draft.id) { EditorFor(view, strip, viewModel) }
}

@Composable
private fun EditorFor(view: EditorViewState, strip: PageStrip, viewModel: NotebookViewModel) {
    val editing = view.editingText ?: return
    val cam = view.camera ?: return
    val density = LocalDensity.current
    val d = editing.draft
    val i = editing.pageIndex
    if (i >= strip.pageCount) return
    var value by remember { mutableStateOf(TextFieldValue(d.text, TextRange(d.text.length))) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        runCatching { focus.requestFocus() }
        keyboard?.show()
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var fieldHeight by remember { mutableIntStateOf(0) }
    var fieldWindowTop by remember { mutableStateOf(0f) }
    val rootView = androidx.compose.ui.platform.LocalView.current
    val latest by rememberUpdatedState(editing)

    val scale = cam.scale
    val sx = cam.toScreenX(strip.pageLeft(i) + d.left)
    val sy = cam.toScreenY(strip.pageTop(i) + d.top)
    val widthPx = d.width * scale
    // Bold as Android draws it on the page (the font has one weight, so it's drawn thicker).
    val family = remember(TextBoxLayout.typeface, d.bold) {
        val face = if (d.bold) android.graphics.Typeface.create(TextBoxLayout.typeface, android.graphics.Typeface.BOLD) else TextBoxLayout.typeface
        FontFamily(androidx.compose.ui.text.font.Typeface(face))
    }
    val style = with(density) {
        TextStyle(
            color = Color(d.colorArgb),
            fontSize = (d.size * scale).toSp(),
            lineHeight = (TextBoxLayout.lineHeight(d.size) * scale).toSp(),
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Proportional, LineHeightStyle.Trim.None),
            fontFamily = family,
            fontWeight = FontWeight.Normal,
        )
    }

    // Keep the line being typed above the keyboard: move the pages up when it would be hidden.
    val imeBottom = WindowInsets.ime.getBottom(density)
    LaunchedEffect(imeBottom, value.selection, layout, fieldHeight, fieldWindowTop) {
        val c = view.camera ?: return@LaunchedEffect
        val l = layout ?: return@LaunchedEffect
        if (imeBottom <= 0) return@LaunchedEffect
        val caret = runCatching { l.getCursorRect(value.selection.end.coerceIn(0, value.text.length)) }.getOrNull() ?: return@LaunchedEffect
        // In window coordinates: in split view the pane may end above the window's bottom (and the keyboard).
        val caretBottom = fieldWindowTop + caret.bottom
        val visibleBottom = rootView.height - imeBottom - with(density) { 24.dp.toPx() }
        if (caretBottom > visibleBottom) view.camera = c.copy(offsetY = c.offsetY - (caretBottom - visibleBottom))
    }

    val outline = Color(0xFF2F6BDB)
    BasicTextField(
        value = value,
        onValueChange = { next ->
            val text = next.text.take(InkText.MAX_LENGTH)
            value = if (text.length == next.text.length) next else next.copy(text = text)
            view.editingText = latest.copy(draft = latest.draft.copy(text = text))
        },
        textStyle = style,
        cursorBrush = SolidColor(outline),
        onTextLayout = { layout = it },
        modifier = Modifier
            .offset { IntOffset(sx.roundToInt(), sy.roundToInt()) }
            .requiredWidth(with(density) { widthPx.toDp() })
            .onSizeChanged { fieldHeight = it.height }
            .onGloballyPositioned { fieldWindowTop = it.positionInWindow().y }
            .drawBehind {
                val pad = 3.dp.toPx()
                drawRect(
                    outline,
                    topLeft = Offset(-pad, -pad),
                    size = Size(size.width + 2 * pad, size.height + 2 * pad),
                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))),
                )
            }
            .focusRequester(focus)
            .testTag("text-box-field"),
    )

    // The width handle: a knob on the right edge, halfway down.
    val knob = with(density) { 22.dp.toPx() }
    val pageWidth = strip.pageWidth(i)
    Box(
        Modifier
            // Just outside the box's right edge, so it never covers the text.
            .offset { IntOffset((sx + widthPx + knob * 0.15f).roundToInt(), (sy + fieldHeight / 2f - knob / 2).roundToInt()) }
            .size(with(density) { knob.toDp() })
            .shadow(2.dp, CircleShape)
            .background(Color.White, CircleShape)
            .border(2.dp, outline, CircleShape)
            .semantics { contentDescription = "Change the text box's width" }
            .testTag("text-box-width")
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    val e = view.editingText ?: return@detectDragGestures
                    val c = view.camera ?: return@detectDragGestures
                    val w = (e.draft.width + drag.x / c.scale).coerceIn(InkText.MIN_WIDTH, (pageWidth - e.draft.left).coerceAtLeast(InkText.MIN_WIDTH))
                    view.editingText = e.copy(draft = e.draft.copy(width = w))
                }
            },
    )

    // The text bar: above the box when there's room, otherwise below it.
    var barWidth by remember { mutableIntStateOf(0) }
    val barHeight = with(density) { 48.dp.toPx() }
    val gap = with(density) { 12.dp.toPx() }
    val above = sy - gap - barHeight
    val by = if (above > with(density) { 72.dp.toPx() }) above else sy + fieldHeight + gap
    val bx = sx.coerceIn(8f, (view.viewSize.width - barWidth - 8f).coerceAtLeast(8f))
    TextBar(
        box = d,
        onSize = { s -> update(view) { it.copy(size = s) } },
        onColor = { color -> update(view) { it.copy(colorArgb = color) } },
        onBold = { update(view) { it.copy(bold = !it.bold) } },
        onDelete = {
            update(view) { it.copy(text = "") }
            finishTextEditing(view, viewModel)
        },
        onDone = { finishTextEditing(view, viewModel) },
        modifier = Modifier
            .offset { IntOffset(bx.roundToInt(), by.roundToInt()) }
            .onSizeChanged { barWidth = it.width },
    )
}

private fun update(view: EditorViewState, change: (InkText) -> InkText) {
    val e = view.editingText ?: return
    view.editingText = e.copy(draft = change(e.draft))
}

/** The text bar (PHASE-5.md 5.3): A− size A+, colours, Bold, Delete, Done, in the dark pill style. */
@Composable
private fun TextBar(
    box: InkText,
    onSize: (Float) -> Unit,
    onColor: (Int) -> Unit,
    onBold: () -> Unit,
    onDelete: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .shadow(6.dp, RoundedCornerShape(50))
            .background(EditorBarButton, RoundedCornerShape(50))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(50))
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .testTag("text-bar"),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        IconMenuButton(IconMenuItem(R.drawable.ic_remove, "Smaller text", "text-smaller", enabled = box.size > InkText.SIZES.first() + 0.01f) { onSize(InkText.smaller(box.size)) })
        Text(
            box.size.roundToInt().toString(),
            style = MaterialTheme.typography.labelLarge,
            color = EditorBarIcon,
            modifier = Modifier.widthIn(min = 24.dp).testTag("text-size"),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        IconMenuButton(IconMenuItem(R.drawable.ic_add, "Larger text", "text-larger", enabled = box.size < InkText.MAX_SIZE - 0.01f) { onSize(InkText.larger(box.size)) })
        Box(Modifier.size(width = 1.dp, height = 24.dp).background(Color.White.copy(alpha = 0.18f)))
        for (color in TEXT_COLORS) {
            val picked = color == box.colorArgb
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(26.dp)
                    .border(if (picked) 2.dp else 1.dp, if (picked) Color.White else Color.White.copy(alpha = 0.35f), CircleShape)
                    .padding(4.dp)
                    .background(Color(color), CircleShape)
                    .semantics { contentDescription = "Text colour: ${TEXT_COLOR_NAMES[TEXT_COLORS.indexOf(color)]}" }
                    .testTag("text-color-${TEXT_COLORS.indexOf(color)}")
                    .clickable { onColor(color) },
            )
        }
        Box(Modifier.size(width = 1.dp, height = 24.dp).background(Color.White.copy(alpha = 0.18f)))
        IconMenuButton(IconMenuItem(R.drawable.ic_format_bold, if (box.bold) "Bold (on)" else "Bold", "text-bold", selected = box.bold, onClick = onBold))
        IconMenuButton(IconMenuItem(R.drawable.ic_delete, "Delete the text box", "text-delete", destructive = true, onClick = onDelete))
        IconMenuButton(IconMenuItem(R.drawable.ic_check, "Done", "text-done", onClick = onDone))
    }
}

/** The text colours: the first six pen swatches (black, navy, blue, red, green, orange). */
private val TEXT_COLORS = InkPalette.PEN_COLORS.take(6)
private val TEXT_COLOR_NAMES = listOf("black", "navy", "blue", "red", "green", "orange")

/** A new text box is this much of the page wide. */
private const val NEW_BOX_WIDTH = 0.45f

/** Keeps a new box this far (page units) from the page's right edge. */
private const val PAGE_MARGIN = 18f
