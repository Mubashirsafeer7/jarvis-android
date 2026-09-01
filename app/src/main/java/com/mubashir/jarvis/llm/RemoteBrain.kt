package com.mubashir.jarvis.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/**
 * A model running somewhere else on the network — the machine at home.
 *
 * Speaks the OpenAI chat-completions format, which llama.cpp's own server,
 * Ollama, LM Studio and vLLM all serve, so one client reaches whichever gets
 * set up without another dependency.
 *
 * This is not a step away from the original rule. "No API" meant no rate limit,
 * no subscription and nobody else holding the thing — a machine in the house
 * over a private network is all three, and it is the only way the phone gets to
 * ask a model far larger than it could ever hold.
 */
class RemoteBrain(
    private val baseUrl: () -> String,
    private val model: () -> String,
    private val systemPrompt: String,
) : Brain {

    override val label: String
        get() = model().ifBlank { "Server" }

    override suspend fun isReady(): Boolean = baseUrl().isNotBlank()

    override suspend fun check(): Result<String> = runCatching {
        val url = URL(endpoint("/v1/models"))
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = CONNECT_TIMEOUT_MS
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) error("The server answered HTTP $code.")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val names = JSONObject(body).optJSONArray("data")?.let { array ->
                (0 until array.length()).mapNotNull { array.optJSONObject(it)?.optString("id") }
            }.orEmpty()
            if (names.isEmpty()) "Reachable" else names.joinToString(", ")
        } finally {
            connection.disconnect()
        }
    }

    override fun ask(prompt: String, predictLength: Int): Flow<String> = flow {
        val payload = JSONObject()
            .put("model", model())
            .put("stream", true)
            .put("max_tokens", predictLength)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", systemPrompt))
                    .put(JSONObject().put("role", "user").put("content", prompt)),
            )
            .toString()

        val connection = (URL(endpoint("/v1/chat/completions")).openConnection()
            as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = CONNECT_TIMEOUT_MS
            // Generous: a large model on a home machine can take a while to
            // produce its first token, and a timeout here reads as a crash.
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "text/event-stream")
        }

        try {
            connection.outputStream.use { it.write(payload.toByteArray()) }
            val code = connection.responseCode
            if (code !in 200..299) {
                val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }
                error("The server answered HTTP $code. ${detail.orEmpty()}".trim())
            }

            connection.inputStream.bufferedReader().use { reader ->
                while (true) {
                    coroutineContext.ensureActive()
                    val line = reader.readLine() ?: break
                    val text = ChatChunk.textOf(line) ?: break
                    if (text.isNotEmpty()) emit(text)
                }
            }
        } finally {
            connection.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    private fun endpoint(path: String): String {
        val base = baseUrl().trim().trimEnd('/')
        // Both "http://192.168.1.5:8080" and ".../v1" are things people paste.
        val root = if (base.endsWith("/v1")) base.dropLast(3) else base
        return "$root$path"
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 8_000
        const val READ_TIMEOUT_MS = 180_000
    }
}
