package com.nzfrankwu.noteapp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nzfrankwu.noteapp.ui.home.HomeScreen
import com.nzfrankwu.noteapp.ui.theme.NoteAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The home screen picks the right layout for the available width. */
@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun showHomeAt(widthDp: Int) {
        compose.setContent {
            NoteAppTheme {
                Box(Modifier.size(width = widthDp.dp, height = 700.dp)) {
                    HomeScreen(onOpenSettings = {})
                }
            }
        }
    }

    @Test
    fun tabletPortraitWidth_showsSideBySide() {
        showHomeAt(800) // OPPO Pad 3 in portrait
        compose.onNodeWithTag("home-wide").assertExists()
        compose.onNodeWithText("Due soon").assertIsDisplayed()
        compose.onNodeWithText("Folders").assertIsDisplayed()
    }

    @Test
    fun splitScreenWidth_showsStacked() {
        showHomeAt(560) // e.g. half of the tablet in split-screen
        compose.onNodeWithTag("home-narrow").assertExists()
        compose.onNodeWithText("Due soon").assertIsDisplayed()
    }
}
