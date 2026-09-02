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
