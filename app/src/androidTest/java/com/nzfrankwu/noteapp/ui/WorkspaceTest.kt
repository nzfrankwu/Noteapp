package com.nzfrankwu.noteapp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nzfrankwu.noteapp.data.FolderRepository
import com.nzfrankwu.noteapp.data.FolderResult
import com.nzfrankwu.noteapp.data.InMemoryEditorViewStore
import com.nzfrankwu.noteapp.data.InMemoryInkSettings
import com.nzfrankwu.noteapp.data.NotebookFiles
import com.nzfrankwu.noteapp.data.NotebookRepository
import com.nzfrankwu.noteapp.data.NotebookResult
import com.nzfrankwu.noteapp.data.db.NoteAppDatabase
import com.nzfrankwu.noteapp.domain.SplitMode
import com.nzfrankwu.noteapp.domain.ink.InkToolSettings
import com.nzfrankwu.noteapp.ui.notebook.NotebookViewModel
import com.nzfrankwu.noteapp.ui.notebook.NotebookWorkspace
import com.nzfrankwu.noteapp.ui.theme.NoteAppTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/** The recent panel and split view (PHASE-5.md 5.7–5.9). */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class WorkspaceTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var db: NoteAppDatabase
    private lateinit var notebooks: NotebookRepository
    private lateinit var files: NotebookFiles
    private lateinit var lecture: String
    private lateinit var tutorial: String
    private val store = InMemoryEditorViewStore()
    private val ink = InMemoryInkSettings(InkToolSettings())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val models = HashMap<String, NotebookViewModel>()
    private var replacedWith: String? = null

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, NoteAppDatabase::class.java).build()
        val folderId = (FolderRepository(db).createFolder("EE 292", null) as FolderResult.Success).folderId
        notebooks = NotebookRepository(db)
        lecture = (notebooks.createNotebook(folderId, "Lecture 7") as NotebookResult.Success).id
        tutorial = (notebooks.createNotebook(folderId, "Tutorial 5") as NotebookResult.Success).id
        files = NotebookFiles(tmp.root)
        store.notebookOpened(tutorial)
    }

    @After
    fun tearDown() = scope.cancel()

    private fun show(width: Dp = 1280.dp, height: Dp = 800.dp) {
        compose.setContent {
            NoteAppTheme {
                Box(Modifier.size(width, height)) {
                    NotebookWorkspace(
                        firstId = lecture,
                        viewModelFor = { id -> models.getOrPut(id) { NotebookViewModel(id, notebooks, files, ink, scope) } },
                        viewStore = store,
                        allNotebooks = notebooks.observeAllListItems(),
                        onBack = {},
                        onReplace = { replacedWith = it },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun openTutorialBeside() {
        compose.onNodeWithTag("recent-button").performClick()
        compose.onNodeWithTag("recent-menu-Tutorial 5").performClick()
        compose.onNodeWithTag("open-beside").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("split-panes"), 5_000)
    }

    @Test
    fun thePanel_listsRecentNotebooks_newestFirst_withTheOpenOneMarked() {
        show()
        compose.waitUntil(5_000) { runBlocking { store.recentNotebooks.first() }.firstOrNull() == lecture }
        compose.onNodeWithTag("recent-button").performClick()
        compose.onNodeWithTag("recent-panel").assertExists()
        compose.onNodeWithContentDescription("Lecture 7 (open)").assertExists()
        compose.onNodeWithContentDescription("Tutorial 5").assertExists()
        assertEquals(listOf(lecture, tutorial), runBlocking { store.recentNotebooks.first() })
    }

    @Test
    fun tappingANotebook_opensItInPlace() {
        show()
        compose.onNodeWithTag("recent-button").performClick()
        compose.onNodeWithTag("recent-Tutorial 5").performClick()
        compose.waitForIdle()
        assertEquals(tutorial, replacedWith)
    }

    @Test
    fun openBeside_splitsTheScreen_sideBySideInLandscape() {
        show()
        openTutorialBeside()
        compose.onNodeWithTag("pane-0-$lecture").assertExists()
        compose.onNodeWithTag("pane-1-$tutorial").assertExists()
        assertEquals(SplitMode.SIDE_BY_SIDE, runBlocking { store.splitMode.first() })
    }

    @Test
    fun theHandleMenu_swaps_changesTheMode_andCloses_keepingTheActiveNotebook() {
        show()
        openTutorialBeside() // the new one (Tutorial 5) is active
        compose.onNodeWithTag("split-handle").performClick()
        compose.onNodeWithTag("split-swap").performClick()
        compose.onNodeWithTag("pane-0-$tutorial").assertExists()
        compose.onNodeWithTag("pane-1-$lecture").assertExists()

        compose.onNodeWithTag("split-handle").performClick()
        compose.onNodeWithTag("split-mode").performClick()
        compose.waitUntil(3_000) { runBlocking { store.splitMode.first() } == SplitMode.TOP_BOTTOM }

        compose.onNodeWithTag("split-handle").performClick()
        compose.onNodeWithTag("split-close").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("split-panes").assertDoesNotExist()
        compose.onNodeWithTag("pane-0-$tutorial").assertDoesNotExist()
        // Tutorial 5 (active) stays open alone: the panel shows it as the open one.
        compose.onNodeWithTag("recent-button").performClick()
        compose.onNodeWithContentDescription("Tutorial 5 (open)").assertExists()
    }

    @Test
    fun draggingTheDivider_toTheEdge_closesThatNotebook_andElsewhereSpringsBack() {
        show()
        openTutorialBeside()
        // Past 75% (but not to the edge): springs back to 75%.
        compose.onNodeWithTag("split-handle").performTouchInput {
            swipe(center, center + Offset(700f, 0f), durationMillis = 400) // about 77% on a 2560-px-wide area
        }
        compose.waitUntil(3_000) { runBlocking { store.splitRatio.first() } == 0.75f }
        compose.onNodeWithTag("split-panes").assertExists()
        // To the right edge: the right notebook (Tutorial 5) closes.
        compose.onNodeWithTag("split-handle").performTouchInput {
            swipe(center, center + Offset(2000f, 0f), durationMillis = 400)
        }
        compose.waitForIdle()
        compose.onNodeWithTag("split-panes").assertDoesNotExist()
        compose.onNodeWithTag("recent-button").performClick()
        compose.onNodeWithContentDescription("Lecture 7 (open)").assertExists()
    }

    @Test
    fun aSmallWindow_offersNoSplit() {
        show(width = 600.dp, height = 700.dp)
        compose.onNodeWithTag("recent-button").performClick()
        compose.onNodeWithText("Make the window bigger to open two notebooks at once.").assertExists()
        compose.onNodeWithTag("recent-menu-Tutorial 5").performClick()
        compose.onNodeWithTag("open-beside").assertIsNotEnabled()
    }
}
