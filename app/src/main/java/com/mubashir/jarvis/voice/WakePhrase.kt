package com.mubashir.jarvis.voice

import java.util.Locale

/**
 * Decides whether the name was actually said.
 *
 * The recogniser is given a grammar of exactly two things — the wake word and a
 * catch-all for everything else — so it can only ever answer with one or the
 * other. That makes the match strict on purpose: anything looser trades a wake
 * word that sometimes misses for one that fires while you are talking to
 * somebody else, and a microphone that opens itself is the worse failure.
 *
 * Pure logic, because the two ways this goes wrong — never waking, or waking
 * constantly — are both miserable to debug on a phone.
 */
object WakePhrase {

    /** What the recogniser is allowed to return. Anything else becomes [UNKNOWN]. */
    const val WORD = "jarvis"

    /** Vosk's own token for speech that is not in the grammar. */
    const val UNKNOWN = "[unk]"

    /** The grammar handed to the recogniser, as the JSON array it expects. */
    val GRAMMAR: String = """["$WORD", "$UNKNOWN"]"""

    /**
     * @param hypothesis what the recogniser heard, as plain text
     * @return true when the wake word is present as a whole word
     */
    fun heard(hypothesis: String?): Boolean {
        if (hypothesis.isNullOrBlank()) return false
        return hypothesis
            .lowercase(Locale.ROOT)
            .split(' ', '\n', '\t')
            .any { token -> token.trim { it in TRIM } == WORD }
    }

    private val TRIM = charArrayOf('.', ',', '!', '?', '"', '\'').toSet()
}
