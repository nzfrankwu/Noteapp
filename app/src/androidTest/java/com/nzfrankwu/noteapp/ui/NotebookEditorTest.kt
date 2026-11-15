package com.nzfrankwu.noteapp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
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

    private fun show(settings: InkToolSettings) {
        compose.runOnUiThread {
            viewModel = NotebookViewModel(notebookId, notebooks, files, InMemoryInkSettings(settings), scope)
        }
        compose.setContent {
            NoteAppTheme {
                Box(Modifier.size(width = 1100.dp, height = 760.dp)) {
                    NotebookScreen(viewModel, onBack = {})
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
    fun addingAPage_showsItAndCountsIt() {
        show(InkToolSettings())
        compose.onNodeWithTag("add-page").performClick()
        compose.waitUntil(5_000) { viewModel.pages.value.size == 2 }
        compose.onNodeWithText("2 / 2").assertExists()
        compose.onNodeWithTag("undo").performClick()
        compose.waitUntil(5_000) { viewModel.pages.value.size == 1 }
    }
}
