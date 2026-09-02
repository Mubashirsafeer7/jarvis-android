package com.mubashir.jarvis.sense

import java.util.Locale

/** One thing that arrived in the shade. */
data class Notice(
    val app: String,
    val title: String,
    val text: String,
    val postedAt: Long,
    /** A permanent one — a running download, a music player, a foreground service. */
    val ongoing: Boolean = false,
    /** The header Android puts above a stack of notifications from one app. */
    val groupSummary: Boolean = false,
    /** Android's own id for this one, so a dismissal can remove the right row. */
    val key: String = "",
)

/**
 * What arrived, and which of it is worth saying.
 *
 * Most of what lands in the shade is not news. A music player, a running
 * download and a VPN each keep a permanent notification that says the same
 * thing all day; Android adds a summary header above every stack; and an app
 * that updates a progress bar posts the same notification forty times. Reading
 * all of that back is not an answer to "what did I miss", it is a list of what
 * the phone is currently doing.
 *
 * Pure, so the judgement — what to drop, what counts as the same thing twice,
 * how many is too many — can be tested without a phone full of apps.
 */
object NoticeWords {

    /** Beyond this it stops being an answer. The rest are counted. */
    const val SPOKEN_LIMIT = 5

    /** Older than this is not "what did I miss", it is history. */
    const val RECENT_MINUTES = 120L

    /**
     * Drops the noise and the repeats, newest first.
     *
     * @param now epoch millis, passed in rather than read, so this can be tested
     */
    fun worthSaying(notices: List<Notice>, now: Long): List<Notice> {
        val cutoff = now - RECENT_MINUTES * 60_000
        return notices
            .asSequence()
            .filterNot { it.ongoing }
            .filterNot { it.groupSummary }
            .filter { it.postedAt >= cutoff }
            .filter { it.title.isNotBlank() || it.text.isNotBlank() }
            .sortedByDescending { it.postedAt }
            // An app that updates one notification in place posts it many
            // times. Only the newest of each is news; the rest are the same
            // notification changing its mind.
            .distinctBy { it.app.lowercase(Locale.ROOT) to it.title.lowercase(Locale.ROOT) }
            .toList()
    }

    fun describe(notices: List<Notice>, now: Long): String {
        val worth = worthSaying(notices, now)
        if (worth.isEmpty()) return "Nothing new."

        val named = worth.take(SPOKEN_LIMIT).joinToString(". ") { line(it) }
        val rest = worth.size - SPOKEN_LIMIT
        return if (rest > 0) "$named. And $rest more." else "$named."
    }

    private fun line(notice: Notice): String {
        val who = notice.app.trim()
        val what = listOf(notice.title, notice.text)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            // Apps very often set the title and the text to the same string.
            .distinctBy { it.lowercase(Locale.ROOT) }
            .joinToString(": ")
        return if (who.isEmpty()) what else "$who, $what"
    }
}
