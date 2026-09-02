package com.mubashir.jarvis

import com.mubashir.jarvis.routine.Moment
import com.mubashir.jarvis.routine.Routine
import com.mubashir.jarvis.routine.RoutineRules
import com.mubashir.jarvis.routine.Trigger
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Almost entirely about not firing twice.
 *
 * The checker runs every fifteen minutes, so a daily routine is "due" for the
 * rest of the day unless something remembers it went, and a battery routine is
 * due continuously while the battery is flat — an alarm every quarter hour
 * until the phone dies. A routine that fires twice is worse than one that fires
 * late: the first is maddening, the second is barely noticed.
 */
class RoutineRulesTest {

    private val zone: ZoneId = ZoneId.of("UTC")

    /** A Wednesday morning. */
    private val wednesday8am: LocalDateTime = LocalDateTime.of(2026, 9, 2, 8, 0)

    private fun daily(
        at: LocalTime = LocalTime.of(8, 0),
        days: Set<DayOfWeek> = Trigger.Daily.EVERY_DAY,
        lastRun: Long? = null,
        enabled: Boolean = true,
    ) = Routine(
        id = 1,
        what = "tell me today's schedule",
        trigger = Trigger.Daily(at, days),
        enabled = enabled,
        lastRun = lastRun,
    )

    private fun moment(
        now: LocalDateTime = wednesday8am,
        battery: Int? = 80,
        charging: Boolean = false,
        next: LocalDateTime? = null,
    ) = Moment(now, battery, charging, next)

    private fun millis(at: LocalDateTime) = at.atZone(zone).toInstant().toEpochMilli()

    // ---- daily ----

    @Test
    fun `a daily routine fires at its time`() {
        assertTrue(RoutineRules.due(daily(), moment(), zone))
    }

    @Test
    fun `it does not fire before its time`() {
        assertFalse(RoutineRules.due(daily(), moment(now = wednesday8am.minusMinutes(5)), zone))
    }

    @Test
    fun `a little late still counts`() {
        // The check is periodic and the phone sleeps. Eight o'clock may not be
        // looked at until twenty past.
        assertTrue(RoutineRules.due(daily(), moment(now = wednesday8am.plusMinutes(20)), zone))
    }

    @Test
    fun `hours late is a different routine and is skipped`() {
        // Nobody wants their morning briefing at lunchtime.
        assertFalse(RoutineRules.due(daily(), moment(now = wednesday8am.plusHours(5)), zone))
    }

    @Test
    fun `it does not fire twice in one day`() {
        // The one that matters. Without this it fires every fifteen minutes for
        // the rest of the hour.
        val alreadyRan = daily(lastRun = millis(wednesday8am))
        assertFalse(RoutineRules.due(alreadyRan, moment(now = wednesday8am.plusMinutes(15)), zone))
    }

    @Test
    fun `yesterday's run does not stop today's`() {
        val ranYesterday = daily(lastRun = millis(wednesday8am.minusDays(1)))
        assertTrue(RoutineRules.due(ranYesterday, moment(), zone))
    }

    @Test
    fun `a weekday routine stays quiet at the weekend`() {
        val saturday = LocalDateTime.of(2026, 9, 5, 8, 0)
        assertTrue(saturday.dayOfWeek == DayOfWeek.SATURDAY)
        assertFalse(
            RoutineRules.due(daily(days = Trigger.Daily.WEEKDAYS), moment(now = saturday), zone),
        )
        assertTrue(RoutineRules.due(daily(days = Trigger.Daily.WEEKDAYS), moment(), zone))
    }

    @Test
    fun `a switched off routine never fires`() {
        assertFalse(RoutineRules.due(daily(enabled = false), moment(), zone))
    }

    // ---- battery ----

    private fun batteryRoutine(percent: Int = 20, lastRun: Long? = null) = Routine(
        id = 2,
        what = "tell me the battery is low",
        trigger = Trigger.BatteryBelow(percent),
        lastRun = lastRun,
    )

    @Test
    fun `a battery routine fires when it is low`() {
        assertTrue(RoutineRules.due(batteryRoutine(), moment(battery = 15), zone))
    }

    @Test
    fun `it stays quiet while the battery is fine`() {
        assertFalse(RoutineRules.due(batteryRoutine(), moment(battery = 60), zone))
    }

    @Test
    fun `plugging in is the problem being solved, so it says nothing`() {
        assertFalse(
            RoutineRules.due(batteryRoutine(), moment(battery = 15, charging = true), zone),
        )
    }

    @Test
    fun `it does not nag every fifteen minutes while the phone stays flat`() {
        val justSaid = batteryRoutine(lastRun = millis(wednesday8am.minusMinutes(30)))
        assertFalse(RoutineRules.due(justSaid, moment(battery = 12), zone))
    }

    @Test
    fun `it will say it again much later`() {
        val hoursAgo = batteryRoutine(lastRun = millis(wednesday8am.minusHours(8)))
        assertTrue(RoutineRules.due(hoursAgo, moment(battery = 12), zone))
    }

    @Test
    fun `no battery reading at all is not a low battery`() {
        assertFalse(RoutineRules.due(batteryRoutine(), moment(battery = null), zone))
    }

    // ---- before an appointment ----

    private fun beforeMeeting(minutes: Int = 20, lastRun: Long? = null) = Routine(
        id = 3,
        what = "remind me about the meeting",
        trigger = Trigger.BeforeAppointment(minutes),
        lastRun = lastRun,
    )

    @Test
    fun `it warns shortly before something starts`() {
        assertTrue(
            RoutineRules.due(beforeMeeting(), moment(next = wednesday8am.plusMinutes(10)), zone),
        )
    }

    @Test
    fun `it does not warn about the whole afternoon`() {
        assertFalse(
            RoutineRules.due(beforeMeeting(), moment(next = wednesday8am.plusHours(3)), zone),
        )
    }

    @Test
    fun `it does not warn about something that already started`() {
        assertFalse(
            RoutineRules.due(beforeMeeting(), moment(next = wednesday8am.minusMinutes(5)), zone),
        )
    }

    @Test
    fun `one warning per appointment, not one per check`() {
        val justWarned = beforeMeeting(lastRun = millis(wednesday8am.minusMinutes(5)))
        assertFalse(
            RoutineRules.due(justWarned, moment(next = wednesday8am.plusMinutes(10)), zone),
        )
    }

    @Test
    fun `nothing in the calendar means nothing to warn about`() {
        assertFalse(RoutineRules.due(beforeMeeting(), moment(next = null), zone))
    }

    // ---- how it reads ----

    @Test
    fun `each kind of routine says when it will happen`() {
        assertTrue(
            RoutineRules.nextInWords(daily(), wednesday8am).contains("At 8 am, every day"),
        )
        assertTrue(
            RoutineRules.nextInWords(daily(days = Trigger.Daily.WEEKDAYS), wednesday8am)
                .contains("on weekdays"),
        )
        assertTrue(
            RoutineRules.nextInWords(batteryRoutine(15), wednesday8am).contains("below 15 percent"),
        )
        assertTrue(
            RoutineRules.nextInWords(beforeMeeting(30), wednesday8am).contains("30 minutes before"),
        )
    }
}
