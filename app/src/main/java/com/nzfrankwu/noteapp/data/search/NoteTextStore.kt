package com.nzfrankwu.noteapp.data.search

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * The words in your notes, page by page, for search (PHASE-7.md 7.9–7.10): the text of imported
 * PDF pages, of text boxes, and of handwriting (recognised on the tablet, line by line, with
 * each line's band on the page so a search can zoom to it).
 *
 * It's a CACHE, rebuilt from the notes themselves by [NoteTextIndexer]: its own small database
 * (`databases/search.db`), separate from Noteapp's real one, never backed up (backups take only
 * `noteapp.db` and the notebook files). Deleting it loses nothing; the indexer fills it again.
 * That's also why a new version of this table simply starts empty (onUpgrade) instead of
 * migrating, which the real database must never do (IDEA.md §5a).
 */
class NoteTextStore(context: Context, name: String? = FILE_NAME) :
    SQLiteOpenHelper(context.applicationContext, name, null, VERSION) {

    /** What a page's text was made from, so unchanged pages aren't read again. */
    data class Keys(val pdfKey: String?, val boxKey: String?, val handKey: String? = null)

    data class Row(
        val pageId: String,
        val notebookId: String,
        val pdfText: String?,
        val boxText: String?,
        val pdfKey: String?,
        val boxKey: String?,
    )

    /** A page whose text has every word searched for. */
    data class Hit(
        val pageId: String,
        val notebookId: String,
        val pdfText: String?,
        val boxText: String?,
        val handText: String? = null,
        /** Each handwriting line's band, "top,bottom;top,bottom…", in the same order as [handText]'s lines. */
        val handLines: String? = null,
    )

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE page_text (page_id TEXT PRIMARY KEY NOT NULL, notebook_id TEXT NOT NULL, " +
                "pdf_text TEXT, box_text TEXT, pdf_key TEXT, box_key TEXT, hand_text TEXT, hand_lines TEXT, hand_key TEXT)",
        )
        db.execSQL("CREATE INDEX page_text_notebook ON page_text (notebook_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = rebuild(db)

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = rebuild(db)

    /** Only for this cache: start empty, the indexer reads everything again. */
    private fun rebuild(db: SQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS page_text")
        onCreate(db)
    }

    @Synchronized
    fun keys(): Map<String, Keys> = readableDatabase.rawQuery("SELECT page_id, pdf_key, box_key, hand_key FROM page_text", null).use { c ->
        buildMap { while (c.moveToNext()) put(c.getString(0), Keys(c.getString(1), c.getString(2), c.getString(3))) }
    }

    @Synchronized
    fun put(rows: List<Row>) {
        if (rows.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (r in rows) {
                // Keeps the page's handwriting columns (they're read separately, more slowly).
                upsert(
                    db, r.pageId, r.notebookId,
                    ContentValues().apply {
                        put("pdf_text", r.pdfText)
                        put("box_text", r.boxText)
                        put("pdf_key", r.pdfKey)
                        put("box_key", r.boxKey)
                    },
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** A page's handwriting: its lines' text, their bands ("top,bottom" each) and the key it was read from. */
    @Synchronized
    fun putHandwriting(pageId: String, notebookId: String, lines: List<Pair<String, Pair<Float, Float>>>, key: String) {
        upsert(
            writableDatabase, pageId, notebookId,
            ContentValues().apply {
                put("hand_text", lines.joinToString("\n") { it.first }.ifEmpty { null })
                put("hand_lines", lines.joinToString(";") { "${it.second.first},${it.second.second}" }.ifEmpty { null })
                put("hand_key", key)
            },
        )
    }

    private fun upsert(db: SQLiteDatabase, pageId: String, notebookId: String, values: ContentValues) {
        values.put("notebook_id", notebookId)
        if (db.update("page_text", values, "page_id = ?", arrayOf(pageId)) == 0) {
            values.put("page_id", pageId)
            db.insert("page_text", null, values)
        }
    }

    @Synchronized
    fun delete(pageIds: Collection<String>) {
        if (pageIds.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (id in pageIds) db.delete("page_text", "page_id = ?", arrayOf(id))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Pages whose PDF text and text boxes together hold every one of [words] (any case), in
     * [notebookId] only when given. At most [limit].
     */
    @Synchronized
    fun search(words: List<String>, notebookId: String? = null, limit: Int = 100): List<Hit> {
        if (words.isEmpty()) return emptyList()
        val where = ArrayList<String>()
        val args = ArrayList<String>()
        for (w in words) {
            where += "(COALESCE(pdf_text, '') || ' ' || COALESCE(box_text, '') || ' ' || COALESCE(hand_text, '')) LIKE ? ESCAPE '\\'"
            args += "%" + w.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
        }
        if (notebookId != null) {
            where += "notebook_id = ?"
            args += notebookId
        }
        // Pages with the words together ("slide 3") first, so the limit never cuts those.
        val text = "(COALESCE(pdf_text, '') || ' ' || COALESCE(box_text, '') || ' ' || COALESCE(hand_text, ''))"
        val order = if (words.size > 1) {
            args += "%" + words.joinToString(" ") { it.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") } + "%"
            "ORDER BY CASE WHEN $text LIKE ? ESCAPE '\\' THEN 0 ELSE 1 END"
        } else {
            ""
        }
        val sql = "SELECT page_id, notebook_id, pdf_text, box_text, hand_text, hand_lines FROM page_text " +
            "WHERE ${where.joinToString(" AND ")} $order LIMIT $limit"
        return readableDatabase.rawQuery(sql, args.toTypedArray()).use { c ->
            buildList {
                while (c.moveToNext()) add(Hit(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getString(5)))
            }
        }
    }

    companion object {
        const val FILE_NAME = "search.db"
        /** Bump to start the cache again (e.g. when what's stored changes). */
        const val VERSION = 2 // 2: handwriting (7.10)
    }
}
