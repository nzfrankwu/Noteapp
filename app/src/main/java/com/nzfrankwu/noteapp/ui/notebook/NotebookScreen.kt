package com.nzfrankwu.noteapp.ui.notebook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nzfrankwu.noteapp.R
import com.nzfrankwu.noteapp.domain.PageTemplate
import com.nzfrankwu.noteapp.domain.ink.Camera
import com.nzfrankwu.noteapp.domain.ink.EditorTool
import com.nzfrankwu.noteapp.domain.ink.PageStrip
import com.nzfrankwu.noteapp.ui.components.RoundIconButton
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * An open notebook (PHASE-2.md 2.6): a slim top bar, the pages side by side, the floating
 * toolbar, and a page-number badge. Changes save themselves.
 */
@Composable
fun NotebookScreen(
    viewModel: NotebookViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val notebook by viewModel.notebook.collectAsStateWithLifecycle()
    val pages by viewModel.pages.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val density = LocalDensity.current

    var camera by remember { mutableStateOf<Camera?>(null) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var lasso by remember { mutableStateOf<LassoState?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    // Toolbar drag in progress (pixels), saved as screen fractions when the drag ends.
    var dragX by remember { mutableFloatStateOf(Float.NaN) }
    var dragY by remember { mutableFloatStateOf(Float.NaN) }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.saveNow() }
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); viewModel.messageShown() }
    }
    val damaged = viewModel.pageStatus.values.count { it is PageStatus.Damaged }
    LaunchedEffect(damaged) {
        if (damaged > 0) snackbar.showSnackbar("A page couldn't be read. Its file is kept safe and won't be changed.")
    }
    LaunchedEffect(settings.tool) { if (settings.tool != EditorTool.LASSO) lasso = null }

    val nb = notebook
    val strip = nb?.let { PageStrip(it.pageWidth, it.pageHeight, pages.size) }
    // First fit once the screen size is known, and refit the same page after rotating.
    var fittedFor by remember { mutableStateOf(IntSize.Zero) }
    if (strip != null && pages.isNotEmpty() && viewSize.width > 0 && fittedFor != viewSize) {
        val page = camera?.let { strip.currentPage(it, fittedFor.width.toFloat()) } ?: 0
        camera = strip.fit(settings.fitMode, page, viewSize.width.toFloat(), viewSize.height.toFloat())
        fittedFor = viewSize
    }
    val currentPage = if (strip != null && camera != null) strip.currentPage(camera!!, viewSize.width.toFloat()) else 0

    fun leave() {
        viewModel.saveNow()
        onBack()
    }

    Column(modifier.fillMaxSize()) {
        // ---- Top bar ----
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                RoundIconButton(R.drawable.ic_arrow_back, "Back", ::leave)
                Text(
                    nb?.title.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                IconButton(onClick = { lasso = null; viewModel.undo() }, enabled = viewModel.canUndo, modifier = Modifier.testTag("undo")) {
                    Icon(painterResource(R.drawable.ic_undo), contentDescription = "Undo")
                }
                IconButton(onClick = { lasso = null; viewModel.redo() }, enabled = viewModel.canRedo, modifier = Modifier.testTag("redo")) {
                    Icon(painterResource(R.drawable.ic_redo), contentDescription = "Redo")
                }
                IconButton(
                    onClick = {
                        scope.launch {
                            val at = currentPage + 1
                            if (viewModel.addPage(at) != null && strip != null) {
                                val grown = PageStrip(strip.pageWidth, strip.pageHeight, pages.size + 1)
                                camera = grown.fit(settings.fitMode, at, viewSize.width.toFloat(), viewSize.height.toFloat())
                            }
                        }
                    },
                    modifier = Modifier.testTag("add-page"),
                ) { Icon(painterResource(R.drawable.ic_note_add), contentDescription = "Add a page after this one") }
                Box {
                    RoundIconButton(R.drawable.ic_more_vert, "Notebook options", { menuOpen = true })
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        Text(
                            "Paper",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                        PageTemplate.entries.forEach { t ->
                            DropdownMenuItem(
                                text = { Text(if (nb?.template == t) "✓  ${t.label}" else "    ${t.label}") },
                                onClick = { menuOpen = false; viewModel.setTemplate(t) },
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Delete this page", color = MaterialTheme.colorScheme.error) },
                            enabled = pages.size > 1,
                            onClick = {
                                menuOpen = false
                                pages.getOrNull(currentPage)?.let { viewModel.deletePage(it.id) }
                                scope.launch { snackbar.showSnackbar("Page deleted. Tap Undo to bring it back.") }
                            },
                        )
                    }
                }
            }
        }

        // ---- Pages, toolbar and overlays ----
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (nb != null && pages.isNotEmpty()) {
                NotebookCanvas(
                    viewModel = viewModel,
                    notebook = nb,
                    pages = pages,
                    settings = settings,
                    camera = camera,
                    onCamera = { camera = it },
                    onViewSize = { viewSize = it },
                    lasso = lasso,
                    onLasso = { lasso = it },
                )
            }

            // Floating toolbar (or its tucked-away button).
            val fullW = viewSize.width.toFloat()
            val fullH = viewSize.height.toFloat()
            if (fullW > 0f) {
                val toolbarPx = with(density) { ToolbarWidth.toPx() }
                val x = if (dragX.isNaN()) settings.toolbarX * fullW else dragX
                val y = if (dragY.isNaN()) settings.toolbarY * fullH else dragY
                val margin = with(density) { 8.dp.toPx() }
                val ox = x.coerceIn(margin, (fullW - toolbarPx - margin).coerceAtLeast(margin))
                val oy = y.coerceIn(margin, (fullH - with(density) { 120.dp.toPx() }).coerceAtLeast(margin))
                Box(Modifier.offset { IntOffset(ox.roundToInt(), oy.roundToInt()) }) {
                    if (settings.toolbarCollapsed) {
                        CollapsedToolbar(settings, onExpand = { viewModel.updateSettings { it.copy(toolbarCollapsed = false) } })
                    } else {
                        Row(verticalAlignment = Alignment.Top) {
                            InkToolbar(
                                settings = settings,
                                onPickTool = { showSettings = false; viewModel.pickTool(it) },
                                onStyle = { tool, style -> viewModel.updateSettings { it.withStyle(tool, style) } },
                                onOpenSettings = { showSettings = !showSettings },
                                onDrag = { dx, dy ->
                                    dragX = (if (dragX.isNaN()) ox else dragX) + dx
                                    dragY = (if (dragY.isNaN()) oy else dragY) + dy
                                },
                                onDragEnd = {
                                    val fx = (dragX / fullW).coerceIn(0f, 1f)
                                    val fy = (dragY / fullH).coerceIn(0f, 1f)
                                    viewModel.updateSettings { it.copy(toolbarX = fx, toolbarY = fy) }
                                    dragX = Float.NaN; dragY = Float.NaN
                                },
                                onCollapse = { showSettings = false; viewModel.updateSettings { it.copy(toolbarCollapsed = true) } },
                            )
                            if (showSettings) {
                                ToolSettingsPanel(
                                    settings = settings,
                                    onStyle = { tool, style -> viewModel.updateSettings { it.withStyle(tool, style) } },
                                    onChange = viewModel::updateSettings,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                    }
                }
            }

            // Lasso: Delete / Done under the selection.
            val sel = lasso as? LassoState.Selected
            val cam = camera
            if (sel != null && cam != null && strip != null) {
                val box = sel.box()
                val pl = strip.pageLeft(sel.pageIndex)
                val bx = cam.toScreenX(pl + box[0])
                val by = cam.toScreenY(box[3]) + with(density) { 12.dp.toPx() }
                Surface(
                    modifier = Modifier.offset { IntOffset(bx.roundToInt(), by.roundToInt().coerceAtMost(viewSize.height - 160)) }.testTag("lasso-menu"),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shadowElevation = 6.dp,
                ) {
                    Row {
                        TextButton(onClick = {
                            viewModel.deleteStrokes(sel.pageId, sel.strokes.map { it.id }.toSet())
                            lasso = null
                        }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                        TextButton(onClick = { lasso = null }) { Text("Done") }
                    }
                }
            }

            // Page number.
            if (pages.isNotEmpty()) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomStart).padding(16.dp).testTag("page-badge"),
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.8f),
                ) {
                    Text(
                        "${currentPage + 1} / ${pages.size}",
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }
}
