package com.nzfrankwu.noteapp.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupPathsTest {
    @Test
    fun notebookFiles_areIncluded() {
        assertTrue(BackupPaths.includeFromFilesDir("notebooks/nb-1/page-1.ink"))
        assertTrue(BackupPaths.includeFromFilesDir("notebooks/nb-1/pdf-0a1b.pdf"))
        assertTrue(BackupPaths.includeFromFilesDir("notebooks/nb-1/images/abc.jpg"))
        assertTrue(BackupPaths.includeFromFilesDir("notebooks/nb-1/bookmarks/abc.png"))
    }

    @Test
    fun unfinishedSaves_areNeverIncluded() {
        assertFalse(BackupPaths.includeFromFilesDir("notebooks/nb-1/page-1.ink.saving"))
        assertFalse(BackupPaths.includeFromFilesDir("notebooks/nb-1/bookmarks/abc.png.writing"))
        assertFalse(BackupPaths.includeFromFilesDir("notebooks/stray-file"))
    }

    @Test
    fun canvasAndPrivateStores_areNeverIncluded() {
        // IDEA.md §2.9 / §3.5: the Canvas sign-in never goes into a backup.
        assertFalse(BackupPaths.includeFromFilesDir("datastore/canvas_settings.preferences_pb"))
        assertFalse(BackupPaths.includeFromFilesDir("datastore/backup_settings.preferences_pb"))
        assertFalse(BackupPaths.includeFromFilesDir("shared-inbox/x.pdf"))
        assertFalse(BackupPaths.includeFromFilesDir("app_webview/Default/Cookies"))
        assertFalse(BackupPaths.isRestorable("files/datastore/canvas_settings.preferences_pb"))
        assertFalse(BackupPaths.isRestorable("app_webview/Default/Cookies"))
        assertFalse(BackupPaths.isRestorable("shared_prefs/reminders.xml"))
    }

    @Test
    fun theTwoSettingsStores_areIncluded() {
        assertEquals(listOf("ink_settings.preferences_pb", "view_settings.preferences_pb"), BackupPaths.SETTINGS_FILES)
        assertTrue(BackupPaths.includeFromFilesDir("datastore/ink_settings.preferences_pb"))
        assertTrue(BackupPaths.includeFromFilesDir("datastore/view_settings.preferences_pb"))
    }

    @Test
    fun restorablePaths_cantEscapeTheAppsFolders() {
        assertTrue(BackupPaths.isRestorable("manifest.json"))
        assertTrue(BackupPaths.isRestorable("db/noteapp.db"))
        assertTrue(BackupPaths.isRestorable("files/notebooks/nb/p.ink"))
        for (bad in listOf(
            "../evil", "files/notebooks/../../evil.ink", "/etc/passwd", "files\\notebooks\\a\\b.ink",
            "files/notebooks//x.ink", "C:/x", "db/other.db", "files/notebooks/./a/b.ink", "",
        )) {
            assertFalse(bad, BackupPaths.isRestorable(bad))
        }
    }

    @Test
    fun manifest_refusesUnexpectedPaths() {
        val md5 = Md5.of(ByteArray(0))
        fun manifest(vararg paths: String) = BackupManifest(
            createdAt = 1, appVersion = "1.0", databaseVersion = 7,
            files = paths.map { BackupEntry(it, 0, md5) },
        ).toJson()
        BackupManifest.fromJson(manifest("db/noteapp.db", "files/notebooks/a/b.ink"))
        for (bad in listOf(
            manifest("db/noteapp.db", "../x"),
            manifest("files/notebooks/a/b.ink"), // no database
            manifest("db/noteapp.db", "db/noteapp.db"), // twice
            manifest("db/noteapp.db", "files/datastore/canvas_settings.preferences_pb"),
            "not json",
        )) {
            try {
                BackupManifest.fromJson(bad)
                fail("Accepted: $bad")
            } catch (e: BackupException) {
                // expected
            }
        }
    }

    @Test
    fun manifest_fromANewerApp_isRefused() {
        val json = BackupManifest(format = BackupManifest.FORMAT + 1, createdAt = 1, appVersion = "9", databaseVersion = 7, files = emptyList()).toJson()
        try {
            BackupManifest.fromJson(json)
            fail()
        } catch (e: BackupException) {
            assertTrue(e.message!!.contains("newer"))
        }
    }

    @Test
    fun manifest_roundTrips() {
        val m = BackupManifest(
            createdAt = 1_763_000_000_000, appVersion = "1.0", databaseVersion = 7,
            counts = BackupCounts(folders = 3, notebooks = 2, pages = 10, tasks = 5),
            files = listOf(BackupEntry("db/noteapp.db", 4096, Md5.of("x".toByteArray()))),
        )
        assertEquals(m, BackupManifest.fromJson(m.toJson()))
        assertEquals(4096, m.totalSize)
    }

    @Test
    fun md5_matchesTheKnownValue() {
        assertEquals("900150983cd24fb0d6963f7d28e17f72", Md5.of("abc".toByteArray()))
        assertEquals("900150983cd24fb0d6963f7d28e17f72", Md5.of("abc".byteInputStream()))
        assertTrue(Md5.isValid("900150983cd24fb0d6963f7d28e17f72"))
        assertFalse(Md5.isValid("900150983CD24FB0D6963F7D28E17F72"))
    }
}
