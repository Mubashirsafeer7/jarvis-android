package com.mubashir.jarvis.memory

import java.util.Locale

/**
 * Picks personal facts out of what the user just said, without asking a model.
 *
 * The obvious design is to hand the conversation to the model and ask it what
 * is worth remembering. On a phone-sized model that is the wrong trade twice
 * over: it doubles the cost of every message, and a 3B model asked to extract
 * facts invents them often enough that memory would fill with things its owner
 * never said. Wrong memories are worse than no memory, because they are
 * repeated back with confidence.
 *
 * So this handles the phrasings people actually use to state a fact about
 * themselves, in both the languages this phone's owner writes, and stays quiet
 * about everything else. It is deliberately narrow: a missed fact costs nothing
 * — the user can always say "remember this" — while a false one is repeated
 * back for months.
 *
 * A model-based pass makes sense when the answering brain is a real one on a
 * machine with room for it. Not here.
 */
object MemoryNoticer {

    /** How the user refers to themselves, rewritten so a fact reads on its own. */
    private const val OWNER = "The user"

    /**
     * @return facts worth writing down, usually none
     */
    fun notice(said: String): List<String> {
        val text = said.lowercase(Locale.ROOT)
            .replace(Regex("""\s+"""), " ")
            .trim()
            .trimEnd('.', '!', ' ')

        // A question about a fact is not a statement of one. "what is my
        // brother's name" contains "my brother" and would otherwise be filed
        // as though the user had just announced it.
        if (text.isEmpty() || said.trimEnd().endsWith("?")) return emptyList()
        if (isAsking(text)) return emptyList()

        // The first pattern that matches wins, rather than every pattern that
        // does. "My name is Mubashir" matches both the name rule and the
        // general "my X is Y" rule, and storing both files the same fact twice
        // in two different shapes — which then look like two facts that
        // disagree.
        val found = PATTERNS.firstNotNullOfOrNull { (pattern, render) ->
            pattern.matchEntire(text)?.let { match ->
                val parts = match.groupValues.drop(1).map { it.trim() }
                when {
                    parts.any { it.isEmpty() || it.length > 60 } -> null
                    // A run-on sentence matches "my X is Y" with everything
                    // after the verb swallowed into Y, and files one long
                    // nonsense fact. Better to remember nothing than to record
                    // the first clause and silently invent the rest.
                    parts.any { tooMuch(it) } -> null
                    // "mera bhai kaun hai" fits "mera X Y hai" perfectly and
                    // would be filed as though the user had announced that
                    // their brother is named Kaun.
                    parts.any { it in QUESTION_WORDS } -> null
                    else -> render(parts)
                }
            }
        }
        return listOfNotNull(found)
    }

    /**
     * Whether this is asking rather than telling.
     *
     * English puts its question word first, so anchoring is right there and
     * keeps "I like what you did" out of it. Urdu and Hindi put theirs in the
     * middle — "mera bhai kaun hai" — so those have to be caught anywhere, and
     * they are near enough unused in a plain statement for that to be safe.
     */
    private fun isAsking(text: String): Boolean {
        if (QUESTION.containsMatchIn(text)) return true
        return text.split(' ').any { it.trim('?', ',', '.') in QUESTION_WORDS }
    }

    /**
     * Whether a captured value has stopped being a value.
     *
     * A fact's value is a name, a place, a short phrase. Once it carries a
     * clause joiner it is a sentence, and what was matched is the shape of a
     * fact rather than a fact.
     */
    private fun tooMuch(value: String): Boolean {
        val words = value.split(' ').filter { it.isNotEmpty() }
        if (words.size > 6) return true
        return words.any { it in JOINERS }
    }

    private val JOINERS = setOf(
        "and", "but", "because", "although", "while", "who", "which",
        "aur", "lekin", "magar", "kyunki", "jo",
    )

    private val QUESTION_WORDS = setOf(
        "kya", "kaun", "kaunsa", "kahan", "kahaan", "kab", "kaise", "kyun", "kyu",
    )

    /** English asks with its first word. */
    private val QUESTION = Regex(
        """^(?:what|who|whose|where|when|which|why|how|is|are|do|does|did|can|could|""" +
            """tell me|batao|bata|yaad)\b"""
    )

    private val PATTERNS: List<Pair<Regex, (List<String>) -> String>> = listOf(
        // "my name is Mubashir" — first, so it does not fall into the general
        // "my X is Y" rule and come out as "The user's name is Mubashir",
        // which is right but reads worse than the plain form.
        Regex("""^(?:my name is|mera naam) (.+?)(?: hai)?$""") to
            { p -> "$OWNER is called ${p[0].titled()}" },

        Regex("""^i(?:'m| am) (?:called |named )(.+)$""") to
            { p -> "$OWNER is called ${p[0].titled()}" },

        // "my brother is called Ali", "my car is a Civic"
        Regex("""^my ([a-z' ]{2,25}) is (?:called |named )?(.+)$""") to
            { p -> "$OWNER's ${p[0]} is ${p[1].titled()}" },

        // "mera bhai Ali hai", "meri behen ka naam Sara hai"
        Regex("""^(?:mera|meri|mere) ([a-z' ]{2,25}?)(?: ka naam| ki naam)? (.+?) hai$""") to
            { p -> "$OWNER's ${p[0]} is ${p[1].titled()}" },

        Regex("""^i live in (.+)$""") to { p -> "$OWNER lives in ${p[0].titled()}" },
        Regex("""^mein (.+?) (?:mein )?rehta hu(?:n)?$""") to
            { p -> "$OWNER lives in ${p[0].titled()}" },

        Regex("""^i work (?:at|on|for|in) (.+)$""") to { p -> "$OWNER works on ${p[0]}" },
        Regex("""^i(?:'m| am) (?:a|an) (.+)$""") to { p -> "$OWNER is a ${p[0]}" },

        Regex("""^i (?:like|love|enjoy|prefer) (.+)$""") to { p -> "$OWNER likes ${p[0]}" },
        Regex("""^mujhe (.+?) pasand hai$""") to { p -> "$OWNER likes ${p[0]}" },

        Regex("""^i (?:hate|dislike|don'?t like) (.+)$""") to
            { p -> "$OWNER does not like ${p[0]}" },
        Regex("""^mujhe (.+?) pasand nahi(?: hai)?$""") to
            { p -> "$OWNER does not like ${p[0]}" },

        Regex("""^i(?:'m| am) (?:using|running|on) (?:a |an )?(.+)$""") to
            { p -> "$OWNER uses ${p[0]}" },
    )

    /** Names keep their capital; the rest of a fact does not need one. */
    private fun String.titled(): String =
        split(' ').joinToString(" ") { word ->
            if (word.length > 1 && word.all { it.isLetter() }) {
                word.replaceFirstChar { it.uppercase() }
            } else {
                word
            }
        }
}
