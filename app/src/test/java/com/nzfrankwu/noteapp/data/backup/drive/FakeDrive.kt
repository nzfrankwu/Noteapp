package com.nzfrankwu.noteapp.data.backup.drive

import com.nzfrankwu.noteapp.data.backup.Md5
import java.io.File
import java.time.Instant

/** An in-memory Google Drive for tests: folders, files with md5s and times, and ways to make it fail. */
class FakeDrive(var now: () -> Instant = { Instant.parse("2026-11-17T10:00:00Z") }) : DriveApi {
    class Item(
        val id: String,
        var name: String,
        var parent: String?,
        val mimeType: String,
        var bytes: ByteArray,
        val appProperties: Map<String, String>,
        var modified: Instant,
        /** What Drive reports as the md5 (tests can make it lie). */
        var reportedMd5: String? = null,
    ) {
        fun toFile() = DriveFile(
            id = id, name = name, mimeType = mimeType,
            size = if (mimeType == DriveClient.FOLDER) null else bytes.size.toString(),
            md5Checksum = if (mimeType == DriveClient.FOLDER) null else (reportedMd5 ?: Md5.of(bytes)),
            appProperties = appProperties, modifiedTime = modified.toString(),
        )
    }

    val items = LinkedHashMap<String, Item>()
    private var nextId = 1
    var uploads = mutableListOf<String>() // names, in order
    var downloads = 0

    /** Throw on the upload with this (1-based) number, counting from now. */
    var failUploadNumber: Int? = null
    /** Uploads report this wrong md5. */
    var lieAboutMd5 = false
    /** Downloads of these file ids throw. */
    val failDownloads = HashSet<String>()

    fun inFolder(id: String) = items.values.filter { it.parent == id }

    fun folderId(vararg path: String): String? {
        var parent: String? = null
        for (name in path) parent = items.values.firstOrNull { it.parent == parent && it.name == name && it.mimeType == DriveClient.FOLDER }?.id ?: return null
        return parent
    }

    override suspend fun findFolder(name: String, parentId: String?) =
        items.values.firstOrNull { it.parent == parentId && it.name == name && it.mimeType == DriveClient.FOLDER }?.toFile()

    override suspend fun createFolder(name: String, parentId: String?, appProperties: Map<String, String>): DriveFile {
        val item = Item("id${nextId++}", name, parentId, DriveClient.FOLDER, ByteArray(0), appProperties, now())
        items[item.id] = item
        return item.toFile()
    }

    override suspend fun listFiles(parentId: String) =
        inFolder(parentId).filter { it.mimeType != DriveClient.FOLDER }.map { it.toFile() }

    override suspend fun listFolders(parentId: String) =
        inFolder(parentId).filter { it.mimeType == DriveClient.FOLDER }.map { it.toFile() }

    override suspend fun upload(file: File, name: String, parentId: String, mimeType: String, appProperties: Map<String, String>): DriveFile {
        failUploadNumber?.let { n ->
            if (n <= 1) {
                failUploadNumber = null
                throw DriveException(DriveException.Kind.OFFLINE, "Couldn't reach Google Drive.")
            }
            failUploadNumber = n - 1
        }
        uploads += name
        val item = Item("id${nextId++}", name, parentId, mimeType, file.readBytes(), appProperties, now())
        if (lieAboutMd5) item.reportedMd5 = "0".repeat(32)
        items[item.id] = item
        return item.toFile()
    }

    override suspend fun replace(fileId: String, file: File, mimeType: String, name: String?): DriveFile {
        val item = items[fileId] ?: throw DriveException(DriveException.Kind.NOT_FOUND, "Not found in Google Drive.")
        item.bytes = file.readBytes()
        if (name != null) item.name = name
        item.modified = now()
        return item.toFile()
    }

    override suspend fun move(fileId: String, name: String?, addParent: String?, removeParent: String?): DriveFile {
        val item = items[fileId] ?: throw DriveException(DriveException.Kind.NOT_FOUND, "Not found in Google Drive.")
        if (name != null) item.name = name
        if (addParent != null) item.parent = addParent
        return item.toFile()
    }

    override suspend fun download(fileId: String, target: File) {
        if (fileId in failDownloads) throw DriveException(DriveException.Kind.OFFLINE, "Couldn't reach Google Drive.")
        val item = items[fileId] ?: throw DriveException(DriveException.Kind.NOT_FOUND, "Not found in Google Drive.")
        downloads++
        target.parentFile?.mkdirs()
        target.writeBytes(item.bytes)
    }

    override suspend fun delete(fileId: String) {
        items.remove(fileId)
        items.values.filter { it.parent == fileId }.map { it.id }.forEach { items.remove(it) }
    }
}
