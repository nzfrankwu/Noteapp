package com.nzfrankwu.noteapp.data.backup.drive

import com.nzfrankwu.noteapp.data.backup.BackupEntry
import com.nzfrankwu.noteapp.data.backup.BackupException
import com.nzfrankwu.noteapp.data.backup.BackupManifest
import com.nzfrankwu.noteapp.data.backup.BackupPaths
import com.nzfrankwu.noteapp.data.backup.Md5
import com.nzfrankwu.noteapp.data.backup.Snapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** PHASE-6.md 6.9 / 6.11: the Drive backup engine against a fake Drive. */
class DriveBackupTest {
    @get:Rule val temp = TemporaryFolder()

    private var now = Instant.parse("2026-11-17T10:00:00Z")
    private val clock = object : Clock() {
        override fun instant() = now
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?) = this
    }
    private val drive = FakeDrive { now }
    private lateinit var live: File
    private lateinit var backup: DriveBackup

    @Before
    fun setUp() {
        live = temp.newFolder("live")
        write(BackupPaths.DATABASE, "database v1")
        write("files/notebooks/n1/p1.ink", "page one")
        write("files/notebooks/n1/pdf-a.pdf", "a big pdf")
        write("files/notebooks/n2/images/same.png", "a big pdf") // same content as the PDF: uploaded once
        backup = DriveBackup(drive, ::takeSnapshot, temp.newFolder("staging"), clock)
    }

    private fun write(path: String, text: String) = File(live, path).apply { parentFile!!.mkdirs(); writeText(text) }

    /** Like LocalSnapshot: copies the live files and lists them. */
    private fun takeSnapshot(staging: File): Snapshot {
        staging.deleteRecursively()
        val sources = LinkedHashMap<String, File>()
        live.walkTopDown().filter { it.isFile }.forEach { file ->
            val path = file.relativeTo(live).invariantSeparatorsPath
            sources[path] = file.copyTo(File(staging, path))
        }
        val entries = sources.map { (path, f) -> BackupEntry(path, f.length(), Md5.of(f)) }.sortedBy { it.path }
        return Snapshot(BackupManifest(createdAt = now.toEpochMilli(), appVersion = "test", databaseVersion = 7, files = entries), sources)
    }

    private fun <T> run(block: suspend () -> T): T = runBlocking { block() }
    private fun blobs() = drive.inFolder(drive.folderId("Noteapp", "Backup", "blobs")!!)
    private fun snapshotFiles() = drive.inFolder(drive.folderId("Noteapp", "Backup", "snapshots")!!)
    private fun later(duration: Duration) { now = now.plus(duration) }

    @Test
    fun firstBackup_uploadsEachContentOnce_andTheListLast() {
        val result = run { backup.backUp() }
        assertEquals(3, result.uploadedFiles) // 4 files, 2 with the same content
        assertEquals(3, blobs().size)
        assertEquals(1, snapshotFiles().size)
        assertEquals("the list is written last", result.snapshot.name, drive.uploads.last())
        assertEquals("2026-11-17 10-00-00 UTC.json", result.snapshot.name)
        assertTrue(result.storedBytes > 0)
        assertFalse("nothing left behind", File(temp.root, "staging").exists())
    }

    @Test
    fun nothingChanged_onlyANewListIsUploaded() {
        run { backup.backUp() }
        drive.uploads.clear()
        later(Duration.ofHours(1))
        val result = run { backup.backUp() }
        assertEquals(0, result.uploadedFiles)
        assertEquals(1, drive.uploads.size)
    }

    @Test
    fun aChangedPage_isTheOnlyFileUploaded() {
        run { backup.backUp() }
        drive.uploads.clear()
        write("files/notebooks/n1/p1.ink", "page one, more ink")
        later(Duration.ofHours(1))
        val result = run { backup.backUp() }
        assertEquals(1, result.uploadedFiles)
        assertEquals(listOf(Md5.of(File(live, "files/notebooks/n1/p1.ink")), result.snapshot.name), drive.uploads)
    }

    @Test
    fun aRunThatStopsHalfway_leavesTheLastBackupWhole_andTheNextRunFinishes() {
        val first = run { backup.backUp() }
        write("files/notebooks/n1/p1.ink", "changed")
        write("files/notebooks/n1/p2.ink", "new page")
        later(Duration.ofHours(1))
        drive.failUploadNumber = 2
        assertThrows(DriveException::class.java) { run { backup.backUp() } }
        assertEquals("no new list", listOf(first.snapshot.fileId), snapshotFiles().map { it.id })
        restoreAndCompare(first.snapshot, expectChangedPage = false)

        drive.uploads.clear()
        val second = run { backup.backUp() }
        assertEquals("the file sent before the stop isn't sent again", 1, second.uploadedFiles)
        assertEquals("same day: the newer one replaces the first", listOf(second.snapshot.fileId), snapshotFiles().map { it.id })
        restoreAndCompare(second.snapshot, expectChangedPage = true)
    }

    @Test
    fun aFileDamagedOnTheWay_failsTheRun_andIsRemoved() {
        drive.lieAboutMd5 = true
        assertThrows(BackupException::class.java) { run { backup.backUp() } }
        assertTrue(blobs().isEmpty())
        assertTrue(snapshotFiles().isEmpty())
    }

    @Test
    fun retention_keepsTheNewest_andOnePerDayForAWeek() {
        val start = Instant.parse("2026-11-01T08:00:00Z")
        now = start
        run { backup.backUp() }
        now = start.plus(Duration.ofHours(12)) // same day, later: this one stands for 1 Nov
        run { backup.backUp() }
        for (day in 1..9) {
            now = start.plus(Duration.ofDays(day.toLong())).plus(Duration.ofHours(4))
            write("files/notebooks/n1/p1.ink", "day $day")
            run { backup.backUp() }
        }
        now = now.plus(Duration.ofHours(2)) // a second backup on the last day (10 Nov)
        run { backup.backUp() }

        val kept = snapshotFiles().map { it.name }.sorted()
        assertEquals(
            listOf(
                "2026-11-04 12-00-00 UTC.json", "2026-11-05 12-00-00 UTC.json", "2026-11-06 12-00-00 UTC.json",
                "2026-11-07 12-00-00 UTC.json", "2026-11-08 12-00-00 UTC.json", "2026-11-09 12-00-00 UTC.json",
                "2026-11-10 14-00-00 UTC.json",
            ),
            kept,
        )
    }

    @Test
    fun filesNoKeptBackupUses_goAfterADay_andNotBefore() {
        now = Instant.parse("2026-11-01T10:00:00Z")
        run { backup.backUp() }
        val oldPage = Md5.of(File(live, "files/notebooks/n1/p1.ink"))
        // Eight days of new pages: the first backup is dropped, and its page with it.
        for (day in 1..8) {
            now = Instant.parse("2026-11-01T10:00:00Z").plus(Duration.ofDays(day.toLong()))
            write("files/notebooks/n1/p1.ink", "day $day")
            run { backup.backUp() }
        }
        val names = blobs().map { it.name }
        assertFalse("unused for days: removed", oldPage in names)
        assertTrue("still used", Md5.of(File(live, "files/notebooks/n1/pdf-a.pdf")) in names)

        // A file just uploaded by a run that hasn't finished yet is never removed.
        val orphan = temp.newFile("orphan").apply { writeText("half a backup") }
        run { drive.upload(orphan, Md5.of(orphan), drive.folderId("Noteapp", "Backup", "blobs")!!) }
        later(Duration.ofHours(1))
        run { backup.backUp() }
        assertTrue(Md5.of(orphan) in blobs().map { it.name })
    }

    @Test
    fun aKeptBackupThatCantBeRead_meansNoFileIsRemoved() {
        now = Instant.parse("2026-11-01T10:00:00Z")
        val first = run { backup.backUp() }
        val orphan = temp.newFile("orphan").apply { writeText("from a run that never finished") }
        run { drive.upload(orphan, Md5.of(orphan), drive.folderId("Noteapp", "Backup", "blobs")!!) }
        now = Instant.parse("2026-11-05T10:00:00Z")
        write("files/notebooks/n1/p1.ink", "changed")
        drive.failDownloads += first.snapshot.fileId
        run { backup.backUp() }
        assertTrue("kept: the first backup's list couldn't be read", Md5.of(orphan) in blobs().map { it.name })
        assertEquals(2, snapshotFiles().size)

        drive.failDownloads.clear()
        later(Duration.ofHours(1))
        run { backup.backUp() }
        assertFalse(Md5.of(orphan) in blobs().map { it.name })
    }

    @Test
    fun snapshots_areListedNewestFirst() {
        assertTrue("no backup folder yet: nothing, and nothing made", run { backup.snapshots() }.isEmpty())
        assertTrue(drive.items.isEmpty())
        run { backup.backUp() }
        later(Duration.ofDays(1))
        run { backup.backUp() }
        val list = run { backup.snapshots() }
        assertEquals(2, list.size)
        assertTrue(list[0].createdAt > list[1].createdAt)
    }

    @Test
    fun restore_downloadsEveryFile_andChecksThem() {
        val result = run { backup.backUp() }
        restoreAndCompare(result.snapshot, expectChangedPage = false)
    }

    @Test
    fun restore_ofAMissingFile_isRefused() {
        val result = run { backup.backUp() }
        drive.items.remove(blobs().first().id)
        val manifest = run { backup.manifest(result.snapshot, temp.newFolder()) }
        val e = assertThrows(BackupException::class.java) { run { backup.download(manifest, temp.newFolder("restore")) } }
        assertTrue(e.message!!, e.message!!.contains("missing"))
    }

    @Test
    fun restore_ofADamagedFile_isRefused() {
        val result = run { backup.backUp() }
        blobs().first { it.bytes.decodeToString() == "page one" }.apply {
            reportedMd5 = Md5.of(bytes) // Drive still claims the old md5, but the content is wrong
            bytes = "page 0ne".encodeToByteArray()
        }
        val manifest = run { backup.manifest(result.snapshot, temp.newFolder()) }
        val e = assertThrows(BackupException::class.java) { run { backup.download(manifest, temp.newFolder("restore")) } }
        assertTrue(e.message!!, e.message!!.contains("damaged"))
    }

    private fun restoreAndCompare(snapshot: DriveBackup.RemoteSnapshot, expectChangedPage: Boolean) {
        val staging = temp.newFolder()
        val manifest = run { backup.manifest(snapshot, temp.newFolder()) }
        run { backup.download(manifest, staging) }
        for (entry in manifest.files) {
            assertEquals(entry.md5, Md5.of(File(staging, entry.path)))
        }
        assertArrayEquals(
            (if (expectChangedPage) "changed" else "page one").encodeToByteArray(),
            File(staging, "files/notebooks/n1/p1.ink").readBytes(),
        )
        assertTrue(File(staging, BackupPaths.MANIFEST).isFile)
    }
}
