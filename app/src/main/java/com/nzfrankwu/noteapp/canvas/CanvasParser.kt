package com.nzfrankwu.noteapp.canvas

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.OffsetDateTime

/** Canvas sent something Noteapp couldn't read (e.g. a login page instead of data). */
class CanvasFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Reads Canvas API responses (JSON) into Noteapp's Canvas models. No network here: the client
 * fetches, this only parses, so it's fully tested with sample responses.
 */
object CanvasParser {
    private val json = Json {
        ignoreUnknownKeys = true // Canvas sends many fields Noteapp doesn't use
        coerceInputValues = true // a null where a default exists uses the default
        explicitNulls = false
    }

    /**
     * Canvas starts JSON with `while(1);` when the request is signed in with browser cookies
     * (a protection against other websites reading it). Remove it before parsing.
     */
    fun stripGuard(body: String): String {
        val trimmed = body.trimStart()
        return if (trimmed.startsWith(GUARD)) trimmed.removePrefix(GUARD) else trimmed
    }

    /** GET /api/v1/users/self */
    fun parseUser(body: String): CanvasUser {
        val user = decode<UserJson>(body)
        return CanvasUser(user.id, user.name?.takeIf { it.isNotBlank() } ?: user.shortName ?: "Canvas user")
    }

    /**
     * GET /api/v1/courses — skips courses Canvas only half-describes (e.g. ones whose dates
     * restrict access, which come back with just an ID).
     */
    fun parseCourses(body: String): List<CanvasCourse> = decode<List<CourseJson>>(body).mapNotNull { c ->
        val code = c.courseCode?.trim().orEmpty()
        val name = c.name?.trim().orEmpty()
        if (c.accessRestrictedByDate == true || (code.isEmpty() && name.isEmpty())) {
            null
        } else {
            CanvasCourse(id = c.id, name = name.ifEmpty { code }, courseCode = code.ifEmpty { name })
        }
    }

    /** GET /api/v1/courses/:id/assignments?include[]=submission (quizzes are included as assignments). */
    fun parseAssignments(body: String): List<CanvasAssignment> = decode<List<AssignmentJson>>(body).map { a ->
        CanvasAssignment(
            id = a.id,
            courseId = a.courseId,
            name = a.name?.trim()?.takeIf { it.isNotEmpty() } ?: "Untitled assignment",
            dueAt = parseTime(a.dueAt),
            descriptionHtml = a.description?.takeIf { it.isNotBlank() },
            htmlUrl = a.htmlUrl,
            submission = a.submission?.let { CanvasSubmission(it.workflowState, parseTime(it.submittedAt)) },
        )
    }

    /**
     * Canvas splits long lists into pages and says where the next one is in the `Link` header:
     * `<https://…&page=2>; rel="next", <https://…&page=1>; rel="first"`. Returns the next page's
     * address, or null on the last page.
     */
    fun nextPageUrl(linkHeader: String?): String? {
        if (linkHeader.isNullOrBlank()) return null
        return linkPart.findAll(linkHeader)
            .firstOrNull { match -> match.groupValues[2].split(' ').any { it == "next" } }
            ?.groupValues?.get(1)
    }

    /** Canvas times are ISO 8601, e.g. "2026-11-20T10:59:00Z" (or with an offset). Unreadable → null. */
    fun parseTime(text: String?): Instant? =
        text?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }

    private const val GUARD = "while(1);"
    private val linkPart = Regex("<([^>]*)>\\s*;\\s*rel=\"([^\"]*)\"")

    private inline fun <reified T> decode(body: String): T = try {
        json.decodeFromString<T>(stripGuard(body))
    } catch (e: IllegalArgumentException) { // includes kotlinx SerializationException
        throw CanvasFormatException("Canvas sent data Noteapp couldn't read", e)
    }

    @Serializable
    private data class UserJson(
        val id: Long,
        val name: String? = null,
        @SerialName("short_name") val shortName: String? = null,
    )

    @Serializable
    private data class CourseJson(
        val id: Long,
        val name: String? = null,
        @SerialName("course_code") val courseCode: String? = null,
        @SerialName("access_restricted_by_date") val accessRestrictedByDate: Boolean? = null,
    )

    @Serializable
    private data class AssignmentJson(
        val id: Long,
        @SerialName("course_id") val courseId: Long,
        val name: String? = null,
        @SerialName("due_at") val dueAt: String? = null,
        val description: String? = null,
        @SerialName("html_url") val htmlUrl: String? = null,
        val submission: SubmissionJson? = null,
    )

    @Serializable
    private data class SubmissionJson(
        @SerialName("workflow_state") val workflowState: String? = null,
        @SerialName("submitted_at") val submittedAt: String? = null,
    )
}
