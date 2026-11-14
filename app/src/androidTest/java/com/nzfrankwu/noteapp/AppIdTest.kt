package com.nzfrankwu.noteapp

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppIdTest {
    /**
     * The app ID must never change: Android would treat NoteApp as a different app, so an update
     * couldn't reach the existing install and the user's notes wouldn't carry over.
     */
    @Test
    fun appId_neverChanges() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.nzfrankwu.noteapp", context.packageName)
    }
}
