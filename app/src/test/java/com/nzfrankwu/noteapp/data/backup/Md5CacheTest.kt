package com.nzfrankwu.noteapp.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Phase 7, 7.2: write-once files aren't hashed again while path, size and time are the same. */
class Md5CacheTest {
    @get:Rule val temp = TemporaryFolder()

    private fun file(name: String, text: String, modified: Long = 1_000_000L) =
        File(temp.root, name).apply { writeText(text); setLastModified(modified) }

    /** A cache entry is used: the md5 it gives back is the remembered one, not the file's. */
    private fun plant(cacheFile: File, path: String, f: File, md5: String) =
        cacheFile.writeText("${f.length()}\t${f.lastModified()}\t$md5\t$path\n")

    @Test
    fun sameFile_usesTheRememberedMd5_acrossRuns() {
        val pdf = file("a.pdf", "lecture")
        val cacheFile = File(temp.root, "md5.txt")
        Md5Cache(cacheFile).apply { assertEquals(Md5.of(pdf), md5("files/notebooks/n/pdf-a.pdf", pdf)); save(setOf("files/notebooks/n/pdf-a.pdf")) }
        assertTrue(cacheFile.readText().contains(Md5.of(pdf)))

        // A new cache object (a later backup) trusts the saved entry.
        val fake = "f".repeat(32)
        plant(cacheFile, "files/notebooks/n/pdf-a.pdf", pdf, fake)
        assertEquals(fake, Md5Cache(cacheFile).md5("files/notebooks/n/pdf-a.pdf", pdf))
    }

    @Test
    fun aDifferentSizeTimeOrPath_isHashedAgain() {
        val pdf = file("a.pdf", "lecture")
        val cacheFile = File(temp.root, "md5.txt")
        val fake = "f".repeat(32)

        plant(cacheFile, "files/notebooks/n/pdf-b.pdf", pdf, fake)
        assertEquals("another path", Md5.of(pdf), Md5Cache(cacheFile).md5("files/notebooks/n/pdf-a.pdf", pdf))

        plant(cacheFile, "p", pdf, fake)
        pdf.setLastModified(2_000_000L)
        assertEquals("another time", Md5.of(pdf), Md5Cache(cacheFile).md5("p", pdf))

        plant(cacheFile, "p", pdf, fake)
        pdf.writeText("lecture, longer"); pdf.setLastModified(2_000_000L)
        assertEquals("another size", Md5.of(pdf), Md5Cache(cacheFile).md5("p", pdf))
    }

    @Test
    fun save_forgetsFilesNoLongerThere_andAGarbledCacheIsIgnored() {
        val a = file("a.png", "one")
        val b = file("b.png", "two")
        val cacheFile = File(temp.root, "md5.txt")
        Md5Cache(cacheFile).apply { md5("a", a); md5("b", b); save(setOf("a", "b")) }
        Md5Cache(cacheFile).apply { md5("a", a); save(setOf("a")) }
        assertFalse(cacheFile.readText().contains(Md5.of(b)))

        cacheFile.writeText("garbage\nmore\tgarbage\n")
        assertEquals(Md5.of(a), Md5Cache(cacheFile).md5("a", a))
        assertEquals(Md5.of(a), Md5Cache(File(temp.root, "missing/md5.txt")).md5("a", a))
    }
}
