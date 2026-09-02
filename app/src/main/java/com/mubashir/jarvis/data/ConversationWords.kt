package com.mubashir.jarvis.data

import java.util.Locale

/**
 * Naming a conversation, and finding one again.
 *
 * Both are pure because both are judgement rather than storage, and both are
 * the kind of thing that is only obviously wrong once there are two hundred
 * conversations and the list reads as a wall of "mera bhai Ali hai yaad rakh…".
 */
object ConversationWords {

    /** Longer than this and the list stops being scannable. */
    const val TITLE_LIMIT = 42

    /** Characters either side of a match, so a result reads as a sentence. */
    const val SNIPPET_PADDING = 34

    /**
     * A conversation's name, taken from the first thing said in it.
     *
     * No model call. Asking a 3B model to title a conversation costs a
     * generation for every new chat and comes back with "Certainly! Here is a
     * title:" often enough to matter. The first line of what the user actually
     * said is both cheaper and more recognisable to them.
     */
    fun title(firstMessage: String): String {
        val cleaned = firstMessage
            .replace(Regex("""\s+"""), " ")
            .trim()
            .trim('.', ',', '!', '?', ':', ';', '-', '"', '\'')
            .trim()

        if (cleaned.isEmpty()) return "New conversation"
        if (cleaned.length <= TITLE_LIMIT) return cleaned.capitalised()

        // Cut at a word, not mid-word. A title ending in "sched" reads as
        // damage rather than as an abbreviation.
        val cut = cleaned.take(TITLE_LIMIT + 1)
        val lastSpace = cut.lastIndexOf(' ')
        val kept = if (lastSpace > TITLE_LIMIT / 2) cut.take(lastSpace) else cleaned.take(TITLE_LIMIT)
        return kept.trim().capitalised() + "…"
    }

    private fun String.capitalised(): String =
        replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }

    /**
     * Whether a search finds this text.
     *
     * Every word has to appear somewhere, in any order — so "ali call" finds
     * "calling Ali about the review". Substring rather than whole-word, because
     * a search box is used while still typing and "sched" should find
     * "schedule" before the word is finished.
     */
    fun matches(query: String, text: String): Boolean {
        val wanted = words(query)
        if (wanted.isEmpty()) return false
        val haystack = text.lowercase(Locale.ROOT)
        return wanted.all { haystack.contains(it) }
    }

    /**
     * The part of a long message worth showing as a result.
     *
     * A result that starts at the beginning of a four hundred word answer shows
     * the user nothing about why it matched.
     */
    fun snippet(text: String, query: String, padding: Int = SNIPPET_PADDING): String {
        val flat = text.replace(Regex("""\s+"""), " ").trim()
        val first = words(query).firstOrNull() ?: return flat.take(padding * 2)
        val at = flat.lowercase(Locale.ROOT).indexOf(first)
        if (at < 0) return flat.take(padding * 2)

        val from = (at - padding).coerceAtLeast(0)
        val to = (at + first.length + padding).coerceAtMost(flat.length)
        return buildString {
            if (from > 0) append("…")
            append(flat.substring(from, to).trim())
            if (to < flat.length) append("…")
        }
    }

    /**
     * Ranks results so the useful ones are at the top.
     *
     * A title match beats a body match: someone searching for a conversation is
     * usually looking for the conversation, not for every time a word was said
     * inside one.
     */
    fun score(query: String, title: String, body: String): Int {
        val wanted = words(query)
        if (wanted.isEmpty()) return 0
        val loweredTitle = title.lowercase(Locale.ROOT)
        val loweredBody = body.lowercase(Locale.ROOT)

        var score = 0
        wanted.forEach { word ->
            if (loweredTitle.contains(word)) score += 10
            if (loweredBody.contains(word)) score += 3
        }
        // The whole phrase, in order, is what the user most likely meant.
        val phrase = query.trim().lowercase(Locale.ROOT)
        if (loweredTitle.contains(phrase)) score += 20
        return score
    }

    private fun words(query: String): List<String> = query
        .lowercase(Locale.ROOT)
        .split(' ', '\n', '\t')
        .map { it.trim { c -> !c.isLetterOrDigit() } }
        .filter { it.isNotEmpty() }
}
