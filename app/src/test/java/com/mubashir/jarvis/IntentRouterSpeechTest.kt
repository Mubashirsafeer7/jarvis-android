package com.mubashir.jarvis

import com.mubashir.jarvis.tools.Command
import com.mubashir.jarvis.tools.IntentRouter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * How commands actually arrive, rather than how the patterns were written.
 *
 * Every case here failed before this file existed. They were found by running
 * real phrasings through the router instead of reading it, which is the only
 * way this class of bug shows up: each pattern looks right on its own, and the
 * miss is silent — an unmatched command falls through to the model, which
 * talks about the torch instead of turning it on.
 */
class IntentRouterSpeechTest {

    @Test
    fun `a full stop from the recogniser does not kill the command`() {
        // Recognisers punctuate. Every pattern anchors to the whole line, so a
        // single trailing character made the command miss entirely.
        assertEquals(Command.Torch(on = true), IntentRouter.route("torch on."))
        assertEquals(Command.Battery, IntentRouter.route("battery kitni hai."))
        assertEquals(Command.Call("ali"), IntentRouter.route("Call Ali."))
        assertEquals(Command.Call("ali"), IntentRouter.route("call ali!"))
    }

    @Test
    fun `the urdu full stop counts too`() {
        assertEquals(Command.Torch(on = true), IntentRouter.route("torch on۔"))
    }

    @Test
    fun `a doubled space does not kill the command`() {
        assertEquals(Command.Torch(on = true), IntentRouter.route("torch  on"))
        assertEquals(Command.Call("ali"), IntentRouter.route("call  ali"))
    }

    @Test
    fun `capitals do not matter`() {
        assertEquals(Command.Torch(on = true), IntentRouter.route("Torch On"))
        assertEquals(Command.Call("ali"), IntentRouter.route("Ali Ko Call Karo"))
    }

    @Test
    fun `kardo and kar do are the same word said the same way`() {
        // The whole reason this test exists: the joined spellings are at least
        // as common as the split ones, and none of them matched.
        assertEquals(Command.Call("ali"), IntentRouter.route("ali ko call kardo"))
        assertEquals(Command.Call("ali"), IntentRouter.route("ali ko call kar do"))
        assertEquals(Command.Call("ali"), IntentRouter.route("ali ko phone kardo"))
        assertEquals(Command.Call("ali"), IntentRouter.route("ali ko call kardein"))
        assertEquals(Command.Call("ali"), IntentRouter.route("ali ko call karein"))
        assertEquals(Command.Call("ali"), IntentRouter.route("ali ko call lagao"))
        assertEquals(Command.Call("ali"), IntentRouter.route("ali ko call laga do"))
    }

    @Test
    fun `kholdo and khol do open the same app`() {
        assertEquals(Command.OpenApp("youtube"), IntentRouter.route("youtube kholdo"))
        assertEquals(Command.OpenApp("youtube"), IntentRouter.route("youtube khol do"))
        assertEquals(Command.OpenApp("youtube"), IntentRouter.route("youtube kholo"))
        assertEquals(Command.OpenApp("youtube"), IntentRouter.route("youtube open kardo"))
    }

    @Test
    fun `the torch turns on however it is asked`() {
        listOf(
            "torch on", "torch on karo", "torch on kardo", "torch on kar do",
            "light jala do", "light jalado", "torch jalao", "flashlight on",
            "turn on the torch", "torch chalu karo",
        ).forEach { said ->
            assertEquals(said, Command.Torch(on = true), IntentRouter.route(said))
        }
    }

    @Test
    fun `the torch turns off however it is asked`() {
        listOf(
            "torch off", "torch band karo", "torch bandkaro", "torch band kardo",
            "light bujha do", "light bujhao", "turn off the torch",
        ).forEach { said ->
            assertEquals(said, Command.Torch(on = false), IntentRouter.route(said))
        }
    }

    @Test
    fun `a message is sent however it is asked`() {
        assertEquals(
            Command.SendSms("ali", "hello"),
            IntentRouter.route("ali ko message kardo hello"),
        )
        assertEquals(
            Command.SendSms("ali", "hello"),
            IntentRouter.route("ali ko sms kardo, hello"),
        )
        assertEquals(
            Command.SendSms("ali", "hello"),
            IntentRouter.route("ali ko message bhej do hello"),
        )
    }

    @Test
    fun `a timer is set however it is asked`() {
        assertEquals(Command.Timer(300), IntentRouter.route("5 minute ka timer lagado"))
        assertEquals(Command.Timer(300), IntentRouter.route("5 minute ka timer laga do"))
        assertEquals(Command.Timer(300), IntentRouter.route("set a timer for 5 minutes"))
    }

    @Test
    fun `talking about calling someone is not a command to call them`() {
        // This is the dangerous direction. "call me back later" was read as an
        // instruction to ring a contact named "me back later" — three words, so
        // counting them never caught it.
        assertNull(IntentRouter.route("call me back later"))
        assertNull(IntentRouter.route("call you tomorrow"))
        assertNull(IntentRouter.route("call him back"))
        assertNull(IntentRouter.route("call someone"))
        assertNull(IntentRouter.route("ali ko call karne ka socha tha"))
    }

    @Test
    fun `a question about a thing is not a command about it`() {
        assertNull(IntentRouter.route("what is a battery made of"))
        assertNull(IntentRouter.route("how does a torch work"))
        assertNull(IntentRouter.route("tell me about ali"))
    }

    @Test
    fun `politeness is not part of a name`() {
        assertEquals(Command.Call("ali"), IntentRouter.route("call ali please"))
        assertEquals(Command.Call("ali"), IntentRouter.route("call ali, please."))
    }

    @Test
    fun `nothing at all is not a command`() {
        assertNull(IntentRouter.route(""))
        assertNull(IntentRouter.route("   "))
        assertNull(IntentRouter.route("."))
        assertNull(IntentRouter.route("..."))
    }
}
