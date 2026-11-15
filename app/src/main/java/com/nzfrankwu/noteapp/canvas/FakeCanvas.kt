package com.nzfrankwu.noteapp.canvas

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Debug builds only: a pretend Canvas with made-up courses and assignments, so the sign-in,
 * sync and course-linking screens can be tried without a real Canvas login. It never touches
 * the network. Turned on from Settings → Canvas → "Use fake Canvas (debug)".
 *
 * Dates are relative to today, so there's always something overdue, due soon and later.
 * Most assignments have an "available from" date (9:00 am a few days back, one in the future);
 * a couple have none, like many real Canvas assignments.
 */
class FakeCanvas(
    private val clock: Clock = Clock.systemUTC(),
    private val zone: ZoneId = ZoneId.systemDefault(),
) : CanvasSource {
    val user = CanvasUser(1, "Test Student")

    override suspend fun getActiveCourses(): List<CanvasCourse> = listOf(
        CanvasCourse(COURSE_204, "Engineering Electromagnetics", "ELECTENG 204"),
        CanvasCourse(COURSE_292, "Electronics Design 2", "ELECTENG 292"),
        CanvasCourse(COURSE_201, "Computer Systems Engineering", "COMPSYS 201"),
    )

    override suspend fun getAssignments(courseId: Long): List<CanvasAssignment> {
        val now = clock.instant()
        fun dueIn(days: Long): Instant =
            now.atZone(zone).toLocalDate().plusDays(days).atTime(LocalTime.of(23, 59, 59)).atZone(zone).toInstant()
        /** 9:00 am, [days] from today (negative = in the past). */
        fun opensIn(days: Long): Instant =
            now.atZone(zone).toLocalDate().plusDays(days).atTime(LocalTime.of(9, 0)).atZone(zone).toInstant()
        fun assignment(
            id: Long,
            name: String,
            due: Instant?,
            html: String?,
            submittedDaysAgo: Long? = null,
            opens: Instant? = null,
        ) = CanvasAssignment(
            id = id,
            courseId = courseId,
            name = name,
            dueAt = due,
            descriptionHtml = html,
            htmlUrl = "https://$HOST/courses/$courseId/assignments/$id",
            submission = CanvasSubmission(
                workflowState = if (submittedDaysAgo != null) "submitted" else "unsubmitted",
                // Midday on that day, so the same sync twice reports the same submission.
                submittedAt = submittedDaysAgo?.let { dueIn(-it).minus(Duration.ofHours(12)).truncatedTo(ChronoUnit.HOURS) },
            ),
            unlockAt = opens,
        )
        return when (courseId) {
            COURSE_204 -> listOf(
                assignment(
                    9001, "Assignment 3: Transmission lines", dueIn(5),
                    "<p>Answer <b>all</b> questions. Show your working.</p><p>See the <a href=\"/courses/$COURSE_204/files/1\">formula sheet</a>.</p>",
                    opens = opensIn(-9),
                ),
                assignment(9002, "Quiz 4", dueIn(1), "<p>Online quiz, 30 minutes.</p>", opens = opensIn(-1)),
                assignment(9003, "Lab 2 report", dueIn(-2), "<p>Upload as one PDF.</p>", opens = opensIn(-12)),
                assignment(9004, "Lab 1 report", dueIn(-9), null, submittedDaysAgo = 10, opens = opensIn(-20)),
            )
            COURSE_292 -> listOf(
                assignment(9101, "Design project: milestone 1", dueIn(12), "<p>Group submission.</p>", opens = opensIn(2)),
                assignment(9102, "Reading: op-amp notes", null, "<p>No due date; read before week 8.</p>"),
            )
            COURSE_201 -> listOf(
                assignment(9201, "Assignment 1", dueIn(3), "<p>C programming warm-up.</p>"),
            )
            else -> emptyList()
        }
    }

    companion object {
        /** The fake Canvas's address. Using it in Settings is what switches the fake on. */
        const val HOST = "fake-canvas.example.edu"
        const val ADDRESS = "https://$HOST"

        private const val COURSE_204 = 555L
        private const val COURSE_292 = 556L
        private const val COURSE_201 = 557L

        fun isFake(address: String?): Boolean = address == ADDRESS
    }
}
