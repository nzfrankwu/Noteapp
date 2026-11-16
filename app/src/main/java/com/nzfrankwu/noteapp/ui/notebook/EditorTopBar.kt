package com.nzfrankwu.noteapp.ui.notebook

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.nzfrankwu.noteapp.R
import com.nzfrankwu.noteapp.ui.theme.EditorBarButton
import com.nzfrankwu.noteapp.ui.theme.EditorBarIcon
import com.nzfrankwu.noteapp.ui.theme.EditorBarIconDisabled

/**
 * The notebook's buttons, floating over the pages (PHASE-2.md F5, F17, like Notein): no bar
 * behind them, so the page can fill the screen. Dark round buttons in both light and dark mode.
 * Back on the left (with the linked task's chip); Undo and Redo in a pill in the middle; Add page and ⋮ on the right.
 * [menu] fills the ⋮ menu. Taps between the buttons reach the page.
 */
@Composable
fun EditorTopBar(
    canUndo: Boolean,
    canRedo: Boolean,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onAddPage: () -> Unit,
    /** Opens the "Add Page" menu (F34). */
    onAddMenu: () -> Unit,
    /** Opens or closes the image menu. */
    onImage: () -> Unit,
    imageMenuOpen: Boolean,
    /** Starts the screenshot tool. */
    onScreenshot: () -> Unit,
    /** Turns the T button (text boxes, Phase 5) on or off; [textMode] is whether it's on. */
    onText: () -> Unit = {},
    textMode: Boolean = false,
    menuOpen: Boolean,
    onMenuOpen: (Boolean) -> Unit,
    /** Bookmarks the page on screen for a task (Phase 4), or null to hide the button. */
    onBookmark: (() -> Unit)? = null,
    /** The linked task's chip, next to Back (Phase 4). */
    taskChip: @Composable () -> Unit = {},
    /** A button just right of Back (the recent notebooks panel, PHASE-5.md 5.7). */
    leftButton: @Composable () -> Unit = {},
    menu: @Composable ColumnScope.() -> Unit,
) {
    LightStatusBarIcons()
    androidx.compose.foundation.layout.BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
    ) {
        // In a narrow window (e.g. half of Android's split screen, PHASE-5.md 5.11) the middle pill
        // doesn't fit between the side buttons: it goes on a second row underneath.
        val rightCount = if (onBookmark != null) 4 else 3
        val rightWidth = ButtonSize * rightCount + 8.dp * (rightCount - 1)
        val narrow = maxWidth < (CenterPillHalfWidth + maxOf(LeftGroupWidth, rightWidth) + 8.dp) * 2
        // The chip stops short of the pill in the middle (or of the right buttons when narrow).
        val chipMax = if (narrow) {
            (maxWidth - rightWidth - LeftGroupWidth - 16.dp).coerceAtLeast(0.dp)
        } else {
            (maxWidth / 2 - CenterPillHalfWidth - LeftGroupWidth - 16.dp).coerceAtLeast(0.dp)
        }
        Row(
            Modifier.height(BarHeight).align(Alignment.TopStart),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarButton(R.drawable.ic_arrow_back, "Back", onBack)
            leftButton()
            Box(Modifier.widthIn(max = chipMax)) { taskChip() }
        }

        Row(
            Modifier
                // Narrow: on the right, under the side buttons, clear of the recent panel on the left.
                .align(if (narrow) Alignment.TopEnd else Alignment.TopCenter)
                .padding(top = if (narrow) BarHeight else (BarHeight - ButtonSize) / 2)
                .shadow(3.dp, RoundedCornerShape(50))
                .background(EditorBarButton, RoundedCornerShape(50))
                .border(1.dp, ButtonRim, RoundedCornerShape(50))
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarButton(R.drawable.ic_undo, "Undo", onUndo, Modifier.testTag("undo"), enabled = canUndo, filled = false)
            BarButton(R.drawable.ic_redo, "Redo", onRedo, Modifier.testTag("redo"), enabled = canRedo, filled = false)
            Box(Modifier.size(width = 1.dp, height = 24.dp).background(ButtonRim))
            BarButton(
                R.drawable.ic_image, if (imageMenuOpen) "Close the image menu" else "Insert an image", onImage,
                Modifier.testTag("image-button"), filled = false,
            )
            // Image and Screenshot side by side (FB10), then text boxes.
            BarButton(R.drawable.ic_crop, "Take a screenshot", onScreenshot, Modifier.testTag("screenshot-button"), filled = false)
            BarButton(
                R.drawable.ic_text_fields, if (textMode) "Text boxes (on): tap the page to add one" else "Add a text box", onText,
                Modifier.testTag("text-button"), filled = false, selected = textMode,
            )
        }

        Row(
            Modifier.height(BarHeight).align(Alignment.TopEnd),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBookmark != null) {
                BarButton(R.drawable.ic_bookmark_add, "Bookmark this page for a task", onBookmark, Modifier.testTag("bookmark-page"))
            }
            BarButton(R.drawable.ic_add, "Add pages, photos or a PDF", onAddMenu, Modifier.testTag("add-menu"))
            BarButton(R.drawable.ic_note_add, "Add a page after this one", onAddPage, Modifier.testTag("add-page"))
            Box {
                BarButton(R.drawable.ic_more_vert, "Notebook options", { onMenuOpen(true) })
                DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenuOpen(false) }, content = menu)
            }
        }
    }
}

/** A round dark top-bar button for things outside this file (the recent panel's button, 5.7). */
@Composable
fun TopBarIconButton(@DrawableRes icon: Int, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false) =
    BarButton(icon, description, onClick, modifier, selected = selected)

/** A round dark button with a white icon. [filled] = false sits inside the Undo/Redo pill. */
@Composable
private fun BarButton(
    @DrawableRes icon: Int,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = true,
    /** A mode that's on (the T button): a light ring behind the icon. */
    selected: Boolean = false,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        // A faint light rim keeps the dark buttons visible over the dark desk (F25).
        modifier = if (filled) modifier.size(ButtonSize).shadow(3.dp, CircleShape).border(1.dp, ButtonRim, CircleShape) else modifier.size(ButtonSize),
        shape = CircleShape,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = when {
                selected && filled -> androidx.compose.ui.graphics.lerp(EditorBarButton, Color.White, 0.25f)
                selected -> Color.White.copy(alpha = 0.22f)
                filled -> EditorBarButton
                else -> Color.Transparent
            },
            contentColor = EditorBarIcon,
            disabledContainerColor = if (filled) EditorBarButton else Color.Transparent,
            disabledContentColor = EditorBarIconDisabled,
        ),
    ) {
        Icon(painterResource(icon), contentDescription = description, modifier = Modifier.size(22.dp))
    }
}

/** The desk under the status bar is dark (F25), so its clock and icons turn white while the editor shows. */
@Composable
private fun LightStatusBarIcons() {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val before = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if (before != null) controller.isAppearanceLightStatusBars = before }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private val ButtonRim = Color.White.copy(alpha = 0.18f)

private val BarHeight = 64.dp
private val ButtonSize = 44.dp
/** About half the width of the Undo/Redo/Image/Text/Screenshot pill. */
private val CenterPillHalfWidth = 128.dp
/** Back and the recent panel's button. */
private val LeftGroupWidth = 98.dp
