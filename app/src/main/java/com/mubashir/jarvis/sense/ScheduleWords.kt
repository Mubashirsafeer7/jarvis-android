package com.mubashir.jarvis.sense

import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One thing in the calendar, as the phone stores it. */
data class Appointment(
    val title: String,
    val start: LocalDateTime,
    val end: LocalDateTime?,
    val allDay: Boolean = false,
    val where: String? = null,
)

/**
 * Today's calendar, said out loud.
 *
 * Pure, because the awkward parts here are all judgement rather than data: how
 * many to read before it stops being an answer, what to do about the three that
 * already happened, and how to say a time so it sounds like a person rather
 * than a database. None of those need a phone to get right, and all of them are
 * easy to get wrong in a way nobody notices until it is being read aloud.
 */
object ScheduleWords {

    /**
     * Beyond this it stops being an answer and becomes a recitation. The rest
     * are counted rather than named.
     */
    const val SPOKEN_LIMIT = 4

    fun describe(appointments: List<Appointment>, now: LocalDateTime): String {
        if (appointments.isEmpty()) return "Nothing in your calendar today."

        val sorted = appointments.sortedWith(
            compareByDescending<Appointment> { it.allDay }.thenBy { it.start },
        )

        // What is still to come is the answer to "what is my schedule". What
        // already happened is history, and only worth a count.
        val ahead = sorted.filter { it.allDay || (it.end ?: it.start) >= now }
        val done = sorted.size - ahead.size

        if (ahead.isEmpty()) {
            return "Nothing left today. ${countOf(done)} already been and gone."
        }

        val named = ahead.take(SPOKEN_LIMIT).joinToString(". ") { line(it, now) }
        val unnamed = ahead.size - SPOKEN_LIMIT

        return buildString {
            append(named)
            if (unnamed > 0) append(". And $unnamed more after that")
            if (done > 0) append(". ${countOf(done)} already finished")
            append(".")
        }.replace("..", ".")
    }

    private fun line(appointment: Appointment, now: LocalDateTime): String {
        val title = appointment.title.trim().ifEmpty { "Something untitled" }
        if (appointment.allDay) return "$title, all day"

        val minutesAway = java.time.Duration.between(now, appointment.start).toMinutes()
        val when_ = when {
            // Already started but not finished. "At 10" would be wrong and
            // "in minus twenty minutes" is worse.
            minutesAway < 0 -> "on now"
            minutesAway < 1 -> "starting now"
            minutesAway < 60 -> "in $minutesAway minutes"
            else -> "at ${clock(appointment.start.toLocalTime())}"
        }
        val where = appointment.where?.trim()?.takeIf { it.isNotEmpty() }
        return if (where != null) "$title $when_, at $where" else "$title $when_"
    }

    /** Half past nine, not 09:30:00. */
    private fun clock(time: LocalTime): String =
        time.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ROOT))
            .replace(":00", "")
            .lowercase(Locale.ROOT)

    private fun countOf(many: Int) = if (many == 1) "One has" else "$many have"
}
