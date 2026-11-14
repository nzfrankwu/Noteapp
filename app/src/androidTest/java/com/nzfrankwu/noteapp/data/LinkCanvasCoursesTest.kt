package com.nzfrankwu.noteapp.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nzfrankwu.noteapp.data.db.Course
import com.nzfrankwu.noteapp.data.db.NoteAppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/** Giving imported Canvas courses a folder (step 6.2). */
@RunWith(AndroidJUnit4::class)
class LinkCanvasCoursesTest {
    private lateinit var db: NoteAppDatabase
    private lateinit var folders: FolderRepository

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, NoteAppDatabase::class.java).build()
        folders = FolderRepository(db)
        db.courseDao().insert(Course("c-204", "ELECTENG 204", "Engineering Electromagnetics", 555, Instant.EPOCH, Instant.EPOCH))
        db.courseDao().insert(Course("c-201", "COMPSYS 201", "Computer Systems", 557, Instant.EPOCH, Instant.EPOCH))
        db.courseDao().insert(Course("c-own", "MATHS 208", null, null, Instant.EPOCH, Instant.EPOCH)) // your own, not Canvas
    }

    @After
    fun tearDown() = db.close()

    private suspend fun unlinkedCodes() = folders.observeCanvasCoursesWithoutFolder().first().map { it.code }

    @Test
    fun onlyCanvasCoursesWithoutAFolder_areListed() = runBlocking {
        assertEquals(listOf("COMPSYS 201", "ELECTENG 204"), unlinkedCodes())
    }

    @Test
    fun createFolderForCourse_linksIt() = runBlocking {
        val result = folders.createFolderForCourse("Engineering Electromagnetics", "c-204")
        assertTrue(result is FolderResult.Success)
        assertEquals(listOf("COMPSYS 201"), unlinkedCodes())
        assertEquals("ELECTENG 204", folders.observeFolders().first().single().courseCode)
    }

    @Test
    fun addToExistingFolder_byCourseId() = runBlocking {
        val folderId = (folders.createFolder("Systems", null) as FolderResult.Success).folderId
        assertTrue(folders.linkCourseById(folderId, "c-201") is FolderResult.Success)
        assertEquals(listOf("ELECTENG 204"), unlinkedCodes())
    }

    @Test
    fun aCourseCantGetTwoFolders() = runBlocking {
        folders.createFolderForCourse("EM", "c-204")
        val second = folders.createFolderForCourse("EM again", "c-204")
        assertTrue((second as FolderResult.Failure).error is FolderError.CourseAlreadyLinked)
        val other = (folders.createFolder("Other", null) as FolderResult.Success).folderId
        assertTrue(folders.linkCourseById(other, "c-204") is FolderResult.Failure)
    }
}
