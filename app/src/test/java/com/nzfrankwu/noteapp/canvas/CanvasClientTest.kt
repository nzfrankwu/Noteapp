package com.nzfrankwu.noteapp.canvas

import kotlinx.coroutines.runBlocking
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The Canvas client against a fake Canvas server (MockWebServer) on this computer.
 * No real Canvas, no real login, made-up data only.
 */
class CanvasClientTest {
    private lateinit var server: MockWebServer
    private var serverRunning = false
    private var cookie: String? = "canvas_session=made-up-test-value"
    private val sleeps = mutableListOf<Long>()
    private lateinit var client: CanvasClient

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        serverRunning = true
        client = CanvasClient(
            baseUrl = server.url("/").toString(),
            cookies = { cookie },
            sleep = { sleeps += it },
        )
    }

    @After
    fun tearDown() {
        if (serverRunning) server.shutdown()
    }

    private fun sample(name: String) = checkNotNull(javaClass.getResource("/canvas/$name")).readText()

    private fun ok(body: String, nextPath: String? = null) = MockResponse().setResponseCode(200).setBody(body).apply {
        if (nextPath != null) addHeader("Link", "<${server.url(nextPath)}>; rel=\"next\"")
    }

    private fun <T> run(block: suspend () -> T): T = runBlocking { block() }

    @Test
    fun getSelf_sendsTheCookieAndAsksForJson() {
        server.enqueue(ok(sample("user_self.json")))
        assertEquals(CanvasUser(1001, "Test Student"), run { client.getSelf() })

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/v1/users/self?per_page=100", request.path)
        assertEquals("canvas_session=made-up-test-value", request.getHeader("Cookie"))
        assertEquals("application/json", request.getHeader("Accept"))
    }

    @Test
    fun notSignedIn_noRequestIsMade() {
        cookie = null
        assertThrows(CanvasException.NotSignedIn::class.java) { run { client.getSelf() } }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun courses_askForActiveOnes() {
        server.enqueue(ok(sample("courses.json")))
        assertEquals(2, run { client.getActiveCourses() }.size)
        val path = server.takeRequest().path.orEmpty()
        assertTrue(path, path.startsWith("/api/v1/courses?"))
        assertTrue(path, path.contains("enrollment_state=active"))
        assertTrue(path, path.contains("per_page=100"))
    }

    @Test
    fun assignments_includeSubmissions_andEveryPageIsRead_politely() {
        val page1 = """[{"id":1,"course_id":555,"name":"A1"},{"id":2,"course_id":555,"name":"A2"}]"""
        val page2 = """while(1);[{"id":3,"course_id":555,"name":"A3"}]"""
        server.enqueue(ok(page1, nextPath = "/api/v1/courses/555/assignments?page=2&per_page=100"))
        server.enqueue(ok(page2))

        val all = run { client.getAssignments(555) }

        assertEquals(listOf(1L, 2L, 3L), all.map { it.id })
        assertEquals(2, server.requestCount)
        val first = server.takeRequest().path.orEmpty()
        assertTrue(first, first.contains("include%5B%5D=submission") || first.contains("include[]=submission"))
        assertEquals("/api/v1/courses/555/assignments?page=2&per_page=100", server.takeRequest().path)
        assertEquals("One polite pause between the two pages", listOf(250L), sleeps)
    }

    @Test
    fun aFailingLaterPage_failsTheWholeList() {
        // So callers never get a partial list (which could wrongly mark assignments "removed").
        server.enqueue(ok("""[{"id":1,"course_id":555,"name":"A1"}]""", nextPath = "/api/v1/courses/555/assignments?page=2"))
        server.enqueue(MockResponse().setResponseCode(500))
        assertThrows(CanvasException.HttpError::class.java) { run { client.getAssignments(555) } }
    }

    @Test
    fun unauthorized_meansSignInExpired() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"status":"unauthenticated"}"""))
        assertThrows(CanvasException.SessionExpired::class.java) { run { client.getSelf() } }
    }

    @Test
    fun redirectToLoginPage_meansSignInExpired() {
        server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", server.url("/login/saml").toString()))
        assertThrows(CanvasException.SessionExpired::class.java) { run { client.getSelf() } }
        assertEquals("The redirect must not be followed", 1, server.requestCount)
    }

    @Test
    fun serverError_isReported() {
        server.enqueue(MockResponse().setResponseCode(500))
        val error = assertThrows(CanvasException.HttpError::class.java) { run { client.getSelf() } }
        assertEquals(500, error.code)
    }

    @Test
    fun rateLimited_waitsAndTriesAgain() {
        server.enqueue(MockResponse().setResponseCode(403).setBody("403 Forbidden (Rate Limit Exceeded)"))
        server.enqueue(ok(sample("user_self.json")))
        assertEquals("Test Student", run { client.getSelf() }.name)
        assertEquals(2, server.requestCount)
        assertEquals(listOf(1_000L), sleeps)
    }

    @Test
    fun rateLimitedEveryTime_givesUpAfterThreeTries() {
        repeat(3) { server.enqueue(MockResponse().setResponseCode(429)) }
        assertThrows(CanvasException.RateLimited::class.java) { run { client.getSelf() } }
        assertEquals(3, server.requestCount)
        assertEquals("Waits get longer each time", listOf(1_000L, 2_000L), sleeps)
    }

    @Test
    fun nextPageOnAnotherSite_isRefused_andNoCookieGoesThere() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody("[]")
                .addHeader("Link", "<https://evil.example.com/steal?page=2>; rel=\"next\""),
        )
        assertThrows(CanvasException.UnsafeLink::class.java) { run { client.getActiveCourses() } }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun loginPageInsteadOfData_isBadData() {
        server.enqueue(ok("<!DOCTYPE html><html><body>Log in</body></html>"))
        assertThrows(CanvasException.BadData::class.java) { run { client.getActiveCourses() } }
    }

    @Test
    fun canvasUnreachable_isOffline() {
        server.shutdown()
        serverRunning = false
        assertThrows(CanvasException.Offline::class.java) { run { client.getSelf() } }
    }

    @Test
    fun readOnlyGuard_blocksAnythingButGet() {
        val http = CanvasClient.defaultHttpClient()
        val post = Request.Builder().url(server.url("/api/v1/courses/1/assignments")).post("{}".toRequestBody()).build()
        val error = assertThrows(IllegalStateException::class.java) { http.newCall(post).execute() }
        assertTrue(error.message.orEmpty().contains("read-only"))
        assertEquals("Nothing reached the server", 0, server.requestCount)
        assertNull(server.takeRequest(100, java.util.concurrent.TimeUnit.MILLISECONDS))
    }
}
