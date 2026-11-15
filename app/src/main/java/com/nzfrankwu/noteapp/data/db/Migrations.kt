package com.nzfrankwu.noteapp.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// Database migrations. IDEA.md §5a: an app update must never lose data.
//
// How to change a table (add a column, a new table, ...):
//  1. Change the entity class and bump NoteAppDatabase.VERSION (e.g. 2 → 3).
//  2. Build. Room writes the new schema to app/schemas/.../3.json. Commit that file.
//  3. Add a Migration below (e.g. MIGRATION_2_3) that turns the old tables into the new ones
//     WITHOUT losing rows: "ALTER TABLE ... ADD COLUMN ...", or create-new / copy / drop / rename.
//     Then add it to the END of ALL_MIGRATIONS (at the bottom of this file).
//  4. Record version 3's identity hash in SchemaHistoryTest, and extend MigrationTest with a
//     check that data saved by the older versions is still correct after migrating.
//  5. Run the unit tests and the emulator tests (connectedDebugAndroidTest).
//
// Rules that keep every install alike:
//  - Add a new column as the LAST field of its entity. "ALTER TABLE ... ADD COLUMN" always puts
//    it last, so a fresh install and an upgraded one then have their columns in the same order.
//  - When a migration copies rows between tables, name every column. Never "SELECT *".
//  - Declare each migration ABOVE ALL_MIGRATIONS: Kotlin sets up top-level values in file
//    order, so a migration declared below the list would be put into it as null.
//
// Never "fix" a missing migration by letting Room delete and recreate the database.

/**
 * Version 1 → 2 (Phase 1b, step B.3): tasks get `available_at`, Canvas's "available from" time
 * (`unlock_at`), for the timeline.
 *
 * It only adds an empty (null) column, so every existing row and value stays exactly as it was.
 * Canvas tasks get their "available from" on the next Canvas sync; your own tasks keep null.
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `tasks` ADD COLUMN `available_at` INTEGER")
    }
}

/**
 * Version 2 → 3 (Phase 1b, step B.8): courses get `archived_at`, for archiving last semester's
 * courses.
 *
 * It only adds an empty (null) column, so every existing row and value stays exactly as it was,
 * and every course starts out current (not archived).
 */
val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `courses` ADD COLUMN `archived_at` INTEGER")
    }
}

/** Every migration, oldest first. Keep this list at the bottom of the file (see above). */
val ALL_MIGRATIONS: Array<Migration> = arrayOf(
    MIGRATION_1_2,
    MIGRATION_2_3,
)
