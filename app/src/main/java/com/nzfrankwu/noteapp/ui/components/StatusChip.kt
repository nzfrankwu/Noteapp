package com.nzfrankwu.noteapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nzfrankwu.noteapp.R
import com.nzfrankwu.noteapp.domain.TaskStatus
import com.nzfrankwu.noteapp.ui.theme.Dimens
import com.nzfrankwu.noteapp.ui.theme.LocalStatusColors
import com.nzfrankwu.noteapp.ui.theme.TagColors

/**
 * A task's status tag, e.g. "Started", in that status's colour (see StatusColors.kt).
 * If [onStatusChange] is given, tapping it opens a menu of all statuses to pick from.
 */
@Composable
fun StatusChip(
    status: TaskStatus,
    onStatusChange: ((TaskStatus) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val statusColors = LocalStatusColors.current
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier) {
        Tag(
            text = status.label,
            colors = statusColors.of(status),
            showArrow = onStatusChange != null,
            modifier = Modifier
                .testTag("status-chip")
                .clip(MaterialTheme.shapes.extraSmall)
                .then(
                    if (onStatusChange != null) {
                        Modifier.clickable(role = Role.DropdownList, onClickLabel = "Change status") { menuOpen = true }
                    } else {
                        Modifier
                    },
                ),
        )
        if (onStatusChange != null) {
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                TaskStatus.entries.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                option.label,
                                fontWeight = if (option == status) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        },
                        leadingIcon = {
                            Box(Modifier.size(12.dp).background(statusColors.of(option).content, CircleShape))
                        },
                        onClick = {
                            menuOpen = false
                            if (option != status) onStatusChange(option)
                        },
                        modifier = Modifier.testTag("status-option-${option.key}"),
                    )
                }
            }
        }
    }
}

/** The automatic red "Overdue" badge (not a status: it comes from the due date). */
@Composable
fun OverdueBadge(modifier: Modifier = Modifier) {
    Tag("Overdue", LocalStatusColors.current.overdue, showArrow = false, modifier = modifier)
}

/** A small neutral label, e.g. "Past target" or "Removed from Canvas". */
@Composable
fun InfoBadge(text: String, modifier: Modifier = Modifier) {
    Tag(
        text = text,
        colors = TagColors(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurfaceVariant),
        showArrow = false,
        modifier = modifier,
    )
}

/** The rounded tag shape shared by status tags and badges. */
@Composable
private fun Tag(text: String, colors: TagColors, showArrow: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.heightIn(min = Dimens.TagHeight),
        shape = MaterialTheme.shapes.extraSmall,
        color = colors.container,
        contentColor = colors.content,
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = if (showArrow) 2.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(vertical = 4.dp))
            if (showArrow) {
                Icon(painterResource(R.drawable.ic_arrow_drop_down), contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}
