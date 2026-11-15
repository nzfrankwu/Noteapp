package com.nzfrankwu.noteapp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nzfrankwu.noteapp.data.FolderRepository
import com.nzfrankwu.noteapp.data.FolderResult
import com.nzfrankwu.noteapp.data.NotebookRepository
import com.nzfrankwu.noteapp.data.NotebookResult
import com.nzfrankwu.noteapp.data.TaskRepository
import com.nzfrankwu.noteapp.data.db.NoteAppDatabase
import com.nzfrankwu.noteapp.domain.PageTemplate
import com.nzfrankwu.noteapp.ui.folder.FolderScreen
import com.nzfrankwu.noteapp.ui.folder.FolderViewModel
import com.nzfrankwu.noteapp.ui.theme.NoteAppTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Subfolders and notebooks in the folder screen (PHASE-2.md, 2.2 and 2.3). */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class FolderLibraryTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var db: NoteAppDatabase
    private lateinit var folders: FolderRepository
    private lateinit var notebooks: NotebookRepository
    private var openedFolder: String? = null
    private var openedNotebook: String? = null

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, NoteAppDatabase::class.java).build()
        folders = FolderRepository(db)
        notebooks = NotebookRepository(db)
    }

    @After
    fun tearDown() = db.close()

    private fun show(folderId: String) {
        val viewModel = FolderViewModel(folderId, folders, TaskRepository(db), notebooks)
        compose.setContent {
            NoteAppTheme {
                Box(Modifier.size(width = 1120.dp, height = 800.dp)) {
                    FolderScreen(
                        viewModel, onBack = {}, onOpenTask = {}, onAddTask = {},
                        onOpenFolder = { openedFolder = it },
                        onOpenNotebook = { openedNotebook = it },
                    )
                }
            }
        }
    }

    private fun waitFor(text: String) = compose.waitUntilAtLeastOneExists(hasText(text), 5_000)

    private fun newFolder(name: String) =
        runBlocking { (folders.createFolder(name, "ELECTENG 292") as FolderResult.Success).folderId }

    @Test
    fun emptyFolder_saysHowToAddThings() {
        show(newFolder("EE 292"))
        waitFor("No notebooks yet")
    }

    @Test
    fun newFolder_appearsAsATile_andOpens() {
        val top = newFolder("EE 292")
        show(top)
        compose.onNodeWithTag("folder-add").performClick()
        compose.onNodeWithTag("new-subfolder").performClick()
        compose.onNodeWithTag("folder-name-field").performTextInput("tutorials")
        compose.onNodeWithTag("dialog-confirm").performClick()

        waitFor("tutorials")
        compose.onNodeWithText("Empty").assertExists()
        compose.onNodeWithText("tutorials").performClick()
        val sub = runBlocking { folders.observeSubfolders(top).first().single().folder.id }
        assertEquals(sub, openedFolder)
    }

    @Test
    fun newNotebook_isCreatedWithTheChosenPaper_andOpensRightAway() {
        val top = newFolder("EE 292")
        show(top)
        compose.onNodeWithTag("folder-add").performClick()
        compose.onNodeWithTag("new-notebook").performClick()
        compose.onNodeWithTag("notebook-name-field").performTextClearance()
        compose.onNodeWithTag("notebook-name-field").performTextInput("Lecture 3")
        compose.onNodeWithTag("template-grid").performClick()
        compose.onNodeWithTag("dialog-confirm").performClick()

        compose.waitUntil(5_000) { openedNotebook != null }
        val created = runBlocking { notebooks.getNotebook(openedNotebook!!) }
        assertNotNull(created)
        assertEquals("Lecture 3", created!!.title)
        assertEquals(PageTemplate.GRID, created.template)
        waitFor("Lecture 3")
        compose.onNodeWithText("1 page", substring = true).assertExists()
    }

    @Test
    fun filter_showsOnlyNotebooksOrOnlyFolders() {
        val top = newFolder("EE 292")
        runBlocking {
            folders.createSubfolder(top, "practice")
            notebooks.createNotebook(top, "Tutorial 5")
        }
        show(top)
        waitFor("Tutorial 5")
        waitFor("practice")

        compose.onNodeWithTag("library-filter-notebooks").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("practice")).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Tutorial 5").assertExists()

        compose.onNodeWithTag("library-filter-folders").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Tutorial 5")).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("practice").assertExists()
    }

    @Test
    fun deletingANotebook_hidesIt_andUndoBringsItBack() {
        val top = newFolder("EE 292")
        val id = runBlocking { (notebooks.createNotebook(top, "Tutorial 5") as NotebookResult.Success).id }
        show(top)
        waitFor("Tutorial 5")
        compose.onNodeWithContentDescription("Options for Tutorial 5").performClick()
        compose.onNodeWithText("Delete").performClick()

        waitFor("Deleted “Tutorial 5”")
        compose.onNodeWithText("Undo").performClick()
        compose.waitUntil(5_000) { runBlocking { notebooks.getNotebook(id)!!.deletedAt == null } }
        waitFor("Tutorial 5")
    }

    @Test
    fun aFolderWithThingsInside_explainsWhyItCantBeDeleted() {
        val top = newFolder("EE 292")
        runBlocking { folders.createSubfolder(top, "practice") }
        show(top)
        waitFor("practice")
        compose.onNodeWithContentDescription("Folder options").performClick()
        compose.onNodeWithText("Delete folder").performClick()
        compose.onNodeWithTag("dialog-confirm").performClick()
        waitFor("Only empty folders can be deleted. Move or delete what's inside first.")
        assertNotNull(runBlocking { db.folderDao().getById(top) })
    }

    @Test
    fun subfolder_showsTheCoursePathAndNoTasks() {
        val top = newFolder("EE 292")
        val sub = runBlocking { (folders.createSubfolder(top, "tutorials") as FolderResult.Success).folderId }
        show(sub)
        waitFor("ELECTENG 292")
        waitFor("in EE 292")
        compose.onNodeWithText("Tasks").assertDoesNotExist()
        compose.onNodeWithTag("folder-tab-calendar").assertDoesNotExist()
    }
}
