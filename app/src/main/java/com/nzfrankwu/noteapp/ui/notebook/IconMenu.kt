package com.nzfrankwu.noteapp.ui.notebook

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.nzfrankwu.noteapp.ui.theme.EditorBarButton
import com.nzfrankwu.noteapp.ui.theme.EditorBarIcon

/**
 * One button of an [IconMenu]. [label] is its tooltip and its content description (screen
 * readers, tests). [selected] marks a mode that's on (e.g. Resize).
 */
data class IconMenuItem(
    @DrawableRes val icon: Int,
    val label: String,
    val testTag: String? = null,
    val destructive: Boolean = false,
    val selected: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * A dark pill of icon buttons with no text (PHASE-4.md 4.4), used for the lasso menu, the PDF
 * text menu and the Paste bubble. Holding an icon shows its name.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconMenu(items: List<IconMenuItem>, modifier: Modifier = Modifier) {
    Row(
        modifier
            .shadow(6.dp, RoundedCornerShape(50))
            .background(EditorBarButton, RoundedCornerShape(50))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(50))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            @Suppress("DEPRECATION")
            TooltipBox(
                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = { PlainTooltip { Text(item.label) } },
                state = rememberTooltipState(),
            ) {
                IconButton(
                    onClick = item.onClick,
                    modifier = Modifier.size(44.dp).then(if (item.testTag != null) Modifier.testTag(item.testTag) else Modifier),
                    shape = CircleShape,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (item.selected) Color.White.copy(alpha = 0.22f) else Color.Transparent,
                        contentColor = if (item.destructive) DestructiveIcon else EditorBarIcon,
                    ),
                ) {
                    Icon(painterResource(item.icon), contentDescription = item.label, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

/** A light red that reads on the dark pill (Delete). */
private val DestructiveIcon = Color(0xFFFF8A80)
