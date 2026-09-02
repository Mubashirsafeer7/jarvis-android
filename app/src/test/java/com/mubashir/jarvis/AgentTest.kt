package com.mubashir.jarvis

import com.mubashir.jarvis.agent.Agent
import com.mubashir.jarvis.agent.AgentEvent
import com.mubashir.jarvis.agent.StepOutcome
import com.mubashir.jarvis.agent.StepState
import com.mubashir.jarvis.agent.asLines
import com.mubashir.jarvis.llm.Brain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The whole loop, against a fake brain and a fake pair of hands.
 *
 * On a real brain this is slow and different every time, and cannot be run here
 * at all. Injecting both ends is what makes the part that matters — the order
 * of events, when it stops, what it does with a failure — something that can be
 * checked in milliseconds instead of guessed at.
 */
class AgentTest {

    private class FakeBrain(private val reply: String) : Brain {
        override val label = "fake"
        override suspend fun isReady() = true
        override fun ask(prompt: String, predictLength: Int): Flow<String> = flowOf(reply)
        override suspend fun check() = Result.success("fake")
    }

    private fun agent(
        plans: String,
        carryOut: suspend (String) -> StepOutcome,
    ) = Agent(brain = { FakeBrain(plans) }, carryOut = carryOut)

    private fun run(agent: Agent, goal: String = "build the thing"): List<AgentEvent> =
        runBlocking { agent.pursue(goal).toList() }

    private val threeSteps = """
        1. Check the battery
        2. Turn on the torch
        3. Set a timer
    """.trimIndent()

    @Test
    fun `a plan runs to the end and says so`() {
        val done = mutableListOf<String>()
        val events = run(
            agent(threeSteps) { what ->
                done += what
                StepOutcome.Done("did $what")
            },
        )

        assertEquals(listOf("Check the battery", "Turn on the torch", "Set a timer"), done)

        val last = events.last()
        assertTrue(last is AgentEvent.Finished)
        assertTrue((last as AgentEvent.Finished).summary.contains("all 3"))
        assertTrue(last.plan.steps.all { it.state == StepState.Done })
    }

    @Test
    fun `the screen is told before each step, not after the lot`() {
        // A plan that runs silently and announces a result at the end is
        // indistinguishable from a hang, and leaves no moment to stop it at
        // step two when it is obviously going wrong.
        val events = run(agent(threeSteps) { StepOutcome.Done("ok") })

        assertTrue(events.first() is AgentEvent.Planning)
        assertTrue(events[1] is AgentEvent.Planned)

        val running = events.filterIsInstance<AgentEvent.Progress>()
            .count { event -> event.plan.steps.any { it.state == StepState.Running } }
        assertEquals(3, running)
    }

    @Test
    fun `an answer instead of a plan is handed over, not thrown away`() {
        // The model very often answers rather than plans, and that answer is
        // usually right. Reporting "could not make a plan" would lose it.
        val events = run(agent("The battery is at 80 percent.") { StepOutcome.Done("ok") })
        val only = events.last()
        assertTrue(only is AgentEvent.JustAnAnswer)
        assertEquals("The battery is at 80 percent.", (only as AgentEvent.JustAnAnswer).reply)
    }

    @Test
    fun `one failure does not stop the plan`() {
        val tried = mutableListOf<String>()
        val events = run(
            agent(threeSteps) { what ->
                tried += what
                if (what.contains("torch")) StepOutcome.Failed("no torch") else StepOutcome.Done("ok")
            },
        )
        assertEquals(3, tried.size)
        assertTrue(events.last() is AgentEvent.Finished)
        assertTrue((events.last() as AgentEvent.Finished).summary.contains("2 of 3"))
    }

    @Test
    fun `two failures in a row stop it before the rest`() {
        val tried = mutableListOf<String>()
        val events = run(
            agent(threeSteps) { what ->
                tried += what
                StepOutcome.Failed("nope")
            },
        )
        // Two attempts, not three. The third step was built on the same wrong
        // assumption and would have failed the same way.
        assertEquals(2, tried.size)
        val last = events.last()
        assertTrue(last is AgentEvent.Stopped)
        assertTrue((last as AgentEvent.Stopped).why.contains("in a row"))
    }

    @Test
    fun `a step that needs the user pauses rather than fails`() {
        val events = run(
            agent(threeSteps) { what ->
                if (what.contains("torch")) StepOutcome.NeedsUser("Allow the camera first.")
                else StepOutcome.Done("ok")
            },
        )
        val last = events.last() as AgentEvent.Stopped
        assertTrue(last.why.contains("Allow the camera"))
        // Left waiting, not marked broken. The plan is paused, not wrong.
        assertEquals(StepState.Waiting, last.plan.steps[1].state)
        assertEquals(StepState.Waiting, last.plan.steps[2].state)
    }

    @Test
    fun `a step that throws is a failed step, not a crashed loop`() {
        val events = run(
            agent(threeSteps) { what ->
                if (what.contains("torch")) error("the camera exploded")
                StepOutcome.Done("ok")
            },
        )
        assertTrue(events.last() is AgentEvent.Finished)
        val plan = (events.last() as AgentEvent.Finished).plan
        assertEquals(StepState.Failed, plan.steps[1].state)
        assertEquals("the camera exploded", plan.steps[1].result)
    }

    @Test
    fun `the result of each step is kept, not just whether it worked`() {
        val events = run(agent(threeSteps) { what -> StepOutcome.Done("result of $what") })
        val plan = (events.last() as AgentEvent.Finished).plan
        assertEquals("result of Check the battery", plan.steps[0].result)
    }

    @Test
    fun `a plan of one step is still a plan`() {
        val events = run(agent("1. Turn on the torch") { StepOutcome.Done("on") })
        assertTrue(events.last() is AgentEvent.Finished)
        assertEquals(1, (events.last() as AgentEvent.Finished).plan.steps.size)
    }

    @Test
    fun `the plan reads as a record of what happened`() {
        val events = run(
            agent(threeSteps) { what ->
                if (what.contains("torch")) StepOutcome.Failed("no torch")
                else StepOutcome.Done("done: $what")
            },
        )
        val text = (events.last() as AgentEvent.Finished).plan.asLines()
        assertTrue(text.contains("\u2713 1. Check the battery"))
        assertTrue(text.contains("done: Check the battery"))
        assertTrue(text.contains("\u2717 2. Turn on the torch"))
        // A failed step shows the mark, not a result line pretending it worked.
        assertTrue(!text.contains("    no torch"))
    }
}
