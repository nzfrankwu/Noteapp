package com.nzfrankwu.noteapp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nzfrankwu.noteapp.data.FolderRepository
import com.nzfrankwu.noteapp.data.db.NoteAppDatabase
import com.nzfrankwu.noteapp.ui.home.HomeScreen
import com.nzfrankwu.noteapp.ui.home.HomeViewModel
import com.nzfrankwu.noteapp.ui.theme.NoteAppTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Creating, renaming and deleting folders through the real home screen (in-memory database). */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class FolderFlowTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var db: NoteAppDatabase

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, NoteAppDatabase::class.java).build()
        val viewModel = HomeViewModel(FolderRepository(db))
        compose.setContent {
            NoteAppTheme {
                Box(Modifier.size(width = 1120.dp, height = 800.dp)) {
                    HomeScreen(viewModel = viewModel, onOpenSettings = {}, onOpenFolder = {})
                }
            }
        }
    }

    @After
    fun tearDown() = db.close()

    private fun createFolder(name: String, code: String = "") {
        compose.onNodeWithText("New folder").performClick()
        compose.onNodeWithTag("folder-name-field").performTextInput(name)
        if (code.isNotEmpty()) compose.onNodeWithTag("folder-code-field").performTextInput(code)
        compose.onNodeWithTag("dialog-confirm").performClick()
    }

    private fun waitForText(text: String) = compose.waitUntilAtLeastOneExists(hasText(text), 5_000)

    @Test
    fun createButton_isDisabledUntilThereIsAName() {
        compose.onNodeWithText("New folder").performClick()
        compose.onNodeWithTag("dialog-confirm").assertIsNotEnabled()
    }

    @Test
    fun newFolder_withCourseCode_appearsAsATile() {
        createFolder("Electromagnetics", "electeng 204")
        waitForText("Electromagnetics")
        compose.onNodeWithText("ELECTENG 204").assertIsDisplayed()
    }

    @Test
    fun secondFolderForTheSameCourse_showsAnError() {
        createFolder("EE 204", "ELECTENG 204")
        waitForText("EE 204")
        createFolder("Another", "electeng204")
        waitForText("ELECTENG 204 is already linked to the folder “EE 204”.")
    }

    @Test
    fun renameFolder_throughTheOptionsMenu() {
        createFolder("Old name")
        waitForText("Old name")
        compose.onNodeWithContentDescription("Options for Old name").performClick()
        compose.onNodeWithText("Rename").performClick()
        compose.onNodeWithTag("folder-name-field").performTextReplacement("New name")
        compose.onNodeWithTag("dialog-confirm").performClick()
        waitForText("New name")
    }

    @Test
    fun deleteFolder_afterConfirming() {
        createFolder("Temporary")
        waitForText("Temporary")
        compose.onNodeWithContentDescription("Options for Temporary").performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.onNodeWithTag("dialog-confirm").performClick()
        waitForText("No folders yet")
    }
}
