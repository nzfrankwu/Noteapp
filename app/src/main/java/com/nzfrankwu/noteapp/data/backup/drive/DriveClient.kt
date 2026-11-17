package com.nzfrankwu.noteapp.data.backup.drive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Gives Google Drive access tokens (PHASE-6.md, "Auth"). In the app it's Play Services'
 * AuthorizationClient (scope `drive.file`); tests use a fixed fake. Tokens are passwords:
 * never log, print or store them.
 */
interface DriveTokens {
    /** A current token. Throws [DriveException] with [DriveException.Kind.AUTH] if the user must sign in again. */
    suspend fun token(): String

    /** Drive refused [token] (expired): forget it, so [token] fetches a fresh one. */
    suspend fun invalidate(token: String)
}

/** Why a Drive request failed. The message is safe to show (never holds a token). */
class DriveException(
    val kind: Kind,
    message: String,
    cause: Throwable? = null,
    /** Drive's own reason, e.g. `exportSizeLimitExceeded`, when it gave one. */
    val reason: String? = null,
) : Exception(message, cause) {
    enum class Kind {
        /** Signed out, or the permission was taken back: the user must sign in again. */
        AUTH,
        /** The Drive is full. */
        QUOTA,
        /** No connection, or Drive is busy: try again later. */
        OFFLINE,
        /** The file or folder isn't there (any more). */
        NOT_FOUND,
        /** Anything else Drive refused, or something arrived damaged. */
        OTHER,
    }
}

/** A file or folder in Drive, as much of it as Noteapp asks for. */
@Serializable
data class DriveFile(
    val id: String,
    val name: String = "",
    val mimeType: String = "",
    /** Bytes; Drive sends it as a string, and not for folders. */
    val size: String? = null,
    val md5Checksum: String? = null,
    val appProperties: Map<String, String> = emptyMap(),
    /** RFC 3339, e.g. `2026-11-17T10:00:00.000Z`. */
    val modifiedTime: String? = null,
) {
    val sizeBytes: Long get() = size?.toLongOrNull() ?: 0L
}

/**
 * The Drive operations the backup engine needs ([DriveClient] in the app, an in-memory fake in tests).
 * Every call may throw [DriveException].
 */
interface DriveApi {
    /** The folder called [name] inside [parentId] (null = the top of My Drive), or null. */
    suspend fun findFolder(name: String, parentId: String?): DriveFile?

    /** Makes a folder called [name] inside [parentId] (null = the top of My Drive). */
    suspend fun createFolder(name: String, parentId: String?, appProperties: Map<String, String> = emptyMap()): DriveFile

    /** The folder [name] in [parentId], made if it isn't there yet. */
    suspend fun folder(name: String, parentId: String?): DriveFile =
        findFolder(name, parentId) ?: createFolder(name, parentId)

    /** Every file (not folders, not in the bin) directly inside [parentId]. */
    suspend fun listFiles(parentId: String): List<DriveFile>

    /** Every folder directly inside [parentId]. */
    suspend fun listFolders(parentId: String): List<DriveFile>

    /** Uploads [file] as a new file; the answer carries Drive's own md5 for the caller to compare. */
    suspend fun upload(
        file: File,
        name: String,
        parentId: String,
        mimeType: String = "application/octet-stream",
        appProperties: Map<String, String> = emptyMap(),
    ): DriveFile

    /** Replaces the content of [fileId] (and renames it to [name], sets [appProperties], if given). */
    suspend fun replace(
        fileId: String,
        file: File,
        mimeType: String,
        name: String? = null,
        appProperties: Map<String, String> = emptyMap(),
    ): DriveFile

    /** Renames and/or moves a file or folder. */
    suspend fun move(fileId: String, name: String?, addParent: String?, removeParent: String?): DriveFile

    /** Downloads [fileId] into [target]; [target] appears only once the whole file arrived. */
    suspend fun download(fileId: String, target: File)

    /** Uploads a Word/PowerPoint [file] ([sourceMime]) and lets Drive turn it into [googleMime] (Docs/Slides). */
    suspend fun uploadToConvert(file: File, name: String, parentId: String, sourceMime: String, googleMime: String): DriveFile

    /** Downloads a Docs/Slides file as a PDF into [target]. */
    suspend fun exportPdf(fileId: String, target: File)

    /** Deletes [fileId] for good. A file that's already gone is fine. */
    suspend fun delete(fileId: String)
}

/**
 * A small Google Drive v3 client over OkHttp (PHASE-6.md 6.8; the user chose this over Google's
 * big Java client). It only ever sees files Noteapp created itself (scope `drive.file`).
 *
 * - Every request carries `Authorization: Bearer <token>`; a 401 gets one fresh token and one retry.
 * - Busy or failing servers (429, 5xx, rate-limit 403s) and dropped connections are retried a few
 *   times with growing pauses, then reported as [DriveException.Kind.OFFLINE].
 * - Uploads use Drive's resumable upload: the file appears in Drive only once all of it arrived.
 *   If sending the content fails, a fresh upload is started (Noteapp's files are small enough).
 *
 * This client never sends Canvas cookies: it has its own OkHttp client with no cookie store, and
 * nothing here logs requests (they carry the token).
 */
class DriveClient(
    private val tokens: DriveTokens,
    private val http: OkHttpClient = defaultHttpClient(),
    private val apiBase: HttpUrl = "https://www.googleapis.com/drive/v3/".toHttpUrl(),
    private val uploadBase: HttpUrl = "https://www.googleapis.com/upload/drive/v3/".toHttpUrl(),
    private val retryPausesMs: List<Long> = listOf(1_000, 4_000, 15_000),
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) : DriveApi {
    /** The signed-in account's email address (shown in Settings → Backup). */
    suspend fun accountEmail(): String? {
        val url = api("about").addQueryParameter("fields", "user(emailAddress)").build()
        val body = call { Request.Builder().url(url).get() }.use { it.body.string() }
        return parse<About>(body).user?.emailAddress
    }

    override suspend fun findFolder(name: String, parentId: String?): DriveFile? {
        val q = "name = '${escape(name)}' and mimeType = '$FOLDER' and trashed = false and '${escape(parentId ?: "root")}' in parents"
        return search(q).firstOrNull()
    }

    override suspend fun createFolder(name: String, parentId: String?, appProperties: Map<String, String>): DriveFile {
        val metadata = metadata(name, FOLDER, parentId, appProperties)
        val url = api("files").addQueryParameter("fields", FIELDS).build()
        val body = call { Request.Builder().url(url).post(metadata.toString().toRequestBody(JSON)) }.use { it.body.string() }
        return parse(body)
    }

    override suspend fun listFiles(parentId: String): List<DriveFile> =
        search("'${escape(parentId)}' in parents and trashed = false and mimeType != '$FOLDER'")

    override suspend fun listFolders(parentId: String): List<DriveFile> =
        search("'${escape(parentId)}' in parents and trashed = false and mimeType = '$FOLDER'")

    override suspend fun upload(
        file: File,
        name: String,
        parentId: String,
        mimeType: String,
        appProperties: Map<String, String>,
    ): DriveFile {
        val start = uploadUrl("files").addQueryParameter("uploadType", "resumable").addQueryParameter("fields", FIELDS).build()
        return resumable(start, "POST", metadata(name, mimeType, parentId, appProperties), file, mimeType)
    }

    override suspend fun replace(fileId: String, file: File, mimeType: String, name: String?, appProperties: Map<String, String>): DriveFile {
        val start = uploadUrl("files/${checkId(fileId)}").addQueryParameter("uploadType", "resumable")
            .addQueryParameter("fields", FIELDS).build()
        val metadata = buildJsonObject {
            if (name != null) put("name", name)
            if (appProperties.isNotEmpty()) put("appProperties", JsonObject(appProperties.mapValues { JsonPrimitive(it.value) }))
        }
        return resumable(start, "PATCH", metadata, file, mimeType)
    }

    override suspend fun move(fileId: String, name: String?, addParent: String?, removeParent: String?): DriveFile {
        val url = api("files/${checkId(fileId)}").apply {
            addQueryParameter("fields", FIELDS)
            if (addParent != null) addQueryParameter("addParents", checkId(addParent))
            if (removeParent != null) addQueryParameter("removeParents", checkId(removeParent))
        }.build()
        val metadata = buildJsonObject { if (name != null) put("name", name) }
        val body = call { Request.Builder().url(url).patch(metadata.toString().toRequestBody(JSON)) }.use { it.body.string() }
        return parse(body)
    }

    override suspend fun download(fileId: String, target: File) =
        downloadTo(api("files/${checkId(fileId)}").addQueryParameter("alt", "media").build(), target)

    override suspend fun uploadToConvert(file: File, name: String, parentId: String, sourceMime: String, googleMime: String): DriveFile {
        val start = uploadUrl("files").addQueryParameter("uploadType", "resumable").addQueryParameter("fields", FIELDS).build()
        return resumable(start, "POST", metadata(name, googleMime, parentId, emptyMap()), file, sourceMime)
    }

    override suspend fun exportPdf(fileId: String, target: File) =
        downloadTo(api("files/${checkId(fileId)}/export").addQueryParameter("mimeType", "application/pdf").build(), target)

    private suspend fun downloadTo(url: HttpUrl, target: File) {
        call { Request.Builder().url(url).get() }.use { response ->
            withContext(Dispatchers.IO) {
                target.parentFile?.mkdirs()
                val temp = File(target.path + ".download")
                try {
                    try {
                        temp.outputStream().use { out -> response.body.byteStream().copyTo(out) }
                    } catch (e: IOException) {
                        throw DriveException(DriveException.Kind.OFFLINE, "The download from Google Drive stopped halfway.", e)
                    }
                    if (!temp.renameTo(target)) {
                        target.delete()
                        if (!temp.renameTo(target)) throw IOException("Couldn't save the download")
                    }
                } finally {
                    temp.delete()
                }
            }
        }
    }

    override suspend fun delete(fileId: String) {
        val url = api("files/${checkId(fileId)}").build()
        try {
            call { Request.Builder().url(url).delete() }.close()
        } catch (e: DriveException) {
            if (e.kind != DriveException.Kind.NOT_FOUND) throw e
        }
    }

    // ---- Requests ----

    private suspend fun search(q: String): List<DriveFile> {
        val all = ArrayList<DriveFile>()
        var pageToken: String? = null
        do {
            val url = api("files").apply {
                addQueryParameter("q", q)
                addQueryParameter("spaces", "drive")
                addQueryParameter("pageSize", "1000")
                addQueryParameter("fields", "nextPageToken,files($FIELDS)")
                if (pageToken != null) addQueryParameter("pageToken", pageToken)
            }.build()
            val page = parse<FileList>(call { Request.Builder().url(url).get() }.use { it.body.string() })
            all += page.files
            pageToken = page.nextPageToken?.takeIf { it.isNotEmpty() }
        } while (pageToken != null)
        return all
    }

    /**
     * Starts a resumable upload session, then sends the whole file to it. If sending fails in a way
     * worth retrying, the next attempt starts a new session (simpler and safer than resuming one).
     */
    private suspend fun resumable(start: HttpUrl, method: String, metadata: JsonObject, file: File, mimeType: String): DriveFile {
        var attempt = 0
        while (true) {
            val session = call {
                Request.Builder().url(start)
                    .header("X-Upload-Content-Type", mimeType)
                    .header("X-Upload-Content-Length", file.length().toString())
                    .method(method, metadata.toString().toRequestBody(JSON))
            }.use { response ->
                response.header("Location")?.toHttpUrlOrNull()
                    ?: throw DriveException(DriveException.Kind.OTHER, "Google Drive didn't start the upload.")
            }
            try {
                val text = call(retries = emptyList()) {
                    Request.Builder().url(session).put(file.asRequestBody(mimeType.toMediaType()))
                }.use { it.body.string() }
                return parse(text)
            } catch (e: DriveException) {
                if (e.kind != DriveException.Kind.OFFLINE || attempt >= retryPausesMs.size) throw e
                sleep(retryPausesMs[attempt++])
            }
        }
    }

    /**
     * Sends a request with a token, retrying as described on the class (pauses from [retries]).
     * The caller closes the response. Throws [DriveException] for anything but a 2xx answer.
     */
    private suspend fun call(retries: List<Long> = retryPausesMs, build: () -> Request.Builder): Response {
        var refreshed = false
        var attempt = 0
        while (true) {
            val token = tokens.token()
            val request = build().header("Authorization", "Bearer $token").build()
            val response = try {
                withContext(Dispatchers.IO) { http.newCall(request).execute() }
            } catch (e: IOException) {
                if (attempt < retries.size) {
                    sleep(retries[attempt++])
                    continue
                }
                throw DriveException(DriveException.Kind.OFFLINE, "Couldn't reach Google Drive.", e)
            }
            if (response.isSuccessful) return response
            val code = response.code
            val reason = response.use { runCatching { errorReason(it.body.string()) }.getOrNull() }
            val busy = code == 429 || code >= 500 || (code == 403 && reason in RATE_LIMITS)
            when {
                code == 401 && !refreshed -> {
                    refreshed = true
                    tokens.invalidate(token)
                }
                code == 401 -> throw DriveException(DriveException.Kind.AUTH, "Sign in to Google again to keep backing up.")
                code == 403 && reason == "storageQuotaExceeded" ->
                    throw DriveException(DriveException.Kind.QUOTA, "Your Google Drive is full.")
                busy && attempt < retries.size -> sleep(retries[attempt++])
                busy -> throw DriveException(DriveException.Kind.OFFLINE, "Google Drive is busy. Noteapp will try again later.")
                code == 403 && reason in AUTH_REASONS ->
                    throw DriveException(DriveException.Kind.AUTH, "Noteapp isn't allowed to use your Google Drive. Sign in to Google again.")
                code == 404 -> throw DriveException(DriveException.Kind.NOT_FOUND, "Not found in Google Drive.")
                else -> throw DriveException(
                    DriveException.Kind.OTHER,
                    "Google Drive refused the request ($code${reason?.let { ", $it" } ?: ""}).",
                    reason = reason,
                )
            }
        }
    }

    private fun api(path: String): HttpUrl.Builder = apiBase.newBuilder().addPathSegments(path)
    private fun uploadUrl(path: String): HttpUrl.Builder = uploadBase.newBuilder().addPathSegments(path)

    private fun metadata(name: String, mimeType: String, parentId: String?, appProperties: Map<String, String>) = buildJsonObject {
        put("name", name)
        put("mimeType", mimeType)
        if (parentId != null) put("parents", JsonArray(listOf(JsonPrimitive(parentId))))
        if (appProperties.isNotEmpty()) put("appProperties", JsonObject(appProperties.mapValues { JsonPrimitive(it.value) }))
    }

    private inline fun <reified T> parse(text: String): T = try {
        json.decodeFromString<T>(text)
    } catch (e: Exception) {
        throw DriveException(DriveException.Kind.OTHER, "Google Drive sent something Noteapp couldn't read.", e)
    }

    @Serializable private data class FileList(val files: List<DriveFile> = emptyList(), val nextPageToken: String? = null)
    @Serializable private data class About(val user: User? = null)
    @Serializable private data class User(val emailAddress: String? = null)
    @Serializable private data class ErrorBody(val error: ErrorInfo? = null)
    @Serializable private data class ErrorInfo(val errors: List<ErrorItem> = emptyList())
    @Serializable private data class ErrorItem(val reason: String? = null)

    private fun errorReason(body: String): String? = json.decodeFromString<ErrorBody>(body).error?.errors?.firstOrNull()?.reason

    companion object {
        const val FOLDER = "application/vnd.google-apps.folder"
        private const val FIELDS = "id,name,mimeType,size,md5Checksum,appProperties,modifiedTime"
        private val JSON = "application/json; charset=UTF-8".toMediaType()
        private val RATE_LIMITS = setOf("rateLimitExceeded", "userRateLimitExceeded")
        private val AUTH_REASONS = setOf("insufficientPermissions", "appNotAuthorizedToFile", "forbidden")
        private val json = Json { ignoreUnknownKeys = true }

        /** Drive search strings quote names with '…': escape \ and '. */
        fun escape(text: String): String = text.replace("\\", "\\\\").replace("'", "\\'")

        /** File IDs are letters, digits, - and _; anything else is refused rather than sent. */
        fun checkId(id: String): String {
            require(id.isNotEmpty() && id.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' || it == '_' }) {
                "Unexpected Drive file ID"
            }
            return id
        }

        /** No cookie jar (Canvas cookies can never ride along) and no logging (requests carry the token). */
        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
    }
}
