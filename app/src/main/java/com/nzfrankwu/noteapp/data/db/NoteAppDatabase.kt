package com.nzfrankwu.noteapp.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * NoteApp's on-device database: the single source of truth for courses, folders, tasks,
 * notebooks, pages, and the links and bookmarks between tasks and notebooks. Screens watch it live, and Canvas sync writes into it. Handwriting itself
 * is saved in per-page files next to it (see NotebookFiles).
 *
 * Changing any table means a new [VERSION] plus a migration; see Migrations.kt.
 */
@Database(
    entities = [
        Course::class, Folder::class, Task::class, Notebook::class, Page::class,
        TaskNotebookLink::class, TaskBookmark::class,
    ],
    version = NoteAppDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class NoteAppDatabase : RoomDatabase() {
    abstract fun courseDao(): CourseDao
    abstract fun folderDao(): FolderDao
    abstract fun taskDao(): TaskDao
    abstract fun notebookDao(): NotebookDao
    abstract fun linkDao(): LinkDao

    companion object {
        /** The current database version. See Migrations.kt before changing it. */
        const val VERSION = 8

        /** File name inside the app's private storage. Must never change (it would "lose" the data). */
        const val FILE_NAME = "noteapp.db"

        /** Every table (for watching changes, e.g. to back up soon after an edit). */
        val TABLES = arrayOf("courses", "folders", "tasks", "notebooks", "pages", "task_notebook_links", "task_bookmarks")

        fun create(context: Context): NoteAppDatabase =
            Room.databaseBuilder(context.applicationContext, NoteAppDatabase::class.java, FILE_NAME)
                .addMigrations(*ALL_MIGRATIONS)
                // No destructive fallback, ever (IDEA.md §5a). If a migration is missing, the app
                // should fail loudly in testing rather than quietly wipe everyone's notes.
                .build()
    }
}
