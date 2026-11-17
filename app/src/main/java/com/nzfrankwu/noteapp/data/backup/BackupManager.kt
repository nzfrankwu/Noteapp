package com.nzfrankwu.noteapp.data.backup

import com.nzfrankwu.noteapp.data.StorageLock
import com.nzfrankwu.noteapp.data.db.NoteAppDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** What the Settings → Backup section shows while exporting or importing. */
sealed interface BackupJob {
    data object Idle : BackupJob
    data class Working(val text: String, val progress: Float? = null) : BackupJob
    data class Done(val text: String) : BackupJob
    data class Failed(val text: String) : BackupJob
    /** An export was checked and waits for the user to confirm the restore. */
    data class ConfirmRestore(val manifest: BackupManifest) : BackupJob
    /** The restore is ready; the app restarts to swap it in. */
    data object RestartToRestore : BackupJob
}

/**
 * "Export everything" and "Import from export" (PHASE-6.md 6.2–6.3). The work runs in [scope]
 * (the app's own), so leaving Settings doesn't stop it.
 */
class BackupManager(
    private val snapshots: LocalSnapshot,
    private val swap: RestoreSwap,
    /** A scratch folder for snapshots (in the cache: it's rebuilt every time). */
    private val stagingDir: File,
    private val scope: CoroutineScope,
) {
    private val _job = MutableStateFlow<BackupJob>(BackupJob.Idle)
    val job: StateFlow<BackupJob> = _job

    private val busy get() = _job.value is BackupJob.Working

    /** Writes every notebook, folder, task and setting as one .zip to [open]'s stream. */
    fun export(open: () -> OutputStream?, onFailedCleanup: () -> Unit = {}) {
        if (busy) return
        _job.value = BackupJob.Working("Preparing the export…")
        scope.launch {
            try {
                val manifest = withContext(Dispatchers.IO) {
                    StorageLock.backup.withLock {
                        try {
                            val snapshot = snapshots.take(stagingDir)
                            val out = open() ?: throw BackupException("Couldn't open the file to save to.")
                            out.use { stream ->
                                ZipBackup.write(snapshot, stream) { p -> _job.value = BackupJob.Working("Exporting…", p) }
                            }
                            snapshot.manifest
                        } finally {
                            stagingDir.deleteRecursively()
                        }
                    }
                }
                _job.value = BackupJob.Done("Exported ${describe(manifest)}.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                runCatching(onFailedCleanup)
                _job.value = BackupJob.Failed(e.userMessage("The export didn't finish"))
            }
        }
    }

    /** Unpacks and checks an export; if it's good, asks the user to confirm ([BackupJob.ConfirmRestore]). */
    fun checkImport(open: () -> InputStream?) {
        if (busy) return
        _job.value = BackupJob.Working("Checking the export…")
        scope.launch {
            try {
                val manifest = withContext(Dispatchers.IO) {
                    StorageLock.backup.withLock {
                        try {
                            val input = open() ?: throw BackupException("Couldn't open the export.")
                            val manifest = input.use { ZipBackup.unpack(it, swap.staging) }
                            checkDatabase(manifest)
                            manifest
                        } catch (e: Exception) {
                            swap.cancel()
                            throw e
                        }
                    }
                }
                _job.value = BackupJob.ConfirmRestore(manifest)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _job.value = BackupJob.Failed(e.userMessage("This export can't be used"))
            }
        }
    }

    /** The user confirmed: mark the checked set ready. The screen then restarts the app. */
    fun confirmRestore() {
        val confirm = _job.value as? BackupJob.ConfirmRestore ?: return
        try {
            swap.markReady(confirm.manifest.createdAt)
            _job.value = BackupJob.RestartToRestore
        } catch (e: Exception) {
            swap.cancel()
            _job.value = BackupJob.Failed(e.userMessage("The restore couldn't be started"))
        }
    }

    fun cancelRestore() {
        if (_job.value !is BackupJob.ConfirmRestore) return
        scope.launch(Dispatchers.IO) { StorageLock.backup.withLock { swap.cancel() } }
        _job.value = BackupJob.Idle
    }

    fun dismiss() {
        if (_job.value is BackupJob.Done || _job.value is BackupJob.Failed) _job.value = BackupJob.Idle
    }

    /** Shows a message, e.g. how the last restore went (read once at start-up). */
    fun report(outcome: RestoreSwap.Outcome) {
        when (outcome) {
            is RestoreSwap.Outcome.Restored -> _job.value = BackupJob.Done("Restored the backup from ${BackupText.date(outcome.backupCreatedAt)}. Sign in to Canvas again to keep your tasks up to date.")
            is RestoreSwap.Outcome.Failed -> _job.value = BackupJob.Failed(outcome.message)
            RestoreSwap.Outcome.Nothing -> Unit
        }
    }

    fun beforeRestoreCopies(): List<RestoreSwap.KeptCopy> = swap.beforeRestoreCopies()

    suspend fun deleteCopy(copy: RestoreSwap.KeptCopy) = withContext(Dispatchers.IO) { swap.deleteCopy(copy) }

    /** The staged database must be readable, undamaged, and not from a newer Noteapp. */
    private fun checkDatabase(manifest: BackupManifest) {
        val info = DatabaseCheck.inspectReadOnly(File(swap.staging, BackupPaths.DATABASE))
        if (info.version > NoteAppDatabase.VERSION || manifest.databaseVersion > NoteAppDatabase.VERSION) {
            throw BackupException("This backup was made by a newer Noteapp. Update Noteapp first.")
        }
        if (info.version != manifest.databaseVersion) throw BackupException("This backup's database doesn't match its file list.")
    }

    private fun describe(m: BackupManifest) =
        "${BackupText.plural(m.counts.notebooks, "notebook")}, ${BackupText.plural(m.counts.tasks, "task")} (${BackupText.size(m.totalSize)})"

    private fun Exception.userMessage(prefix: String): String =
        if (this is BackupException) message.orEmpty() else "$prefix: ${message ?: javaClass.simpleName}"
}

/** Wording shared by the backup screens. */
object BackupText {
    fun plural(n: Int, word: String) = if (n == 1) "1 $word" else "$n ${word}s"

    fun size(bytes: Long): String = when {
        bytes >= 1024L * 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f GB", bytes / (1024.0 * 1024 * 1024))
        bytes >= 1024L * 1024 -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
        bytes >= 1024 -> "${bytes / 1024} KB"
        else -> "$bytes bytes"
    }

    fun date(millis: Long): String =
        java.text.SimpleDateFormat("d MMM yyyy, h:mm a", java.util.Locale.getDefault()).format(java.util.Date(millis))

    /** The suggested file name for an export, e.g. "Noteapp backup 2026-11-17.zip". */
    fun exportFileName(millis: Long): String =
        "Noteapp backup ${java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(millis))}.zip"
}
