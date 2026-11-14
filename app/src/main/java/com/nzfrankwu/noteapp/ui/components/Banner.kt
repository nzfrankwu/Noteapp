package com.nzfrankwu.noteapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nzfrankwu.noteapp.ui.theme.Dimens
import com.nzfrankwu.noteapp.ui.theme.LocalStatusColors

/**
 * A message strip across the top of a screen, with buttons, e.g. "Canvas sign-in expired ·
 * Sign in again". [warning] uses the red Overdue colours; otherwise the soft blue accent.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Banner(
    text: String,
    modifier: Modifier = Modifier,
    warning: Boolean = false,
    actions: @Composable () -> Unit,
) {
    val container: Color
    val content: Color
    if (warning) {
        val tag = LocalStatusColors.current.overdue
        container = tag.container
        content = tag.content
    } else {
        container = MaterialTheme.colorScheme.primaryContainer
        content = MaterialTheme.colorScheme.onPrimaryContainer
    }
    Surface(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = container, contentColor = content) {
        Column(Modifier.padding(horizontal = Dimens.TilePadding, vertical = 10.dp)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 6.dp))
                actions()
            }
        }
    }
}
