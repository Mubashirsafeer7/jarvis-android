package com.mubashir.jarvis.agent

/** One thing to do, in the order it was planned. */
data class Step(
    val number: Int,
    val what: String,
    val state: StepState = StepState.Waiting,
    /** What happened, once it has been tried. */
    val result: String? = null,
)

enum class StepState { Waiting, Running, Done, Failed, Skipped }

/**
 * A goal broken into steps, and how far along it is.
 *
 * Kept as data rather than as control flow so the screen can show the whole
 * plan while it runs — which is the difference between an assistant that is
 * working and one that has hung. It is also the only way the user can stop it
 * at step three instead of after step eight.
 */
data class Plan(
    val goal: String,
    val steps: List<Step>,
) {
    val current: Step? get() = steps.firstOrNull { it.state == StepState.Waiting }
    val finished: Boolean get() = current == null
    val failed: Int get() = steps.count { it.state == StepState.Failed }

    fun with(number: Int, state: StepState, result: String? = null) = copy(
        steps = steps.map { step ->
            if (step.number == number) step.copy(state = state, result = result ?: step.result)
            else step
        },
    )
}

/**
 * The plan as the user reads it while it runs.
 *
 * One message that is rewritten in place rather than a new message per step:
 * a plan is one thing happening, and eight bubbles arriving one at a time
 * buries the conversation it was part of.
 */
fun Plan.asLines(): String = steps.joinToString("\n") { step ->
    val mark = when (step.state) {
        StepState.Waiting -> "·"
        StepState.Running -> "▸"
        StepState.Done -> "✓"
        StepState.Failed -> "✗"
        StepState.Skipped -> "–"
    }
    val line = "$mark ${step.number}. ${step.what}"
    // The result of a step is shown under it, indented, so a plan reads as a
    // record of what happened rather than a list of what was intended.
    if (step.state == StepState.Done && !step.result.isNullOrBlank()) {
        "$line\n    ${step.result}"
    } else {
        line
    }
}
