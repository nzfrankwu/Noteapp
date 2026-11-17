package com.nzfrankwu.noteapp.data.backup

import java.io.File

/**
 * Remembers the md5 of files that are written once and never changed (imported PDFs, pictures,
 * bookmark pictures), so a backup doesn't read a 50 MB lecture PDF again every time (Phase 7, 7.2).
 *
 * An entry is used only while the file's path, size and last-modified time are all the same as
 * when it was hashed; anything else is hashed again. The cache is a small text file in the
 * app's cache folder: if Android clears it, everything is simply hashed again.
 */
class Md5Cache(private val file: File) {
    private data class Key(val path: String, val size: Long, val modified: Long)

    private val entries = HashMap<Key, String>()
    private var loaded = false
    private var changed = false

    /** The md5 of [source] (stored as [path]), from the cache when it can be trusted. */
    @Synchronized
    fun md5(path: String, source: File): String {
        load()
        val key = Key(path, source.length(), source.lastModified())
        entries[key]?.let { return it }
        val md5 = Md5.of(source)
        // A file that changed while it was read isn't remembered (it'll be hashed again next time).
        if (source.length() == key.size && source.lastModified() == key.modified) {
            entries.keys.removeAll { it.path == path }
            entries[key] = md5
            changed = true
        }
        return md5
    }

    /** Forgets every file not in [paths] and writes the cache out. */
    @Synchronized
    fun save(paths: Set<String>) {
        load()
        if (entries.keys.removeAll { it.path !in paths }) changed = true
        if (!changed) return
        runCatching {
            file.parentFile?.mkdirs()
            val temp = File(file.path + ".writing")
            temp.writeText(
                entries.entries.joinToString("") { (k, md5) -> "${k.size}\t${k.modified}\t$md5\t${k.path}\n" },
            )
            if (!temp.renameTo(file)) {
                file.delete()
                temp.renameTo(file)
            }
            changed = false
        }
    }

    private fun load() {
        if (loaded) return
        loaded = true
        runCatching {
            if (!file.isFile) return
            file.forEachLine { line ->
                val parts = line.split('\t', limit = 4)
                if (parts.size != 4) return@forEachLine
                val size = parts[0].toLongOrNull() ?: return@forEachLine
                val modified = parts[1].toLongOrNull() ?: return@forEachLine
                if (!Md5.isValid(parts[2])) return@forEachLine
                entries[Key(parts[3], size, modified)] = parts[2]
            }
        }.onFailure { entries.clear() }
    }
}
