package com.mubashir.jarvis.agent

import java.util.Locale

/** What the loop should do next. */
sealed interface Next {
    data class Run(val step: Step) : Next
    data object Finished : Next

    /** Stop and say why. Running on past this makes things worse, not better. */
    data class Abandon(val reason: String) : Next
}

/**
 * When to plan at all, and when to stop.
 *
 * Both halves exist to stop the loop being annoying rather than to make it
 * clever. An assistant that draws up a five step plan for "what is the battery"
 * is worse than one that never plans; an assistant that ploughs through six
 * failing steps because the plan said so is worse still.
 */
object AgentRules {

    /**
     * Two failures in a row means the plan was wrong, not the step.
     *
     * Retrying the third step of a plan built on a false assumption produces a
     * third failure and a fourth. Stopping and saying so is the useful answer.
     */
    const val CONSECUTIVE_FAILURES_ALLOWED = 2

    /**
     * Whether this is a job rather than a question.
     *
     * Deliberately narrow. A false negative costs nothing — the message is
     * answered normally, which is what happens today. A false positive makes
     * every ordinary sentence take a planning round first, which is slow and
     * makes Jarvis look like it is overthinking a greeting.
     */
    fun looksLikeAGoal(text: String): Boolean {
        val said = text.trim().lowercase(Locale.ROOT)
        if (said.length < 8) return false
        if (said.endsWith("?")) return false

        val words = said
            .map { if (it.isLetterOrDigit() || it == '\'') it else ' ' }
            .joinToString("")
            .split(' ')
            .filter { it.isNotEmpty() }

        // Under four words it is a command, and the router already handles
        // those far faster than any plan could.
        if (words.size < 4) return false
        if (words.first() in ASKING) return false
        if (words.any { it in ASKING_ANYWHERE }) return false

        if (DOING_PHRASES.any { said.contains(it) }) return true
        return words.any { it in DOING }
    }

    /**
     * @param consecutiveFailures how many steps have failed back to back
     */
    fun next(plan: Plan, consecutiveFailures: Int): Next = when {
        consecutiveFailures >= CONSECUTIVE_FAILURES_ALLOWED -> Next.Abandon(
            "Two steps in a row did not work, so the rest of the plan probably will not either.",
        )

        plan.steps.isEmpty() -> Next.Abandon("There was no plan to follow.")

        else -> plan.current?.let { Next.Run(it) } ?: Next.Finished
    }

    /** What to say when a plan ends, however it ended. */
    fun summarise(plan: Plan): String {
        val done = plan.steps.count { it.state == StepState.Done }
        val failed = plan.failed
        return when {
            failed == 0 && done == plan.steps.size -> "Done, all $done steps."
            done == 0 -> "None of it worked."
            else -> "$done of ${plan.steps.size} steps done, $failed did not work."
        }
    }

    /**
     * Verbs that mean a job, in both languages.
     *
     * Notably absent: "karo", "karna", "lagao", and bare "set". They are the
     * obvious additions and all of them are wrong here, because they attach to
     * any action at all — "call karo", "torch on karo", "set a timer" are
     * single commands, and reading them as projects makes Jarvis draw up a plan
     * to turn on a torch. The router catches those first today, but a rule that
     * is only correct because something else runs before it is not a rule.
     */
    private val DOING = setOf(
        "build", "make", "create", "start", "write", "install",
        "deploy", "organise", "organize", "prepare", "publish", "launch",
        "banao", "bana", "banana", "banado", "shuru", "likho", "likhna",
    )

    /** Two-word verbs, checked against the sentence rather than its words. */
    private val DOING_PHRASES = setOf("set up", "setup")

    /** A question is not a job, however it is phrased. */
    private val ASKING = setOf(
        "what", "who", "where", "when", "which", "why", "how", "is", "are",
        "do", "does", "did", "can", "could", "would", "should", "tell",
        "batao", "bata", "kya", "kaun", "kahan", "kab", "kaise", "kyun", "kyu",
    )

    /** Urdu asks from the middle of the sentence, so these count anywhere. */
    private val ASKING_ANYWHERE = setOf("kya", "kaun", "kahan", "kaise", "kyun", "kyu")
}
