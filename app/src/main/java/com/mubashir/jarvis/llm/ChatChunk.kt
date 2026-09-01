package com.mubashir.jarvis.llm

import org.json.JSONObject

/**
 * Reads one line of a streamed chat completion.
 *
 * Every server worth pointing at — llama.cpp's own, Ollama, LM Studio, vLLM —
 * speaks the OpenAI streaming format, so this one parser reaches all of them
 * without a client library that could not be checked before shipping.
 *
 * Kept apart from the networking so it can be tested: a stream parser that
 * silently drops content is the kind of bug that looks like a slow model.
 */
object ChatChunk {

    /** The sentinel a server sends to say the answer is over. */
    const val DONE = "[DONE]"

    /**
     * @return the text this line carries, "" for a line that carries none
     * (keep-alives and role-only first chunks are normal), or null when the
     * stream has ended.
     */
    fun textOf(line: String): String? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return ""
        if (!trimmed.startsWith("data:")) return ""

        val payload = trimmed.removePrefix("data:").trim()
        if (payload.isEmpty()) return ""
        if (payload == DONE) return null

        return runCatching {
            val choices = JSONObject(payload).optJSONArray("choices") ?: return ""
            val first = choices.optJSONObject(0) ?: return ""
            // Streaming puts it under "delta"; a server answering in one shot
            // puts the whole thing under "message".
            val delta = first.optJSONObject("delta") ?: first.optJSONObject("message")
            delta?.optString("content").orEmpty()
        }.getOrDefault("")
    }
}
