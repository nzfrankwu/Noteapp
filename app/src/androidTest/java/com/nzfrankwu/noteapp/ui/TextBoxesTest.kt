package com.nzfrankwu.noteapp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
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
import com.nzfrankwu.noteapp.data.TextBoxLayout
import com.nzfrankwu.noteapp.data.db.NoteAppDatabase
import com.nzfrankwu.noteapp.domain.ink.InkPalette
import com.nzfrankwu.noteapp.domain.ink.InkText
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/** Text boxes in the editor (PHASE-5.md 5.3): placing, typing, styling, editing, deleting, undo. */
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class TextBoxesTest {
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
        TextBoxLayout.install(context.assets)
        db = Room.inMemoryDatabaseBuilder(context, NoteAppDatabase::class.java).build()
        val folderId = (FolderRepository(db).createFolder("EE 292", null) as FolderResult.Success).folderId
        notebooks = NotebookRepository(db)
        notebookId = (notebooks.createNotebook(folderId, "Lecture 1") as NotebookResult.Success).id
        files = NotebookFiles(tmp.root)
    }

    @After
    fun tearDown() = scope.cancel()

    private fun show(settings: InkToolSettings = InkToolSettings()) {
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
    private fun texts() = viewModel.ink.texts(pageId())

    /** A finger tap on the page, a little above the middle of the screen. */
    private fun tapPage(dy: Float = -120f) {
        compose.onNodeWithTag("notebook-canvas").performTouchInput { click(Offset(centerX, centerY + dy)) }
        compose.waitForIdle()
    }

    private fun typeNewBox(text: String) {
        compose.onNodeWithTag("text-button").performClick()
        tapPage()
        compose.onNodeWithTag("text-box-field").performTextInput(text)
        compose.waitForIdle()
    }

    private fun done() {
        compose.onNodeWithTag("text-done").performClick()
        compose.waitForIdle()
    }

    @Test
    fun withTheTButton_aTapMakesABox_typedTextIsSaved_andUndoRedo() {
        show()
        typeNewBox("Hello, page 1")
        done()
        assertEquals(listOf("Hello, page 1"), texts().map { it.text })
        val box = texts().single()
        assertEquals(InkText.DEFAULT_SIZE, box.size, 0f)
        assertTrue("Measured height ${box.height}", box.height > 10f)

        runBlocking { viewModel.saveNow()?.join() }
        // (An autosave may already be writing, and then saveNow has nothing left to wait for.)
        compose.waitUntil(5_000) { files.read(notebookId, pageId()) is PageRead.Ok }
        val read = files.read(notebookId, pageId()) as PageRead.Ok
        assertEquals(listOf(box), read.texts)

        compose.onNodeWithTag("undo").performClick()
        compose.waitForIdle()
        assertTrue(texts().isEmpty())
        compose.onNodeWithTag("redo").performClick()
        compose.waitForIdle()
        assertEquals(listOf(box), texts())
    }

    @Test
    fun aBoxLeftEmpty_disappears_andIsNoUndoStep() {
        show()
        compose.onNodeWithTag("text-button").performClick()
        tapPage()
        compose.onNodeWithTag("text-box-field").assertExists()
        done()
        assertTrue(texts().isEmpty())
        assertFalse(viewModel.canUndo)
    }

    @Test
    fun sizeBoldAndColour_areSavedWithTheBox() {
        show()
        typeNewBox("Big red bold")
        compose.onNodeWithTag("text-larger").performClick()
        compose.onNodeWithTag("text-larger").performClick()
        compose.onNodeWithTag("text-size").assertTextEquals("16")
        compose.onNodeWithTag("text-bold").performClick()
        compose.onNodeWithTag("text-color-3").performClick()
        done()
        val box = texts().single()
        assertEquals(16f, box.size, 0f)
        assertTrue(box.bold)
        assertEquals(InkPalette.RED, box.colorArgb)
        // The next new box (T is still on) starts with the same look.
        tapPage(dy = 150f)
        compose.onNodeWithTag("text-box-field").performTextInput("Second")
        done()
        assertEquals(listOf(16f, 16f), texts().map { it.size })
    }

    @Test
    fun tappingABoxWithAFinger_editsIt_asOneMoreUndoStep() {
        show()
        typeNewBox("Hello")
        done()
        compose.onNodeWithTag("text-button").performClick() // T off: the finger only edits boxes now
        tapPage()
        compose.onNodeWithTag("text-box-field").performTextInput(" world")
        done()
        assertEquals(listOf("Hello world"), texts().map { it.text })
        compose.onNodeWithTag("undo").performClick()
        compose.waitForIdle()
        assertEquals(listOf("Hello"), texts().map { it.text })
    }

    @Test
    fun aTapOnBlankPaperWithTOff_makesNoBox() {
        show()
        tapPage()
        compose.onNodeWithTag("text-box-field").assertDoesNotExist()
        assertTrue(texts().isEmpty())
    }

    @Test
    fun tappingOutsideTheBox_finishesIt() {
        show()
        typeNewBox("Finished by a tap")
        tapPage(dy = 200f)
        compose.onNodeWithTag("text-box-field").assertDoesNotExist()
        assertEquals(listOf("Finished by a tap"), texts().map { it.text })
    }

    @Test
    fun holdingAFingerOnABox_typesInIt() {
        show()
        typeNewBox("Hold")
        done()
        compose.onNodeWithTag("text-button").performClick() // T off
        compose.onNodeWithTag("notebook-canvas").performTouchInput { longClick(Offset(centerX, centerY - 120f)) }
        compose.waitUntilAtLeastOneExists(androidx.compose.ui.test.hasTestTag("text-box-field"), 3_000)
        compose.onNodeWithTag("text-box-field").performTextInput(" to edit")
        done()
        assertEquals(listOf("Hold to edit"), texts().map { it.text })
    }

    @Test
    fun holdingAnotherBox_whileTyping_keepsWhatWasTyped() {
        // Review fix: the box being typed in is saved, not dropped, when another box is held.
        show()
        typeNewBox("First")
        done()
        // T is still on: a second box, ABOVE the first (low on the screen the keyboard would move the page).
        tapPage(dy = -300f)
        compose.onNodeWithTag("text-box-field").performTextInput("Second")
        compose.onNodeWithTag("notebook-canvas").performTouchInput { longClick(Offset(centerX, centerY - 120f)) }
        compose.waitForIdle()
        compose.onNodeWithTag("text-box-field").performTextInput(" box")
        done()
        assertEquals(setOf("First box", "Second"), texts().map { it.text }.toSet())
    }

    @Test
    fun aLassoTapOnABox_selectsIt_forDuplicateAndDelete() {
        // With "Draw with finger" on, the test's finger works like the pen.
        show(InkToolSettings(drawWithFinger = true))
        typeNewBox("Pick me up")
        done()
        compose.onNodeWithTag("text-button").performClick() // T off
        compose.runOnUiThread { viewModel.pickTool(com.nzfrankwu.noteapp.domain.ink.EditorTool.LASSO) }
        compose.waitForIdle()
        tapPage()
        compose.onNodeWithTag("lasso-menu").assertExists()
        compose.onNodeWithTag("text-box-field").assertDoesNotExist()
        compose.onNodeWithTag("lasso-duplicate").performClick()
        compose.waitForIdle()
        assertEquals(listOf("Pick me up", "Pick me up"), texts().map { it.text })
        assertEquals(2, texts().map { it.id }.toSet().size)
        // The copy is selected now: Delete takes it away, leaving the first.
        compose.onNodeWithTag("lasso-delete").performClick()
        compose.waitForIdle()
        assertEquals(1, texts().size)
        compose.onNodeWithTag("undo").performClick()
        compose.waitForIdle()
        assertEquals(2, texts().size)
    }

    @Test
    fun delete_removesTheBox_andUndoBringsItBack() {
        show()
        typeNewBox("Delete me")
        done()
        tapPage()
        compose.onNodeWithTag("text-delete").performClick()
        compose.waitForIdle()
        assertTrue(texts().isEmpty())
        compose.onNodeWithTag("undo").performClick()
        compose.waitForIdle()
        assertEquals(listOf("Delete me"), texts().map { it.text })
    }
}
