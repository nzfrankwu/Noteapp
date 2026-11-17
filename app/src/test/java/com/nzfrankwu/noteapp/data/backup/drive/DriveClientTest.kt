package com.nzfrankwu.noteapp.data.backup.drive

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** PHASE-6.md 6.8: the Drive client against a fake Drive server (MockWebServer). Made-up tokens only. */
class DriveClientTest {
    @get:Rule val temp = TemporaryFolder()

    private lateinit var server: MockWebServer
    private val sleeps = mutableListOf<Long>()
    private val invalidated = mutableListOf<String>()
    private var tokenNumber = 1
    private val tokens = object : DriveTokens {
        override suspend fun token() = "made-up-token-$tokenNumber"
        override suspend fun invalidate(token: String) {
            invalidated += token
            tokenNumber++
        }
    }
    private lateinit var client: DriveClient

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        client = DriveClient(
            tokens = tokens,
            apiBase = server.url("/drive/v3/"),
            uploadBase = server.url("/upload/drive/v3/"),
            retryPausesMs = listOf(10, 20),
            sleep = { sleeps += it },
        )
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    private fun <T> run(block: suspend () -> T): T = runBlocking { block() }
    private fun ok(body: String) = MockResponse().setResponseCode(200).setBody(body)
    private fun error(code: Int, reason: String? = null) = MockResponse().setResponseCode(code)
        .setBody(if (reason == null) "{}" else """{"error":{"code":$code,"errors":[{"reason":"$reason"}]}}""")

    private fun kind(block: suspend () -> Unit): DriveException.Kind =
        assertThrows(DriveException::class.java) { run(block) }.kind

    @Test
    fun accountEmail_sendsTheToken() {
        server.enqueue(ok("""{"user":{"emailAddress":"test@example.com","displayName":"Test"}}"""))
        assertEquals("test@example.com", run { client.accountEmail() })
        val request = server.takeRequest()
        assertEquals("Bearer made-up-token-1", request.getHeader("Authorization"))
        assertEquals("/drive/v3/about?fields=user%28emailAddress%29", request.path)
        assertNull("no cookies, ever", request.getHeader("Cookie"))
    }

    @Test
    fun findFolder_quotesTheName_andLooksAtTheTopOfMyDrive() {
        server.enqueue(ok("""{"files":[]}"""))
        assertNull(run { client.findFolder("Tom's notes", null) })
        val q = server.takeRequest().requestUrl!!.queryParameter("q")!!
        assertTrue(q, q.contains("name = 'Tom\\'s notes'"))
        assertTrue(q, q.contains("'root' in parents"))
        assertTrue(q, q.contains("trashed = false"))
    }

    @Test
    fun folder_isMadeWhenMissing() {
        server.enqueue(ok("""{"files":[]}"""))
        server.enqueue(ok("""{"id":"f1","name":"Noteapp","mimeType":"${DriveClient.FOLDER}"}"""))
        assertEquals("f1", run { client.folder("Noteapp", null) }.id)
        server.takeRequest()
        val create = server.takeRequest()
        assertEquals("POST", create.method)
        val body = create.body.readUtf8()
        assertTrue(body, body.contains("\"name\":\"Noteapp\""))
        assertFalse("top of My Drive: no parents", body.contains("parents"))
    }

    @Test
    fun listFiles_readsEveryPage() {
        server.enqueue(ok("""{"files":[{"id":"a","name":"1","size":"5","md5Checksum":"x"}],"nextPageToken":"p2"}"""))
        server.enqueue(ok("""{"files":[{"id":"b","name":"2","appProperties":{"kind":"blob"},"unknown":1}]}"""))
        val files = run { client.listFiles("parent_1") }
        assertEquals(listOf("a", "b"), files.map { it.id })
        assertEquals(5L, files[0].sizeBytes)
        assertEquals("blob", files[1].appProperties["kind"])
        assertNull(server.takeRequest().requestUrl!!.queryParameter("pageToken"))
        assertEquals("p2", server.takeRequest().requestUrl!!.queryParameter("pageToken"))
    }

    @Test
    fun upload_startsAResumableSession_thenSendsTheFile() {
        val file = temp.newFile("page.ink").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        server.enqueue(MockResponse().setResponseCode(200).addHeader("Location", server.url("/upload-session/1")))
        server.enqueue(ok("""{"id":"new1","name":"abc","size":"4","md5Checksum":"08d6c05a21512a79a1dfeb9d2a8f262f"}"""))

        val uploaded = run { client.upload(file, "abc", "blobs_1", appProperties = mapOf("kind" to "blob")) }
        assertEquals("08d6c05a21512a79a1dfeb9d2a8f262f", uploaded.md5Checksum)

        val start = server.takeRequest()
        assertEquals("POST", start.method)
        assertEquals("resumable", start.requestUrl!!.queryParameter("uploadType"))
        assertTrue(start.path!!.startsWith("/upload/drive/v3/files?"))
        assertEquals("4", start.getHeader("X-Upload-Content-Length"))
        val metadata = start.body.readUtf8()
        assertTrue(metadata, metadata.contains("\"parents\":[\"blobs_1\"]"))
        assertTrue(metadata, metadata.contains("\"appProperties\":{\"kind\":\"blob\"}"))

        val put = server.takeRequest()
        assertEquals("PUT", put.method)
        assertEquals("/upload-session/1", put.path)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), put.body.readByteArray())
    }

    @Test
    fun upload_whoseContentFails_startsAFreshSession() {
        val file = temp.newFile("a.bin").apply { writeBytes(byteArrayOf(7)) }
        server.enqueue(MockResponse().setResponseCode(200).addHeader("Location", server.url("/upload-session/1")))
        server.enqueue(error(503))
        server.enqueue(MockResponse().setResponseCode(200).addHeader("Location", server.url("/upload-session/2")))
        server.enqueue(ok("""{"id":"new1"}"""))
        assertEquals("new1", run { client.upload(file, "a", "p") }.id)
        assertEquals(listOf("POST", "PUT", "POST", "PUT"), List(4) { server.takeRequest().method })
        assertEquals(listOf(10L), sleeps)
    }

    @Test
    fun anExpiredToken_isRefreshedOnce() {
        server.enqueue(error(401))
        server.enqueue(ok("""{"user":{"emailAddress":"a@b.c"}}"""))
        assertEquals("a@b.c", run { client.accountEmail() })
        assertEquals(listOf("made-up-token-1"), invalidated)
        server.takeRequest()
        assertEquals("Bearer made-up-token-2", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun aSecondRefusal_meansSignInAgain() {
        server.enqueue(error(401))
        server.enqueue(error(401))
        assertEquals(DriveException.Kind.AUTH, kind { client.accountEmail() })
    }

    @Test
    fun aRevokedPermission_meansSignInAgain() {
        server.enqueue(error(403, "insufficientPermissions"))
        assertEquals(DriveException.Kind.AUTH, kind { client.listFiles("p") })
        assertEquals(1, server.requestCount)
    }

    @Test
    fun aBusyServer_isRetried_withPauses() {
        server.enqueue(error(503))
        server.enqueue(error(403, "userRateLimitExceeded"))
        server.enqueue(ok("""{"files":[]}"""))
        assertEquals(emptyList<DriveFile>(), run { client.listFiles("p") })
        assertEquals(listOf(10L, 20L), sleeps)
    }

    @Test
    fun aServerThatStaysBusy_isReportedAsTryLater() {
        repeat(3) { server.enqueue(error(429)) }
        assertEquals(DriveException.Kind.OFFLINE, kind { client.listFiles("p") })
        assertEquals(3, server.requestCount)
    }

    @Test
    fun aFullDrive_isReported_withoutRetrying() {
        server.enqueue(error(403, "storageQuotaExceeded"))
        assertEquals(DriveException.Kind.QUOTA, kind { client.listFiles("p") })
        assertEquals(1, server.requestCount)
    }

    @Test
    fun noConnection_isReportedAsOffline() {
        server.shutdown()
        assertEquals(DriveException.Kind.OFFLINE, kind { client.listFiles("p") })
        assertEquals(listOf(10L, 20L), sleeps)
    }

    @Test
    fun deletingAFileThatIsAlreadyGone_isFine() {
        server.enqueue(error(404))
        run { client.delete("gone_1") }
        assertEquals("DELETE", server.takeRequest().method)
        server.enqueue(error(404))
        assertEquals(DriveException.Kind.NOT_FOUND, kind { client.download("gone_1", temp.root.resolve("x")) })
    }

    @Test
    fun download_writesTheFile_andLeavesNothingElse() {
        server.enqueue(ok("hello"))
        val target = temp.root.resolve("sub/out.bin")
        run { client.download("file_1", target) }
        assertEquals("hello", target.readText())
        assertEquals("media", server.takeRequest().requestUrl!!.queryParameter("alt"))
        assertEquals(listOf("out.bin"), target.parentFile!!.list()!!.toList())
    }

    @Test
    fun oldRevisions_areDeleted_butNotTheCurrentOne() {
        server.enqueue(ok("""{"revisions":[{"id":"r1"},{"id":"r2"},{"id":"r3"}]}"""))
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(error(404)) // already gone: fine
        run { client.deleteOldRevisions("file_1") }
        assertEquals("/drive/v3/files/file_1/revisions?fields=revisions%28id%29&pageSize=1000", server.takeRequest().path)
        val first = server.takeRequest()
        assertEquals("DELETE", first.method)
        assertEquals("/drive/v3/files/file_1/revisions/r1", first.path)
        assertEquals("/drive/v3/files/file_1/revisions/r2", server.takeRequest().path)
        assertEquals("r3, the current content, stays", 3, server.requestCount)
    }

    @Test
    fun aStrangeFileId_isNeverSent() {
        assertThrows(IllegalArgumentException::class.java) { run { client.delete("../about") } }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun errorMessages_neverHoldTheToken() {
        server.enqueue(error(400, "badRequest"))
        val e = assertThrows(DriveException::class.java) { run { client.listFiles("p") } }
        assertEquals(DriveException.Kind.OTHER, e.kind)
        assertFalse(e.message!!.contains("made-up-token"))
    }
}
