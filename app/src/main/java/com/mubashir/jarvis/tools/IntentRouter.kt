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
        val text = normalise(input)
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

    /**
     * What the rest of this file is allowed to assume it is matching.
     *
     * Recognisers do not hand back tidy strings. They capitalise, they add a
     * full stop, and they sometimes double a space. Every pattern below anchors
     * to the whole line, so any one of those made the command silently miss and
     * fall through to the model — "torch on." did nothing at all.
     */
    private fun normalise(input: String): String = input
        .lowercase(Locale.ROOT)
        .replace(SPACES, " ")
        .trim()
        .replace(TRAILING_PUNCTUATION, "")
        .trim()

    private val SPACES = Regex("""\s+""")

    /** Includes the Urdu full stop, which a recogniser set to Urdu will produce. */
    private val TRAILING_PUNCTUATION = Regex("""[.!?\u06D4]+$""")

    /**
     * Urdu verbs get written both joined and split, by the same person in the
     * same sentence — "kar do" and "kardo" are one word said one way. Matching
     * only the spelling I happened to think of first is why "call kardo" did
     * nothing.
     */
    private const val KAR = """kar\s*(?:o|do|ein|dein)"""
    private const val KHOL = """khol\s*(?:o|do|ein|dein)"""
    private const val LAGA = """laga\s*(?:o|do|dein)"""
    private const val BHEJ = """bhej\s*(?:o|do|dein)"""

    private val TORCH_ON = listOf(
        // "jala do" is two words; matching only "jalado" missed how it is said.
        Regex("""^(?:turn |switch )?(?:the )?(?:torch|flashlight|light) (?:on|jala\s*(?:o|do|dein)?)$"""),
        Regex("""^(?:torch|flashlight|light) (?:on |chalu )?$KAR$"""),
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
        Regex("""^(.+?) ko (?:sms|message|text) (?:$KAR|$BHEJ)[, ]+(.+)$"""),
    )

    private fun sms(text: String): Command? {
        for (pattern in SMS) {
            val match = pattern.matchEntire(text) ?: continue
            val who = cleanName(match.groupValues[1])
            val body = match.groupValues[2].trim()
            if (who.isNotEmpty() && body.isNotEmpty()) return Command.SendSms(who, body)
        }
        return null
    }

    private val CALL = listOf(
        Regex("""^(?:call|phone|dial) (.+)$"""),
        Regex("""^(.+?) ko (?:call|phone|dial) (?:$KAR|$LAGA)$"""),
    )

    private fun call(text: String): Command? {
        for (pattern in CALL) {
            val match = pattern.matchEntire(text) ?: continue
            val who = cleanName(match.groupValues[1])
            if (!isPlausibleName(who)) continue
            return Command.Call(who)
        }
        return null
    }

    private val OPEN_APP = listOf(
        Regex("""^open (.+?)(?: app)?$"""),
        Regex("""^(.+?) (?:app )?(?:$KHOL|open\s+$KAR)$"""),
    )

    private fun openApp(text: String): Command? {
        for (pattern in OPEN_APP) {
            val match = pattern.matchEntire(text) ?: continue
            val name = cleanName(match.groupValues[1])
            if (name.isEmpty() || name.split(' ').size > 4) continue
            return Command.OpenApp(name)
        }
        return null
    }

    /** Strips the politeness and punctuation a name never actually contains. */
    private fun cleanName(raw: String): String = raw
        .trim()
        .removeSuffix(" please")
        .trim()
        .trim(',', '.', '!', '?', ' ')

    /**
     * "call me back later" is a thing people say to each other, and it used to
     * be read as an instruction to ring somebody called "me back later". The
     * name is only three words, so counting them never caught it. What does
     * catch it is that no name starts with a pronoun.
     */
    private fun isPlausibleName(who: String): Boolean {
        if (who.isEmpty()) return false
        val words = who.split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty() || words.size > 4) return false
        return words.first() !in NOT_A_NAME
    }

    private val NOT_A_NAME = setOf(
        "me", "him", "her", "them", "us", "you", "it",
        "back", "again", "later", "someone", "somebody", "anyone",
    )
}
