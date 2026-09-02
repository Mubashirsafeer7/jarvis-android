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
        val tidied = tidy(input)
        val text = tidied.lowercase(Locale.ROOT)
        if (text.isEmpty()) return null

        // Before everything else. "remember to call ali at five" is a thing to
        // write down, not an instruction to dial anybody — and the call
        // patterns would happily have read it as one.
        //
        // Given both spellings: matching wants the lowercase one, but a fact is
        // read aloud and shown in settings, and "my brother is called ali"
        // reads as though Jarvis was not listening properly.
        memory(text, tidied)?.let { return it }

        // Before the rest, for the same reason memory is: "every day at 8am
        // turn the torch on" is an instruction to set something up, and the
        // torch patterns would happily read it as an instruction to do it now.
        routines(text, tidied)?.let { return it }

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
    private fun normalise(input: String): String = tidy(input).lowercase(Locale.ROOT)

    /** Everything [normalise] does except flattening the case. */
    private fun tidy(input: String): String = input
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

    private val REMEMBER = listOf(
        Regex("""^(?:please )?remember (?:that |this[:,]? )?(.+)$"""),
        Regex("""^(?:yaad|yad) (?:rakh(?:o|na|lo|lena|iye)?|karlo) (?:ke |ki )?(.+)$"""),
        Regex("""^(?:note|likh) (?:kar\s*(?:o|lo|do)|karo|lo) (?:ke |ki )?(.+)$"""),
        Regex("""^(.+?) (?:yaad|yad) rakh(?:o|na|lo|lena|iye)$"""),
    )

    private val FORGET = listOf(
        Regex("""^forget (?:about |that |what you know about )?(.+)$"""),
        Regex("""^(?:bhool|bhul) ja(?:o|na|iye) (.+)$"""),
        Regex("""^(.+?) (?:bhool|bhul) ja(?:o|na|iye)$"""),
    )

    private val WHAT_YOU_KNOW = Regex(
        """^(?:what do you (?:know|remember)(?: about me| about us)?|""" +
            """what have you remembered|""" +
            """(?:tumhe|tumko|tume) (?:mere baare mein |mere bare mein )?kya (?:pata|yaad) hai|""" +
            """kya kya yaad hai|memory (?:dikhao|batao|kya hai))$"""
    )

    private fun memory(text: String, cased: String): Command? {
        if (WHAT_YOU_KNOW.matches(text)) return Command.WhatYouKnow

        for (pattern in FORGET) {
            val what = pattern.matchEntire(text)?.groupValues?.get(1)?.trim().orEmpty()
            if (what.isNotEmpty()) return Command.Forget(what)
        }
        for (pattern in REMEMBER) {
            val match = pattern.matchEntire(text) ?: continue
            val what = asWritten(match, cased, text).trim()
            // One word is a mishearing, not a fact worth keeping.
            if (what.length > 2 && what.contains(' ')) return Command.Remember(what)
        }
        return null
    }

    /**
     * The matched group as the user actually wrote it.
     *
     * Matching happens on the flattened text, so the group's range indexes that
     * — and lowercasing is character-for-character for everything this will
     * ever see. Where it is not, the flattened text is still correct, only
     * uglier, so the length check falls back rather than slicing at the wrong
     * place.
     */
    private fun asWritten(match: MatchResult, cased: String, lowered: String): String {
        val range = match.groups[1]?.range ?: return ""
        if (cased.length != lowered.length) return match.groupValues[1]
        return cased.substring(range.first, range.last + 1)
    }

    private val LIST_ROUTINES = Regex(
        """^(?:what (?:are )?my routines|list (?:my )?routines|""" +
            """(?:meri |mere )?routines? (?:kya hain|batao|dikhao)|""" +
            """what do you do (?:by yourself|automatically))$"""
    )

    /** The shapes that mean a standing instruction rather than a one-off. */
    private val ROUTINE_HINTS = listOf(
        Regex("""^(?:every ?day|daily|each day|every weekday)\b"""),
        Regex("""^at \S+ (?:every ?day|daily)\b"""),
        Regex("""^(?:roz|har\s?roz|rozana)\b"""),
        Regex("""^(?:when|if) (?:the )?battery\b"""),
        Regex("""^battery \d{1,3}\b"""),
    )

    private fun routines(text: String, cased: String): Command? {
        if (LIST_ROUTINES.matches(text)) return Command.ListRoutines
        if (ROUTINE_HINTS.none { it.containsMatchIn(text) }) return null
        // The words as written, because what a routine does is kept verbatim
        // and shown back in settings.
        return Command.AddRoutine(cased)
    }

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
        """^(?:read (?:my )?notifications|what(?:'s| is) new|""" +
            """what did i miss|kya (?:kuch )?miss (?:hua|kiya)|""" +
            """(?:naye |new )?(?:messages|notifications)(?: padho| dikhao| batao)?)$"""
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
