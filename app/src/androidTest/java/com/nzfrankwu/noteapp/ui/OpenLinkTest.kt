package com.nzfrankwu.noteapp.ui

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nzfrankwu.noteapp.ui.components.needsNewTask
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** "Open in Canvas" (PHASE-5.md FB11): the Chrome tab opens over Noteapp, not as its own task. */
@RunWith(AndroidJUnit4::class)
class OpenLinkTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun fromAScreen_theTabOpensInNoteappsOwnTask() {
        var fromScreen: Boolean? = null
        compose.setContent { fromScreen = needsNewTask(LocalContext.current) }
        compose.waitForIdle()
        assertEquals(false, fromScreen)
    }

    @Test
    fun withNoScreen_itNeedsANewTask() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        assertEquals(true, needsNewTask(app))
    }
}
