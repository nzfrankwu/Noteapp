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

/**
 * Version 3 → 4 (Phase 2, step 2.1): subfolders and notebooks.
 *  - folders get `parent_id` (null = a top-level folder, which every existing folder stays).
 *  - new, empty `notebooks` and `pages` tables. (The handwriting itself goes in per-page files.)
 *
 * Existing rows are never copied or rewritten: the folders table only gains an empty column, so
 * every course, folder and task stays exactly as it was. The SQL matches Room's schema 4 exactly
 * (app/schemas/.../4.json); MigrationTest checks that.
 */
val MIGRATION_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `folders` ADD COLUMN `parent_id` TEXT " +
                "REFERENCES `folders`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_folders_parent_id` ON `folders` (`parent_id`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `notebooks` (`id` TEXT NOT NULL, `folder_id` TEXT, `title` TEXT NOT NULL, " +
                "`template` TEXT NOT NULL, `page_width` REAL NOT NULL, `page_height` REAL NOT NULL, " +
                "`created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `deleted_at` INTEGER, PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`folder_id`) REFERENCES `folders`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_notebooks_folder_id` ON `notebooks` (`folder_id`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `pages` (`id` TEXT NOT NULL, `notebook_id` TEXT NOT NULL, " +
                "`position` INTEGER NOT NULL, `template` TEXT, `created_at` INTEGER NOT NULL, " +
                "`updated_at` INTEGER NOT NULL, `deleted_at` INTEGER, PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`notebook_id`) REFERENCES `notebooks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pages_notebook_id` ON `pages` (`notebook_id`)")
    }
}

/**
 * Version 4 → 5 (Phase 3, step 3.1): pages of their own size, and pages of imported PDFs.
 * Pages get four empty (null) columns:
 *  - `width`, `height`: the page's own size (null = the notebook's size, as every page so far)
 *  - `pdf_file`, `pdf_page`: which PDF file in the notebook's folder, and which page of it
 *
 * It only adds empty columns, so every existing row and value stays exactly as it was, and every
 * existing page stays a plain page of its notebook's size.
 */
val MIGRATION_4_5: Migration = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `pages` ADD COLUMN `width` REAL")
        db.execSQL("ALTER TABLE `pages` ADD COLUMN `height` REAL")
        db.execSQL("ALTER TABLE `pages` ADD COLUMN `pdf_file` TEXT")
        db.execSQL("ALTER TABLE `pages` ADD COLUMN `pdf_page` INTEGER")
    }
}

/** Every migration, oldest first. Keep this list at the bottom of the file (see above). */
val ALL_MIGRATIONS: Array<Migration> = arrayOf(
    MIGRATION_1_2,
    MIGRATION_2_3,
    MIGRATION_3_4,
    MIGRATION_4_5,
)
