package com.nzfrankwu.noteapp.canvas

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.nzfrankwu.noteapp.data.CanvasSettings
import com.nzfrankwu.noteapp.data.db.NoteAppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** Running syncs with saved settings, recording results, and the background job (fake Canvas). */
@RunWith(AndroidJUnit4::class)
class CanvasSyncRunnerTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: NoteAppDatabase
    private val settings = CanvasSettings(context)
    private var now = Instant.parse("2026-11-15T09:00:00Z")
    private val clock = object : Clock() {
        override fun instant() = now
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?) = this
    }

    private var failure: CanvasException? = null
    private var activeCourses = listOf(CanvasCourse(555, "Engineering Electromagnetics", "ELECTENG 204"))
    private val fakeCanvas = object : CanvasSource {
        override suspend fun getActiveCourses(): List<CanvasCourse> {
            failure?.let { throw it }
            return activeCourses
        }
        override suspend fun getAssignments(courseId: Long): List<CanvasAssignment> = emptyList()
    }
    private lateinit var runner: CanvasSyncRunner

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, NoteAppDatabase::class.java).build()
        runner = CanvasSyncRunner(settings, { CanvasSync(db, fakeCanvas, clock) }, clock)
        resetSettings()
    }

    @After
    fun tearDown() = runBlocking {
        resetSettings()
        db.close()
    }

    private suspend fun resetSettings() {
        settings.setAddress(null)
        settings.setSignedInAs(null)
        settings.setSignInExpired(false)
        settings.clearLastSynced()
        settings.setCourseMissingSince(emptyMap())
    }

    private suspend fun signIn() {
        settings.setAddress("https://canvas.example.edu")
        settings.setSignedInAs("Test Student")
    }

    @Test
    fun notSetUp_doesNothing() = runBlocking {
        assertEquals(SyncOutcome.NotSetUp, runner.run())
        assertTrue(db.courseDao().getAll().isEmpty())
    }

    @Test
    fun successfulSync_recordsLastSynced() = runBlocking {
        signIn()
        val outcome = runner.run()
        assertTrue(outcome is SyncOutcome.Synced)
        assertEquals(now, settings.lastSyncedAt.first())
        assertFalse(settings.signInExpired.first())
        assertEquals(1, db.courseDao().getAll().size)
    }

    @Test
    fun aCourseCanvasStopsListing_isRemembered_fromTheFirstSyncItWasMissing() = runBlocking {
        signIn()
        activeCourses = activeCourses + CanvasCourse(777, "Circuits", "ELECTENG 101")
        runner.run()
        assertTrue(settings.courseMissingSince.first().isEmpty())

        activeCourses = activeCourses.take(1) // the semester of ELECTENG 101 ended
        now = now.plusSeconds(3600)
        runner.run()
        val ended = checkNotNull(db.courseDao().getByCanvasId(777)).id
        assertEquals(mapOf(ended to now.toEpochMilli()), settings.courseMissingSince.first())

        val firstMissed = now.toEpochMilli()
        now = now.plusSeconds(15 * 86_400)
        runner.run()
        assertEquals("keeps the first time", mapOf(ended to firstMissed), settings.courseMissingSince.first())

        failure = CanvasException.HttpError(503)
        runner.run()
        assertEquals("a failed sync changes nothing", mapOf(ended to firstMissed), settings.courseMissingSince.first())
    }

    @Test
    fun expiredSignIn_raisesTheBannerFlag_andKeepsData() = runBlocking {
        signIn()
        runner.run()
        failure = CanvasException.SessionExpired()
        assertEquals(SyncOutcome.SignInExpired, runner.run())
        assertTrue(settings.signInExpired.first())
        assertEquals("Existing data kept", 1, db.courseDao().getAll().size)
    }

    @Test
    fun otherFailures_areReported_andDoNotTouchLastSynced() = runBlocking {
        signIn()
        failure = CanvasException.HttpError(503)
        assertTrue(runner.run() is SyncOutcome.Failed)
        assertNull(settings.lastSyncedAt.first())
        assertFalse(settings.signInExpired.first())
    }

    @Test
    fun appOpen_syncsOnlyIfDue() = runBlocking {
        signIn()
        assertTrue(runner.runIfDue() is SyncOutcome.Synced)
        now = now.plusSeconds(5 * 60)
        assertNull("5 minutes later: not due yet", runner.runIfDue())
        now = now.plusSeconds(15 * 60)
        assertTrue(runner.runIfDue() is SyncOutcome.Synced)
    }

    @Test
    fun appOpen_doesNotKeepAskingCanvas_whileTheSignInIsExpired() = runBlocking {
        signIn()
        settings.setSignInExpired(true)
        assertNull(runner.runIfDue())
        settings.setSignInExpired(false)
        assertTrue(runner.runIfDue() is SyncOutcome.Synced)
    }

    @Test
    fun backgroundJob_canBeScheduledAndCancelled() {
        CanvasSyncWorker.schedule(context)
        val scheduled = WorkManager.getInstance(context).getWorkInfosForUniqueWork(CanvasSyncWorker.UNIQUE_NAME).get()
        assertEquals(WorkInfo.State.ENQUEUED, scheduled.single().state)

        CanvasSyncWorker.cancel(context)
        val after = WorkManager.getInstance(context).getWorkInfosForUniqueWork(CanvasSyncWorker.UNIQUE_NAME).get()
        assertTrue(after.all { it.state == WorkInfo.State.CANCELLED })
    }
}
