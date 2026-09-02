package com.mubashir.jarvis.memory

import java.util.Locale

/**
 * Everything memory does that is a decision rather than a database call.
 *
 * Kept pure and apart from the store for the same reason as the engine's state
 * rules: the ways this goes wrong are all quiet. Remembering the same thing
 * five times, answering with a fact that has been superseded, or filling a
 * small model's context with things that have nothing to do with the question
 * are none of them crashes. They just make Jarvis seem stupid, which is the
 * only thing this feature exists to prevent.
 */
object MemoryRules {

    /**
     * How many facts may ride along with a prompt.
     *
     * A phone-sized model has a small context and gets worse, not better, as it
     * fills. Eight short facts is roughly two hundred tokens — enough to know
     * who it is talking to, not enough to crowd out the actual question.
     */
    const val MAX_FACTS_IN_PROMPT = 8

    /** Words that say nothing about what a fact is about. */
    private val STOPWORDS = setOf(
        "a", "an", "the", "is", "are", "was", "were", "be", "been", "am",
        "my", "me", "i", "mine", "his", "her", "their", "our", "your",
        "of", "to", "in", "on", "at", "for", "with", "and", "or", "but",
        "that", "this", "it", "its", "he", "she", "they", "we", "you",
        "has", "have", "had", "do", "does", "did", "will", "would",
        "ka", "ki", "ke", "ko", "se", "hai", "hain", "tha", "thi", "mera",
        "meri", "mere", "uska", "uski", "yeh", "woh", "aur", "bhi", "hi",
        "mujhe", "mein", "main", "kya", "kaun", "kab", "kahan", "naam",
    )

    /**
     * The words a fact or a question is *about*.
     *
     * Names are what matter here, and stripping the scaffolding is what lets
     * "who is my brother" find "Mubashir's brother is called Ali" without
     * needing an embedding model on a phone.
     */
    fun keywordsOf(text: String): Set<String> = text
        .lowercase(Locale.ROOT)
        .map { if (it.isLetterOrDigit()) it else ' ' }
        .joinToString("")
        .split(' ')
        .filter { it.length > 2 && it !in STOPWORDS }
        .toSet()

    /**
     * A short label for what a fact is about, used to notice that a newer fact
     * replaces an older one rather than sitting alongside it contradicting it.
     */
    fun topicOf(text: String): String =
        keywordsOf(text).sorted().take(3).joinToString(" ")

    /**
     * Whether [newer] is about the same thing as [older], and so should replace
     * it.
     *
     * Being reluctant here is the safer direction: two facts that overlap are
     * clutter, but wrongly replacing one loses something the user asked to keep.
     * So it takes a real majority of shared subject matter, not one word.
     */
    fun replaces(newer: Fact, older: Fact): Boolean {
        if (newer.text.equals(older.text, ignoreCase = true)) return true
        val a = keywordsOf(newer.topic)
        val b = keywordsOf(older.topic)
        if (a.isEmpty() || b.isEmpty()) return false
        val shared = a.intersect(b).size
        return shared >= 2 || (shared == 1 && a.size == 1 && b.size == 1)
    }

    /**
     * Which facts are worth sending with this prompt.
     *
     * Scored rather than filtered, because the cost of leaving a fact out is
     * that Jarvis appears not to know something it does. Pinned facts and the
     * things the user said outright outrank anything worked out from a
     * conversation, and a fact that shares words with the question outranks
     * both.
     */
    fun relevant(
        prompt: String,
        facts: List<Fact>,
        limit: Int = MAX_FACTS_IN_PROMPT,
    ): List<Fact> {
        if (facts.isEmpty() || limit <= 0) return emptyList()
        val asked = keywordsOf(prompt)

        return facts
            .map { fact -> fact to scoreOf(fact, asked) }
            .sortedWith(
                compareByDescending<Pair<Fact, Int>> { it.second }
                    .thenByDescending { it.first.createdAt },
            )
            .take(limit)
            .map { it.first }
    }

    private fun scoreOf(fact: Fact, asked: Set<String>): Int {
        var score = 0
        if (fact.pinned) score += 100
        if (fact.source == FactSource.Told) score += 10
        val overlap = keywordsOf(fact.text).intersect(asked).size
        score += overlap * 20
        return score
    }

    /**
     * The block of context that travels with a prompt.
     *
     * Not the system prompt, which the engine will only accept immediately
     * after a model is loaded — anything learned afterwards would not reach the
     * model until the next restart. Riding along with each message costs a
     * couple of hundred tokens and is always current.
     *
     * @return the block, or null when there is nothing worth saying
     */
    fun contextBlock(facts: List<Fact>): String? {
        if (facts.isEmpty()) return null
        val lines = facts.joinToString("\n") { "- ${it.text}" }
        return "Things you already know about the person you are talking to:\n" +
            lines +
            "\nUse these only if they are relevant. Do not list them back."
    }

    /** A prompt with whatever Jarvis knows that bears on it. */
    fun withContext(prompt: String, facts: List<Fact>): String {
        val block = contextBlock(relevant(prompt, facts)) ?: return prompt
        return "$block\n\n$prompt"
    }
}
