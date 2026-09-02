package com.mubashir.jarvis

import com.mubashir.jarvis.agent.AgentRules
import com.mubashir.jarvis.agent.Next
import com.mubashir.jarvis.agent.Plan
import com.mubashir.jarvis.agent.Step
import com.mubashir.jarvis.agent.StepState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentRulesTest {

    private fun plan(vararg states: StepState) = Plan(
        goal = "do the thing",
        steps = states.mapIndexed { i, state -> Step(i + 1, "step ${i + 1}", state) },
    )

    // ---- when to plan at all ----

    @Test
    fun `a job gets a plan`() {
        listOf(
            "build me a website for my VPS business",
            "VPS website banana start karo",
            "set up a new project folder and install the dependencies",
            "mere liye ek landing page bana do",
            "write a script that backs up my files",
            "fix the login page and deploy it",
        ).forEach { assertTrue(it, AgentRules.looksLikeAGoal(it)) }
    }

    @Test
    fun `a question never gets a plan, in either language`() {
        listOf(
            "what is the battery",
            "how do I build a website",
            "kya tum website bana sakte ho",
            "mera bhai kaun hai",
            "what is my brother's name",
            "tell me how to set up a server",
        ).forEach { assertFalse(it, AgentRules.looksLikeAGoal(it)) }
    }

    @Test
    fun `a single command never gets a plan`() {
        // The one that was wrong. "karo" just means "do" and attaches to any
        // action, so "ali ko call karo" read as a project and Jarvis would have
        // drawn up a plan to make one phone call. The router catches these
        // first today, but a rule that is only correct because something else
        // runs before it is not a rule.
        listOf(
            "ali ko call karo",
            "torch on",
            "5 minute ka timer laga do",
            "remember that my brother is called Ali",
        ).forEach { assertFalse(it, AgentRules.looksLikeAGoal(it)) }
    }

    @Test
    fun `small talk never gets a plan`() {
        listOf("hi", "hello there", "thanks", "", "   ", "ok")
            .forEach { assertFalse(it, AgentRules.looksLikeAGoal(it)) }
    }

    // ---- when to stop ----

    @Test
    fun `the next waiting step is the one to run`() {
        val next = AgentRules.next(plan(StepState.Done, StepState.Waiting, StepState.Waiting), 0)
        assertEquals(2, (next as Next.Run).step.number)
    }

    @Test
    fun `a plan with nothing left is finished`() {
        assertEquals(Next.Finished, AgentRules.next(plan(StepState.Done, StepState.Done), 0))
    }

    @Test
    fun `a plan that failed every step is still finished, not abandoned`() {
        // Abandoning is about stopping early. Once there is nothing left to
        // run, the loop is over whatever the results were, and the summary is
        // what tells the user it went badly.
        assertEquals(Next.Finished, AgentRules.next(plan(StepState.Failed, StepState.Failed), 0))
    }

    @Test
    fun `two failures in a row stops the rest of the plan`() {
        val next = AgentRules.next(
            plan(StepState.Failed, StepState.Failed, StepState.Waiting),
            consecutiveFailures = 2,
        )
        assertTrue(next is Next.Abandon)
        assertTrue((next as Next.Abandon).reason.contains("in a row"))
    }

    @Test
    fun `one failure is not enough to give up`() {
        val next = AgentRules.next(
            plan(StepState.Failed, StepState.Waiting, StepState.Waiting),
            consecutiveFailures = 1,
        )
        assertTrue(next is Next.Run)
    }

    @Test
    fun `an empty plan is abandoned rather than reported as done`() {
        val next = AgentRules.next(Plan("do the thing", emptyList()), 0)
        assertTrue(next is Next.Abandon)
    }

    // ---- what to say afterwards ----

    @Test
    fun `the summary says what actually happened`() {
        assertTrue(AgentRules.summarise(plan(StepState.Done, StepState.Done)).contains("all 2"))
        assertTrue(AgentRules.summarise(plan(StepState.Failed, StepState.Failed)).contains("None"))
        val mixed = AgentRules.summarise(plan(StepState.Done, StepState.Failed, StepState.Done))
        assertTrue(mixed.contains("2 of 3"))
        assertTrue(mixed.contains("1 did not work"))
    }

    @Test
    fun `marking a step does not disturb the others`() {
        val started = plan(StepState.Waiting, StepState.Waiting)
        val after = started.with(1, StepState.Done, "worked")
        assertEquals(StepState.Done, after.steps[0].state)
        assertEquals("worked", after.steps[0].result)
        assertEquals(StepState.Waiting, after.steps[1].state)
        assertEquals(2, (AgentRules.next(after, 0) as Next.Run).step.number)
    }
}
