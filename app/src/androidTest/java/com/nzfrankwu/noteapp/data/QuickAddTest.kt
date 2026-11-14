package com.nzfrankwu.noteapp.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nzfrankwu.noteapp.data.db.Course
import com.nzfrankwu.noteapp.data.db.NoteAppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/** Quick-add from a shared link: the prefilled title, link and (for Canvas links) course. */
@RunWith(AndroidJUnit4::class)
class QuickAddTest {
    private lateinit var db: NoteAppDatabase
    private var address: String? = "https://canvas.example.edu"
    private lateinit var quickAdd: QuickAdd

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, NoteAppDatabase::class.java).build()
        db.courseDao().insert(Course("c-204", "ELECTENG 204", "Electromagnetics", 555L, Instant.EPOCH, Instant.EPOCH))
        quickAdd = QuickAdd(db) { address }
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun canvasLink_picksItsCourse() = runBlocking {
        val prefill = quickAdd.prefill("https://canvas.example.edu/courses/555/assignments/9", "Assignment 3: Transmission lines")
        assertEquals("Assignment 3: Transmission lines", prefill.title)
        assertEquals("https://canvas.example.edu/courses/555/assignments/9", prefill.url)
        assertEquals("c-204", prefill.courseId)
    }

    @Test
    fun linkOnAnotherSite_leavesTheCourseEmpty() = runBlocking {
        val prefill = quickAdd.prefill("Check this https://other.example.com/courses/555/x", null)
        assertEquals("https://other.example.com/courses/555/x", prefill.url)
        assertNull(prefill.courseId)
    }

    @Test
    fun unknownCanvasCourse_orNoCanvasAddress_leavesTheCourseEmpty() = runBlocking {
        assertNull(quickAdd.prefill("https://canvas.example.edu/courses/999/pages/x", null).courseId)
        address = null
        assertNull(quickAdd.prefill("https://canvas.example.edu/courses/555/assignments/9", null).courseId)
    }

    @Test
    fun plainText_becomesTheTitle() = runBlocking {
        val prefill = quickAdd.prefill("Ask tutor about question 4", null)
        assertEquals("Ask tutor about question 4", prefill.title)
        assertNull(prefill.url)
    }
}
