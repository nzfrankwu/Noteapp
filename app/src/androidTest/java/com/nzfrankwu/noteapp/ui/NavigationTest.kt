package com.nzfrankwu.noteapp.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nzfrankwu.noteapp.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Moving between screens in the real app. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class NavigationTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    /** Home reopens on the last tab used, so tests start from Overview explicitly. */
    private fun showOverview() {
        compose.onNodeWithTag("home-tab-overview").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Due soon"), 5_000)
    }

    @Test
    fun appOpens_onHome() {
        showOverview()
        compose.onNodeWithText("Due soon").assertIsDisplayed()
    }

    @Test
    fun appName_isNoteapp() {
        // The user's chosen spelling: "Noteapp", lowercase a.
        compose.onNodeWithText("Noteapp").assertIsDisplayed()
    }

    @Test
    fun settingsButton_opensSettings_andBackArrowReturnsHome() {
        showOverview()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("About").assertIsDisplayed()

        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Due soon").assertIsDisplayed()
    }

    @Test
    fun systemBack_fromSettings_returnsHome() {
        showOverview()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("About").assertIsDisplayed()

        Espresso.pressBack()
        compose.onNodeWithText("Due soon").assertIsDisplayed()
    }
}
