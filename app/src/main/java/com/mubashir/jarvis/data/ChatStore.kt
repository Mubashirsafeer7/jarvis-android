package com.mubashir.jarvis.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One turn of the conversation, as stored. */
data class StoredMessage(val fromUser: Boolean, val text: String)

/**
 * Keeps the conversation across restarts.
 *
 * The app holds gigabytes, so Android kills it readily — and every kill threw
 * away the whole conversation. Loading a model wiped it too, which meant simply
 * looking at the model list and coming back cost the user everything they had
 * said.
 *
 * Written with org.json and a plain file: no new dependency, and the volume here
 * is a few hundred short strings.
 */
class ChatStore(context: Context) {

    private val file = File(context.applicationContext.filesDir, FILE_NAME)

    fun load(): List<StoredMessage> = runCatching {
        if (!file.exists()) return emptyList()
        val array = JSONArray(file.readText())
        buildList {
            for (i in 0 until array.length()) {
                val row = array.getJSONObject(i)
                add(StoredMessage(row.getBoolean(KEY_USER), row.getString(KEY_TEXT)))
            }
        }
    }.getOrElse {
        // A corrupt history is not worth failing to start over.
        file.delete()
        emptyList()
    }

    fun save(messages: List<StoredMessage>) {
        runCatching {
            // Only the tail is worth keeping, and an unbounded file would grow
            // for the life of the install.
            val kept = messages.takeLast(MAX_MESSAGES)
            val array = JSONArray()
            kept.forEach { message ->
                array.put(
                    JSONObject()
                        .put(KEY_USER, message.fromUser)
                        .put(KEY_TEXT, message.text),
                )
            }
            file.writeText(array.toString())
        }
    }

    fun clear() {
        file.delete()
    }

    private companion object {
        const val FILE_NAME = "chat.json"
        const val KEY_USER = "user"
        const val KEY_TEXT = "text"
        const val MAX_MESSAGES = 200
    }
}
