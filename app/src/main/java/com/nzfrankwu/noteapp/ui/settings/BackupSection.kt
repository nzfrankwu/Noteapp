package com.nzfrankwu.noteapp.ui.settings

import android.content.Context
import android.content.Intent
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.nzfrankwu.noteapp.data.backup.BackupJob
import com.nzfrankwu.noteapp.data.backup.BackupManager
import com.nzfrankwu.noteapp.data.backup.BackupManifest
import com.nzfrankwu.noteapp.data.backup.BackupText
import com.nzfrankwu.noteapp.data.backup.RestoreSwap
import kotlinx.coroutines.launch

/**
 * Settings → Backup (PHASE-6.md 6.2–6.3): "Export everything" to a .zip saved anywhere, and
 * "Import from export", which replaces everything on this tablet after a confirmation.
 * Google Drive backup ([DriveBackupBlock]) comes first when the app has it.
 */
@Composable
fun BackupSection(manager: BackupManager, drive: com.nzfrankwu.noteapp.data.backup.drive.DriveConnection? = null) {
    val context = LocalContext.current
    val job by manager.job.collectAsState()
    var copies by remember { mutableStateOf(manager.beforeRestoreCopies()) }
    var deleting by remember { mutableStateOf<RestoreSwap.KeptCopy?>(null) }
    val scope = rememberCoroutineScope()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            manager.export(
                open = { context.contentResolver.openOutputStream(uri, "wt") },
                // Don't leave a broken half-written .zip behind.
                onFailedCleanup = { DocumentsContract.deleteDocument(context.contentResolver, uri) },
            )
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) manager.checkImport { context.contentResolver.openInputStream(uri) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (drive != null) {
            DriveBackupBlock(drive, manager)
            androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Export", style = MaterialTheme.typography.titleSmall)
        }
        Text(
            "Export everything (notebooks, PDFs, pictures, folders, tasks and pen settings) as one .zip file " +
                "and keep it somewhere safe, like Google Drive or a computer. Canvas sign-in is never included.",
            style = MaterialTheme.typography.bodyMedium,
        )
        val working = job is BackupJob.Working
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = { exportLauncher.launch(BackupText.exportFileName(System.currentTimeMillis())) },
                enabled = !working,
                modifier = Modifier.testTag("export-everything"),
            ) { Text("Export everything") }
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
                enabled = !working,
                modifier = Modifier.testTag("import-export"),
            ) { Text("Import from export") }
        }
        when (val j = job) {
            is BackupJob.Working -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(j.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("backup-status"))
                if (j.progress != null) {
                    LinearProgressIndicator(progress = { j.progress }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
            is BackupJob.Done -> StatusText(j.text, error = false, onDismiss = manager::dismiss)
            is BackupJob.Failed -> StatusText(j.text, error = true, onDismiss = manager::dismiss)
            else -> Unit
        }
        for (copy in copies) {
            Text(
                "A copy of what was on this tablet before the restore on ${BackupText.date(copy.restoredAt)} is kept here " +
                    "(${BackupText.size(copy.size)}), in case you need it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { deleting = copy }, modifier = Modifier.testTag("delete-before-restore")) { Text("Delete this copy") }
        }
    }

    (job as? BackupJob.ConfirmRestore)?.let { confirm ->
        ConfirmRestoreDialog(confirm.manifest, onConfirm = manager::confirmRestore, onCancel = manager::cancelRestore)
    }
    if (job is BackupJob.RestartToRestore) {
        LaunchedEffect(Unit) { restartApp(context) }
    }
    deleting?.let { copy ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete the copy from before the restore?") },
            text = { Text("This deletes the notebooks, tasks and settings that were on this tablet before the restore on ${BackupText.date(copy.restoredAt)}. It can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    scope.launch {
                        manager.deleteCopy(copy)
                        copies = manager.beforeRestoreCopies()
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun StatusText(text: String, error: Boolean, onDismiss: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).testTag("backup-status"),
        )
        TextButton(onClick = onDismiss) { Text("OK") }
    }
}

@Composable
private fun ConfirmRestoreDialog(manifest: BackupManifest, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val c = manifest.counts
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Restore this backup?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Made ${BackupText.date(manifest.createdAt)}\n" +
                        "${BackupText.plural(c.folders, "folder")} · ${BackupText.plural(c.notebooks, "notebook")} · " +
                        "${BackupText.plural(c.pages, "page")} · ${BackupText.plural(c.tasks, "task")} · ${BackupText.size(manifest.totalSize)}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("restore-summary"),
                )
                Text(
                    "This replaces everything in Noteapp on this tablet: notebooks, folders, tasks and pen settings. " +
                        "What's here now is kept as a copy on this tablet until you delete it in Settings. Noteapp restarts to finish.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm, modifier = Modifier.testTag("confirm-restore")) { Text("Restore and restart") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

/** Starts Noteapp afresh, so the restore is swapped in before anything opens the data. */
private fun restartApp(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    context.startActivity(intent)
    Runtime.getRuntime().exit(0)
}
