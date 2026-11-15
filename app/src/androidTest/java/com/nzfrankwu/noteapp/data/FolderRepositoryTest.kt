package com.nzfrankwu.noteapp.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nzfrankwu.noteapp.data.db.NoteAppDatabase
import com.nzfrankwu.noteapp.data.db.Task
import com.nzfrankwu.noteapp.domain.TaskSource
import com.nzfrankwu.noteapp.domain.TaskStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class FolderRepositoryTest {
    private lateinit var db: NoteAppDatabase
    private lateinit var repo: FolderRepository
    private var now = Instant.parse("2026-11-14T10:00:00Z")
    private var nextId = 0

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, NoteAppDatabase::class.java).build()
        val clock = object : Clock() {
            override fun instant() = now
            override fun getZone() = ZoneOffset.UTC
            override fun withZone(zone: java.time.ZoneId?) = this
        }
        repo = FolderRepository(db, clock) { "id-${++nextId}" }
    }

    @After
    fun tearDown() = db.close()

    private suspend fun folders() = repo.observeFolders().first()

    @Test
    fun createFolder_withoutCourse() = runBlocking {
        val result = repo.createFolder("  Personal  ", courseCode = null)
        assertTrue(result is FolderResult.Success)
        val only = folders().single()
        assertEquals("Personal", only.folder.name)
        assertNull(only.courseCode)
        assertEquals(0, db.courseDao().getAll().size)
    }

    @Test
    fun createFolder_withCourseCode_createsAndLinksTheCourse() = runBlocking {
        repo.createFolder("Electromagnetics", courseCode = "  electeng   204 ")
        val item = folders().single()
        assertEquals("ELECTENG 204", item.courseCode)
        assertEquals(1, db.courseDao().getAll().size)
    }

    @Test
    fun createFolder_blankName_isRefused() = runBlocking {
        val result = repo.createFolder("   ", courseCode = "ELECTENG 204")
        assertEquals(FolderResult.Failure(FolderError.BlankName), result)
        assertTrue(folders().isEmpty())
        assertEquals("Nothing should be created", 0, db.courseDao().getAll().size)
    }

    @Test
    fun aCourse_canOnlyHaveOneFolder_evenIfTypedDifferently() = runBlocking {
        repo.createFolder("EE 204", courseCode = "ELECTENG 204")
        val second = repo.createFolder("Another", courseCode = "electeng204")
        assertEquals(FolderResult.Failure(FolderError.CourseAlreadyLinked("ELECTENG 204", "EE 204")), second)
        assertEquals(1, folders().size)
        assertEquals(1, db.courseDao().getAll().size)
    }

    @Test
    fun anExistingUnlinkedCourse_isReused() = runBlocking {
        val first = repo.createFolder("Old", courseCode = "ELECTENG 204") as FolderResult.Success
        repo.deleteFolder(first.folderId)
        repo.createFolder("New", courseCode = "ELECTENG 204")
        assertEquals(1, db.courseDao().getAll().size)
        assertEquals("ELECTENG 204", folders().single().courseCode)
    }

    @Test
    fun renameFolder_changesNameAndTime_keepsCourseLink() = runBlocking {
        val created = repo.createFolder("Old name", courseCode = "ELECTENG 204") as FolderResult.Success
        now = now.plusSeconds(60)
        repo.renameFolder(created.folderId, "  New name ")

        val folder = checkNotNull(db.folderDao().getById(created.folderId))
        assertEquals("New name", folder.name)
        assertEquals(now, folder.updatedAt)
        assertEquals("ELECTENG 204", folders().single().courseCode)
    }

    @Test
    fun renameFolder_blankName_isRefused() = runBlocking {
        val created = repo.createFolder("Keep me", courseCode = null) as FolderResult.Success
        assertEquals(FolderResult.Failure(FolderError.BlankName), repo.renameFolder(created.folderId, " "))
        assertEquals("Keep me", folders().single().folder.name)
    }

    @Test
    fun renameFolder_missingFolder_reportsNotFound() = runBlocking {
        assertEquals(FolderResult.Failure(FolderError.NotFound), repo.renameFolder("no-such-id", "X"))
    }

    @Test
    fun deleteFolder_keepsTheCourseAndItsTasks() = runBlocking {
        val created = repo.createFolder("EE 204", courseCode = "ELECTENG 204") as FolderResult.Success
        val courseId = db.courseDao().getAll().single().id
        db.taskDao().insert(
            Task(
                id = "t1", title = "Lab 3", courseId = courseId, dueAt = null, url = null, descriptionHtml = null,
                status = TaskStatus.NOT_STARTED, targetAt = null, notes = null, source = TaskSource.MANUAL,
                canvasAssignmentId = null, canvasSubmissionState = null, canvasSubmittedAt = null,
                removedFromCanvas = false, createdAt = now, updatedAt = now,
            ),
        )

        repo.deleteFolder(created.folderId)

        assertTrue(folders().isEmpty())
        assertEquals(1, db.courseDao().getAll().size)
        assertEquals(courseId, db.taskDao().getById("t1")?.courseId)
    }

    @Test
    fun linkCourse_toAnUnlinkedFolder() = runBlocking {
        val folder = repo.createFolder("Personal", null) as FolderResult.Success
        now = now.plusSeconds(60)
        assertEquals(FolderResult.Success(folder.folderId), repo.linkCourse(folder.folderId, " electeng 204 "))
        val item = checkNotNull(repo.observeFolder(folder.folderId).first())
        assertEquals("ELECTENG 204", item.courseCode)
        assertEquals(now, item.folder.updatedAt)
    }

    @Test
    fun linkCourse_alreadyLinkedElsewhere_isRefused() = runBlocking {
        repo.createFolder("EE 204", "ELECTENG 204")
        val other = repo.createFolder("Other", null) as FolderResult.Success
        assertEquals(
            FolderResult.Failure(FolderError.CourseAlreadyLinked("ELECTENG 204", "EE 204")),
            repo.linkCourse(other.folderId, "electeng204"),
        )
    }

    @Test
    fun linkCourse_sameCourseAgain_isFine() = runBlocking {
        val folder = repo.createFolder("EE 204", "ELECTENG 204") as FolderResult.Success
        assertEquals(FolderResult.Success(folder.folderId), repo.linkCourse(folder.folderId, "ELECTENG204"))
    }

    @Test
    fun linkCourse_blankCode_isRefused() = runBlocking {
        val folder = repo.createFolder("Personal", null) as FolderResult.Success
        assertEquals(FolderResult.Failure(FolderError.BlankCourseCode), repo.linkCourse(folder.folderId, "  "))
    }

    @Test
    fun unlinkCourse_keepsTheCourse() = runBlocking {
        val folder = repo.createFolder("EE 204", "ELECTENG 204") as FolderResult.Success
        repo.unlinkCourse(folder.folderId)
        assertNull(repo.observeFolder(folder.folderId).first()?.courseCode)
        assertEquals(1, db.courseDao().getAll().size)
    }

    @Test
    fun observeFolder_isNullOnceDeleted() = runBlocking {
        val folder = repo.createFolder("Temp", null) as FolderResult.Success
        repo.deleteFolder(folder.folderId)
        assertNull(repo.observeFolder(folder.folderId).first())
    }

    @Test
    fun folders_areListedAlphabetically() = runBlocking {
        repo.createFolder("b", null)
        repo.createFolder("C", null)
        repo.createFolder("a", null)
        assertEquals(listOf("a", "b", "C"), folders().map { it.folder.name })
    }

    // ---- Archiving courses ----

    @Test
    fun archivingACourse_marksItAndKeepsEverything() = runBlocking {
        val folderId = (repo.createFolder("EE 204", "ELECTENG 204") as FolderResult.Success).folderId
        val courseId = db.folderDao().getById(folderId)!!.courseId!!

        assertTrue(repo.setCourseArchived(courseId, true))
        val archived = db.courseDao().getById(courseId)!!
        assertNotNull(archived.archivedAt)
        assertEquals(courseId, db.folderDao().getById(folderId)!!.courseId) // the folder stays linked
        assertTrue(repo.observeFolders().first().single().isArchived)

        assertTrue(repo.setCourseArchived(courseId, false))
        assertNull(db.courseDao().getById(courseId)!!.archivedAt)
    }

    @Test
    fun archivingTwice_keepsTheFirstTime() = runBlocking {
        repo.createFolder("EE 204", "ELECTENG 204")
        val courseId = db.courseDao().getAll().single().id
        repo.setCourseArchived(courseId, true)
        val first = db.courseDao().getById(courseId)!!
        repo.setCourseArchived(courseId, true)
        assertEquals(first, db.courseDao().getById(courseId))
    }

    @Test
    fun archivingAMissingCourse_saysSo() = runBlocking {
        assertTrue(!repo.setCourseArchived("no-such-course", true))
    }

    @Test
    fun newFolderForAnArchivedCoursesCode_bringsTheCourseBack() = runBlocking {
        val first = (repo.createFolder("EE 204", "ELECTENG 204") as FolderResult.Success).folderId
        val courseId = db.folderDao().getById(first)!!.courseId!!
        repo.setCourseArchived(courseId, true)
        repo.deleteFolder(first) // archived course, no folder

        val second = (repo.createFolder("Electromagnetics", "electeng 204") as FolderResult.Success).folderId
        assertEquals(courseId, db.folderDao().getById(second)!!.courseId) // same course, not a new one
        assertNull(db.courseDao().getById(courseId)!!.archivedAt) // and it's current again
    }
}
