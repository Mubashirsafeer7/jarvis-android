package com.mubashir.jarvis

import com.mubashir.jarvis.agent.PlanParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Written against what models actually produce, not what they are asked for.
 *
 * The instruction says "no explanation before or after the list". Models add
 * one anyway, renumber halfway, bold the verbs, and sometimes answer in prose
 * instead. All of that has to survive, because a plan thrown away for a stray
 * sentence shows the user nothing at all.
 */
class PlanParserTest {

    @Test
    fun `a clean numbered plan reads as written`() {
        val steps = PlanParser.parse(
            """
            1. Check the battery level
            2. Turn on the torch
            3. Set a five minute timer
            """.trimIndent(),
        )
        assertEquals(3, steps.size)
        assertEquals("Check the battery level", steps[0].what)
        assertEquals(1, steps[0].number)
        assertEquals(3, steps[2].number)
    }

    @Test
    fun `preamble and sign-off are dropped`() {
        val steps = PlanParser.parse(
            """
            Sure! Here is a plan for you:

            1. Open the browser
            2. Search for the answer

            Let me know if you would like me to change anything.
            """.trimIndent(),
        )
        assertEquals(2, steps.size)
        assertEquals("Open the browser", steps[0].what)
    }

    @Test
    fun `bullets work when the model forgets to number`() {
        val steps = PlanParser.parse(
            """
            - Check the battery
            - Turn on the torch
            """.trimIndent(),
        )
        assertEquals(2, steps.size)
        assertEquals(1, steps[0].number)
    }

    @Test
    fun `numbered wins when the model does both`() {
        // The common shape: numbered steps with bulleted detail underneath.
        // Reading both gives a plan where every other line is a footnote.
        val steps = PlanParser.parse(
            """
            1. Open the project
              - it is in the home folder
              - use the terminal
            2. Run the tests
              - the command is gradle test
            """.trimIndent(),
        )
        assertEquals(2, steps.size)
        assertEquals("Open the project", steps[0].what)
        assertEquals("Run the tests", steps[1].what)
    }

    @Test
    fun `bad numbering is renumbered rather than obeyed`() {
        // Models restart at 1, skip numbers, and repeat them. The order they
        // were written in is the only thing that can be trusted.
        val steps = PlanParser.parse(
            """
            1. First thing
            1. Second thing
            5. Third thing
            """.trimIndent(),
        )
        assertEquals(listOf(1, 2, 3), steps.map { it.number })
        assertEquals("Third thing", steps[2].what)
    }

    @Test
    fun `markdown emphasis is stripped`() {
        val steps = PlanParser.parse("1. **Open** the settings screen")
        assertEquals("Open the settings screen", steps.single().what)
    }

    @Test
    fun `a repeated step is listed once`() {
        val steps = PlanParser.parse(
            """
            1. Check the battery
            2. Check the battery
            3. Turn on the torch
            """.trimIndent(),
        )
        assertEquals(2, steps.size)
    }

    @Test
    fun `the word step is not part of the step`() {
        val steps = PlanParser.parse(
            """
            Step 1: Open the app
            Step 2: Sign in
            """.trimIndent(),
        )
        assertEquals("Open the app", steps[0].what)
        assertEquals("Sign in", steps[1].what)
    }

    @Test
    fun `a runaway plan is cut off rather than run`() {
        val many = (1..40).joinToString("\n") { "$it. Do thing number $it" }
        assertEquals(PlanParser.MAX_STEPS, PlanParser.parse(many).size)
    }

    @Test
    fun `prose is not a plan of one step`() {
        // The important negative. A model that answers the question instead of
        // planning must come back empty, so the caller can tell the difference
        // between "here is a plan" and "here is an answer".
        assertEquals(
            emptyList<Any>(),
            PlanParser.parse("The battery is at 80 percent and the torch is off."),
        )
        assertEquals(emptyList<Any>(), PlanParser.parse(""))
        assertEquals(emptyList<Any>(), PlanParser.parse("   \n  \n "))
    }

    @Test
    fun `a decimal in a sentence is not a step number`() {
        assertEquals(emptyList<Any>(), PlanParser.parse("It costs 3.50 and takes 2.5 hours."))
    }

    @Test
    fun `the planning prompt says what it needs to`() {
        val prompt = PlanParser.planningPrompt("build a website", listOf("turn the torch on"))
        assertTrue(prompt.contains("build a website"))
        assertTrue(prompt.contains("turn the torch on"))
        assertTrue(prompt.contains(PlanParser.MAX_STEPS.toString()))
    }
}
