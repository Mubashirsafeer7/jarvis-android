package com.mubashir.jarvis

import com.mubashir.jarvis.llm.Abilities
import com.mubashir.jarvis.llm.Persona
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The prompt has one job beyond character: it must be true.
 *
 * It used to be a constant with the capability list typed into it, written when
 * nothing was wired up. By the time contacts, calls, messages and memory all
 * worked, it was still telling the model it could not see contacts and had no
 * memory — so Jarvis denied, in its own words, things it had just done for him.
 */
class PersonaTest {

    private val everything = Abilities(
        phoneControl = true,
        canCall = true,
        canMessage = true,
        canReadContacts = true,
        canSeeLocation = true,
        canReadCalendar = true,
        canReadNotifications = true,
        canSearchInternet = true,
        remembers = true,
    )

    private val nothing = Abilities(
        phoneControl = false,
        canCall = false,
        canMessage = false,
        canReadContacts = false,
        remembers = false,
    )

    @Test
    fun `nothing is ever in both lists at once`() {
        // The bug this file exists for, stated as a rule. Whatever the state,
        // an ability cannot be claimed and denied in the same breath.
        //
        // Compared by identity rather than by looking for the word in the
        // English. The first version of this test matched substrings and
        // reported a contradiction because "recall" contains "call" — which is
        // the same class of mistake it is here to catch.
        listOf(
            everything, nothing, Abilities(),
            Abilities(remembers = true), Abilities(canCall = true),
            Abilities(canReadContacts = true), Abilities(brainIsRemote = true),
        ).forEach { state ->
            val claimed = Persona.canDo(state).map { it.ability }.toSet()
            val denied = Persona.cannotDo(state).map { it.ability }.toSet()
            assertTrue(
                "claimed and denied at once for $state: ${claimed intersect denied}",
                (claimed intersect denied).isEmpty(),
            )
        }
    }

    @Test
    fun `memory is admitted once it exists`() {
        assertTrue(Persona.systemPrompt(everything).contains("Remember things about him"))
        assertFalse(Persona.systemPrompt(everything).contains("Remember anything between conversations"))
    }

    @Test
    fun `memory is denied when there is none`() {
        val prompt = Persona.systemPrompt(nothing)
        assertTrue(prompt.contains("Remember anything between conversations"))
    }

    @Test
    fun `calling needs both the permission and the contacts`() {
        // Half the permission is not the ability. Claiming it produces a Jarvis
        // that agrees to place a call and then cannot.
        val halfWay = Abilities(canCall = true, canReadContacts = false)
        assertTrue(Persona.cannotDo(halfWay).any { it.text.contains("Place calls") })
        assertFalse(Persona.canDo(halfWay).any { it.text.contains("Call someone") })
    }

    @Test
    fun `switching phone control off is stated, not silently obeyed`() {
        val prompt = Persona.systemPrompt(Abilities(phoneControl = false))
        assertTrue(prompt.contains("switched that off in settings"))
        assertFalse(prompt.contains("Turn the torch on and off"))
    }

    @Test
    fun `a remote brain is not told it is offline for the wrong reason`() {
        val remote = Abilities(brainIsRemote = true, canSearchInternet = false)
        assertTrue(Persona.cannotDo(remote).any { it.text.contains("no internet access") })
        assertFalse(Persona.cannotDo(remote).any { it.text.contains("You are offline") })
    }

    @Test
    fun `the character survives every configuration`() {
        listOf(everything, nothing, Abilities()).forEach { state ->
            val prompt = Persona.systemPrompt(state)
            assertTrue(prompt.contains("One sentence when one will do"))
            assertTrue(prompt.contains("Never repeat his question back"))
            assertTrue(prompt.contains("Never say you are an AI"))
            assertTrue(prompt.contains("No markdown"))
        }
    }

    @Test
    fun `the prompt stays short enough to be worth sending`() {
        // It rides in front of every conversation on a model with a small
        // context. A prompt that explains itself at length leaves less room for
        // the actual exchange, which is the thing the user came for.
        assertTrue(Persona.systemPrompt(everything).length < 2200)
    }
}
