package com.nzfrankwu.noteapp.data.db

import androidx.room.migration.Migration

/**
 * Every database migration, oldest first. IDEA.md §5a: an app update must never lose data.
 *
 * How to change a table (add a column, a new table, ...):
 *  1. Change the entity class and bump [NoteAppDatabase.VERSION] (e.g. 1 → 2).
 *  2. Build. Room writes the new schema to app/schemas/.../2.json. Commit that file.
 *  3. Add a Migration below (e.g. MIGRATION_1_2) that turns the old tables into the new ones
 *     WITHOUT losing rows: "ALTER TABLE ... ADD COLUMN ...", or create-new / copy / drop / rename.
 *  4. Record version 2's identity hash in SchemaHistoryTest, and extend MigrationTest with a
 *     check that version-1 data is still correct after migrating.
 *  5. Run the unit tests and the emulator tests (connectedDebugAndroidTest).
 *
 * Never "fix" a missing migration by letting Room delete and recreate the database.
 */
val ALL_MIGRATIONS: Array<Migration> = arrayOf()
