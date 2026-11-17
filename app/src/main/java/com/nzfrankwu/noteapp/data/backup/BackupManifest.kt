package com.nzfrankwu.noteapp.data.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * The list that describes one backup set: when it was made, by which app and database version,
 * what it holds, and every file with its size and md5. A backup set is only valid if every file
 * matches this list (PHASE-6.md). The same list is used by the .zip export and by Drive.
 */
@Serializable
data class BackupManifest(
    /** This file's own format. Raise it only with a reader for the old one. */
    val format: Int = FORMAT,
    /** When the snapshot was taken (milliseconds since 1970, UTC). */
    val createdAt: Long,
    val appVersion: String,
    /** The database's version (Room). A backup from a newer app can't be restored here. */
    val databaseVersion: Int,
    val counts: BackupCounts = BackupCounts(),
    val files: List<BackupEntry>,
) {
    /** The total size of every file in the set, in bytes. */
    val totalSize: Long get() = files.sumOf { it.size }

    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        const val FORMAT = 1

        private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

        /** Reads a manifest; throws [BackupException] if it isn't one we understand. */
        fun fromJson(text: String): BackupManifest {
            val manifest = try {
                json.decodeFromString(serializer(), text)
            } catch (e: Exception) {
                throw BackupException("This isn't a Noteapp backup (its file list can't be read).")
            }
            if (manifest.format > FORMAT) throw BackupException("This backup was made by a newer Noteapp. Update Noteapp first.")
            val paths = HashSet<String>()
            for (entry in manifest.files) {
                if (!BackupPaths.isRestorable(entry.path) || entry.path == BackupPaths.MANIFEST) {
                    throw BackupException("This backup holds an unexpected file and can't be used.")
                }
                if (!paths.add(entry.path)) throw BackupException("This backup lists a file twice and can't be used.")
                if (entry.size < 0 || !Md5.isValid(entry.md5)) throw BackupException("This backup's file list is damaged.")
            }
            if (BackupPaths.DATABASE !in paths) throw BackupException("This backup has no database and can't be used.")
            return manifest
        }
    }
}

/** What's in the backup, shown before restoring ("12 notebooks · 340 pages"). */
@Serializable
data class BackupCounts(
    val folders: Int = 0,
    val notebooks: Int = 0,
    val pages: Int = 0,
    val tasks: Int = 0,
)

/** One file of a backup set: its path in the set (see [BackupPaths]), size and md5 (lowercase hex). */
@Serializable
data class BackupEntry(val path: String, val size: Long, val md5: String)

/** A backup that can't be made or used. The message is written for the user. */
class BackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** md5 checksums, in the lowercase hex that Google Drive also reports (`md5Checksum`). */
object Md5 {
    fun of(file: File): String = file.inputStream().use(::of)

    fun of(input: InputStream): String {
        val digest = MessageDigest.getInstance("MD5")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            digest.update(buffer, 0, n)
        }
        return hex(digest.digest())
    }

    fun of(bytes: ByteArray): String = hex(MessageDigest.getInstance("MD5").digest(bytes))

    fun isValid(text: String): Boolean = text.length == 32 && text.all { it in '0'..'9' || it in 'a'..'f' }

    private fun hex(bytes: ByteArray): String = buildString(bytes.size * 2) {
        for (b in bytes) {
            val v = b.toInt() and 0xff
            append("0123456789abcdef"[v shr 4])
            append("0123456789abcdef"[v and 0xf])
        }
    }
}
