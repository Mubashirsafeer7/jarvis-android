package com.mubashir.jarvis.voice

/**
 * Pulls whole sentences out of a stream of tokens so speech can start on the
 * first one.
 *
 * Waiting for the full answer meant half a minute of silence on a 3B model
 * before Jarvis said anything. Speaking sentence by sentence starts in about a
 * second, and the queue keeps up with generation from there.
 *
 * A terminator only ends a sentence when whitespace follows it, which keeps
 * "3.5 GB" and "e.g." in one piece, and a minimum length stops "Hi." from
 * being shipped off on its own.
 */
class SentenceSplitter(private val minLength: Int = MIN_LENGTH) {

    private val buffer = StringBuilder()

    /** Adds streamed text and returns any sentences that are now complete. */
    fun accept(chunk: String): List<String> {
        buffer.append(chunk)
        val ready = mutableListOf<String>()

        while (true) {
            val cut = nextSentenceEnd() ?: break
            val sentence = buffer.substring(0, cut).trim()
            buffer.delete(0, cut)
            if (sentence.isNotEmpty()) ready += sentence
        }
        return ready
    }

    /** Whatever is left when generation ends — usually the last sentence. */
    fun flush(): String? = buffer.toString().trim()
        .takeIf { it.isNotEmpty() }
        .also { buffer.setLength(0) }

    private fun nextSentenceEnd(): Int? {
        for (i in buffer.indices) {
            if (buffer[i] !in TERMINATORS) continue
            // Needs whitespace after it, or we cannot tell a full stop from a
            // decimal point yet — wait for more text.
            val next = buffer.getOrNull(i + 1) ?: return null
            if (!next.isWhitespace()) continue
            if (i + 1 < minLength) continue
            return i + 1
        }
        return null
    }

    private companion object {
        const val MIN_LENGTH = 12
        val TERMINATORS = charArrayOf('.', '?', '!', '।') // '।' ends Devanagari
    }
}
