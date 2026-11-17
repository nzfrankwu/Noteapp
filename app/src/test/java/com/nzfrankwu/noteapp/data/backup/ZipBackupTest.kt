package com.nzfrankwu.noteapp.data.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ZipBackupTest {
    @get:Rule val tmp = TemporaryFolder()

    private val contents = mapOf(
        "db/noteapp.db" to "database bytes".toByteArray(),
        "files/notebooks/nb1/p1.ink" to ByteArray(5000) { (it % 251).toByte() },
        "files/notebooks/nb1/images/a.png" to "png".toByteArray(),
        "files/datastore/ink_settings.preferences_pb" to "pens".toByteArray(),
    )

    private fun snapshot(): Snapshot {
        val dir = tmp.newFolder("source")
        val sources = contents.mapValues { (path, bytes) -> File(dir, path).apply { parentFile!!.mkdirs(); writeBytes(bytes) } }
        val manifest = BackupManifest(
            createdAt = 1_763_000_000_000, appVersion = "1.0", databaseVersion = 7,
            files = contents.map { (path, bytes) -> BackupEntry(path, bytes.size.toLong(), Md5.of(bytes)) }.sortedBy { it.path },
        )
        return Snapshot(manifest, sources)
    }

    private fun zipOf(entries: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, bytes) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun entriesOf(zip: ByteArray): MutableMap<String, ByteArray> {
        val map = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(zip)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                map[e.name] = z.readBytes()
            }
        }
        return map
    }

    private fun exported(): ByteArray {
        val out = ByteArrayOutputStream()
        var last = 0f
        ZipBackup.write(snapshot(), out) { last = it }
        assertEquals(1f, last, 0.0001f)
        return out.toByteArray()
    }

    private fun assertRefused(zip: ByteArray, staging: File = tmp.newFolder()) {
        try {
            ZipBackup.unpack(ByteArrayInputStream(zip), staging)
            fail("An unusable export was accepted")
        } catch (e: BackupException) {
            assertTrue(e.message!!.isNotBlank())
        }
    }

    @Test
    fun export_thenUnpack_givesBackEveryFile() {
        val staging = tmp.newFolder("staging")
        val manifest = ZipBackup.unpack(ByteArrayInputStream(exported()), staging)
        assertEquals(contents.keys.sorted(), manifest.files.map { it.path })
        for ((path, bytes) in contents) assertArrayEquals(bytes, File(staging, path).readBytes())
    }

    @Test
    fun unpack_emptiesTheStagingFolderFirst() {
        val staging = tmp.newFolder("staging")
        File(staging, "files/notebooks/old/leftover.ink").apply { parentFile!!.mkdirs(); writeText("x") }
        ZipBackup.unpack(ByteArrayInputStream(exported()), staging)
        assertFalse(File(staging, "files/notebooks/old").exists())
    }

    @Test
    fun aChangedFile_isRefused() {
        val entries = entriesOf(exported())
        entries["files/notebooks/nb1/p1.ink"] = ByteArray(5000) { 1 } // same size, different bytes
        assertRefused(zipOf(entries))
    }

    @Test
    fun aMissingFile_isRefused() {
        val entries = entriesOf(exported())
        entries.remove("files/notebooks/nb1/images/a.png")
        assertRefused(zipOf(entries))
    }

    @Test
    fun anExtraFile_notInTheList_isRefused() {
        val entries = entriesOf(exported())
        entries["files/notebooks/nb1/extra.ink"] = "sneaky".toByteArray()
        assertRefused(zipOf(entries))
    }

    @Test
    fun aPathOutsideTheAppsFolders_isRefused_andNeverWritten() {
        val root = tmp.newFolder("root")
        val staging = File(root, "staging")
        val entries = entriesOf(exported())
        entries["../escaped.txt"] = "x".toByteArray()
        assertRefused(zipOf(entries), staging)
        assertFalse(File(root, "escaped.txt").exists())
    }

    @Test
    fun canvasSettings_inAZip_isRefused() {
        val entries = entriesOf(exported())
        entries["files/datastore/canvas_settings.preferences_pb"] = "cookie".toByteArray()
        assertRefused(zipOf(entries))
    }

    @Test
    fun aZipWithoutAManifest_orNotAZip_isRefused() {
        val entries = entriesOf(exported())
        entries.remove("manifest.json")
        assertRefused(zipOf(entries))
        assertRefused("this is not a zip file at all".toByteArray())
    }

    @Test
    fun aFileThatChangedWhileExporting_failsTheExport() {
        val snap = snapshot()
        snap.source("files/notebooks/nb1/p1.ink").appendText("more")
        try {
            ZipBackup.write(snap, ByteArrayOutputStream())
            fail()
        } catch (e: BackupException) {
            assertTrue(e.message!!.contains("changed"))
        }
    }
}
