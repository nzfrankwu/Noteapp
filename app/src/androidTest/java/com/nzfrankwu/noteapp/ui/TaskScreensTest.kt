package com.nzfrankwu.noteapp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nzfrankwu.noteapp.data.FolderRepository
import com.nzfrankwu.noteapp.data.FolderResult
import com.nzfrankwu.noteapp.data.NotebookRepository
import com.nzfrankwu.noteapp.data.TaskDraft
import com.nzfrankwu.noteapp.data.TaskRepository
import com.nzfrankwu.noteapp.data.TaskResult
import com.nzfrankwu.noteapp.data.db.NoteAppDatabase
import com.nzfrankwu.noteapp.data.db.Task
import com.nzfrankwu.noteapp.domain.TaskSource
import com.nzfrankwu.noteapp.domain.TaskStatus
import com.nzfrankwu.noteapp.ui.folder.FolderScreen
import com.nzfrankwu.noteapp.ui.folder.FolderViewModel
import com.nzfrankwu.noteapp.ui.task.TaskDetailScreen
import com.nzfrankwu.noteapp.ui.task.TaskDetailViewModel
import com.nzfrankwu.noteapp.ui.task.TaskEditorScreen
import com.nzfrankwu.noteapp.ui.task.TaskEditorViewModel
import com.nzfrankwu.noteapp.ui.theme.NoteAppTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.temporal.ChronoUnit

/** The task form, task page and task cards, with a real (in-memory) database. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class TaskScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var db: NoteAppDatabase
    private lateinit var folders: FolderRepository
    private lateinit var tasks: TaskRepository
    private lateinit var folderId: String
    private lateinit var courseId: String

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, NoteAppDatabase::class.java).build()
        folders = FolderRepository(db)
        tasks = TaskRepository(db)
        runBlocking {
            folderId = (folders.createFolder("Electromagnetics", "ELECTENG 204") as FolderResult.Success).folderId
            courseId = db.courseDao().getAll().single().id
        }
    }

    // The database is not closed after each test: screens may still be reading it when the
    // test ends, and closing it then crashes the run. In-memory databases are freed anyway.

    private fun showOnTablet(content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent { NoteAppTheme { Box(Modifier.size(width = 1120.dp, height = 800.dp)) { content() } } }
    }

    private fun waitFor(text: String) = compose.waitUntilAtLeastOneExists(hasText(text), 5_000)

    private fun addTask(title: String, dueAt: Instant? = null, notes: String? = null): String = runBlocking {
        (tasks.createTask(TaskDraft(title, courseId, dueAt = dueAt, notes = notes)) as TaskResult.Success).taskId
    }

    @Test
    fun newTask_fromAFolder_savesWithThatCourse() {
        var savedId: String? = null
        val viewModel = TaskEditorViewModel(null, courseId, null, null, tasks)
        showOnTablet { TaskEditorScreen(viewModel, onClose = {}, onSaved = { id, _ -> savedId = id }) }

        compose.onNodeWithText("New task").assertExists()
        waitFor("ELECTENG 204") // the course is already picked
        compose.onNodeWithTag("save-task").assertIsNotEnabled()

        compose.onNodeWithTag("title-field").performTextInput("Study for Test 2")
        compose.onNodeWithTag("notes-field").performTextInput("Chapters 3-5")
        compose.onNodeWithTag("save-task").assertIsEnabled().performClick()

        compose.waitUntil(5_000) { savedId != null }
        val saved = runBlocking { tasks.getTask(savedId!!) }!!
        assertEquals("Study for Test 2", saved.title)
        assertEquals(courseId, saved.courseId)
        assertEquals("Chapters 3-5", saved.notes)
        assertEquals(TaskStatus.NOT_STARTED, saved.status)
        assertNull(saved.dueAt)
    }

    @Test
    fun newTask_withADueDate_defaultsTo1159pm() {
        var savedId: String? = null
        val viewModel = TaskEditorViewModel(null, courseId, null, null, tasks)
        showOnTablet { TaskEditorScreen(viewModel, onClose = {}, onSaved = { id, _ -> savedId = id }) }

        compose.onNodeWithTag("title-field").performTextInput("Lab report")
        compose.onNodeWithTag("due-date").performClick()
        compose.onNodeWithTag("picker-ok").performClick() // today's date, already selected
        waitFor("11:59 pm")
        compose.onNodeWithTag("save-task").performClick()

        compose.waitUntil(5_000) { savedId != null }
        val due = runBlocking { tasks.getTask(savedId!!) }!!.dueAt!!.atZone(java.time.ZoneId.systemDefault())
        assertEquals(java.time.LocalDate.now(), due.toLocalDate())
        assertEquals(java.time.LocalTime.of(23, 59), due.toLocalTime())
    }

    @Test
    fun editTask_showsItsValues_andSavesChanges() {
        val id = addTask("Read chapter 3", notes = "pages 40-60")
        var saved = false
        val viewModel = TaskEditorViewModel(id, null, null, null, tasks)
        showOnTablet { TaskEditorScreen(viewModel, onClose = {}, onSaved = { _, _ -> saved = true }) }

        compose.onNodeWithText("Edit task").assertExists()
        waitFor("Read chapter 3")
        compose.onNodeWithTag("title-field").performTextReplacement("Read chapter 4")
        compose.onNodeWithTag("save-task").performClick()

        compose.waitUntil(5_000) { saved }
        val task = runBlocking { tasks.getTask(id) }!!
        assertEquals("Read chapter 4", task.title)
        assertEquals("pages 40-60", task.notes)
    }

    @Test
    fun taskPage_showsDetails_andStatusCanBeChanged() {
        val id = addTask("Assignment 2", dueAt = Instant.now().plus(3, ChronoUnit.DAYS), notes = "Q1 to Q4")
        showOnTablet { TaskDetailScreen(TaskDetailViewModel(id, tasks), onBack = {}, onEdit = {}, onDeleted = {}) }

        waitFor("Assignment 2")
        compose.onNodeWithText("ELECTENG 204").assertExists()
        compose.onNodeWithText(" · Your task").assertExists()
        compose.onNodeWithText("Q1 to Q4").assertExists()
        compose.onNodeWithText("Not set").assertExists() // no target date

        compose.onNodeWithTag("status-chip").performClick()
        compose.onNodeWithTag("status-option-${TaskStatus.STUCK.key}").performClick()

        waitFor("Stuck / need help")
        compose.waitUntil(5_000) { runBlocking { tasks.getTask(id) }!!.status == TaskStatus.STUCK }
    }

    @Test
    fun overdueTask_showsTheOverdueBadge() {
        val id = addTask("Late lab", dueAt = Instant.now().minus(1, ChronoUnit.DAYS))
        showOnTablet { TaskDetailScreen(TaskDetailViewModel(id, tasks), onBack = {}, onEdit = {}, onDeleted = {}) }
        waitFor("Overdue")
    }

    @Test
    fun deleting_handsBackTheTask_soItCanBeRestored() {
        val id = addTask("Throwaway")
        var deleted: com.nzfrankwu.noteapp.data.DeleteTaskResult.Deleted? = null
        showOnTablet { TaskDetailScreen(TaskDetailViewModel(id, tasks), onBack = {}, onEdit = {}, onDeleted = { deleted = it }) }

        waitFor("Throwaway")
        compose.onNodeWithContentDescription("Delete task").performClick()
        compose.waitUntil(5_000) { deleted != null }
        assertNull(runBlocking { tasks.getTask(id) })

        runBlocking { tasks.restoreTask(deleted!!) } // what the snackbar's Undo does
        assertNotNull(runBlocking { tasks.getTask(id) })
    }

    @Test
    fun canvasTaskPage_showsCanvasParts_andHasNoDeleteButton() {
        val now = Instant.now()
        runBlocking {
            db.taskDao().insert(
                Task(
                    id = "canvas-1", title = "Quiz 4", courseId = courseId, dueAt = now.plus(2, ChronoUnit.DAYS),
                    url = "https://canvas.example.edu/courses/1/assignments/4", descriptionHtml = "<p>Covers <b>week 6</b>.</p>",
                    status = TaskStatus.NOT_STARTED, targetAt = null, notes = null, source = TaskSource.CANVAS,
                    canvasAssignmentId = 4, canvasSubmissionState = "unsubmitted", canvasSubmittedAt = null,
                    removedFromCanvas = false, createdAt = now, updatedAt = now,
                ),
            )
        }
        showOnTablet {
            TaskDetailScreen(TaskDetailViewModel("canvas-1", tasks), onBack = {}, onEdit = {}, onDeleted = {})
        }
        waitFor("Quiz 4")
        compose.onNodeWithText("Open in Canvas").assertExists()
        compose.onNodeWithText("Covers week 6.", substring = true).assertExists()
        compose.onNodeWithContentDescription("Edit task").assertExists()
        compose.onNodeWithContentDescription("Delete task").assertDoesNotExist()
    }

    @Test
    fun canvasTaskForm_hidesCanvasOwnedFields() {
        val now = Instant.now()
        runBlocking {
            db.taskDao().insert(
                Task(
                    id = "canvas-2", title = "Quiz 5", courseId = courseId, dueAt = now.plus(2, ChronoUnit.DAYS),
                    url = null, descriptionHtml = null, status = TaskStatus.NOT_STARTED, targetAt = null, notes = null,
                    source = TaskSource.CANVAS, canvasAssignmentId = 5, canvasSubmissionState = null,
                    canvasSubmittedAt = null, removedFromCanvas = false, createdAt = now, updatedAt = now,
                ),
            )
        }
        showOnTablet { TaskEditorScreen(TaskEditorViewModel("canvas-2", null, null, null, tasks), onClose = {}, onSaved = { _, _ -> }) }
        waitFor("Quiz 5")
        compose.onNodeWithTag("title-field").assertDoesNotExist()
        compose.onNodeWithTag("link-field").assertDoesNotExist()
        compose.onNodeWithTag("target-date").assertExists()
        compose.onNodeWithTag("notes-field").assertExists()
    }

    @Test
    fun folder_hasAddTask_andTappingACardOpensIt() {
        addTask("Problem set 1")
        var addedFor: String? = null
        var opened: String? = null
        val viewModel = FolderViewModel(folderId, folders, tasks, NotebookRepository(db))
        showOnTablet {
            FolderScreen(viewModel, onBack = {}, onOpenTask = { opened = it }, onAddTask = { addedFor = it })
        }
        waitFor("Problem set 1")
        compose.onNodeWithTag("add-task").performClick()
        assertEquals(courseId, addedFor)

        compose.onNodeWithText("Problem set 1").performClick()
        assertNotNull(opened)
    }

    // ---- Phase 4: linked notebooks ----

    @Test
    fun linkNotebook_fromTheTask_showsItsTile_andUnlinkOffersUndo() {
        val id = addTask("Assignment 1")
        val notebooks = NotebookRepository(db)
        val links = com.nzfrankwu.noteapp.data.LinkRepository(db, notebooks)
        val nb = runBlocking { (notebooks.createNotebook(folderId, "Lecture 3") as com.nzfrankwu.noteapp.data.NotebookResult.Success).id }
        showOnTablet { TaskDetailScreen(TaskDetailViewModel(id, tasks, links = links), onBack = {}, onEdit = {}, onDeleted = {}) }

        waitFor("No notebooks linked yet.")
        compose.onNodeWithTag("link-notebook").performClick()
        waitFor("Electromagnetics") // the course folder's notebooks come first
        compose.onNodeWithTag("pick-notebook-Lecture 3").performClick()
        compose.waitUntilAtLeastOneExists(androidx.compose.ui.test.hasTestTag("tile-Lecture 3"), 5_000)
        assertEquals(listOf(nb), runBlocking { links.observeNotebooksForTask(id).first() }.map { it.notebook.id })

        compose.onNodeWithContentDescription("Options for Lecture 3").performClick()
        compose.onNodeWithText("Unlink from this task").performClick()
        waitFor("No notebooks linked yet.")
        waitFor("Undo")
        compose.onNodeWithText("Undo").performClick()
        compose.waitUntilAtLeastOneExists(androidx.compose.ui.test.hasTestTag("tile-Lecture 3"), 5_000)
    }

    @Test
    fun newNotebookForTheTask_isMadeInTheCourseFolder_linked_andOpened() {
        val id = addTask("Lab report")
        val notebooks = NotebookRepository(db)
        val links = com.nzfrankwu.noteapp.data.LinkRepository(db, notebooks)
        var opened: String? = null
        showOnTablet {
            TaskDetailScreen(
                TaskDetailViewModel(id, tasks, links = links), onBack = {}, onEdit = {}, onDeleted = {},
                onOpenNotebook = { nbId, _, _ -> opened = nbId },
            )
        }
        waitFor("No notebooks linked yet.")
        compose.onNodeWithTag("link-notebook").performClick()
        compose.onNodeWithTag("new-notebook-for-task").performClick()
        compose.onNodeWithTag("dialog-confirm").performClick() // the name starts as the task's
        compose.waitUntil(5_000) { opened != null }
        val nb = runBlocking { notebooks.getNotebook(opened!!) }!!
        assertEquals("Lab report", nb.title)
        assertEquals(folderId, nb.folderId)
        assertEquals(listOf(nb.id), runBlocking { links.observeNotebooksForTask(id).first() }.map { it.notebook.id })
    }

    @Test
    fun bookmarks_showGroupedInPageOrder_noteCanChange_deleteOffersUndo_andTapOpensThePage() {
        val id = addTask("Assignment 2")
        val notebooks = NotebookRepository(db)
        val links = com.nzfrankwu.noteapp.data.LinkRepository(db, notebooks)
        val spot = com.nzfrankwu.noteapp.domain.PageSpot(10f, 20f, 100f, 30f)
        val page3 = runBlocking {
            val nb = (notebooks.createNotebook(folderId, "Lecture 4") as com.nzfrankwu.noteapp.data.NotebookResult.Success).id
            notebooks.addPage(nb, 1); notebooks.addPage(nb, 2)
            val pages = notebooks.getPages(nb).map { it.id }
            links.addBookmark(id, pages[2], "Ohm's law", spot)
            links.addBookmark(id, pages[0], null)
            pages[2]
        }
        var opened: Triple<String, String?, com.nzfrankwu.noteapp.domain.PageSpot?>? = null
        showOnTablet {
            TaskDetailScreen(
                TaskDetailViewModel(id, tasks, links = links), onBack = {}, onEdit = {}, onDeleted = {},
                onOpenNotebook = { nb, page, at -> opened = Triple(nb, page, at) },
            )
        }
        compose.waitUntilAtLeastOneExists(androidx.compose.ui.test.hasTestTag("bookmark-3-Ohm's law"), 5_000)
        compose.onNodeWithTag("bookmark-1-").assertExists() // p. 1 has no note
        compose.onNodeWithText("No note").assertExists()

        compose.onNodeWithContentDescription("Edit the note on p. 1").performScrollTo().performClick()
        compose.onNodeWithTag("bookmark-note-edit").performTextInput("Intro")
        compose.onNodeWithTag("bookmark-note-save").performClick()
        compose.waitUntilAtLeastOneExists(androidx.compose.ui.test.hasTestTag("bookmark-1-Intro"), 5_000)

        compose.onNodeWithContentDescription("Delete the bookmark on p. 1").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(androidx.compose.ui.test.hasTestTag("bookmark-1-Intro")).fetchSemanticsNodes().isEmpty() }
        waitFor("Undo")
        compose.onNodeWithText("Undo").performClick()
        compose.waitUntilAtLeastOneExists(androidx.compose.ui.test.hasTestTag("bookmark-1-Intro"), 5_000)

        compose.onNodeWithTag("bookmark-3-Ohm's law").performScrollTo().performClick()
        compose.waitUntil(5_000) { opened != null }
        assertEquals(page3, opened!!.second)
        assertEquals(spot, opened!!.third)
    }

    @Test
    fun taskCard_showsLinkedNotebookChips_thatOpenTheNotebook() {
        val id = addTask("Lab 3")
        val task = runBlocking { tasks.getTask(id) }!!
        var opened: String? = null
        val linked = com.nzfrankwu.noteapp.ui.components.TaskNotebooks(
            mapOf(id to listOf(com.nzfrankwu.noteapp.data.db.LinkedNotebook(id, "nb-1", "Lab notes"))),
        ) { opened = it }
        showOnTablet {
            androidx.compose.runtime.CompositionLocalProvider(com.nzfrankwu.noteapp.ui.components.LocalTaskNotebooks provides linked) {
                com.nzfrankwu.noteapp.ui.components.TaskCard(task, "ELECTENG 204", Instant.now(), java.time.ZoneId.systemDefault(), onClick = {}, onStatusChange = {})
            }
        }
        compose.onNodeWithTag("notebook-chip-Lab notes").performClick()
        compose.waitUntil(5_000) { opened == "nb-1" }
    }
}
