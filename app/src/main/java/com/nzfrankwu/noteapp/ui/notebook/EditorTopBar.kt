package com.nzfrankwu.noteapp.ui.notebook

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.nzfrankwu.noteapp.R
import com.nzfrankwu.noteapp.ui.theme.EditorBar
import com.nzfrankwu.noteapp.ui.theme.EditorBarButton
import com.nzfrankwu.noteapp.ui.theme.EditorBarIcon
import com.nzfrankwu.noteapp.ui.theme.EditorBarIconDisabled

/**
 * The notebook's top bar (PHASE-2.md F5, like Notein): one thin dark strip of round icon
 * buttons, in both light and dark mode. Back on the left; Undo and Redo in a pill in the middle;
 * Add page and ⋮ on the right. [menu] fills the ⋮ menu.
 */
@Composable
fun EditorTopBar(
    canUndo: Boolean,
    canRedo: Boolean,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onAddPage: () -> Unit,
    menuOpen: Boolean,
    onMenuOpen: (Boolean) -> Unit,
    menu: @Composable ColumnScope.() -> Unit,
) {
    LightStatusBarIcons()
    Box(
        Modifier
            .fillMaxWidth()
            .background(EditorBar)
            .statusBarsPadding()
            .height(BarHeight)
            .padding(horizontal = 10.dp),
    ) {
        BarButton(R.drawable.ic_arrow_back, "Back", onBack, Modifier.align(Alignment.CenterStart))

        Row(
            Modifier
                .align(Alignment.Center)
                .background(EditorBarButton, RoundedCornerShape(50))
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarButton(R.drawable.ic_undo, "Undo", onUndo, Modifier.testTag("undo"), enabled = canUndo, filled = false)
            BarButton(R.drawable.ic_redo, "Redo", onRedo, Modifier.testTag("redo"), enabled = canRedo, filled = false)
        }

        Row(
            Modifier.align(Alignment.CenterEnd),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarButton(R.drawable.ic_note_add, "Add a page after this one", onAddPage, Modifier.testTag("add-page"))
            Box {
                BarButton(R.drawable.ic_more_vert, "Notebook options", { onMenuOpen(true) })
                DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenuOpen(false) }, content = menu)
            }
        }
    }
}

/** A round dark button with a white icon. [filled] = false sits inside the Undo/Redo pill. */
@Composable
private fun BarButton(
    @DrawableRes icon: Int,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = true,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(ButtonSize),
        shape = CircleShape,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (filled) EditorBarButton else EditorBar.copy(alpha = 0f),
            contentColor = EditorBarIcon,
            disabledContainerColor = if (filled) EditorBarButton else EditorBar.copy(alpha = 0f),
            disabledContentColor = EditorBarIconDisabled,
        ),
    ) {
        Icon(painterResource(icon), contentDescription = description, modifier = Modifier.size(22.dp))
    }
}

/** The bar is dark, so the status bar's clock and icons turn white while it shows. */
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

private val BarHeight = 52.dp
private val ButtonSize = 40.dp
