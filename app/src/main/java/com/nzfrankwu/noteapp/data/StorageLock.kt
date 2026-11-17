package com.nzfrankwu.noteapp.data

import kotlinx.coroutines.sync.Mutex

/**
 * The app-wide locks that keep backups consistent (PHASE-6.md, "Consistent snapshot").
 */
object StorageLock {
    /**
     * Held while a page file is written (one save at a time across every open notebook) and while
     * a backup copies the database and page files, so a backup never sees half of a save.
     */
    val pages = Mutex()

    /**
     * Held for a whole export or restore, and by anything that deletes notebook files for good
     * (the 30-day purge, the clean-up of unused files), so files a backup is still reading can't
     * disappear under it. Never take [pages] first and then this one.
     *
     * The Drive backup holds it only while it takes its local snapshot (Phase 7, 7.2); during the
     * long upload it pins the notebooks it still reads ([com.nzfrankwu.noteapp.data.backup.BackupPins])
     * instead. The PDF copies hold it for one notebook's export at a time.
     */
    val backup = Mutex()
}
