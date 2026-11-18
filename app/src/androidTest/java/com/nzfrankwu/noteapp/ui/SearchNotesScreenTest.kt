package com.nzfrankwu.noteapp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nzfrankwu.noteapp.data.FolderRepository
import com.nzfrankwu.noteapp.data.FolderResult
import com.nzfrankwu.noteapp.data.NotebookFiles
import com.nzfrankwu.noteapp.data.NotebookRepository
import com.nzfrankwu.noteapp.data.NotebookResult
import com.nzfrankwu.noteapp.data.TaskRepository
import com.nzfrankwu.noteapp.data.db.NoteAppDatabase
import com.nzfrankwu.noteapp.data.search.NoteTextIndexer
import com.nzfrankwu.noteapp.data.search.NoteTextStore
import com.nzfrankwu.noteapp.domain.ink.InkText
import com.nzfrankwu.noteapp.ui.search.SearchScreen
import com.nzfrankwu.noteapp.ui.search.SearchViewModel
import com.nzfrankwu.noteapp.ui.theme.NoteAppTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** PHASE-7.md 7.9: words in the notes show as "In your notes"; Find in this notebook keeps to one notebook. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class SearchNotesScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: NoteAppDatabase
    private lateinit var root: File
    private lateinit var store: NoteTextStore
    private lateinit var tasks: TaskRepository
    private lateinit var notebooks: NotebookRepository
    private lateinit var folders: FolderRepository
    private lateinit var lecture: String
    private lateinit var lecturePage2: String
    private var opened: Triple<String, String, String>? = null
    private lateinit var eeFolder: String

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, NoteAppDatabase::class.java).build()
        root = File(context.cacheDir, "search-notes-test").apply { deleteRecursively(); mkdirs() }
        val files = NotebookFiles(root)
        store = NoteTextStore(context, name = null)
        tasks = TaskRepository(db)
        notebooks = NotebookRepository(db)
        folders = FolderRepository(db)
        val folder = (folders.createFolder("Electromagnetics", "ELECTENG 204") as FolderResult.Success).folderId
        eeFolder = folder
        lecture = (notebooks.createNotebook(folder, "Lecture 7") as NotebookResult.Success).id
        lecturePage2 = (notebooks.addPage(lecture, 1) as NotebookResult.Success).id
        val tutorial = (notebooks.createNotebook(folder, "Tutorial 3") as NotebookResult.Success).id
        fun box(text: String) = InkText("t-$text", text, 50f, 60f, 300f, 30f)
        files.write(lecture, lecturePage2, emptyList(), texts = listOf(box("The Smith chart maps impedance")))
        files.write(tutorial, notebooks.getPages(tutorial).first().id, emptyList(), texts = listOf(box("Smith chart question 2")))
        NoteTextIndexer(db, files, store).run()
        Unit
    }

    @After
    fun tearDown() {
        store.close()
        root.deleteRecursively()
    }

    private fun show(notebookId: String? = null, folderId: String? = null, status: (suspend () -> String?)? = null) {
        compose.setContent {
            NoteAppTheme {
                Box(Modifier.size(width = 1100.dp, height = 800.dp)) {
                    SearchScreen(
                        SearchViewModel(
                            tasks, notebooks, folders, noteText = store, notebookId = notebookId, folderId = folderId,
                            handwritingStatus = status,
                        ),
                        onBack = {}, onOpenTask = {}, onOpenNotebook = {}, onOpenFolder = {},
                        onOpenPage = { nb, page, query, _ -> opened = Triple(nb, page, query) },
                    )
                }
            }
        }
    }

    @Test
    fun wordsInTextBoxes_areListed_andOpenTheirPage() {
        show()
        compose.onNodeWithTag("search-field").performTextInput("smith chart")
        compose.waitUntilAtLeastOneExists(hasText("In your notes (2)"), 5_000)
        compose.onNodeWithText("Lecture 7 · page 2").assertExists()
        compose.onNodeWithText("Lecture 7 · page 2").performClick()
        assertEquals(Triple(lecture, lecturePage2, "smith chart"), opened)
    }

    /** Messy handwriting: read as "Founer" (no reading says "Fourier"), still found, and listed. */
    @Test
    fun messyHandwriting_aLetterOrTwoOff_isStillFound() {
        val page3 = runBlocking { (notebooks.addPage(lecture, 2) as NotebookResult.Success).id }
        store.putHandwriting(
            page3, lecture,
            listOf(NoteTextStore.HandLine(listOf("Founer series", "Fouver senes"), 100f, 130f)),
            key = "test",
        )
        show()
        compose.onNodeWithTag("search-field").performTextInput("fourier series")
        compose.waitUntilAtLeastOneExists(hasText("In your notes (1)"), 5_000)
        compose.onNodeWithText("Lecture 7 · page 3").assertExists()
        compose.onNodeWithText("Founer series").assertExists() // what was read is shown
    }

    /** 18 Nov: a course folder's search leaves out other courses' notes; notes come before tasks. */
    @Test
    fun aFolderSearch_staysInTheFolder_andNotesComeBeforeTasks() {
        runBlocking {
            val maths = (folders.createFolder("Maths", "MATHS 208") as FolderResult.Success).folderId
            val other = (notebooks.createNotebook(maths, "Maths notes") as NotebookResult.Success).id
            store.putHandwriting(
                notebooks.getPages(other).first().id, other,
                listOf(NoteTextStore.HandLine(listOf("smith chart"), 10f, 30f)), key = "test",
            )
            val ee = db.courseDao().getAll().first { it.code == "ELECTENG 204" }.id
            tasks.createTask(com.nzfrankwu.noteapp.data.TaskDraft("Smith chart worksheet", ee))
        }
        show(folderId = eeFolder)
        compose.onNodeWithTag("search-field").performTextInput("smith")
        compose.waitUntilAtLeastOneExists(hasText("In your notes (2)"), 5_000) // not the Maths page
        compose.onNodeWithText("Maths notes", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Tasks (1)").assertExists()
        val notesTop = compose.onNodeWithText("In your notes (2)").fetchSemanticsNode().boundsInRoot.top
        val tasksTop = compose.onNodeWithText("Tasks (1)").fetchSemanticsNode().boundsInRoot.top
        org.junit.Assert.assertTrue("notes above tasks", notesTop < tasksTop)
    }

    /** 18 Nov: while handwriting is still being read, the progress line stays above the results. */
    @Test
    fun handwritingProgress_showsAboveTheResults() {
        show(notebookId = lecture, status = { "Handwriting in this notebook: read 1 of 2 pages so far" })
        compose.waitUntilAtLeastOneExists(hasText("read 1 of 2 pages", substring = true), 5_000) // before typing
        compose.onNodeWithTag("search-field").performTextInput("smith")
        compose.waitUntilAtLeastOneExists(hasText("In your notes (1)"), 5_000)
        val statusTop = compose.onNodeWithText("read 1 of 2 pages", substring = true).fetchSemanticsNode().boundsInRoot.top
        val notesTop = compose.onNodeWithText("In your notes (1)").fetchSemanticsNode().boundsInRoot.top
        org.junit.Assert.assertTrue("status above the results", statusTop < notesTop)
    }

    @Test
    fun findInThisNotebook_listsOnlyItsPages() {
        show(notebookId = lecture)
        compose.onNodeWithTag("search-field").performTextInput("smith")
        compose.waitUntilAtLeastOneExists(hasText("In your notes (1)"), 5_000)
        compose.onNodeWithText("Page 2").assertExists()
        compose.onNodeWithText("Tutorial 3", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Notebooks", substring = true).assertDoesNotExist()
    }
}
