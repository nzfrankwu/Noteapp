package com.nzfrankwu.noteapp.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nzfrankwu.noteapp.data.InkSettingsStore
import com.nzfrankwu.noteapp.domain.ink.FitMode
import com.nzfrankwu.noteapp.domain.ink.InkToolSettings
import com.nzfrankwu.noteapp.ui.components.SegmentedTabs
import kotlinx.coroutines.launch

/**
 * Settings → Notebooks: what a double-tap does (fit height by default; PHASE-2.md), and in debug
 * builds "Draw with finger" for testing on the emulator.
 */
@Composable
fun NotebookSettingsSection(store: InkSettingsStore, showDebug: Boolean) {
    val settings by store.settings.collectAsStateWithLifecycle(InkToolSettings())
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Double-tap a page to fit it to the screen:", style = MaterialTheme.typography.bodyMedium)
        SegmentedTabs(
            options = FitMode.entries,
            selected = settings.fitMode,
            label = { it.label },
            testTag = { "fit-${it.key}" },
            onSelect = { mode -> scope.launch { store.update { it.copy(fitMode = mode) } } },
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Paper for new notebooks:", style = MaterialTheme.typography.bodyMedium)
        SegmentedTabs(
            options = com.nzfrankwu.noteapp.domain.PageTemplate.entries,
            selected = settings.newNotebookPaper,
            label = { it.label },
            testTag = { "new-paper-${it.key}" },
            onSelect = { paper -> scope.launch { store.update { it.copy(newNotebookPaper = paper) } } },
            modifier = Modifier.fillMaxWidth(),
        )
        if (showDebug) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Draw with finger (debug)", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "For testing on the emulator. One finger writes; two fingers move the page.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.drawWithFinger,
                    onCheckedChange = { on -> scope.launch { store.update { it.copy(drawWithFinger = on) } } },
                    modifier = Modifier.testTag("draw-with-finger"),
                )
            }
        }
    }
}
