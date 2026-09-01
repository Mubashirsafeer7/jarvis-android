package com.mubashir.jarvis.tools

import java.util.Locale

/**
 * Finds the app someone meant from what they said.
 *
 * Speech recognition mangles app names constantly — "whatsapp" comes back as
 * "whats app", "YouTube" as "you tube" — so an exact match would fail on most
 * of the times it matters. Kept as pure logic so the matching rules can be
 * tested without a package manager.
 */
object AppMatcher {

    /**
     * @param query what the user said
     * @param labels the launchable app labels on the phone
     * @return the label to launch, or null when nothing is a confident match.
     * Null matters: launching the wrong app is worse than saying "which one?".
     */
    fun bestMatch(query: String, labels: List<String>): String? {
        val wanted = normalise(query)
        if (wanted.isEmpty()) return null

        val scored = labels.mapNotNull { label ->
            score(wanted, normalise(label))?.let { label to it }
        }
        if (scored.isEmpty()) return null

        val best = scored.maxByOrNull { it.second } ?: return null
        // Two apps equally good is a question, not a guess.
        val tied = scored.count { it.second == best.second }
        return if (tied > 1) null else best.first
    }

    /** Lower case, letters and digits only — spaces and punctuation carry no meaning here. */
    private fun normalise(text: String): String =
        text.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

    private fun score(wanted: String, label: String): Int? = when {
        label.isEmpty() -> null
        label == wanted -> 100
        label.startsWith(wanted) -> 80 - (label.length - wanted.length).coerceAtMost(20)
        wanted.startsWith(label) -> 70 - (wanted.length - label.length).coerceAtMost(20)
        // A three-letter query inside a long name is usually a coincidence.
        wanted.length >= 4 && label.contains(wanted) -> 50
        else -> null
    }
}
