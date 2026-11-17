package com.nzfrankwu.noteapp.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CleanupRulesTest {
    private val used = setOf("images/used.png", "bookmarks/used.png", "pdf-used.pdf")

    @Test
    fun unreferencedPicturesAndPdfs_areUnused() {
        assertTrue(CleanupRules.isUnused("images/old.jpg", used))
        assertTrue(CleanupRules.isUnused("bookmarks/old.png", used))
        assertTrue(CleanupRules.isUnused("pdf-old.pdf", used))
    }

    @Test
    fun referencedFiles_areNeverUnused() {
        for (name in used) assertFalse(name, CleanupRules.isUnused(name, used))
    }

    @Test
    fun pageFilesAndAnythingElse_areNeverUnused() {
        for (name in listOf("page-1.ink", "images/sub/x.png", "notes.txt", "pdf-x.pdf.saving", "images/x.png.writing", "other/x.png")) {
            assertFalse(name, CleanupRules.isUnused(name, emptySet()))
        }
    }

    @Test
    fun leftovers_areRecognised() {
        assertTrue(CleanupRules.isLeftover("p.ink.saving"))
        assertTrue(CleanupRules.isLeftover("x.png.writing"))
        assertFalse(CleanupRules.isLeftover("p.ink"))
    }
}
