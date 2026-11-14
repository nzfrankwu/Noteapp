package com.nzfrankwu.noteapp.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nzfrankwu.noteapp.BuildConfig
import com.nzfrankwu.noteapp.domain.DateText
import java.time.Instant
import java.time.ZoneId

/**
 * Settings → Canvas. Signed out: the school's address and "Sign in" (opens the in-app browser).
 * Signed in: "Signed in as …", Last synced, Sync now and Sign out. An expired sign-in shows in red
 * with "Sign in again".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CanvasSettingsSection(viewModel: CanvasSettingsViewModel, onOpenBrowser: () -> Unit) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val input by viewModel.addressInput.collectAsStateWithLifecycle()
    val addressError by viewModel.addressError.collectAsStateWithLifecycle()
    val syncing by viewModel.syncing.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val openBrowser by viewModel.openBrowser.collectAsStateWithLifecycle()
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(openBrowser) {
        if (openBrowser) {
            viewModel.browserOpened()
            onOpenBrowser()
        }
    }
    if (!status.loaded) return

    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        val name = status.signedInAs
        if (name == null) {
            Text(
                "Sign in to import your courses and assignments. Noteapp only reads from Canvas; it never changes anything there.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = input,
                onValueChange = viewModel::onAddressChange,
                label = { Text("Your school's Canvas address") },
                placeholder = { Text("e.g. canvas.example.ac.nz") },
                singleLine = true,
                isError = addressError != null,
                supportingText = addressError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { viewModel.startSignIn() }),
                modifier = Modifier.fillMaxWidth().testTag("canvas-address"),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                Button(onClick = viewModel::startSignIn, enabled = input.isNotBlank(), modifier = Modifier.testTag("canvas-sign-in")) {
                    Text("Sign in")
                }
                if (BuildConfig.DEBUG) {
                    TextButton(onClick = viewModel::useFakeCanvas, modifier = Modifier.testTag("use-fake-canvas")) {
                        Text("Use fake Canvas (debug)")
                    }
                }
            }
        } else {
            Text("Signed in as $name", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("canvas-signed-in"))
            Text(
                listOfNotNull(status.address?.removePrefix("https://"), if (status.isFake) "fake Canvas for testing" else null)
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (status.signInExpired) {
                Text(
                    "Your Canvas sign-in expired. Sign in again to keep syncing; your tasks are kept.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                "Last synced: ${lastSyncedText(status.lastSyncedAt)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("canvas-last-synced"),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                if (status.signInExpired && !status.isFake) {
                    Button(onClick = viewModel::startSignIn) { Text("Sign in again") }
                }
                FilledTonalButton(onClick = viewModel::syncNow, enabled = !syncing, modifier = Modifier.testTag("canvas-sync-now")) {
                    if (syncing) {
                        CircularProgressIndicator(Modifier.size(ButtonDefaults.IconSize), strokeWidth = 2.dp)
                        Text("  Syncing…")
                    } else {
                        Text("Sync now")
                    }
                }
                TextButton(
                    onClick = { confirmSignOut = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("canvas-sign-out"),
                ) { Text("Sign out") }
            }
        }
        message?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("canvas-message"))
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out of Canvas?") },
            text = {
                Text(
                    "Noteapp stops syncing and forgets the Canvas sign-in. Your courses and tasks, " +
                        "including imported assignments, are kept.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { confirmSignOut = false; viewModel.signOut() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("dialog-confirm"),
                ) { Text("Sign out") }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") } },
        )
    }
}

private fun lastSyncedText(at: Instant?): String =
    if (at == null) "never" else DateText.dateTime(at, ZoneId.systemDefault(), Instant.now())
