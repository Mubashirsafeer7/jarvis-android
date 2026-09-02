package com.mubashir.jarvis.routine

import java.time.LocalTime
import java.util.Locale

/**
 * Turns "roz subah 8 baje mujhe schedule batao" into a routine.
 *
 * Narrow on purpose, in the same way the memory noticer is. A missed phrasing
 * costs one retry; a misread one silently sets an alarm for the wrong time and
 * is not discovered until it goes off — or worse, until it does not.
 *
 * The time is the only part that has to be exactly right. What to do afterwards
 * is left as the words the user said, and carried out down the same path a
 * typed message takes, so a routine can do anything Jarvis can do.
 */
object RoutineSpeech {

    /** @return the routine described, or null when this was not one */
    fun parse(said: String): Routine? {
        val text = said.lowercase(Locale.ROOT).replace(Regex("""\s+"""), " ").trim()
        if (text.isEmpty()) return null

        batteryRoutine(text)?.let { return it }
        dailyRoutine(text)?.let { return it }
        return null
    }

    // ---- every day at a time ----

    private val DAILY = listOf(
        // "every day at 8am tell me the schedule"
        Regex("""^(?:every ?day|daily|each day) at (\S+?)(?:,)? (.+)$"""),
        Regex("""^every (weekday|morning|evening|night) at (\S+?)(?:,)? (.+)$"""),
        // "roz subah 8 baje mujhe schedule batao"
        Regex("""^(?:roz|har\s?roz|rozana|daily) (?:subah |sham |shaam |raat |dopehr )?(\S+?) baje (.+)$"""),
        // "at 8am every day, tell me the schedule"
        Regex("""^at (\S+?) (?:every ?day|daily)(?:,)? (.+)$"""),
    )

    private fun dailyRoutine(text: String): Routine? {
        for (pattern in DAILY) {
            val match = pattern.matchEntire(text) ?: continue
            val groups = match.groupValues.drop(1)

            // The "every weekday at ..." shape has one group more than the rest.
            val weekdaysOnly = groups.size == 3 && groups[0] == "weekday"
            val partOfDay = if (groups.size == 3) groups[0] else null
            val rawTime = if (groups.size == 3) groups[1] else groups[0]
            val what = groups.last().trim()

            val at = timeOf(rawTime, hintFrom(text, partOfDay)) ?: continue
            if (what.length < 3) continue

            return Routine(
                what = what,
                trigger = Trigger.Daily(
                    at = at,
                    days = if (weekdaysOnly) Trigger.Daily.WEEKDAYS else Trigger.Daily.EVERY_DAY,
                ),
            )
        }
        return null
    }

    /**
     * Which half of the day a bare number means.
     *
     * "8 baje" is ambiguous and the words around it are the only evidence. Urdu
     * says it outright — subah, raat — and English says am or pm. With nothing
     * to go on, the guess that surprises people least is that 1 to 6 means the
     * afternoon and 7 to 12 means the morning: nobody sets a routine for four
     * in the morning, and plenty set one for four in the afternoon.
     */
    private fun hintFrom(text: String, partOfDay: String?): Boolean? = when {
        text.contains("subah") || text.contains("morning") || partOfDay == "morning" -> true
        text.contains("raat") || text.contains("night") || partOfDay == "night" -> false
        text.contains("sham") || text.contains("shaam") ||
            text.contains("evening") || partOfDay == "evening" -> false

        text.contains("dopehr") || text.contains("afternoon") -> false
        else -> null
    }

    private val TIME = Regex("""^(\d{1,2})(?::(\d{2}))?\s*(am|pm)?$""")

    /** @param morning true for am, false for pm, null when nothing said either way */
    internal fun timeOf(raw: String, morning: Boolean?): LocalTime? {
        val match = TIME.matchEntire(raw.trim()) ?: return null
        var hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: 0
        if (hour !in 0..23 || minute !in 0..59) return null

        val suffix = match.groupValues[3]
        val isMorning = when {
            suffix == "am" -> true
            suffix == "pm" -> false
            morning != null -> morning
            // Nothing said. 1-6 is the afternoon, 7-12 the morning.
            hour in 1..6 -> false
            else -> true
        }

        if (hour <= 12) {
            if (!isMorning && hour != 12) hour += 12
            if (isMorning && hour == 12) hour = 0
        }
        if (hour !in 0..23) return null
        return LocalTime.of(hour, minute)
    }

    // ---- when the battery is low ----

    private val BATTERY = listOf(
        Regex("""^(?:when|if) (?:the )?battery (?:is |goes |drops |falls )?(?:below |under )?(\d{1,3})(?: percent| %)?(?:,)? (.+)$"""),
        Regex("""^battery (\d{1,3})(?: percent| %)? (?:se )?(?:kam ho(?:ne par)?|neeche ho) (?:to )?(.+)$"""),
    )

    private fun batteryRoutine(text: String): Routine? {
        for (pattern in BATTERY) {
            val match = pattern.matchEntire(text) ?: continue
            val percent = match.groupValues[1].toIntOrNull() ?: continue
            if (percent !in 1..99) continue
            val what = match.groupValues[2].trim()
            if (what.length < 3) continue
            return Routine(what = what, trigger = Trigger.BatteryBelow(percent))
        }
        return null
    }
}
