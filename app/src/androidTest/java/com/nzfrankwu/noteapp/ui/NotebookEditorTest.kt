package com.nzfrankwu.noteapp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performTextInput
import kotlinx.coroutines.flow.first
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.longClick
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nzfrankwu.noteapp.data.FolderRepository
import com.nzfrankwu.noteapp.data.FolderResult
import com.nzfrankwu.noteapp.data.InMemoryInkSettings
import com.nzfrankwu.noteapp.data.NotebookFiles
import com.nzfrankwu.noteapp.data.NotebookRepository
import com.nzfrankwu.noteapp.data.NotebookResult
import com.nzfrankwu.noteapp.data.PageRead
import com.nzfrankwu.noteapp.data.db.NoteAppDatabase
import com.nzfrankwu.noteapp.domain.ink.EditorTool
import com.nzfrankwu.noteapp.domain.ink.InkToolSettings
import com.nzfrankwu.noteapp.ui.notebook.NotebookScreen
import com.nzfrankwu.noteapp.ui.notebook.NotebookViewModel
import com.nzfrankwu.noteapp.ui.theme.NoteAppTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/** The notebook editor end to end: drawing, erasing, undo, autosave, palm rejection (PHASE-2.md 2.4–2.8). */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class NotebookEditorTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var db: NoteAppDatabase
    private lateinit var notebooks: NotebookRepository
    private lateinit var files: NotebookFiles
    private lateinit var notebookId: String
    private lateinit var viewModel: NotebookViewModel
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, NoteAppDatabase::class.java).build()
        val folderId = (FolderRepository(db).createFolder("EE 292", null) as FolderResult.Success).folderId
        notebooks = NotebookRepository(db)
        notebookId = (notebooks.createNotebook(folderId, "Lecture 1") as NotebookResult.Success).id
        files = NotebookFiles(tmp.root)
    }

    @After
    fun tearDown() {
        // The editor's view model isn't cleared in a screen test, so its queries keep running:
        // leave the in-memory database open (like the other screen tests) and stop the saves.
        scope.cancel()
    }

    private fun show(
        settings: InkToolSettings,
        links: com.nzfrankwu.noteapp.data.LinkRepository? = null,
        onOpenTask: (String) -> Unit = {},
    ) {
        compose.runOnUiThread {
            viewModel = NotebookViewModel(notebookId, notebooks, files, InMemoryInkSettings(settings), scope, linkRepository = links)
        }
        compose.setContent {
            NoteAppTheme {
                Box(Modifier.size(width = 1100.dp, height = 760.dp)) {
                    NotebookScreen(viewModel, onBack = {}, onOpenTask = onOpenTask)
                }
            }
        }
        compose.waitUntil(5_000) { viewModel.pages.value.isNotEmpty() && viewModel.isEditable(viewModel.pages.value.first().id) }
        compose.waitForIdle()
    }

    private fun pageId() = viewModel.pages.value.first().id
    private fun strokeCount() = viewModel.ink.strokes(pageId()).size

    /** Draws across the middle of the screen (where the first page is). */
    private fun drawLine(y: Float) {
        compose.onNodeWithTag("notebook-canvas").performTouchInput {
            swipe(Offset(centerX - 120f, y), Offset(centerX + 120f, y + 20f), durationMillis = 300)
        }
        compose.waitForIdle()
    }

    @Test
    fun drawing_addsAStroke_thatIsSavedToItsPageFile() {
        show(InkToolSettings(drawWithFinger = true))
        drawLine(300f)
        compose.waitUntil(5_000) { strokeCount() == 1 }

        runBlocking { viewModel.saveNow()?.join() }
        val read = files.read(notebookId, pageId())
        assertTrue("Saved: $read", read is PageRead.Ok && read.strokes.size == 1)
    }

    @Test
    fun aSaveThatFails_isTriedAgain_andTheStrokeStillReachesItsFile() {
        show(InkToolSettings(drawWithFinger = true))
        // A plain file where the notebooks folder should be makes the first tries fail.
        val blocker = java.io.File(tmp.root, NotebookFiles.DIR)
        blocker.deleteRecursively()
        assertTrue(blocker.createNewFile())

        drawLine(300f)
        compose.waitUntil(5_000) { strokeCount() == 1 }
        Thread.sleep(1_500) // the autosave has run and failed at least once
        assertTrue(files.read(notebookId, pageId()) is PageRead.Missing)
        assertTrue(blocker.delete()) // the disk "recovers" before the retries run out

        compose.waitUntil(10_000) {
            val read = files.read(notebookId, pageId())
            read is PageRead.Ok && read.strokes.size == 1
        }
    }

    @Test
    fun aFinger_neverDraws_whenDrawWithFingerIsOff() {
        show(InkToolSettings(drawWithFinger = false))
        drawLine(300f)
        compose.waitForIdle()
        Thread.sleep(500)
        assertEquals(0, strokeCount())
    }

    @Test
    fun undoAndRedo_removeAndBringBackAStroke() {
        show(InkToolSettings(drawWithFinger = true))
        compose.onNodeWithTag("undo").assertIsNotEnabled()
        drawLine(300f)
        compose.waitUntil(5_000) { strokeCount() == 1 }

        compose.onNodeWithTag("undo").assertIsEnabled().performClick()
        compose.waitUntil(5_000) { strokeCount() == 0 }
        compose.onNodeWithTag("redo").performClick()
        compose.waitUntil(5_000) { strokeCount() == 1 }
    }

    @Test
    fun theEraser_removesTheStrokeItCrosses() {
        show(InkToolSettings(drawWithFinger = true))
        drawLine(300f)
        compose.waitUntil(5_000) { strokeCount() == 1 }

        compose.onNodeWithContentDescription("Eraser").performClick()
        compose.waitUntil(5_000) { viewModel.settings.value.tool == EditorTool.ERASER }
        compose.onNodeWithTag("notebook-canvas").performTouchInput {
            swipe(Offset(centerX, 200f), Offset(centerX, 420f), durationMillis = 300)
        }
        compose.waitUntil(5_000) { strokeCount() == 0 }
    }

    /** Phase 7, 7.4: "Part of strokes" cuts the line in two; the whole drag is one undo step. */
    @Test
    fun thePartialEraser_cutsTheLine_andOneUndoBringsItBack() {
        show(InkToolSettings(drawWithFinger = true, eraserPartial = true))
        drawLine(300f)
        compose.waitUntil(5_000) { strokeCount() == 1 }
        val original = viewModel.ink.strokes(pageId()).single()

        compose.onNodeWithContentDescription("Eraser").performClick()
        compose.waitUntil(5_000) { viewModel.settings.value.tool == EditorTool.ERASER }
        compose.onNodeWithTag("notebook-canvas").performTouchInput {
            swipe(Offset(centerX, 200f), Offset(centerX, 420f), durationMillis = 300)
        }
        compose.waitUntil(5_000) { strokeCount() == 2 }
        assertTrue(viewModel.ink.strokes(pageId()).none { it.id == original.id })

        // Saved like any strokes.
        runBlocking { viewModel.saveNow()?.join() }
        val read = files.read(notebookId, pageId())
        assertTrue("Saved: $read", read is PageRead.Ok && read.strokes.size == 2)

        compose.onNodeWithTag("undo").performClick()
        compose.waitUntil(5_000) { strokeCount() == 1 }
        assertEquals(original, viewModel.ink.strokes(pageId()).single())
        compose.onNodeWithTag("undo").assertIsEnabled().performClick() // then the line itself
        compose.waitUntil(5_000) { strokeCount() == 0 }
    }

    @Test
    fun savedStrokes_comeBackWhenTheNotebookOpensAgain() {
        show(InkToolSettings(drawWithFinger = true))
        drawLine(300f)
        drawLine(400f)
        compose.waitUntil(5_000) { strokeCount() == 2 }
        runBlocking { viewModel.saveNow()?.join() }

        // A fresh editor reads the page file.
        val again = NotebookViewModel(notebookId, notebooks, files, InMemoryInkSettings(), scope)
        compose.waitUntil(5_000) { again.isEditable(pageId()) }
        assertEquals(2, again.ink.strokes(pageId()).size)
    }

    @Test
    fun aQuickFlick_keepsThePagesMoving_butASlowDragDoesnt() {
        runBlocking { repeat(5) { notebooks.addPage(notebookId, Int.MAX_VALUE) } }
        show(InkToolSettings())
        compose.waitUntil(5_000) { viewModel.pages.value.size == 6 }
        compose.onNodeWithText("1 / 6").assertExists()

        // A slow drag of 300 px stays on page 1 (no coasting).
        compose.onNodeWithTag("notebook-canvas").performTouchInput {
            swipe(Offset(centerX + 150f, centerY), Offset(centerX - 150f, centerY), durationMillis = 1_500)
        }
        compose.waitForIdle()
        compose.onNodeWithText("1 / 6").assertExists()

        // The same distance flicked quickly coasts on past several pages.
        compose.onNodeWithTag("notebook-canvas").performTouchInput {
            swipe(Offset(centerX + 150f, centerY), Offset(centerX - 150f, centerY), durationMillis = 60)
        }
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        val badge = (3..6).firstOrNull { n ->
            compose.onAllNodesWithText("$n / 6").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue("The flick should reach page 3 or later (badge page: $badge)", badge != null)
    }

    @Test
    fun addingAPage_showsItAndCountsIt() {
        show(InkToolSettings())
        compose.onNodeWithTag("add-page").performClick()
        compose.waitUntil(5_000) { viewModel.pages.value.size == 2 }
        compose.onNodeWithText("2 / 2").assertExists()
        compose.onNodeWithTag("undo").performClick()
        compose.waitUntil(5_000) { viewModel.pages.value.size == 1 }
    }

    // ---- Phase 4: linked tasks ----

    /** Review fix (7.9): "Find in this notebook" sends the open editor to the page; it keeps its undo history. */
    @Test
    fun aFindRequest_takesTheOpenNotebookToThePage_andKeepsUndo() {
        val second = runBlocking { (notebooks.addPage(notebookId, 1) as NotebookResult.Success).id }
        val requests = kotlinx.coroutines.flow.MutableStateFlow<com.nzfrankwu.noteapp.ui.notebook.FindRequest?>(null)
        compose.runOnUiThread {
            viewModel = NotebookViewModel(notebookId, notebooks, files, InMemoryInkSettings(InkToolSettings(drawWithFinger = true)), scope)
        }
        compose.setContent {
            NoteAppTheme {
                androidx.compose.runtime.CompositionLocalProvider(com.nzfrankwu.noteapp.ui.notebook.LocalFindRequests provides requests) {
                    Box(Modifier.size(width = 1100.dp, height = 760.dp)) { NotebookScreen(viewModel, onBack = {}, onOpenTask = {}) }
                }
            }
        }
        compose.waitUntil(5_000) { viewModel.pages.value.size == 2 && viewModel.isEditable(viewModel.pages.value.first().id) }
        drawLine(300f)
        compose.waitUntil(5_000) { strokeCount() == 1 }

        requests.value = com.nzfrankwu.noteapp.ui.notebook.FindRequest(notebookId, second, "anything")
        compose.waitUntil(5_000) { requests.value == null } // the editor took it
        compose.waitUntil(5_000) { compose.onAllNodesWithText("2 / 2").fetchSemanticsNodes().isNotEmpty() } // on that page
        compose.onNodeWithTag("undo").assertIsEnabled() // same editor: its history is still there
        // A request for another notebook is left alone.
        requests.value = com.nzfrankwu.noteapp.ui.notebook.FindRequest("someone-else", second, "x")
        compose.waitForIdle()
        assertEquals("someone-else", requests.value?.notebookId)
    }

    /**
     * Coming back from "Find in this notebook" rebuilds the editor with the request already waiting
     * (the user's report, 18 Nov): it must still end on the found page, not stay on the first one.
     * A handwritten match (with a spot) goes there too.
     */
    @Test
    fun aFindRequest_waitingWhenTheEditorIsShown_stillGoesToThePage() {
        val second = runBlocking { (notebooks.addPage(notebookId, 1) as NotebookResult.Success).id }
        val third = runBlocking { (notebooks.addPage(notebookId, 2) as NotebookResult.Success).id }
        val requests = kotlinx.coroutines.flow.MutableStateFlow<com.nzfrankwu.noteapp.ui.notebook.FindRequest?>(
            com.nzfrankwu.noteapp.ui.notebook.FindRequest(notebookId, third, "anything"),
        )
        compose.runOnUiThread {
            viewModel = NotebookViewModel(notebookId, notebooks, files, InMemoryInkSettings(InkToolSettings(drawWithFinger = true)), scope)
        }
        compose.setContent {
            NoteAppTheme {
                androidx.compose.runtime.CompositionLocalProvider(com.nzfrankwu.noteapp.ui.notebook.LocalFindRequests provides requests) {
                    Box(Modifier.size(width = 1100.dp, height = 760.dp)) { NotebookScreen(viewModel, onBack = {}, onOpenTask = {}) }
                }
            }
        }
        compose.waitUntil(5_000) { requests.value == null }
        compose.waitForIdle()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("3 / 3").fetchSemanticsNodes().isNotEmpty() }

        // A handwritten match on page 2, zoomed to its line.
        requests.value = com.nzfrankwu.noteapp.ui.notebook.FindRequest(
            notebookId, second, "anything", com.nzfrankwu.noteapp.domain.PageSpot(0f, 200f, 500f, 40f),
        )
        compose.waitUntil(5_000) { requests.value == null }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("2 / 3").fetchSemanticsNodes().isNotEmpty() }
    }

    /** Phase 7, 7.3: one page's paper, then every page's, each one undo step. */
    @Test
    fun paper_forOnePage_thenForAllPages_andUndo() {
        val second = runBlocking { (notebooks.addPage(notebookId, 1) as NotebookResult.Success).id }
        show(InkToolSettings(drawWithFinger = true))
        fun templates() = runBlocking { notebooks.getPages(notebookId).associate { it.id to it.template } }
        fun notebookPaper() = runBlocking { notebooks.getNotebook(notebookId)!!.template }

        compose.onNodeWithContentDescription("Notebook options").performClick()
        compose.onNodeWithTag("page-paper-grid").performScrollTo().performClick()
        compose.waitUntil(5_000) { templates()[pageId()] == com.nzfrankwu.noteapp.domain.PageTemplate.GRID }
        assertEquals("only this page", null, templates()[second])
        assertEquals(com.nzfrankwu.noteapp.domain.PageTemplate.LINED, notebookPaper())

        compose.onNodeWithContentDescription("Notebook options").performClick()
        compose.onNodeWithTag("notebook-paper-dots").performScrollTo().performClick()
        compose.waitUntil(5_000) { notebookPaper() == com.nzfrankwu.noteapp.domain.PageTemplate.DOTS && templates()[pageId()] == null }

        compose.runOnUiThread { viewModel.undo() }
        compose.waitUntil(5_000) {
            notebookPaper() == com.nzfrankwu.noteapp.domain.PageTemplate.LINED &&
                templates()[pageId()] == com.nzfrankwu.noteapp.domain.PageTemplate.GRID
        }
        compose.runOnUiThread { viewModel.undo() }
        compose.waitUntil(5_000) { templates()[pageId()] == null }
        compose.runOnUiThread { viewModel.redo() }
        compose.waitUntil(5_000) { templates()[pageId()] == com.nzfrankwu.noteapp.domain.PageTemplate.GRID }
    }

    @Test
    fun linkToATask_fromTheMenu_showsTheChip_whichOpensTheTask() {
        val links = com.nzfrankwu.noteapp.data.LinkRepository(db, notebooks)
        val taskId = runBlocking {
            FolderRepository(db).createFolder("Maths", "MATHS 208")
            val courseId = db.courseDao().getAll().single().id
            val draft = com.nzfrankwu.noteapp.data.TaskDraft("Problem set 4", courseId, dueAt = java.time.Instant.now().plus(java.time.Duration.ofDays(3)))
            (com.nzfrankwu.noteapp.data.TaskRepository(db).createTask(draft) as com.nzfrankwu.noteapp.data.TaskResult.Success).taskId
        }
        var opened: String? = null
        show(InkToolSettings(drawWithFinger = true), links) { opened = it }

        compose.onNodeWithTag("linked-task-chip").assertDoesNotExist()
        compose.onNodeWithContentDescription("Notebook options").performClick()
        compose.onNodeWithTag("menu-link-task").performClick()
        // The notebook's folder has no course, so "Other tasks" starts open.
        compose.onNodeWithTag("pick-task-Problem set 4").performClick()

        compose.waitUntilAtLeastOneExists(androidx.compose.ui.test.hasTestTag("linked-task-chip"), 5_000)
        compose.onNodeWithText("Problem set 4 · 3 days left", substring = true).assertExists()
        compose.onNodeWithTag("linked-task-chip").performClick()
        compose.onNodeWithTag("linked-task-Problem set 4").performClick()
        compose.waitUntil(5_000) { opened == taskId }
    }

    @Test
    fun bookmarkButton_savesABookmarkOfThePage_forTheChosenTask_withItsNote() {
        val links = com.nzfrankwu.noteapp.data.LinkRepository(db, notebooks)
        val taskId = runBlocking {
            FolderRepository(db).createFolder("Maths", "MATHS 208")
            val courseId = db.courseDao().getAll().single().id
            (com.nzfrankwu.noteapp.data.TaskRepository(db).createTask(com.nzfrankwu.noteapp.data.TaskDraft("Problem set 5", courseId))
                as com.nzfrankwu.noteapp.data.TaskResult.Success).taskId
        }
        show(InkToolSettings(drawWithFinger = true), links)

        compose.onNodeWithTag("bookmark-page").performClick()
        compose.onNodeWithTag("bookmark-save").assertIsNotEnabled() // no task picked yet
        compose.onNodeWithTag("pick-task-Problem set 5").performClick()
        compose.onNodeWithTag("bookmark-note").performTextInput("Q3 working")
        compose.onNodeWithTag("bookmark-save").performClick()

        compose.waitUntil(5_000) { runBlocking { links.observeBookmarksForTask(taskId).first() }.isNotEmpty() }
        val entry = runBlocking { links.observeBookmarksForTask(taskId).first() }.single().bookmarks.single()
        assertEquals(1, entry.pageNumber)
        assertEquals("Q3 working", entry.note)
        assertEquals(null, entry.spot)
        // Bookmarking linked the notebook too, so the chip shows the task.
        compose.waitUntilAtLeastOneExists(androidx.compose.ui.test.hasTestTag("linked-task-chip"), 5_000)
    }

    @Test
    fun taskPickers_searchAndFilter_andTheBookmarkSheetShowsThePickedTask() {
        val links = com.nzfrankwu.noteapp.data.LinkRepository(db, notebooks)
        runBlocking {
            val folders = FolderRepository(db)
            val maths = (folders.createFolder("Maths", "MATHS 208") as FolderResult.Success).folderId
            folders.createFolder("Physics", "PHYSICS 101")
            val courses = db.courseDao().getAll().associateBy { it.code }
            val tasks = com.nzfrankwu.noteapp.data.TaskRepository(db)
            val now = java.time.Instant.now()
            tasks.createTask(com.nzfrankwu.noteapp.data.TaskDraft("Problem set 4", courses.getValue("MATHS 208").id, dueAt = now.plus(java.time.Duration.ofDays(3))))
            tasks.createTask(com.nzfrankwu.noteapp.data.TaskDraft("Old quiz", courses.getValue("MATHS 208").id, dueAt = now.minus(java.time.Duration.ofDays(2))))
            tasks.createTask(com.nzfrankwu.noteapp.data.TaskDraft("Lab 1", courses.getValue("PHYSICS 101").id, dueAt = now.plus(java.time.Duration.ofDays(1))))
            notebookId = (notebooks.createNotebook(maths, "Calculus") as NotebookResult.Success).id
        }
        show(InkToolSettings(drawWithFinger = true), links)

        compose.onNodeWithContentDescription("Notebook options").performClick()
        compose.onNodeWithTag("menu-link-task").performClick()
        // This course, due soon.
        compose.waitUntilAtLeastOneExists(androidx.compose.ui.test.hasTestTag("pick-task-Problem set 4"), 5_000)
        compose.onNodeWithTag("pick-task-Old quiz").assertDoesNotExist()
        compose.onNodeWithTag("pick-task-Lab 1").assertDoesNotExist()
        compose.onNodeWithTag("filter-past").performClick()
        compose.onNodeWithTag("pick-task-Old quiz").assertExists()
        compose.onNodeWithTag("pick-task-Problem set 4").assertDoesNotExist()
        compose.onNodeWithTag("filter-soon").performClick()
        compose.onNodeWithTag("filter-course").performClick()
        compose.onNodeWithTag("course-option-All other courses").performClick()
        compose.onNodeWithTag("pick-task-Lab 1").assertExists()
        compose.onNodeWithTag("task-search").performTextInput("nothing like this")
        compose.onNodeWithText("No tasks match.").assertExists()
        compose.onNodeWithTag("picker-cancel").performClick()

        // The bookmark sheet: the picked task is marked.
        compose.onNodeWithTag("bookmark-page").performClick()
        compose.onNodeWithTag("pick-task-Problem set 4").assertIsNotSelected()
        compose.onNodeWithTag("pick-task-Problem set 4").performClick()
        compose.onNodeWithTag("pick-task-Problem set 4").assertIsSelected()
        compose.onNodeWithTag("bookmark-for").assertTextEquals("For: Problem set 4")
        compose.onNodeWithTag("bookmark-save").assertIsEnabled()
    }

    @Test
    fun theBookmarkPen_takesAPictureOfTheArea_andSavesItWithTheBookmark() {
        val links = com.nzfrankwu.noteapp.data.LinkRepository(db, notebooks, files = files)
        val taskId = runBlocking {
            FolderRepository(db).createFolder("Maths", "MATHS 208")
            val courseId = db.courseDao().getAll().single().id
            (com.nzfrankwu.noteapp.data.TaskRepository(db).createTask(com.nzfrankwu.noteapp.data.TaskDraft("Problem set 6", courseId))
                as com.nzfrankwu.noteapp.data.TaskResult.Success).taskId
        }
        show(InkToolSettings(drawWithFinger = true, bookmarkRect = true), links)
        drawLine(280f) // something to see in the picture
        compose.waitUntil(5_000) { strokeCount() == 1 }
        compose.runOnUiThread { viewModel.pickTool(EditorTool.BOOKMARK) }
        compose.waitForIdle()

        // A rectangle around the line.
        compose.onNodeWithTag("notebook-canvas").performTouchInput {
            swipe(Offset(centerX - 160f, 240f), Offset(centerX + 160f, 340f), durationMillis = 400)
        }
        compose.waitUntilAtLeastOneExists(androidx.compose.ui.test.hasTestTag("bookmark-sheet"), 5_000)
        compose.onNodeWithTag("bookmark-picture").assertExists()
        compose.onNodeWithTag("pick-task-Problem set 6").performClick()
        compose.onNodeWithTag("bookmark-note").performTextInput("The derivative")
        compose.onNodeWithTag("bookmark-save").performClick()

        compose.waitUntil(5_000) { runBlocking { links.observeBookmarksForTask(taskId).first() }.isNotEmpty() }
        val entry = runBlocking { links.observeBookmarksForTask(taskId).first() }.single().bookmarks.single()
        assertEquals("The derivative", entry.note)
        assertTrue("Has a picture: ${entry.image}", entry.image?.startsWith("bookmarks/") == true)
        val picture = links.pictureFile(entry)
        assertTrue("The picture file exists", picture != null && picture.length() > 0)
        val bmp = android.graphics.BitmapFactory.decodeFile(picture!!.path)
        assertTrue("A real picture: ${bmp.width}×${bmp.height}", bmp.width > 100 && bmp.height > 30)
        // The spot is the rectangle (page units), a little wider than tall.
        val spot = entry.spot!!
        assertTrue("Spot $spot", spot.width > spot.height)
    }

    @Test
    fun aBookmarkPenSlip_orCancel_savesNothing() {
        val links = com.nzfrankwu.noteapp.data.LinkRepository(db, notebooks, files = files)
        runBlocking { FolderRepository(db).createFolder("Maths", "MATHS 208") }
        show(InkToolSettings(drawWithFinger = true, tool = EditorTool.BOOKMARK), links)
        // A tiny loop is a slip: no sheet.
        compose.onNodeWithTag("notebook-canvas").performTouchInput { swipe(Offset(centerX, 300f), Offset(centerX + 4f, 302f), durationMillis = 100) }
        compose.waitForIdle()
        compose.onNodeWithTag("bookmark-sheet").assertDoesNotExist()
        // A real loop, then Cancel: nothing saved, and no ink was drawn.
        compose.onNodeWithTag("notebook-canvas").performTouchInput {
            swipe(Offset(centerX - 100f, 250f), Offset(centerX + 100f, 380f), durationMillis = 400)
        }
        compose.waitUntilAtLeastOneExists(androidx.compose.ui.test.hasTestTag("bookmark-sheet"), 5_000)
        compose.onNodeWithTag("bookmark-cancel").performClick()
        compose.waitForIdle()
        assertEquals(0, strokeCount())
        assertTrue(java.io.File(tmp.root, "${NotebookFiles.DIR}/$notebookId/bookmarks").listFiles().isNullOrEmpty())
    }

    // ---- Phase 4: selecting PDF text ----

    @Test
    fun holdingAFingerOnPdfText_selectsIt_andHighlightIsOneUndoableStroke() {
        // A one-page PDF notebook with a line of real text.
        val bytes = java.io.ByteArrayOutputStream().also { out ->
            val doc = android.graphics.pdf.PdfDocument()
            val page = doc.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(842, 595, 1).create())
            page.canvas.drawText("Transmission lines", 60f, 80f, android.graphics.Paint().apply { textSize = 28f })
            doc.finishPage(page)
            doc.writeTo(out)
            doc.close()
        }.toByteArray()
        val size = com.nzfrankwu.noteapp.domain.PageSize(842f, 595f)
        runBlocking {
            val folderId = (FolderRepository(db).createFolder("Slides", null) as FolderResult.Success).folderId
            val id = notebooks.newNotebookId()
            files.assetFile(id, "pdf-1.pdf").apply { parentFile?.mkdirs(); writeBytes(bytes) }
            notebooks.createPdfNotebook(id, folderId, "Slides", "pdf-1.pdf", listOf(size))
            notebookId = id
        }
        val word = com.tom_roush.pdfbox.pdmodel.PDDocument.load(bytes).use { com.nzfrankwu.noteapp.data.PdfText.read(it, 0, size) }.words.first()
        show(InkToolSettings())

        // Where that word is on screen: the page is fitted like the editor fits it.
        val canvas = compose.onNodeWithTag("notebook-canvas").fetchSemanticsNode().size
        val strip = com.nzfrankwu.noteapp.domain.ink.PageStrip(listOf(size))
        val cam = strip.fit(com.nzfrankwu.noteapp.domain.ink.FitMode.DEFAULT, 0, canvas.width.toFloat(), canvas.height.toFloat())
        val at = Offset(cam.toScreenX(strip.pageLeft(0) + word.centreX), cam.toScreenY(strip.pageTop(0) + word.centreY))
        compose.onNodeWithTag("notebook-canvas").performTouchInput { longClick(at, durationMillis = 900) }

        compose.waitUntilAtLeastOneExists(androidx.compose.ui.test.hasTestTag("text-menu"), 10_000)
        compose.onNodeWithTag("text-highlight").performClick()
        compose.waitUntil(5_000) { strokeCount() == 1 }
        assertTrue(viewModel.ink.strokes(pageId()).single().tool.isHighlighter)
        compose.onNodeWithTag("text-menu").assertDoesNotExist()

        compose.onNodeWithTag("undo").performClick()
        compose.waitUntil(5_000) { strokeCount() == 0 }
    }

    @Test
    fun holdingAFingerOnAPlainPage_doesntSelectText() {
        show(InkToolSettings())
        compose.onNodeWithTag("notebook-canvas").performTouchInput { longClick(center, durationMillis = 900) }
        compose.waitForIdle()
        Thread.sleep(500)
        compose.onNodeWithTag("text-menu").assertDoesNotExist()
    }
}
