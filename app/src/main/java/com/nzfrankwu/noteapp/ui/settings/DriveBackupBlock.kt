package com.nzfrankwu.noteapp.ui.settings

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.nzfrankwu.noteapp.data.backup.BackupJob
import com.nzfrankwu.noteapp.data.backup.BackupManager
import com.nzfrankwu.noteapp.data.backup.BackupText
import com.nzfrankwu.noteapp.data.backup.drive.DriveBackup
import com.nzfrankwu.noteapp.data.backup.drive.DriveConnection
import com.nzfrankwu.noteapp.data.backup.drive.DriveText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What the "Restore from Google Drive" dialog shows. */
private sealed interface Pick {
    data object Loading : Pick
    data class List(val snapshots: kotlin.collections.List<DriveBackup.RemoteSnapshot>, val afterConnect: Boolean) : Pick
    data class Failed(val message: String) : Pick
}

/**
 * Settings → Backup → Google Drive (PHASE-6.md 6.7, 6.10, 6.11): connect an account, see when the
 * last backup worked, Back up now, Wi-Fi only, Restore from Google Drive, Disconnect.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DriveBackupBlock(drive: DriveConnection, manager: BackupManager) {
    val status by drive.status.collectAsState(initial = null)
    val running by drive.running.collectAsState(initial = null)
    val connect by drive.connect.collectAsState()
    val job by manager.job.collectAsState()
    var pick by remember { mutableStateOf<Pick?>(null) }
    var confirmDisconnect by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // "3 min ago" stays current while Settings is open.
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis()
        }
    }

    val googleScreen = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        drive.userAnswered(result.resultCode == Activity.RESULT_OK, result.data)
    }
    LaunchedEffect(connect) {
        when (val c = connect) {
            is DriveConnection.Connect.NeedsUser -> {
                drive.shown()
                googleScreen.launch(IntentSenderRequest.Builder(c.intent).build())
            }
            is DriveConnection.Connect.Connected -> {
                if (c.offerRestore.isNotEmpty()) pick = Pick.List(c.offerRestore, afterConnect = true)
                drive.dismiss()
            }
            else -> Unit
        }
    }

    fun openRestore() {
        pick = Pick.Loading
        scope.launch {
            pick = try {
                Pick.List(drive.backup.snapshots(), afterConnect = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Pick.Failed(e.message ?: "Couldn't look in Google Drive.")
            }
        }
    }

    val s = status ?: return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Google Drive", style = MaterialTheme.typography.titleSmall)
        val connecting = connect is DriveConnection.Connect.Working
        if (!s.connected) {
            Text(
                "Back up everything to your Google Drive automatically: every day, and a few minutes after you change " +
                    "something. Noteapp can only see the files it makes there. Canvas sign-in is never included.",
                style = MaterialTheme.typography.bodyMedium,
            )
            FilledTonalButton(onClick = drive::startConnect, enabled = !connecting, modifier = Modifier.testTag("drive-connect")) {
                Text("Connect Google Drive")
            }
        } else {
            Text("Backing up to ${s.email}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("drive-account"))
            val attention = DriveText.needsAttention(s, now) || s.lastError != null
            Text(
                running?.text ?: DriveText.summary(s, now, running = false),
                style = MaterialTheme.typography.bodyMedium,
                color = if (attention && running == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("drive-status"),
            )
            running?.let { r ->
                val p = r.progress
                if (p != null && p > 0f) LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (s.needsSignIn) {
                    FilledTonalButton(onClick = drive::startConnect, enabled = !connecting, modifier = Modifier.testTag("drive-sign-in-again")) {
                        Text("Sign in again")
                    }
                }
                FilledTonalButton(onClick = drive::backUpNow, enabled = running == null, modifier = Modifier.testTag("drive-back-up-now")) {
                    Text("Back up now")
                }
                OutlinedButton(onClick = ::openRestore, enabled = job !is BackupJob.Working, modifier = Modifier.testTag("drive-restore")) {
                    Text("Restore from Google Drive")
                }
                TextButton(onClick = { confirmDisconnect = true }, modifier = Modifier.testTag("drive-disconnect")) { Text("Disconnect") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Only back up automatically on Wi-Fi", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Back up now uses any connection.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = s.wifiOnly, onCheckedChange = drive::setWifiOnly, modifier = Modifier.testTag("drive-wifi-only"))
            }
            Text("Update PDF copies", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Readable copies of the notebooks you changed, in Noteapp › Notes (PDF) in your Drive, to open on " +
                    "a computer. Back up now updates them straight away.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            com.nzfrankwu.noteapp.ui.components.SegmentedTabs(
                options = com.nzfrankwu.noteapp.data.backup.drive.PdfInterval.CHOICES,
                selected = s.pdfIntervalMinutes,
                label = { com.nzfrankwu.noteapp.data.backup.drive.PdfInterval.label(it) },
                testTag = { "pdf-interval-$it" },
                onSelect = drive::setPdfInterval,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        when (val c = connect) {
            DriveConnection.Connect.Working -> Text("Connecting to Google…", style = MaterialTheme.typography.bodyMedium)
            is DriveConnection.Connect.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    c.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f).testTag("drive-connect-error"),
                )
                TextButton(onClick = drive::dismiss) { Text("OK") }
            }
            else -> Unit
        }
    }

    pick?.let { p ->
        RestorePickDialog(
            pick = p,
            onPick = { snapshot ->
                pick = null
                manager.checkDriveRestore(drive.backup, snapshot)
            },
            onDismiss = { pick = null },
        )
    }
    if (confirmDisconnect) {
        AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            title = { Text("Disconnect Google Drive?") },
            text = {
                Text(
                    "Noteapp stops backing up to ${s.email}. The backup already in Google Drive stays there " +
                        "(in the Noteapp folder) until you delete it yourself.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDisconnect = false
                    drive.disconnect()
                }, modifier = Modifier.testTag("drive-confirm-disconnect")) { Text("Disconnect") }
            },
            dismissButton = { TextButton(onClick = { confirmDisconnect = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun RestorePickDialog(pick: Pick, onPick: (DriveBackup.RemoteSnapshot) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (pick is Pick.List && pick.afterConnect) "Restore your notes?" else "Restore from Google Drive") },
        text = {
            when (pick) {
                Pick.Loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Text("Looking for backups…")
                }
                is Pick.Failed -> Text(pick.message, color = MaterialTheme.colorScheme.error)
                is Pick.List -> if (pick.snapshots.isEmpty()) {
                    Text("There's no Noteapp backup in this Google Drive yet.", modifier = Modifier.testTag("drive-no-backups"))
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (pick.afterConnect) "This Google Drive has Noteapp backups. Pick one to put your notes back on this tablet, or start fresh."
                            else "Pick a backup. It replaces everything in Noteapp on this tablet (you'll be asked first).",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        LazyColumn(Modifier.heightIn(max = 320.dp).testTag("drive-backups")) {
                            itemsIndexed(pick.snapshots, key = { _, s -> s.fileId }) { i, snapshot ->
                                Column(
                                    Modifier.fillMaxWidth().clickable { onPick(snapshot) }.padding(vertical = 10.dp),
                                ) {
                                    Text(BackupText.date(snapshot.createdAt), style = MaterialTheme.typography.bodyLarge)
                                    if (i == 0) {
                                        Text("Newest", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(if (pick is Pick.List && pick.afterConnect) "Start fresh" else "Cancel") }
        },
    )
}
