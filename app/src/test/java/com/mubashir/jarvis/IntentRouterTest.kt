package com.mubashir.jarvis

import com.mubashir.jarvis.tools.Command
import com.mubashir.jarvis.tools.IntentRouter
import com.mubashir.jarvis.tools.needsConfirmation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentRouterTest {

    private fun route(text: String) = IntentRouter.route(text)

    @Test
    fun `torch, both languages and both directions`() {
        listOf(
            "torch on", "turn on the torch", "flashlight on",
            "torch jala do", "light jalao", "torch on karo",
        ).forEach { assertEquals(it, Command.Torch(true), route(it)) }

        listOf(
            "torch off", "turn off the flashlight", "flashlight off",
            "torch band karo", "light bujha do", "torch band",
        ).forEach { assertEquals(it, Command.Torch(false), route(it)) }
    }

    @Test
    fun `calling someone, said either way`() {
        assertEquals(Command.Call("ali"), route("call ali"))
        assertEquals(Command.Call("ali"), route("Call Ali"))
        assertEquals(Command.Call("ali"), route("ali ko call karo"))
        assertEquals(Command.Call("ali bhai"), route("ali bhai ko phone karo"))
    }

    @Test
    fun `a message carries who and what`() {
        assertEquals(
            Command.SendSms("ali", "i am late"),
            route("send a message to ali saying i am late"),
        )
        assertEquals(
            Command.SendSms("ali", "aa raha hoon"),
            route("ali ko message karo, aa raha hoon"),
        )
    }

    @Test
    fun `a message is never mistaken for a call`() {
        // Both patterns can see "ali"; the wrong one places a phone call.
        val routed = route("message ali saying hello")
        assertTrue("$routed", routed is Command.SendSms)
    }

    @Test
    fun `opening an app`() {
        assertEquals(Command.OpenApp("youtube"), route("open youtube"))
        assertEquals(Command.OpenApp("youtube"), route("youtube kholo"))
        assertEquals(Command.OpenApp("whatsapp"), route("open whatsapp app"))
    }

    @Test
    fun `timers in whichever unit was said`() {
        assertEquals(Command.Timer(300), route("set a timer for 5 minutes"))
        assertEquals(Command.Timer(30), route("30 seconds ka timer laga do"))
        assertEquals(Command.Timer(7200), route("set a timer for 2 hours"))
    }

    @Test
    fun `the status questions`() {
        assertEquals(Command.Battery, route("battery kitni hai"))
        assertEquals(Command.Battery, route("what's the battery level"))
        assertEquals(Command.WhereAmI, route("where am i"))
        assertEquals(Command.WhereAmI, route("mein kahan hun"))
        assertEquals(Command.TodaySchedule, route("what's my schedule today"))
        assertEquals(Command.ReadNotifications, route("read my notifications"))
    }

    @Test
    fun `conversation is left to the model`() {
        // The router must not swallow anything it is not sure about — a wrong
        // match here places a call or sends a message the user did not ask for.
        listOf(
            "how are you",
            "what is the capital of france",
            "aaj mausam kaisa hai",
            "can you call me back later if i ask you to",
            "explain how a battery works",
            "tell me about opening a bank account in pakistan",
            "",
            "   ",
        ).forEach { assertNull("should have gone to the model: $it", route(it)) }
    }

    @Test
    fun `only the irreversible things ask first`() {
        assertTrue(Command.Call("ali").needsConfirmation)
        assertTrue(Command.SendSms("ali", "hi").needsConfirmation)
        assertFalse(Command.Torch(true).needsConfirmation)
        assertFalse(Command.Battery.needsConfirmation)
        assertFalse(Command.OpenApp("youtube").needsConfirmation)
    }

    @Test
    fun `a trailing question mark does not stop a match`() {
        assertEquals(Command.Battery, route("battery kitni hai?"))
    }
}
