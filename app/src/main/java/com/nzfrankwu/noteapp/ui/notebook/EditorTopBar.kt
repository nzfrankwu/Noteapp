package com.nzfrankwu.noteapp.ui.notebook

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
 * Back on the left; Undo and Redo in a pill in the middle; Add page and ⋮ on the right.
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
    menuOpen: Boolean,
    onMenuOpen: (Boolean) -> Unit,
    menu: @Composable ColumnScope.() -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(BarHeight)
            .padding(horizontal = 12.dp),
    ) {
        BarButton(R.drawable.ic_arrow_back, "Back", onBack, Modifier.align(Alignment.CenterStart))

        Row(
            Modifier
                .align(Alignment.Center)
                .shadow(3.dp, RoundedCornerShape(50))
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
        modifier = if (filled) modifier.size(ButtonSize).shadow(3.dp, CircleShape) else modifier.size(ButtonSize),
        shape = CircleShape,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (filled) EditorBarButton else Color.Transparent,
            contentColor = EditorBarIcon,
            disabledContainerColor = if (filled) EditorBarButton else Color.Transparent,
            disabledContentColor = EditorBarIconDisabled,
        ),
    ) {
        Icon(painterResource(icon), contentDescription = description, modifier = Modifier.size(22.dp))
    }
}

private val BarHeight = 64.dp
private val ButtonSize = 44.dp
