package com.mubashir.jarvis.routine

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** What the phone knows at the moment routines are checked. */
data class Moment(
    val now: LocalDateTime,
    val batteryPercent: Int?,
    val charging: Boolean,
    /** When the next calendar entry starts, if there is one worth knowing about. */
    val nextAppointmentAt: LocalDateTime? = null,
)

/**
 * Whether a routine is due, which is entirely a question about not firing twice.
 *
 * Everything hard here is that shape. The check runs every fifteen minutes, so
 * a daily routine is "due" for the rest of the day unless something remembers
 * it already went; a battery routine is due continuously while the battery is
 * flat, which would be an alarm every quarter of an hour until the phone dies.
 * A routine that fires twice is worse than one that fires late, because the
 * first is annoying and the second is barely noticed.
 *
 * Pure, and tested hard, because getting it wrong is the kind of bug that only
 * shows up as "why did it tell me that four times".
 */
object RoutineRules {

    /**
     * A daily routine that missed its moment still runs, up to this late.
     *
     * The check is periodic and the phone sleeps, so a routine set for eight
     * o'clock may not be looked at until twenty past. Beyond an hour it is no
     * longer the thing that was asked for — nobody wants their morning briefing
     * at lunchtime.
     */
    const val LATE_BUT_STILL_WANTED_MINUTES = 60L

    /** A battery routine will not fire again this soon, however flat it stays. */
    const val BATTERY_QUIET_HOURS = 6L

    fun due(routine: Routine, moment: Moment, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        if (!routine.enabled) return false
        return when (val trigger = routine.trigger) {
            is Trigger.Daily -> dailyDue(routine, trigger, moment, zone)
            is Trigger.BatteryBelow -> batteryDue(routine, trigger, moment, zone)
            is Trigger.BeforeAppointment -> appointmentDue(routine, trigger, moment, zone)
        }
    }

    private fun dailyDue(
        routine: Routine,
        trigger: Trigger.Daily,
        moment: Moment,
        zone: ZoneId,
    ): Boolean {
        if (moment.now.dayOfWeek !in trigger.days) return false

        val dueAt = moment.now.toLocalDate().atTime(trigger.at)
        if (moment.now.isBefore(dueAt)) return false

        // Late is fine. Hours late is a different routine than the one asked
        // for, and is skipped until tomorrow.
        val lateBy = Duration.between(dueAt, moment.now).toMinutes()
        if (lateBy > LATE_BUT_STILL_WANTED_MINUTES) return false

        return !ranOn(routine, moment.now.toLocalDate(), zone)
    }

    private fun batteryDue(
        routine: Routine,
        trigger: Trigger.BatteryBelow,
        moment: Moment,
        zone: ZoneId,
    ): Boolean {
        val battery = moment.batteryPercent ?: return false
        if (battery > trigger.percent) return false
        // Plugged in is the problem already being solved. Saying so is noise.
        if (moment.charging) return false

        val last = routine.lastRun ?: return true
        val since = Duration.between(
            LocalDateTime.ofInstant(Instant.ofEpochMilli(last), zone),
            moment.now,
        ).toHours()
        return since >= BATTERY_QUIET_HOURS
    }

    private fun appointmentDue(
        routine: Routine,
        trigger: Trigger.BeforeAppointment,
        moment: Moment,
        zone: ZoneId,
    ): Boolean {
        val starts = moment.nextAppointmentAt ?: return false
        val minutesAway = Duration.between(moment.now, starts).toMinutes()
        // Already started is too late to warn about, and a whole afternoon away
        // is too early.
        if (minutesAway < 0 || minutesAway > trigger.minutes) return false

        val last = routine.lastRun ?: return true
        // One warning per appointment. Since the checker runs every fifteen
        // minutes, without this a twenty minute warning fires twice.
        val lastAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(last), zone)
        return Duration.between(lastAt, moment.now).toMinutes() > trigger.minutes
    }

    private fun ranOn(routine: Routine, day: LocalDate, zone: ZoneId): Boolean {
        val last = routine.lastRun ?: return false
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(last), zone).toLocalDate() == day
    }

    /** For the settings screen: when this will next happen, in plain words. */
    fun nextInWords(routine: Routine, now: LocalDateTime): String = when (val t = routine.trigger) {
        is Trigger.Daily -> {
            val days = when (t.days) {
                Trigger.Daily.EVERY_DAY -> "every day"
                Trigger.Daily.WEEKDAYS -> "on weekdays"
                else -> t.days.sorted().joinToString(", ") {
                    it.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH)
                }
            }
            "At ${clock(t.at)}, $days"
        }

        is Trigger.BatteryBelow -> "When the battery drops below ${t.percent} percent"
        is Trigger.BeforeAppointment -> "${t.minutes} minutes before anything in the calendar"
    }

    private fun clock(at: java.time.LocalTime): String = at
        .format(java.time.format.DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.ENGLISH))
        .replace(":00", "")
        .lowercase(java.util.Locale.ROOT)
}
