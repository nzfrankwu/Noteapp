package com.nzfrankwu.noteapp.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class FolderPlaceTest {
    private val t = Instant.EPOCH
    private val course = Course("c1", "ELECTENG 292", null, null, t, t)
    private fun folder(id: String, parent: String? = null, course: String? = null) =
        Folder(id = id, name = id, courseId = course, createdAt = t, updatedAt = t, parentId = parent)

    private val folders = listOf(
        folder("top", course = "c1"),
        folder("tutorials", parent = "top"),
        folder("week1", parent = "tutorials"),
        folder("plain"),
    )

    @Test
    fun topLevelFolder_isItsOwnPath() {
        val place = FolderPlace.of("top", folders, listOf(course))!!
        assertEquals(listOf("top"), place.path.map { it.id })
        assertFalse(place.isSubfolder)
        assertEquals(course, place.course)
    }

    @Test
    fun deepSubfolder_hasThePathFromTheTop_andTheTopFoldersCourse() {
        val place = FolderPlace.of("week1", folders, listOf(course))!!
        assertEquals(listOf("top", "tutorials", "week1"), place.path.map { it.id })
        assertTrue(place.isSubfolder)
        assertEquals("top", place.top.id)
        assertEquals("week1", place.folder.id)
        assertEquals(course, place.course)
    }

    @Test
    fun folderWithoutCourse_hasNoCourse() {
        assertNull(FolderPlace.of("plain", folders, listOf(course))!!.course)
    }

    @Test
    fun missingFolder_hasNoPlace() {
        assertNull(FolderPlace.of("gone", folders, listOf(course)))
    }

    @Test
    fun aLoop_neverHangs() {
        val loop = listOf(folder("a", parent = "b"), folder("b", parent = "a"))
        val place = FolderPlace.of("a", loop, emptyList())!!
        assertEquals(listOf("b", "a"), place.path.map { it.id })
    }

    @Test
    fun aMissingParent_makesTheFolderTheTop() {
        val place = FolderPlace.of("orphan", listOf(folder("orphan", parent = "deleted")), emptyList())!!
        assertEquals(listOf("orphan"), place.path.map { it.id })
    }
}
