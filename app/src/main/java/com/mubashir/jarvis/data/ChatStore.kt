package com.mubashir.jarvis.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File

/** One turn of the conversation, as stored. */
data class StoredMessage(val fromUser: Boolean, val text: String)

/** One conversation, as it appears in the list. */
data class Conversation(
    val id: Long,
    val title: String,
    val startedAt: Long,
    val lastAt: Long,
    val messages: Int,
    /** The last thing said in it, for the list. */
    val preview: String,
)

/** Something a search turned up. */
data class ChatHit(
    val conversation: Conversation,
    /** The line that matched, trimmed to the part worth reading. */
    val snippet: String,
    val score: Int,
)

/**
 * Every conversation, kept across restarts.
 *
 * This held exactly one conversation until now — a single JSON file that each
 * new exchange appended to — so there was no history, nothing to search, and no
 * way to start fresh without destroying what came before. On an assistant that
 * remembers things about its owner, that is the wrong shape: the memory
 * outlives any one conversation and the conversations are worth keeping too.
 *
 * SQLite for the same reasons as the other stores here: no dependency this
 * project cannot resolve, and searching a few thousand short rows is what it is
 * for.
 */
class ChatStore(context: Context) {

    private val app = context.applicationContext

    private val helper = object : SQLiteOpenHelper(app, NAME, null, VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $CONVERSATIONS (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    title TEXT NOT NULL,
                    started_at INTEGER NOT NULL,
                    last_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE $MESSAGES (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    conversation_id INTEGER NOT NULL,
                    from_user INTEGER NOT NULL,
                    text TEXT NOT NULL,
                    at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX ${MESSAGES}_conv ON $MESSAGES (conversation_id)")
        }

        override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) = Unit
    }

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _openId = MutableStateFlow<Long?>(null)
    val openId: StateFlow<Long?> = _openId.asStateFlow()

    /**
     * Reads the list, taking over anything the single-file version left behind.
     *
     * Someone updating the app has a conversation in the old file and would
     * otherwise open a new build to find it gone. It becomes the first
     * conversation in the list and the file is removed once it is safely in.
     */
    suspend fun load() = withContext(Dispatchers.IO) {
        adoptOldFile()
        _conversations.value = readConversations()
        if (_openId.value == null) _openId.value = _conversations.value.firstOrNull()?.id
    }

    fun open(id: Long?) {
        _openId.value = id
    }

    suspend fun messages(id: Long?): List<StoredMessage> = withContext(Dispatchers.IO) {
        if (id == null) return@withContext emptyList()
        val rows = helper.readableDatabase.query(
            MESSAGES, arrayOf("from_user", "text"),
            "conversation_id = ?", arrayOf(id.toString()),
            null, null, "id ASC",
        )
        val found = mutableListOf<StoredMessage>()
        rows.use { cursor ->
            while (cursor.moveToNext()) {
                found += StoredMessage(cursor.getInt(0) == 1, cursor.getString(1))
            }
        }
        found
    }

    /**
     * Replaces what is in the open conversation, starting one if none is open.
     *
     * A whole-conversation write rather than an append: the caller owns the list
     * of messages and edits the last one as it streams, so appending would
     * store a hundred half-finished copies of one answer.
     */
    suspend fun save(messages: List<StoredMessage>) = withContext(Dispatchers.IO) {
        if (messages.isEmpty()) return@withContext
        val now = System.currentTimeMillis()
        val kept = messages.takeLast(MAX_MESSAGES)
        val db = helper.writableDatabase

        val id = _openId.value ?: run {
            val title = ConversationWords.title(
                kept.firstOrNull { it.fromUser }?.text.orEmpty(),
            )
            val fresh = db.insert(
                CONVERSATIONS, null,
                ContentValues().apply {
                    put("title", title)
                    put("started_at", now)
                    put("last_at", now)
                },
            )
            _openId.value = fresh
            fresh
        }

        db.beginTransaction()
        try {
            db.delete(MESSAGES, "conversation_id = ?", arrayOf(id.toString()))
            kept.forEach { message ->
                db.insert(
                    MESSAGES, null,
                    ContentValues().apply {
                        put("conversation_id", id)
                        put("from_user", if (message.fromUser) 1 else 0)
                        put("text", message.text)
                        put("at", now)
                    },
                )
            }
            db.update(
                CONVERSATIONS,
                ContentValues().apply { put("last_at", now) },
                "id = ?", arrayOf(id.toString()),
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        _conversations.value = readConversations()
    }

    /** Leaves everything where it is and starts somewhere new. */
    fun startNew() {
        _openId.value = null
    }

    suspend fun remove(id: Long) = withContext(Dispatchers.IO) {
        val db = helper.writableDatabase
        db.delete(MESSAGES, "conversation_id = ?", arrayOf(id.toString()))
        db.delete(CONVERSATIONS, "id = ?", arrayOf(id.toString()))
        if (_openId.value == id) _openId.value = null
        _conversations.value = readConversations()
    }

    suspend fun clearEverything() = withContext(Dispatchers.IO) {
        val db = helper.writableDatabase
        db.delete(MESSAGES, null, null)
        db.delete(CONVERSATIONS, null, null)
        _openId.value = null
        _conversations.value = emptyList()
    }

    /**
     * Searches titles and everything ever said.
     *
     * One row per conversation, not one per matching message: ten hits inside
     * one long conversation is one result, and showing it ten times buries
     * everything else.
     */
    suspend fun search(query: String): List<ChatHit> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        val all = readConversations()
        all.mapNotNull { conversation ->
            val said = messagesTextOf(conversation.id)
            val inTitle = ConversationWords.matches(query, conversation.title)
            val inBody = ConversationWords.matches(query, said)
            if (!inTitle && !inBody) return@mapNotNull null

            ChatHit(
                conversation = conversation,
                snippet = ConversationWords.snippet(
                    if (inBody) said else conversation.title,
                    query,
                ),
                score = ConversationWords.score(query, conversation.title, said),
            )
        }.sortedWith(
            compareByDescending<ChatHit> { it.score }
                .thenByDescending { it.conversation.lastAt },
        )
    }

    private fun messagesTextOf(id: Long): String {
        val rows = helper.readableDatabase.query(
            MESSAGES, arrayOf("text"),
            "conversation_id = ?", arrayOf(id.toString()),
            null, null, "id ASC",
        )
        return rows.use { cursor ->
            buildString {
                while (cursor.moveToNext()) {
                    if (isNotEmpty()) append(' ')
                    append(cursor.getString(0))
                }
            }
        }
    }

    private fun readConversations(): List<Conversation> {
        val rows = helper.readableDatabase.rawQuery(
            """
            SELECT c.id, c.title, c.started_at, c.last_at,
                   (SELECT COUNT(*) FROM $MESSAGES m WHERE m.conversation_id = c.id),
                   (SELECT m.text FROM $MESSAGES m WHERE m.conversation_id = c.id
                    ORDER BY m.id DESC LIMIT 1)
            FROM $CONVERSATIONS c ORDER BY c.last_at DESC
            """.trimIndent(),
            null,
        )
        val found = mutableListOf<Conversation>()
        rows.use { cursor ->
            while (cursor.moveToNext()) {
                found += Conversation(
                    id = cursor.getLong(0),
                    title = cursor.getString(1),
                    startedAt = cursor.getLong(2),
                    lastAt = cursor.getLong(3),
                    messages = cursor.getInt(4),
                    preview = if (cursor.isNull(5)) "" else cursor.getString(5),
                )
            }
        }
        return found
    }

    /** Moves the old single-file conversation in, once, then deletes the file. */
    private fun adoptOldFile() {
        val old = File(app.filesDir, OLD_FILE)
        if (!old.exists()) return

        runCatching {
            val array = JSONArray(old.readText())
            val messages = buildList {
                for (i in 0 until array.length()) {
                    val row = array.getJSONObject(i)
                    add(StoredMessage(row.getBoolean("user"), row.getString("text")))
                }
            }
            if (messages.isNotEmpty()) {
                val now = System.currentTimeMillis()
                val db = helper.writableDatabase
                val id = db.insert(
                    CONVERSATIONS, null,
                    ContentValues().apply {
                        put("title", ConversationWords.title(
                            messages.firstOrNull { it.fromUser }?.text.orEmpty(),
                        ))
                        put("started_at", now)
                        put("last_at", now)
                    },
                )
                messages.forEach { message ->
                    db.insert(
                        MESSAGES, null,
                        ContentValues().apply {
                            put("conversation_id", id)
                            put("from_user", if (message.fromUser) 1 else 0)
                            put("text", message.text)
                            put("at", now)
                        },
                    )
                }
            }
        }
        // Deleted whether or not it parsed. A file that cannot be read will not
        // read any better next time, and leaving it means trying on every load.
        old.delete()
    }

    private companion object {
        const val NAME = "chats.db"
        const val VERSION = 1
        const val CONVERSATIONS = "conversations"
        const val MESSAGES = "messages"
        const val OLD_FILE = "chat.json"
        const val MAX_MESSAGES = 200
    }
}
