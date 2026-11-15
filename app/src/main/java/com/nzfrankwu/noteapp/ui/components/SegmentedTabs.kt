package com.nzfrankwu.noteapp.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/**
 * One choice out of a few, as a segmented button (e.g. Overview · Calendar · Timeline, or
 * 2 weeks | Month). The chosen segment is filled in the app's blue; no tick mark.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SegmentedTabs(
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    testTag: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                icon = {},
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                modifier = Modifier.testTag(testTag(option)),
            ) {
                Text(label(option), maxLines = 1)
            }
        }
    }
}
