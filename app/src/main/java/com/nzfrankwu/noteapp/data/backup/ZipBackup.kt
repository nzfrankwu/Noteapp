package com.nzfrankwu.noteapp.data.backup

import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * "Export everything" and "Import from export" (IDEA.md §5a): one .zip holding a backup set
 * (`manifest.json` plus the files it lists, at their [BackupPaths]).
 */
object ZipBackup {
    /** Writes [snapshot] as a zip to [out]. [onProgress] gets 0..1 as the bytes go out. */
    fun write(snapshot: Snapshot, out: OutputStream, onProgress: (Float) -> Unit = {}) {
        val manifest = snapshot.manifest
        val total = manifest.totalSize.coerceAtLeast(1)
        var done = 0L
        ZipOutputStream(BufferedOutputStream(out, 64 * 1024)).use { zip ->
            zip.putNextEntry(ZipEntry(BackupPaths.MANIFEST))
            zip.write(manifest.toJson().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            val buffer = ByteArray(64 * 1024)
            for (entry in manifest.files) {
                zip.putNextEntry(ZipEntry(entry.path).apply { time = manifest.createdAt })
                var written = 0L
                snapshot.source(entry.path).inputStream().use { input ->
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        zip.write(buffer, 0, n)
                        written += n
                        done += n
                    }
                }
                zip.closeEntry()
                // A file that changed size since it was listed would make a set that can't be restored.
                if (written != entry.size) throw BackupException("A file changed while it was being exported. Try again.")
                onProgress(done.toFloat() / total)
            }
        }
    }

    /**
     * Unpacks a zip into [staging] (emptied first) and checks it against its manifest (sizes and
     * md5s). Anything that isn't a Noteapp backup file is refused, never written. Returns the
     * manifest; throws [BackupException] if the export can't be used.
     */
    fun unpack(input: InputStream, staging: File, onProgress: (Long) -> Unit = {}): BackupManifest {
        staging.deleteRecursively()
        staging.mkdirs()
        val seen = HashSet<String>()
        var bytes = 0L
        try {
            ZipInputStream(input.buffered(64 * 1024)).use { zip ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    val path = entry.name
                    if (!BackupPaths.isRestorable(path)) throw BackupException("This isn't a Noteapp export (it holds \"${path.take(60)}\").")
                    if (!seen.add(path)) throw BackupException("This export holds a file twice and can't be used.")
                    val target = File(staging, path)
                    target.parentFile?.mkdirs()
                    target.outputStream().use { out ->
                        while (true) {
                            val n = zip.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            bytes += n
                            if (bytes > MAX_BYTES) throw BackupException("This export is too big to restore.")
                        }
                    }
                    onProgress(bytes)
                }
            }
        } catch (e: BackupException) {
            throw e
        } catch (e: java.util.zip.ZipException) {
            throw BackupException("This file isn't a readable .zip export.", e)
        } catch (e: java.io.IOException) {
            if (staging.usableSpace < 32L * 1024 * 1024) throw BackupException("Not enough free space on this tablet to restore.", e)
            throw BackupException("The export couldn't be read: ${e.message}", e)
        }
        val manifestFile = File(staging, BackupPaths.MANIFEST)
        if (!manifestFile.isFile) throw BackupException("This isn't a Noteapp export (it has no file list).")
        val manifest = BackupManifest.fromJson(manifestFile.readText(Charsets.UTF_8))
        StagedSet.verify(staging, manifest)
        return manifest
    }

    /** A sanity limit against broken or hostile zips (far more than a few years of notes). */
    private const val MAX_BYTES = 64L * 1024 * 1024 * 1024
}

/** Checks a backup set unpacked or downloaded into a staging folder. */
object StagedSet {
    /**
     * Every listed file must be there with its exact size and md5, and nothing else may be there.
     * Throws [BackupException] otherwise.
     */
    fun verify(staging: File, manifest: BackupManifest) {
        val listed = manifest.files.associateBy { it.path }
        for (entry in manifest.files) {
            val file = File(staging, entry.path)
            if (!file.isFile) throw BackupException("The backup is missing a file (${entry.path.substringAfterLast('/')}).")
            if (file.length() != entry.size || Md5.of(file) != entry.md5) {
                throw BackupException("A file in the backup is damaged (${entry.path.substringAfterLast('/')}).")
            }
        }
        staging.walkTopDown().filter { it.isFile }.forEach { file ->
            val path = file.relativeTo(staging).invariantSeparatorsPath
            if (path != BackupPaths.MANIFEST && path !in listed) {
                throw BackupException("The backup holds a file its list doesn't mention.")
            }
        }
    }
}
