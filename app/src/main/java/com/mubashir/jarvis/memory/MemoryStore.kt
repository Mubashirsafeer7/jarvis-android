package com.mubashir.jarvis.memory

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * What Jarvis knows, on disk.
 *
 * SQLite directly rather than Room. Room's compiler is a dependency this
 * project cannot resolve from where it is written, and the whole of this is one
 * table with four queries — the annotation processor would be more machinery
 * than the thing it generates.
 *
 * Kept out of the chat history file on purpose. Clearing the conversation
 * should not erase what Jarvis knows about its owner, and losing the database
 * should not lose the conversation.
 */
class MemoryStore(context: Context) {

    private val helper = object : SQLiteOpenHelper(
        context.applicationContext, NAME, null, VERSION,
    ) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $TABLE (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    text TEXT NOT NULL,
                    topic TEXT NOT NULL,
                    source TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    pinned INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX ${TABLE}_topic ON $TABLE (topic)")
        }

        override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
            // Nothing to migrate yet. When there is, it is written here rather
            // than by dropping the table — this is the one store in the app
            // whose contents cannot be downloaded again.
        }
    }

    private val _facts = MutableStateFlow<List<Fact>>(emptyList())

    /** Everything known, newest first. Kept in memory because it is read constantly. */
    val facts: StateFlow<List<Fact>> = _facts.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        _facts.value = readAll()
    }

    /**
     * Writes a fact down, replacing anything it supersedes.
     *
     * @return the fact as stored, or null when there was nothing worth storing
     */
    suspend fun remember(
        text: String,
        source: FactSource,
        pinned: Boolean = false,
    ): Fact? = withContext(Dispatchers.IO) {
        val cleaned = text.trim().trim('.', ',', '!', ' ')
        if (cleaned.length < 3) return@withContext null

        val topic = MemoryRules.topicOf(cleaned)
        if (topic.isEmpty()) return@withContext null

        val fact = Fact(
            text = cleaned,
            topic = topic,
            source = source,
            createdAt = System.currentTimeMillis(),
            pinned = pinned,
        )

        // Not .use {}. SQLiteOpenHelper hands out one shared connection and
        // holds it for the life of the process; closing it here would leave
        // every later call reopening a closed object, which throws.
        val db = helper.writableDatabase
        run {
            db.beginTransaction()
            try {
                // A newer fact about the same thing replaces the older one, so
                // the model is never handed two answers to one question. A fact
                // the user pinned is not overwritten by something merely
                // noticed.
                readAll(db)
                    .filter { MemoryRules.replaces(fact, it) }
                    .filterNot { it.pinned && source == FactSource.Noticed }
                    .forEach { db.delete(TABLE, "id = ?", arrayOf(it.id.toString())) }

                db.insert(TABLE, null, fact.asRow())
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
        load()
        _facts.value.firstOrNull { it.text.equals(cleaned, ignoreCase = true) } ?: fact
    }

    /**
     * Drops everything known about something.
     *
     * @return how many facts went
     */
    suspend fun forget(about: String): Int = withContext(Dispatchers.IO) {
        val wanted = MemoryRules.keywordsOf(about)
        if (wanted.isEmpty()) return@withContext 0

        val doomed = _facts.value.filter { fact ->
            MemoryRules.keywordsOf(fact.text).intersect(wanted).isNotEmpty() ||
                MemoryRules.keywordsOf(fact.topic).intersect(wanted).isNotEmpty()
        }
        if (doomed.isEmpty()) return@withContext 0

        val db = helper.writableDatabase
        doomed.forEach { db.delete(TABLE, "id = ?", arrayOf(it.id.toString())) }
        load()
        doomed.size
    }

    suspend fun forgetOne(id: Long) = withContext(Dispatchers.IO) {
        helper.writableDatabase.delete(TABLE, "id = ?", arrayOf(id.toString()))
        load()
    }

    /** Everything, gone. Only ever from an explicit tap in Settings. */
    suspend fun forgetEverything() = withContext(Dispatchers.IO) {
        helper.writableDatabase.delete(TABLE, null, null)
        load()
    }

    private fun readAll(existing: SQLiteDatabase? = null): List<Fact> {
        val db = existing ?: helper.readableDatabase
        val rows = db.query(
            TABLE,
            arrayOf("id", "text", "topic", "source", "created_at", "pinned"),
            null, null, null, null,
            "pinned DESC, created_at DESC",
        )
        val found = mutableListOf<Fact>()
        rows.use { cursor ->
            while (cursor.moveToNext()) {
                found += Fact(
                    id = cursor.getLong(0),
                    text = cursor.getString(1),
                    topic = cursor.getString(2),
                    source = runCatching { FactSource.valueOf(cursor.getString(3)) }
                        .getOrDefault(FactSource.Noticed),
                    createdAt = cursor.getLong(4),
                    pinned = cursor.getInt(5) == 1,
                )
            }
        }
        return found
    }

    private fun Fact.asRow() = ContentValues().apply {
        put("text", text)
        put("topic", topic)
        put("source", source.name)
        put("created_at", createdAt)
        put("pinned", if (pinned) 1 else 0)
    }

    private companion object {
        const val NAME = "memory.db"
        const val VERSION = 1
        const val TABLE = "facts"
    }
}
