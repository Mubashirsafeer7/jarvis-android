package com.mubashir.jarvis.routine

import java.time.DayOfWeek
import java.time.LocalTime

/** What sets a routine off. */
sealed interface Trigger {

    /** At a time, on the chosen days. */
    data class Daily(val at: LocalTime, val days: Set<DayOfWeek>) : Trigger {
        companion object {
            val EVERY_DAY: Set<DayOfWeek> = DayOfWeek.entries.toSet()
            val WEEKDAYS: Set<DayOfWeek> = setOf(
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
            )
        }
    }

    /** When the battery falls this low, and not again until it has recovered. */
    data class BatteryBelow(val percent: Int) : Trigger

    /** Shortly before something in the calendar. */
    data class BeforeAppointment(val minutes: Int) : Trigger
}

/**
 * Something Jarvis does without being asked each time.
 *
 * [what] is written the way the user said it — "tell me today's schedule" — and
 * is carried out down exactly the same path a typed message takes. So a routine
 * can do anything Jarvis can do, and nothing has to be taught twice.
 */
data class Routine(
    val id: Long = 0,
    val what: String,
    val trigger: Trigger,
    val enabled: Boolean = true,
    /** Epoch millis of the last time this actually fired. */
    val lastRun: Long? = null,
)
