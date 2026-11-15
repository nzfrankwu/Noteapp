package com.nzfrankwu.noteapp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nzfrankwu.noteapp.data.FolderRepository
import com.nzfrankwu.noteapp.data.InMemoryHomeTabStore
import com.nzfrankwu.noteapp.data.TaskRepository
import com.nzfrankwu.noteapp.data.db.NoteAppDatabase
import com.nzfrankwu.noteapp.domain.HomeTab
import com.nzfrankwu.noteapp.ui.home.HomeScreen
import com.nzfrankwu.noteapp.ui.home.HomeViewModel
import com.nzfrankwu.noteapp.ui.theme.NoteAppTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Home's Overview · Calendar · Timeline tabs (Phase 1b, B.4). */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class HomeTabsTest {
    @get:Rule
    val compose = createComposeRule()

    private fun showHome(store: InMemoryHomeTabStore) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, NoteAppDatabase::class.java).build()
        val viewModel = HomeViewModel(FolderRepository(db), TaskRepository(db), tabStore = store)
        compose.setContent {
            NoteAppTheme {
                Box(Modifier.size(width = 1120.dp, height = 800.dp)) {
                    HomeScreen(
                        viewModel, onOpenSettings = {}, onOpenFolder = {}, onOpenTask = {}, onAddTask = {},
                        calendar = { Text("calendar", Modifier.testTag("test-calendar")) },
                        timeline = { Text("timeline", Modifier.testTag("test-timeline")) },
                    )
                }
            }
        }
    }

    @Test
    fun startsOnOverview_andTabsSwitchTheView_andAreRemembered() {
        val store = InMemoryHomeTabStore()
        showHome(store)
        compose.waitUntilAtLeastOneExists(hasTestTag("home-overview"), 5_000)
        compose.onNodeWithTag("home-tab-overview").assertIsSelected()

        compose.onNodeWithTag("home-tab-calendar").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("test-calendar"), 5_000)
        compose.onNodeWithTag("home-tab-calendar").assertIsSelected()
        compose.onNodeWithTag("home-overview").assertDoesNotExist()
        assertEquals(HomeTab.CALENDAR, runBlocking { store.homeTab.first() })

        compose.onNodeWithTag("home-tab-timeline").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("test-timeline"), 5_000)
        assertEquals(HomeTab.TIMELINE, runBlocking { store.homeTab.first() })
    }

    @Test
    fun opensOnTheLastTabUsed() {
        showHome(InMemoryHomeTabStore(HomeTab.TIMELINE))
        compose.waitUntilAtLeastOneExists(hasTestTag("test-timeline"), 5_000)
        compose.onNodeWithTag("home-tab-timeline").assertIsSelected()
    }
}
