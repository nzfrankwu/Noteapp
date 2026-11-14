package com.nzfrankwu.noteapp.canvas

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Where the Canvas sign-in cookies come from (the in-app browser's cookie store in the app). */
fun interface CanvasCookieSource {
    /** The Cookie header value for [url], or null if not signed in. Never log this value. */
    fun cookieHeaderFor(url: String): String?
}

/** Why a Canvas request failed. Existing data is always kept; the app just shows a message. */
sealed class CanvasException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NotSignedIn : CanvasException("Not signed in to Canvas")
    class SessionExpired : CanvasException("Canvas sign-in expired: sign in again")
    class Offline(cause: IOException) : CanvasException("Couldn't reach Canvas", cause)
    class RateLimited : CanvasException("Canvas asked Noteapp to slow down; try again later")
    class HttpError(val code: Int) : CanvasException("Canvas returned an error ($code)")
    class BadData(cause: Throwable) : CanvasException("Canvas sent data Noteapp couldn't read", cause)
    class UnsafeLink(host: String) : CanvasException("Canvas pointed to another site ($host); stopped for safety")
}

/**
 * Reads data from Canvas as the signed-in user. READ-ONLY (IDEA.md §3.5):
 * - Only GET requests are possible: [ReadOnlyGuard] refuses anything else.
 * - Session cookies are attached only to requests for the Canvas address itself, never logged,
 *   and never stored by this class.
 * - Lists are read page by page (Canvas `Link` headers), with a short pause between pages.
 *   Each list function returns only after EVERY page succeeded, so callers can trust a
 *   returned list is complete (needed before marking anything "removed from Canvas").
 *
 * @param baseUrl the school's Canvas address, e.g. https://canvas.example.edu
 */
class CanvasClient(
    baseUrl: String,
    private val cookies: CanvasCookieSource,
    private val http: OkHttpClient = defaultHttpClient(),
    private val pageDelayMillis: Long = 250,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) : CanvasSource {
    private val base: HttpUrl = requireNotNull(baseUrl.trim().trimEnd('/').toHttpUrlOrNull()) { "Not a web address" }

    /** GET /api/v1/users/self — who is signed in. */
    suspend fun getSelf(): CanvasUser = parse { CanvasParser.parseUser(get(api("users/self")).body) }

    /** GET /api/v1/courses — the user's active courses (all pages). */
    override suspend fun getActiveCourses(): List<CanvasCourse> =
        allPages(api("courses") { addQueryParameter("enrollment_state", "active") }) { CanvasParser.parseCourses(it) }

    /** GET /api/v1/courses/:id/assignments — every assignment incl. quizzes, with the user's submission. */
    override suspend fun getAssignments(courseId: Long): List<CanvasAssignment> =
        allPages(api("courses/$courseId/assignments") { addQueryParameter("include[]", "submission") }) {
            CanvasParser.parseAssignments(it)
        }

    private fun api(path: String, extra: HttpUrl.Builder.() -> Unit = {}): HttpUrl =
        base.newBuilder()
            .addPathSegments("api/v1/$path")
            .apply(extra)
            .addQueryParameter("per_page", "100")
            .build()

    private suspend fun <T> allPages(first: HttpUrl, parsePage: (String) -> List<T>): List<T> {
        val all = mutableListOf<T>()
        var next: HttpUrl? = first
        var page = 0
        while (next != null) {
            if (page++ > 0) sleep(pageDelayMillis) // be polite to the server
            val response = get(next)
            all += parse { parsePage(response.body) }
            next = CanvasParser.nextPageUrl(response.link)?.let { link ->
                val url = link.toHttpUrlOrNull() ?: throw CanvasException.BadData(IllegalStateException("Bad page link"))
                if (url.host != base.host) throw CanvasException.UnsafeLink(url.host)
                url
            }
        }
        return all
    }

    private class Page(val body: String, val link: String?)

    private suspend fun get(url: HttpUrl): Page {
        val cookie = cookies.cookieHeaderFor(base.toString()) ?: throw CanvasException.NotSignedIn()
        var waitMillis = 1_000L
        repeat(MAX_ATTEMPTS) { attempt ->
            val result = withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .header("Accept", "application/json")
                    .apply { if (url.host == base.host) header("Cookie", cookie) }
                    .build()
                try {
                    http.newCall(request).execute().use { response -> readOrRetry(response) }
                } catch (e: IOException) {
                    throw CanvasException.Offline(e)
                }
            }
            if (result != null) return result
            if (attempt < MAX_ATTEMPTS - 1) {
                sleep(waitMillis)
                waitMillis *= 2
            }
        }
        throw CanvasException.RateLimited()
    }

    /** The page, or null if Canvas said to slow down (so the caller waits and retries). */
    private fun readOrRetry(response: Response): Page? {
        val body = response.body.string()
        return when {
            response.isSuccessful -> Page(body, response.header("Link"))
            response.code == 401 -> throw CanvasException.SessionExpired()
            response.isRedirect && response.header("Location").orEmpty().contains("/login") ->
                throw CanvasException.SessionExpired()
            response.code == 429 || (response.code == 403 && body.contains("Rate Limit Exceeded", ignoreCase = true)) -> null
            else -> throw CanvasException.HttpError(response.code)
        }
    }

    private inline fun <T> parse(block: () -> T): T = try {
        block()
    } catch (e: CanvasFormatException) {
        throw CanvasException.BadData(e)
    }

    companion object {
        private const val MAX_ATTEMPTS = 3

        /**
         * The HTTP client for Canvas: read-only guard, no automatic redirects (a redirect to
         * the login page means the session expired), and sensible time limits.
         * Deliberately no HTTP logging: requests carry the session cookie.
         */
        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .addInterceptor(ReadOnlyGuard)
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}

/** Refuses any request that isn't a GET, so Noteapp can never change anything on Canvas. */
object ReadOnlyGuard : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val method = chain.request().method
        check(method == "GET") { "Noteapp is read-only with Canvas: $method requests are not allowed" }
        return chain.proceed(chain.request())
    }
}
