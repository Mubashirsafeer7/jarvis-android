package com.mubashir.jarvis.sense

import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What is true right now, as far as the phone can tell. */
data class Situation(
    val now: LocalDateTime,
    /** 0..100, or null when it could not be read. */
    val batteryPercent: Int? = null,
    val charging: Boolean = false,
    val nextAppointment: Appointment? = null,
    val unreadCount: Int = 0,
)

/**
 * The one or two lines about the present that travel with every message.
 *
 * A person you ask for help already knows it is Tuesday afternoon, that your
 * phone is nearly flat and that you have something at four. An assistant that
 * has to be told the time before it can answer a question about the time is not
 * an assistant, and asking it to call a tool for every such fact is slow and,
 * on a small model, unreliable.
 *
 * The hard part is restraint. This rides in front of every single message on a
 * model with a small context, so anything included has to earn its tokens.
 * Battery is only worth saying when it is a problem. An appointment is only
 * worth saying when it is close. Location is never included — it costs tokens
 * on every message to answer a question that is almost never asked, and it is
 * the most private thing here.
 */
object SituationWords {

    /** Below this the battery is news. Above it, it is trivia. */
    const val BATTERY_WORTH_MENTIONING = 25

    /** Beyond this an appointment is not "coming up", it is just today. */
    const val APPOINTMENT_WITHIN_HOURS = 4L

    /**
     * @return the context line, or null when nothing about now is worth saying
     */
    fun describe(situation: Situation): String? {
        val parts = buildList {
            add(clock(situation.now))

            val battery = situation.batteryPercent
            if (battery != null && battery <= BATTERY_WORTH_MENTIONING && !situation.charging) {
                add("battery $battery percent")
            }

            situation.nextAppointment
                ?.takeIf { it.start.isAfter(situation.now) }
                ?.takeIf {
                    Duration.between(situation.now, it.start).toHours() < APPOINTMENT_WITHIN_HOURS
                }
                ?.let { add("next up, ${soon(it, situation.now)}") }

            if (situation.unreadCount > 0) {
                add("${situation.unreadCount} unread")
            }
        }

        // The time alone is not context worth spending tokens on — the model
        // does not need to be told it is Tuesday to answer most questions.
        if (parts.size <= 1) return null
        return "Right now: " + parts.joinToString(", ") + "."
    }

    /** The block as it is handed to the model, or the prompt unchanged. */
    fun withContext(prompt: String, situation: Situation): String {
        val line = describe(situation) ?: return prompt
        return "$line\n\n$prompt"
    }

    private fun clock(now: LocalDateTime): String {
        // ENGLISH, not ROOT. Locale.ROOT has no day names of its own and falls
        // back to the abbreviations, so a FULL style still comes out as "Wed" —
        // which reads as a database field rather than as a person talking.
        val day = now.dayOfWeek.getDisplayName(
            java.time.format.TextStyle.FULL,
            Locale.ENGLISH,
        )
        val time = now.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ROOT))
            .lowercase(Locale.ROOT)
        return "$day $time"
    }

    private fun soon(appointment: Appointment, now: LocalDateTime): String {
        val minutes = Duration.between(now, appointment.start).toMinutes()
        val title = appointment.title.trim().ifEmpty { "something" }
        return when {
            minutes < 60 -> "$title in $minutes minutes"
            else -> "$title at " + appointment.start
                .format(DateTimeFormatter.ofPattern("h:mm a", Locale.ROOT))
                .replace(":00", "")
                .lowercase(Locale.ROOT)
        }
    }
}
