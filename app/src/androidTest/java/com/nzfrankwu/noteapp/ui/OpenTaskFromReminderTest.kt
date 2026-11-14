package com.nzfrankwu.noteapp.ui

import android.content.Intent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nzfrankwu.noteapp.MainActivity
import com.nzfrankwu.noteapp.NoteAppApplication
import com.nzfrankwu.noteapp.data.FolderResult
import com.nzfrankwu.noteapp.data.TaskDraft
import com.nzfrankwu.noteapp.data.TaskResult
import com.nzfrankwu.noteapp.reminders.Notifications
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Tapping a reminder opens the app straight on that task (uses the app's real database). */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class OpenTaskFromReminderTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container = (context.applicationContext as NoteAppApplication).container
    private var folderId: String? = null
    private var taskId: String? = null

    @After
    fun cleanUp() = runBlocking {
        taskId?.let { container.taskRepository.deleteTask(it) }
        folderId?.let { container.folderRepository.deleteFolder(it) }
        Unit
    }

    @Test
    fun reminderIntent_opensTheTaskPage() {
        runBlocking {
            folderId = (container.folderRepository.createFolder("Reminder test", "TEST 101") as FolderResult.Success).folderId
            val courseId = container.database.courseDao().getAll().first { it.code == "TEST 101" }.id
            taskId = (container.taskRepository.createTask(TaskDraft("Reminder test task", courseId)) as TaskResult.Success).taskId
        }
        val intent = Intent(context, MainActivity::class.java).putExtra(Notifications.EXTRA_OPEN_TASK_ID, taskId)
        ActivityScenario.launch<MainActivity>(intent).use {
            compose.waitUntilAtLeastOneExists(hasText(" · Your task"), 10_000)
        }
    }
}
