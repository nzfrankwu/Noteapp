package com.nzfrankwu.noteapp.data.backup

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nzfrankwu.noteapp.data.FolderRepository
import com.nzfrankwu.noteapp.data.FolderResult
import com.nzfrankwu.noteapp.data.NotebookFiles
import com.nzfrankwu.noteapp.data.NotebookRepository
import com.nzfrankwu.noteapp.data.NotebookResult
import com.nzfrankwu.noteapp.data.StorageLock
import com.nzfrankwu.noteapp.data.db.NoteAppDatabase
import com.nzfrankwu.noteapp.domain.ink.InkStroke
import com.nzfrankwu.noteapp.domain.ink.InkTool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/** PHASE-6.md 6.1: a snapshot of the real database file (with its -wal) plus the notebook files. */
@RunWith(AndroidJUnit4::class)
class LocalSnapshotTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbFile = context.getDatabasePath("snapshot-test.db")
    private val root = File(context.cacheDir, "snapshot-test")
    private val filesDir = File(root, "files")
    private val staging = File(root, "staging")
    private lateinit var db: NoteAppDatabase
    private lateinit var notebooks: NotebookRepository
    private lateinit var files: NotebookFiles
    private lateinit var snapshots: LocalSnapshot
    private lateinit var folderId: String

    @Before
    fun setUp() = runBlocking {
        context.deleteDatabase(dbFile.name)
        root.deleteRecursively()
        db = Room.databaseBuilder(context, NoteAppDatabase::class.java, dbFile.name).build()
        notebooks = NotebookRepository(db)
        files = NotebookFiles(filesDir)
        snapshots = LocalSnapshot(db, dbFile, filesDir, appVersion = "test")
        folderId = (FolderRepository(db).createFolder("ELECTENG 292", null) as FolderResult.Success).folderId
        File(filesDir, "datastore").mkdirs()
        File(filesDir, "datastore/ink_settings.preferences_pb").writeText("pens")
        File(filesDir, "datastore/canvas_settings.preferences_pb").writeText("canvas sign-in")
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(dbFile.name)
        root.deleteRecursively()
    }

    private fun stroke(x: Float) = InkStroke(
        id = "s-$x", tool = InkTool.PEN, colorArgb = 0xff000000.toInt(), size = 3f,
        xs = floatArrayOf(x, x + 20f), ys = floatArrayOf(10f, 30f), times = intArrayOf(0, 16),
        pressure = floatArrayOf(0.5f, 0.5f), tilt = floatArrayOf(0f, 0f), orientation = floatArrayOf(0f, 0f),
    )

    private suspend fun notebookWithPage(title: String): Pair<String, String> {
        val id = (notebooks.createNotebook(folderId, title) as NotebookResult.Success).id
        val pageId = notebooks.getPages(id).first().id
        StorageLock.pages.withLock { files.write(id, pageId, listOf(stroke(1f))) }
        return id to pageId
    }

    private suspend fun take(): Snapshot = StorageLock.backup.withLock { snapshots.take(staging) }

    @Test
    fun snapshot_holdsTheDatabase_pages_andPenSettings_butNeverCanvas() = runBlocking {
        val (nb, page) = notebookWithPage("Lecture 1")
        files.writeAsset(nb, "images/a.png", ByteArrayInputStream(byteArrayOf(1, 2, 3)))
        File(filesDir, "notebooks/$nb/$page.ink.saving").writeText("half a save")

        val snapshot = take()
        val paths = snapshot.manifest.files.map { it.path }
        assertTrue(BackupPaths.DATABASE in paths)
        assertTrue("files/notebooks/$nb/$page.ink" in paths)
        assertTrue("files/notebooks/$nb/images/a.png" in paths)
        assertTrue("files/datastore/ink_settings.preferences_pb" in paths)
        assertFalse(paths.any { it.contains("canvas") || it.endsWith(".saving") })
        assertEquals(NoteAppDatabase.VERSION, snapshot.manifest.databaseVersion)
        assertEquals(BackupCounts(folders = 1, notebooks = 1, pages = 1, tasks = 0), snapshot.manifest.counts)
        for (entry in snapshot.manifest.files) assertEquals(entry.md5, Md5.of(snapshot.source(entry.path)))
    }

    @Test
    fun theLatestChanges_stillInTheWalFile_areInTheCopy() = runBlocking {
        notebookWithPage("Lecture 1")
        val wal = File(dbFile.path + "-wal")
        notebooks.createNotebook(folderId, "Written just now")
        assertTrue("the test needs recent changes in the -wal file", wal.length() > 0)

        val snapshot = take()
        val copy = snapshot.source(BackupPaths.DATABASE)
        assertFalse(File(copy.path + "-wal").exists()) // one self-contained file
        SQLiteDatabase.openDatabase(copy.path, null, SQLiteDatabase.OPEN_READONLY).use { c ->
            c.rawQuery("SELECT COUNT(*) FROM notebooks WHERE title = 'Written just now'", null).use {
                it.moveToFirst()
                assertEquals(1, it.getInt(0))
            }
        }
        // The live database carries on as before.
        notebooks.createNotebook(folderId, "After the snapshot")
        assertEquals(3, db.notebookDao().observeAllActive().first().size)
    }

    @Test
    fun aSnapshotTakenWhileWriting_isConsistent_andRoundTripsThroughAZip() = runBlocking {
        val (nb, _) = notebookWithPage("Lecture 1")
        // Keep adding pages (database row + page file under the save lock) while the snapshot runs.
        val writer = async(Dispatchers.IO) {
            repeat(40) { i ->
                val pageId = notebooks.addPage(nb, i + 1).let { (it as NotebookResult.Success).id }
                StorageLock.pages.withLock { files.write(nb, pageId, listOf(stroke(i.toFloat()))) }
            }
        }
        val snapshot = take()
        writer.await()

        // Every page file in the snapshot belongs to a page row in the snapshot's database.
        val copy = snapshot.source(BackupPaths.DATABASE)
        val pageRows = SQLiteDatabase.openDatabase(copy.path, null, SQLiteDatabase.OPEN_READONLY).use { c ->
            c.rawQuery("SELECT id FROM pages", null).use { cur -> buildSet { while (cur.moveToNext()) add(cur.getString(0)) } }
        }
        val pageFiles = snapshot.manifest.files.map { it.path }.filter { it.endsWith(".ink") }.map { it.substringAfterLast('/').removeSuffix(".ink") }
        assertTrue(pageRows.containsAll(pageFiles))
        assertEquals(pageRows.size, snapshot.manifest.counts.pages)

        // Zip and unpack it again: same files, same checks.
        val zip = ByteArrayOutputStream().also { ZipBackup.write(snapshot, it) }.toByteArray()
        val unpacked = File(root, "unpacked")
        val manifest = ZipBackup.unpack(ByteArrayInputStream(zip), unpacked)
        assertEquals(snapshot.manifest, manifest)
        val info = DatabaseCheck.inspectReadOnly(File(unpacked, BackupPaths.DATABASE))
        assertEquals(manifest.counts, info.counts)
        assertEquals(NoteAppDatabase.VERSION, info.version)
    }

    @Test(expected = IllegalStateException::class)
    fun aSnapshot_withoutTheBackupLock_isRefused() {
        runBlocking { snapshots.take(staging) }
    }

    @Test
    fun aDamagedDatabase_isCaughtByTheCheck() {
        val bad = File(root, "bad.db").apply { parentFile!!.mkdirs(); writeBytes(ByteArray(8192) { 7 }) }
        try {
            DatabaseCheck.inspectReadOnly(bad)
            throw AssertionError("A damaged database passed the check")
        } catch (e: BackupException) {
            assertTrue(e.message!!.isNotBlank())
        }
    }
}
