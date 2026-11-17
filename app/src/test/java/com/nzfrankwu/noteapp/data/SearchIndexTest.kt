package com.nzfrankwu.noteapp.data

import com.nzfrankwu.noteapp.data.db.Course
import com.nzfrankwu.noteapp.data.db.Folder
import com.nzfrankwu.noteapp.data.db.Notebook
import com.nzfrankwu.noteapp.data.db.NotebookListItem
import com.nzfrankwu.noteapp.data.db.Task
import com.nzfrankwu.noteapp.data.db.TaskWithCourse
import com.nzfrankwu.noteapp.domain.PageTemplate
import com.nzfrankwu.noteapp.domain.TaskSource
import com.nzfrankwu.noteapp.domain.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** PHASE-7.md 7.8: search over names and tasks. */
class SearchIndexTest {
    private val t0 = Instant.parse("2026-11-16T00:00:00Z")
    private fun day(n: Long) = t0.plusSeconds(n * 86_400)

    private val courses = listOf(
        Course("c1", "ELECTENG 204", "Engineering Electromagnetics", 555, t0, t0),
        Course("c2", "MATHS 208", "Applied Mathematics", null, t0, t0),
    )
    private val folders = listOf(
        Folder("f1", "Electromagnetics", "c1", t0, t0),
        Folder("s1", "Tutorials", null, t0, t0, parentId = "f1"),
        Folder("f2", "Maths", "c2", t0, t0),
    )

    private fun task(
        title: String, course: String? = "c1", due: Instant? = null, status: TaskStatus = TaskStatus.NOT_STARTED,
        description: String? = null, notes: String? = null, archived: Boolean = false,
    ) = TaskWithCourse(
        Task(
            id = title, title = title, courseId = course, dueAt = due, url = null, descriptionHtml = description, status = status,
            targetAt = null, notes = notes, source = TaskSource.MANUAL, canvasAssignmentId = null,
            canvasSubmissionState = null, canvasSubmittedAt = null, removedFromCanvas = false, createdAt = t0, updatedAt = t0,
        ),
        courseCode = courses.firstOrNull { it.id == course }?.code,
        courseArchivedAt = if (archived) t0 else null,
    )

    private fun notebook(title: String, folder: String, updated: Instant = t0) =
        NotebookListItem(Notebook(title, folder, title, PageTemplate.LINED, 595f, 842f, t0, updated, null), pageCount = 3)

    private fun index(tasks: List<TaskWithCourse> = emptyList(), notebooks: List<NotebookListItem> = emptyList()) =
        SearchIndex(tasks, notebooks, folders, courses)

    @Test
    fun anEmptyQuery_findsNothing() {
        assertTrue(index(listOf(task("Lab 1"))).search("   ", t0).isEmpty)
    }

    @Test
    fun everyWord_mustMatch_inAnyField_anyCase_anyOrder() {
        val idx = index(
            listOf(
                task("Assignment 3", description = "<p>Transmission <b>lines</b> &amp; Smith charts</p>"),
                task("Quiz 4", notes = "revise smith charts"),
                task("Problem set", course = "c2"),
            ),
        )
        assertEquals(listOf("Assignment 3", "Quiz 4"), idx.search("SMITH charts", t0).tasks.map { it.task.title })
        assertEquals(listOf("Assignment 3"), idx.search("lines & smith", t0).tasks.map { it.task.title })
        assertEquals(listOf("Problem set"), idx.search("applied maths", t0).tasks.map { it.task.title }) // course name
        assertEquals(listOf("Assignment 3", "Quiz 4"), idx.search("electeng", t0).tasks.map { it.task.title }) // course code
        assertTrue(idx.search("smith fourier", t0).isEmpty)
    }

    @Test
    fun tasks_titleMatchesFirst_thenUnfinishedSoonest_archivedLast() {
        val idx = index(
            listOf(
                task("Revise lab", due = day(9)),
                task("Lab 2", due = day(5)),
                task("Lab 1", due = day(1), status = TaskStatus.DONE),
                task("Lab 0", due = day(0), archived = true),
                task("Lab 3"),
                task("Read notes", notes = "lab safety"),
            ),
        )
        assertEquals(
            listOf("Lab 2", "Revise lab", "Lab 3", "Lab 1", "Lab 0", "Read notes"),
            idx.search("lab", t0).tasks.map { it.task.title },
        )
    }

    @Test
    fun notebooks_matchByTitleOrFolder_andShowWhereTheyAre() {
        val idx = index(
            notebooks = listOf(
                notebook("Lecture 7", "f1", updated = day(1)),
                notebook("Week 3", "s1", updated = day(3)),
                notebook("Tutorial 5 answers", "f2", updated = day(2)),
            ),
        )
        val hits = idx.search("tutorial", t0).notebooks
        assertEquals(listOf("Tutorial 5 answers", "Week 3"), hits.map { it.item.notebook.title })
        assertEquals("Electromagnetics › Tutorials", hits[1].place)
        // Neither title matches: most recently changed first.
        assertEquals(listOf("Week 3", "Lecture 7"), idx.search("electromagnetics", t0).notebooks.map { it.item.notebook.title })
    }

    @Test
    fun folders_matchByNameOrCourse_subfoldersFollowTheirTopFolder() {
        val idx = index()
        assertEquals(listOf("Electromagnetics", "Tutorials"), idx.search("electeng 204", t0).folders.map { it.folder.name })
        val tutorials = idx.search("tutorials", t0).folders.single()
        assertEquals("ELECTENG 204", tutorials.courseCode)
        assertEquals("Electromagnetics", tutorials.place)
    }

    @Test
    fun descriptions_areReadAsPlainText() {
        assertEquals("Due Friday: 5 < 6 \"done\" é", SearchIndex.plainText("<p>Due <b>Friday</b>:</p> 5 &lt; 6 &quot;done&quot; &#233;"))
        assertEquals(null, SearchIndex.plainText("  "))
        assertEquals(null, SearchIndex.plainText(null))
    }

    // ---- 7.9: snippets of note text ----

    @Test
    fun snippet_showsTheTextAroundTheFirstMatch_withoutCuttingWords() {
        val text = "Lecture 7\nTransmission lines are modelled as distributed circuits. The Smith chart turns impedance into a picture you can read quickly."
        val s = SearchIndex.snippet(text, listOf("smith"), around = 30)!!
        assertTrue(s, s.startsWith("…") && s.endsWith("…"))
        assertTrue(s, s.contains("The Smith chart"))
        assertTrue("no half words: $s", !s.contains("…ircuits") && !s.contains("imped…"))
        assertEquals("Short text", SearchIndex.snippet("Short   text", listOf("short")))
        assertEquals(null, SearchIndex.snippet(null, listOf("x")))
    }

    @Test
    fun wordsTogether_areSpotted_andTheSnippetCentresOnThem() {
        val text = "Point 1.3: the voltage. Lecture 7 slide\n3 shows the Smith chart"
        assertTrue(SearchIndex.hasPhrase(text, listOf("slide", "3")))
        assertTrue(!SearchIndex.hasPhrase(text, listOf("3", "slide")))
        assertTrue("one word is never a phrase", !SearchIndex.hasPhrase(text, listOf("slide")))
        val s = SearchIndex.snippet(text, listOf("slide", "3"), around = 10)!!
        assertTrue(s, s.contains("slide 3"))
    }
}
