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
    private val clipboard = com.nzfrankwu.noteapp.ui.notebook.EditorClipboard()
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
                        viewModelFor = { id -> models.getOrPut(id) { NotebookViewModel(id, notebooks, files, ink, scope, sharedClipboard = clipboard) } },
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
    fun aPictureCopiedInOneNotebook_pastesIntoTheOther_withItsOwnCopyOfTheFile() {
        show()
        openTutorialBeside()
        val from = models.getValue(lecture)
        val to = models.getValue(tutorial)
        compose.waitUntil(5_000) { from.pages.value.isNotEmpty() && from.isEditable(from.pages.value.first().id) && to.pages.value.isNotEmpty() && to.isEditable(to.pages.value.first().id) }
        val red = android.graphics.Bitmap.createBitmap(40, 20, android.graphics.Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.RED) }
        val picture = runBlocking { com.nzfrankwu.noteapp.data.ImageImport(files).importBitmap(lecture, red)!! }
        val image = com.nzfrankwu.noteapp.domain.ink.InkImage("i", picture.asset, 10f, 10f, 40f, 20f)
        val stroke = com.nzfrankwu.noteapp.domain.ink.InkStroke(
            "s", com.nzfrankwu.noteapp.domain.ink.InkTool.PEN, android.graphics.Color.BLACK, 2f,
            floatArrayOf(20f, 60f), floatArrayOf(20f, 30f), intArrayOf(0, 10),
            floatArrayOf(0.5f, 0.5f), FloatArray(2) { com.nzfrankwu.noteapp.domain.ink.InkStroke.NONE },
            FloatArray(2) { com.nzfrankwu.noteapp.domain.ink.InkStroke.NONE }, com.nzfrankwu.noteapp.domain.ink.InkStroke.TOOL_STYLUS,
        )
        compose.runOnUiThread { from.copy(listOf(stroke), listOf(image)) }
        assertEquals(true, to.clipboard != null) // the other notebook sees it
        val pasted = runBlocking { to.paste(to.pages.value.first().id, 200f, 200f) }
        assertEquals(1, pasted.strokes.size)
        val copy = pasted.images.single()
        val file = files.assetFile(tutorial, copy.asset)
        assertEquals("The picture file is in the target notebook's folder", true, file.exists() && file.length() > 0)
        assertEquals(true, files.assetFile(lecture, picture.asset).exists()) // the original stays
    }

    @Test
    fun aScreenshotInSplitView_asksWhichNotebook_andGoesThere() {
        show()
        openTutorialBeside() // Tutorial 5 is active
        val lectureVm = models.getValue(lecture)
        compose.waitUntil(5_000) { lectureVm.pages.value.isNotEmpty() && lectureVm.isEditable(lectureVm.pages.value.first().id) }
        compose.onNodeWithTag("screenshot-button").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("snip-overlay"), 5_000)
        compose.onNodeWithTag("snip-confirm").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("insert-into-1"), 5_000)
        compose.onNodeWithText("Tutorial 5 (last used)").assertExists()
        compose.onNodeWithTag("insert-into-1").performClick() // Lecture 7
        compose.waitUntil(5_000) { lectureVm.ink.images(lectureVm.pages.value.first().id).isNotEmpty() }
        val tutorialVm = models.getValue(tutorial)
        assertEquals(0, tutorialVm.ink.images(tutorialVm.pages.value.first().id).size)
    }

    /**
     * Holds the panel's [title] tile (unless the finger is already down on it), then drags it to
     * [to] (root coordinates) without letting go.
     */
    private fun holdAndDrag(title: String, to: Offset, press: Boolean = true) {
        val tile = compose.onNodeWithTag("recent-$title")
        val topLeft = tile.fetchSemanticsNode().boundsInRoot.topLeft
        tile.performTouchInput {
            if (press) {
                down(center)
                repeat(10) { advanceEventTime(100); moveTo(center) } // a long press
            }
            val from = currentPosition()!!
            val target = to - topLeft
            for (k in 1..12) {
                advanceEventTime(16)
                moveTo(from + (target - from) * (k / 12f))
            }
        }
    }

    private fun letGo(title: String) = compose.onNodeWithTag("recent-$title").performTouchInput { up() }

    private fun canvasBounds() = compose.onNodeWithTag("notebook-canvas").fetchSemanticsNode().boundsInRoot

    @Test
    fun draggingOverThePanel_lightsNothing_andOverThePage_lightsTheDropZone() {
        show()
        compose.onNodeWithTag("recent-button").performClick()
        val tile = compose.onNodeWithTag("recent-Tutorial 5").fetchSemanticsNode().boundsInRoot
        // Still over the panel: a drop there does nothing, so no zone lights up.
        holdAndDrag("Tutorial 5", tile.center + Offset(0f, 60f))
        compose.onNodeWithTag("drop-zone").assertDoesNotExist()
        // Over the page: the zone shows where it would open.
        val page = canvasBounds()
        holdAndDrag("Tutorial 5", Offset(page.right - 40f, page.center.y), press = false)
        compose.onNodeWithTag("drop-zone").assertExists()
        letGo("Tutorial 5")
        compose.waitUntilAtLeastOneExists(hasTestTag("split-panes"), 5_000)
        compose.onNodeWithTag("pane-1-$tutorial").assertExists()
    }

    @Test
    fun droppingNearTheBottomEdge_stacksTheNotebooks_evenIfSideBySideWasLastUsed() {
        runBlocking { store.setSplitMode(SplitMode.SIDE_BY_SIDE) }
        show()
        compose.onNodeWithTag("recent-button").performClick()
        val page = canvasBounds()
        val nearBottom = with(compose.density) { 30.dp.toPx() }
        holdAndDrag("Tutorial 5", Offset(page.center.x, page.bottom - nearBottom))
        letGo("Tutorial 5")
        compose.waitUntilAtLeastOneExists(hasTestTag("split-panes"), 5_000)
        // Lecture 7 on top, Tutorial 5 below, each the full width.
        val top = compose.onNodeWithTag("pane-0-$lecture").fetchSemanticsNode().boundsInRoot
        val bottom = compose.onNodeWithTag("pane-1-$tutorial").fetchSemanticsNode().boundsInRoot
        assertEquals(true, bottom.top >= top.bottom)
        assertEquals(top.width, bottom.width, 1f)
        assertEquals(SplitMode.TOP_BOTTOM, runBlocking { store.splitMode.first() })
    }

    @Test
    fun theDivider_afterSwapping_staysWhereItWasLeft() {
        show()
        openTutorialBeside()
        compose.onNodeWithTag("split-handle").performClick()
        compose.onNodeWithTag("split-swap").performClick()
        compose.waitForIdle()
        // About 40% (left of the middle): kept there, and both notebooks stay open.
        compose.onNodeWithTag("split-handle").performTouchInput {
            swipe(center, center - Offset(250f, 0f), durationMillis = 400)
        }
        compose.waitForIdle()
        compose.onNodeWithTag("split-panes").assertExists()
        val left = compose.onNodeWithTag("pane-0-$tutorial").fetchSemanticsNode().boundsInRoot
        val right = compose.onNodeWithTag("pane-1-$lecture").fetchSemanticsNode().boundsInRoot
        assertEquals(true, left.width < right.width)
        compose.waitUntil(3_000) { runBlocking { store.splitRatio.first() } < 0.5f }
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
