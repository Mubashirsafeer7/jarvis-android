package com.mubashir.jarvis.agent

import com.mubashir.jarvis.llm.Brain
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** What a step produced. */
sealed interface StepOutcome {
    data class Done(val said: String) : StepOutcome
    data class Failed(val why: String) : StepOutcome

    /**
     * The step needs the user, and the loop cannot go on without them — a call
     * to confirm, a permission to grant. Not a failure: the plan is paused, not
     * wrong.
     */
    data class NeedsUser(val why: String) : StepOutcome
}

/** Everything the screen needs to show the loop working. */
sealed interface AgentEvent {
    data object Planning : AgentEvent
    data class Planned(val plan: Plan) : AgentEvent
    data class Progress(val plan: Plan) : AgentEvent
    data class Finished(val plan: Plan, val summary: String) : AgentEvent

    /**
     * The model answered rather than planned. That is a real reply, and the
     * loop must hand it over rather than treat it as a failure to plan.
     */
    data class JustAnAnswer(val reply: String) : AgentEvent

    data class Stopped(val plan: Plan, val why: String) : AgentEvent
}

/**
 * Goal in, steps out, steps carried out, one at a time.
 *
 * The difference between an assistant that answers and one that does. Written
 * so the whole loop can be tested against a fake brain and a fake pair of
 * hands, because on a real one it is a slow, non-deterministic thing that
 * cannot be run here at all.
 *
 * Every step is emitted as it happens rather than reported at the end. A plan
 * that runs silently for a minute and then announces a result is
 * indistinguishable from a hang, and worse, gives the user no moment to stop it
 * at step two when it is obviously going wrong.
 *
 * @param carryOut how a single step is actually done. The router first, the
 *   brain second — which is the same path a typed message takes, so a step
 *   costs nothing new and behaves exactly as the user already expects.
 */
class Agent(
    private val brain: () -> Brain,
    private val carryOut: suspend (String) -> StepOutcome,
    private val predictLength: () -> Int = { 512 },
    private val toolsOnOffer: () -> List<String> = { emptyList() },
) {

    fun pursue(goal: String): Flow<AgentEvent> = flow {
        emit(AgentEvent.Planning)

        val written = ask(PlanParser.planningPrompt(goal, toolsOnOffer()))
        val steps = PlanParser.parse(written)

        // No steps means the model answered the question instead of planning
        // it. That is not a failure — it is frequently the right response, and
        // throwing it away to report "could not make a plan" would lose a
        // perfectly good reply.
        if (steps.isEmpty()) {
            emit(AgentEvent.JustAnAnswer(written.trim()))
            return@flow
        }

        var plan = Plan(goal, steps)
        emit(AgentEvent.Planned(plan))

        var consecutiveFailures = 0

        while (true) {
            when (val next = AgentRules.next(plan, consecutiveFailures)) {
                is Next.Abandon -> {
                    emit(AgentEvent.Stopped(plan, next.reason))
                    return@flow
                }

                is Next.Finished -> {
                    emit(AgentEvent.Finished(plan, AgentRules.summarise(plan)))
                    return@flow
                }

                is Next.Run -> {
                    plan = plan.with(next.step.number, StepState.Running)
                    emit(AgentEvent.Progress(plan))

                    val outcome = runCatching { carryOut(next.step.what) }
                        .getOrElse { failure ->
                            if (failure is CancellationException) throw failure
                            StepOutcome.Failed(failure.message ?: "It did not work.")
                        }

                    plan = when (outcome) {
                        is StepOutcome.Done -> {
                            consecutiveFailures = 0
                            plan.with(next.step.number, StepState.Done, outcome.said)
                        }

                        is StepOutcome.Failed -> {
                            consecutiveFailures++
                            plan.with(next.step.number, StepState.Failed, outcome.why)
                        }

                        // Waiting on a person is not a failure, and must not
                        // count towards giving up. The plan stops here and the
                        // rest is left alone rather than marked as broken.
                        is StepOutcome.NeedsUser -> {
                            emit(
                                AgentEvent.Stopped(
                                    plan.with(next.step.number, StepState.Waiting),
                                    outcome.why,
                                ),
                            )
                            return@flow
                        }
                    }
                    emit(AgentEvent.Progress(plan))
                }
            }
        }
    }

    private suspend fun ask(prompt: String): String {
        val whole = StringBuilder()
        brain().ask(prompt, predictLength()).collect { whole.append(it) }
        return whole.toString()
    }
}
