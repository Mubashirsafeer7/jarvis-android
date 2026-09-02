package com.mubashir.jarvis.agent

/**
 * Turns whatever the model wrote into steps.
 *
 * Every design that asks a model for structured output has the same failure:
 * the model writes prose around it, or renumbers, or answers in a different
 * shape than last time. Demanding JSON makes that worse rather than better on a
 * small model — a missing brace throws the whole plan away, and the user sees
 * nothing at all.
 *
 * So this reads what models actually produce. Numbered lines, bulleted lines,
 * a sentence of preamble first, a closing remark after. Anything that is not a
 * step is dropped rather than fought with.
 */
object PlanParser {

    /**
     * A plan longer than this is a model that has lost the thread. Refusing it
     * is kinder than running eight wrong steps and then twelve more.
     */
    const val MAX_STEPS = 8

    private val NUMBERED = Regex("""^\s*(\d{1,2})\s*[.)\]:-]\s+(.*)$""")

    /** "Step 1: open the app" — a numbered step that begins with a word. */
    private val STEP_LINE = Regex(
        """^\s*step\s*(\d{1,2})\s*[:.)-]\s*(.+)$""",
        RegexOption.IGNORE_CASE,
    )

    private val BULLETED = Regex("""^\s*[-*•]\s+(.*)$""")
    private val STEP_LABEL = Regex("""^\s*step\s*\d*\s*[:.)-]?\s*""", RegexOption.IGNORE_CASE)
    private val EMPHASIS = Regex("""\*\*(.+?)\*\*""")

    /**
     * @return the steps found, in the order written, or empty when the model
     *   did not produce a plan at all — which is a real answer and must not be
     *   mistaken for a plan of one step
     */
    fun parse(reply: String): List<Step> {
        val numbered = mutableListOf<String>()
        val bulleted = mutableListOf<String>()

        reply.lines().forEach { line ->
            NUMBERED.matchEntire(line)?.let { numbered += it.groupValues[2]; return@forEach }
            STEP_LINE.matchEntire(line)?.let { numbered += it.groupValues[2]; return@forEach }
            BULLETED.matchEntire(line)?.let { bulleted += it.groupValues[1] }
        }

        // Numbered wins outright when both appear. A model that numbers its
        // steps and then bullets the details underneath is common, and reading
        // both gives a plan where every other line is a footnote.
        val raw = if (numbered.isNotEmpty()) numbered else bulleted

        return raw
            .map { clean(it) }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .take(MAX_STEPS)
            .mapIndexed { index, what -> Step(number = index + 1, what = what) }
    }

    private fun clean(line: String): String = line
        // Emphasis first. Trimming the stray asterisks off the ends before
        // this runs leaves "Open** the settings" — the pair is broken by the
        // time the pattern that understands pairs gets to look at it.
        .replace(EMPHASIS, "$1")
        .replace(STEP_LABEL, "")
        .trim()
        .trim('*', '_', '`', ' ')
        .trim()

    /** The instruction that asks for a plan in the shape [parse] reads best. */
    fun planningPrompt(goal: String, tools: List<String>): String = buildString {
        appendLine("Break this goal into a short numbered plan.")
        appendLine()
        appendLine("Goal: $goal")
        appendLine()
        appendLine("Rules:")
        appendLine("- At most $MAX_STEPS steps. Fewer is better.")
        appendLine("- One action per step, written as an instruction.")
        appendLine("- Number every step: 1. 2. 3.")
        appendLine("- No explanation before or after the list.")
        if (tools.isNotEmpty()) {
            appendLine()
            appendLine("You can do these directly:")
            tools.forEach { appendLine("- $it") }
        }
    }
}
