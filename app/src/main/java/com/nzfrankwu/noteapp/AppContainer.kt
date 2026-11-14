package com.nzfrankwu.noteapp

import android.content.Context
import com.nzfrankwu.noteapp.data.FolderRepository
import com.nzfrankwu.noteapp.data.TaskRepository
import com.nzfrankwu.noteapp.data.db.NoteAppDatabase
import com.nzfrankwu.noteapp.reminders.ReminderScheduler

/**
 * Creates the app's long-lived parts once and hands them to screens ("manual dependency
 * injection"). Everything is created lazily: the database file only appears on first use.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: NoteAppDatabase by lazy { NoteAppDatabase.create(appContext) }

    val folderRepository: FolderRepository by lazy { FolderRepository(database) }

    val taskRepository: TaskRepository by lazy { TaskRepository(database) }

    val reminderScheduler: ReminderScheduler by lazy { ReminderScheduler(appContext) }
}
