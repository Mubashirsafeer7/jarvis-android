package com.mubashir.jarvis.tools

import java.util.Locale

/**
 * Matches the commands people actually say, before the model is asked.
 *
 * This exists for two reasons. A 3B model takes a second or two to produce
 * anything, and "torch on" should not wait on it. And small models are
 * genuinely unreliable at emitting well-formed tool calls, so the commands that
 * matter most would be the ones most likely to come back malformed.
 *
 * Anything not matched here falls through to the model, which is the right
 * default: this is a shortcut for the common phrasings, not a parser for the
 * language.
 *
 * Both English and the Hinglish the phone's owner actually speaks — "Ali ko
 * call karo" is as much the real input as "call Ali".
 */
object IntentRouter {

    fun route(input: String): Command? {
        val text = input.trim().lowercase(Locale.ROOT).removeSuffix("?").trim()
        if (text.isEmpty()) return null

        torch(text)?.let { return it }
        battery(text)?.let { return it }
        location(text)?.let { return it }
        schedule(text)?.let { return it }
        notifications(text)?.let { return it }
        timer(text)?.let { return it }
        sms(text)?.let { return it }
        call(text)?.let { return it }
        openApp(text)?.let { return it }
        return null
    }

    private val TORCH_ON = listOf(
        // "jala do" is two words; matching only "jalado" missed how it is said.
        Regex("""^(?:turn |switch )?(?:the )?(?:torch|flashlight|light) (?:on|jala\s*(?:o|do|dein)?)$"""),
        Regex("""^(?:torch|flashlight|light) (?:on )?kar(?:o|do|dein)$"""),
        Regex("""^(?:turn |switch )?on (?:the )?(?:torch|flashlight)$"""),
    )
    private val TORCH_OFF = listOf(
        Regex("""^(?:turn |switch )?(?:the )?(?:torch|flashlight|light) (?:off|band(?:\s*kar\s*(?:o|do|dein)?)?|bujha\s*(?:o|do)?)$"""),
        Regex("""^(?:turn |switch )?off (?:the )?(?:torch|flashlight)$"""),
    )

    private fun torch(text: String): Command? = when {
        TORCH_ON.any { it.matches(text) } -> Command.Torch(on = true)
        TORCH_OFF.any { it.matches(text) } -> Command.Torch(on = false)
        else -> null
    }

    private val BATTERY = Regex(
        """^(?:what(?:'s| is) (?:the )?battery(?: level| percentage)?|battery(?: kitni(?: hai)?| percentage| level| status| kitna hai)?)$"""
    )

    private fun battery(text: String) = if (BATTERY.matches(text)) Command.Battery else null

    private val LOCATION = Regex(
        """^(?:where am i|what(?:'s| is) my location|mein kaha(?:n)? hu(?:n)?|meri location(?: kya hai| batao)?)$"""
    )

    private fun location(text: String) = if (LOCATION.matches(text)) Command.WhereAmI else null

    private val SCHEDULE = Regex(
        """^(?:what(?:'s| is) (?:my |on my )?(?:schedule|calendar)(?: today)?|aaj (?:kya )?(?:schedule|calendar)(?: hai| kya hai)?|today'?s schedule)$"""
    )

    private fun schedule(text: String) = if (SCHEDULE.matches(text)) Command.TodaySchedule else null

    private val NOTIFICATIONS = Regex(
        """^(?:read (?:my )?notifications|what(?:'s| is) new|(?:naye |new )?(?:messages|notifications)(?: padho| dikhao| batao)?)$"""
    )

    private fun notifications(text: String) =
        if (NOTIFICATIONS.matches(text)) Command.ReadNotifications else null

    private val TIMER = Regex(
        """^(?:set\s+(?:a\s+)?timer\s+(?:for\s+)?)?""" +
            """(\d{1,3})\s*(second|seconds|sec|minute|minutes|min|mins|hour|hours|ghant[ae])""" +
            """(?:\s*(?:ka\s+)?timer)?(?:\s*laga\s*(?:o|do|dein)?)?$"""
    )

    private fun timer(text: String): Command? {
        val match = TIMER.matchEntire(text) ?: return null
        val amount = match.groupValues[1].toIntOrNull() ?: return null
        if (amount <= 0) return null
        val seconds = when (match.groupValues[2]) {
            "second", "seconds", "sec" -> amount
            "minute", "minutes", "min", "mins" -> amount * 60
            else -> amount * 3600
        }
        return Command.Timer(seconds)
    }

    // Ordered so "message Ali saying hello" cannot be read as a call.
    private val SMS = listOf(
        Regex("""^(?:send (?:a )?(?:sms|message|text) to |text |message )(.+?) (?:saying|that) (.+)$"""),
        Regex("""^(.+?) ko (?:sms|message|text) (?:karo|kar do|karein|bhejo|bhej do)[, ]+(.+)$"""),
    )

    private fun sms(text: String): Command? {
        for (pattern in SMS) {
            val match = pattern.matchEntire(text) ?: continue
            val who = match.groupValues[1].trim()
            val body = match.groupValues[2].trim()
            if (who.isNotEmpty() && body.isNotEmpty()) return Command.SendSms(who, body)
        }
        return null
    }

    private val CALL = listOf(
        Regex("""^(?:call|phone|dial) (.+)$"""),
        Regex("""^(.+?) ko (?:call|phone|dial) (?:karo|kar do|karein|lagao|laga do)$"""),
    )

    private fun call(text: String): Command? {
        for (pattern in CALL) {
            val match = pattern.matchEntire(text) ?: continue
            val who = match.groupValues[1].trim().removeSuffix(" please").trim()
            // "call me back later" is conversation, not a command.
            if (who.isEmpty() || who.split(' ').size > 4) continue
            return Command.Call(who)
        }
        return null
    }

    private val OPEN_APP = listOf(
        Regex("""^open (.+?)(?: app)?$"""),
        Regex("""^(.+?) (?:app )?(?:kholo|khol do|kholein|open karo|open kar do)$"""),
    )

    private fun openApp(text: String): Command? {
        for (pattern in OPEN_APP) {
            val match = pattern.matchEntire(text) ?: continue
            val name = match.groupValues[1].trim()
            if (name.isEmpty() || name.split(' ').size > 4) continue
            return Command.OpenApp(name)
        }
        return null
    }
}
