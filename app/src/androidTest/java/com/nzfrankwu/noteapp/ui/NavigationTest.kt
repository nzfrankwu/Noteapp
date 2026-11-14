package com.nzfrankwu.noteapp.ui

import androidx.compose.ui.test.assertIsDisplayed
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
@RunWith(AndroidJUnit4::class)
class NavigationTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun appOpens_onHome() {
        compose.onNodeWithText("Due soon").assertIsDisplayed()
    }

    @Test
    fun appName_isNoteapp() {
        // The user's chosen spelling: "Noteapp", lowercase a.
        compose.onNodeWithText("Noteapp").assertIsDisplayed()
    }

    @Test
    fun settingsButton_opensSettings_andBackArrowReturnsHome() {
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("About").assertIsDisplayed()

        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Due soon").assertIsDisplayed()
    }

    @Test
    fun systemBack_fromSettings_returnsHome() {
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("About").assertIsDisplayed()

        Espresso.pressBack()
        compose.onNodeWithText("Due soon").assertIsDisplayed()
    }
}
