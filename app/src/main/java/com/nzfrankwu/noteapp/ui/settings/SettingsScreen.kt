package com.nzfrankwu.noteapp.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import android.content.Intent
import android.provider.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.nzfrankwu.noteapp.reminders.Notifications
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nzfrankwu.noteapp.BuildConfig
import com.nzfrankwu.noteapp.R
import com.nzfrankwu.noteapp.ui.components.RoundIconButton
import com.nzfrankwu.noteapp.ui.theme.Dimens
import com.nzfrankwu.noteapp.ui.theme.NoteAppTheme

/**
 * Settings: Canvas (sign-in arrives in step 5.1), Reminders and About. Backup comes in Phase 6.
 * [onTestReminder] is only given in debug builds: it sets a test reminder for 1 minute from now.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onTestReminder: (() -> Unit)? = null,
    canvas: CanvasSettingsViewModel? = null,
    onOpenCanvasBrowser: () -> Unit = {},
    onOpenCourses: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    RoundIconButton(
                        icon = R.drawable.ic_arrow_back,
                        contentDescription = "Back",
                        onClick = onBack,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        // On wide screens, keep settings a comfortable reading width instead of stretching.
        Box(
            Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = Dimens.ReadingMaxWidth)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(Dimens.ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.PaneGap),
            ) {
                SettingsSection("Canvas") {
                    if (canvas != null) {
                        CanvasSettingsSection(canvas, onOpenCanvasBrowser)
                    } else {
                        Text(
                            text = "Sign in to Canvas to import your courses and assignments.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                SettingsSection("Courses") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Archive last semester's courses to keep Home tidy, or bring one back.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        FilledTonalButton(onClick = onOpenCourses, modifier = Modifier.testTag("open-courses")) {
                            Text("Manage courses")
                        }
                    }
                }
                SettingsSection("Reminders") { RemindersSettings(onTestReminder) }
                SettingsSection("About") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Version", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = BuildConfig.VERSION_NAME,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RemindersSettings(onTestReminder: (() -> Unit)?) {
    val context = LocalContext.current
    // Re-checked whenever you come back to this screen (e.g. from Android's notification settings).
    var notificationsOn by remember { mutableStateOf(Notifications.canNotify(context)) }
    LifecycleResumeEffect(Unit) {
        notificationsOn = Notifications.canNotify(context)
        onPauseOrDispose {}
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "You get a reminder 1 day and 2 hours before each task's target date (or its due date, if it has no target date).",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            if (notificationsOn) "Notifications are on." else "Notifications are off for Noteapp, so reminders can't show.",
            style = MaterialTheme.typography.bodyMedium,
            color = if (notificationsOn) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("notification-status"),
        )
        Text(
            "If reminders arrive late or not at all, allow Noteapp to run in the background in the tablet's battery settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                runCatching { context.startActivity(intent) }
            }) { Text("Notification settings") }
            if (onTestReminder != null) {
                OutlinedButton(onClick = onTestReminder, modifier = Modifier.testTag("test-reminder")) {
                    Text("Test reminder in 1 minute")
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(Modifier.padding(Dimens.PanelPadding)) { content() }
        }
    }
}

@Preview(widthDp = 1120, heightDp = 800)
@Composable
private fun SettingsPreview() {
    NoteAppTheme { SettingsScreen(onBack = {}) }
}
