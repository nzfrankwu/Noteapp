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
import com.nzfrankwu.noteapp.ui.home.HomeContent
import com.nzfrankwu.noteapp.ui.home.HomeBanners
import com.nzfrankwu.noteapp.ui.home.BannerActions
import com.nzfrankwu.noteapp.data.db.Course
import java.time.Instant
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.performClick
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
                    HomeContent(
                        folders = emptyList(),
                        onOpenSettings = {}, onNewFolder = {}, onOpenFolder = {},
                        onRenameFolder = {}, onDeleteFolder = {},
                    )
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

    /** 19 Nov (ISSUES follow-up): with no folders left, Recently deleted is still on Home, wide or stacked. */
    @Test
    fun recentlyDeleted_showsEvenWithNoFolders() {
        val width = androidx.compose.runtime.mutableIntStateOf(800)
        var opened = 0
        compose.setContent {
            NoteAppTheme {
                Box(Modifier.size(width = width.intValue.dp, height = 700.dp)) {
                    HomeContent(
                        folders = emptyList(),
                        onOpenSettings = {}, onNewFolder = {}, onOpenFolder = {},
                        onRenameFolder = {}, onDeleteFolder = {},
                        recentlyDeletedCount = 2, onOpenRecentlyDeleted = { opened++ },
                    )
                }
            }
        }
        compose.onNodeWithTag("home-wide").assertExists()
        compose.onNodeWithText("Recently deleted (2)").performClick()
        org.junit.Assert.assertEquals(1, opened)
        width.intValue = 560
        compose.waitForIdle()
        compose.onNodeWithTag("home-narrow").assertExists()
        compose.onNodeWithText("Recently deleted (2)").performClick()
        org.junit.Assert.assertEquals(2, opened)
    }

    /** 19 Nov: after "Import from export" restarts the app on Home, Home says how the restore went. */
    @Test
    fun aFinishedRestore_isShownOnHome_untilDismissed() {
        var dismissed = 0
        val note = androidx.compose.runtime.mutableStateOf<com.nzfrankwu.noteapp.data.backup.RestoreNote?>(
            com.nzfrankwu.noteapp.data.backup.RestoreNote("Restored the backup from 18 Nov.", failed = false),
        )
        compose.setContent {
            NoteAppTheme {
                Box(Modifier.size(width = 1120.dp, height = 800.dp)) {
                    HomeContent(
                        folders = emptyList(),
                        onOpenSettings = {}, onNewFolder = {}, onOpenFolder = {},
                        onRenameFolder = {}, onDeleteFolder = {},
                        banners = HomeBanners(restoreNote = note.value),
                        bannerActions = BannerActions(onDismissRestore = { dismissed++; note.value = null }),
                    )
                }
            }
        }
        compose.onNodeWithText("Restored the backup from 18 Nov.").assertIsDisplayed()
        compose.onNodeWithTag("dismiss-restored").performClick()
        compose.waitForIdle()
        org.junit.Assert.assertEquals(1, dismissed)
        compose.onNodeWithTag("banner-restored").assertDoesNotExist()
    }

    @Test
    fun splitScreenWidth_showsStacked() {
        showHomeAt(560) // e.g. half of the tablet in split-screen
        compose.onNodeWithTag("home-narrow").assertExists()
        compose.onNodeWithText("Due soon").assertIsDisplayed()
    }

    @Test
    fun canvasBanners_showAndTheirButtonsWork() {
        var signIn = false
        var link = false
        compose.setContent {
            NoteAppTheme {
                Box(Modifier.size(width = 1120.dp, height = 800.dp)) {
                    HomeContent(
                        folders = emptyList(),
                        onOpenSettings = {}, onNewFolder = {}, onOpenFolder = {},
                        onRenameFolder = {}, onDeleteFolder = {},
                        banners = HomeBanners(
                            signInExpired = true,
                            newCourses = listOf(Course("c1", "COMPSYS 201", null, 557, Instant.EPOCH, Instant.EPOCH)),
                        ),
                        bannerActions = BannerActions(onSignInAgain = { signIn = true }, onLinkCourses = { link = true }),
                    )
                }
            }
        }
        compose.onNodeWithTag("banner-expired").assertIsDisplayed()
        compose.onNodeWithText("New from Canvas: COMPSYS 201. Give it a folder?").assertIsDisplayed()
        compose.onNodeWithText("Sign in again").performClick()
        compose.onNodeWithText("Link courses").performClick()
        assertTrue(signIn && link)
    }
}
