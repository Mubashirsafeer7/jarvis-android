package com.mubashir.jarvis

import com.mubashir.jarvis.sense.Appointment
import com.mubashir.jarvis.sense.ScheduleWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ScheduleWordsTest {

    private val now: LocalDateTime = LocalDateTime.of(2026, 9, 2, 9, 0)

    private fun at(hour: Int, minute: Int = 0, title: String = "Meeting", where: String? = null) =
        Appointment(
            title = title,
            start = now.withHour(hour).withMinute(minute),
            end = now.withHour(hour + 1).withMinute(minute),
            where = where,
        )

    @Test
    fun `an empty day says so plainly`() {
        assertEquals("Nothing in your calendar today.", ScheduleWords.describe(emptyList(), now))
    }

    @Test
    fun `something soon is counted in minutes, not read as a clock time`() {
        // "At 9:20" makes you do the arithmetic. "In twenty minutes" is the
        // answer to the question that was actually asked.
        val said = ScheduleWords.describe(listOf(at(9, 20, "Standup")), now)
        assertTrue(said, said.contains("in 20 minutes"))
    }

    @Test
    fun `something later is given as a time`() {
        val said = ScheduleWords.describe(listOf(at(14, 30, "Review")), now)
        assertTrue(said, said.contains("2:30 pm"))
    }

    @Test
    fun `a round hour drops the empty minutes`() {
        val said = ScheduleWords.describe(listOf(at(14, 0, "Review")), now)
        assertTrue(said, said.contains("2 pm"))
        assertTrue(said, !said.contains("2:00"))
    }

    @Test
    fun `something already running is on now, not starting in minus twenty minutes`() {
        val running = Appointment("Workshop", now.minusMinutes(20), now.plusMinutes(40))
        val said = ScheduleWords.describe(listOf(running), now)
        assertTrue(said, said.contains("on now"))
        assertTrue(said, !said.contains("-"))
    }

    @Test
    fun `what already finished is counted, not listed`() {
        val over = Appointment("Early call", now.minusHours(2), now.minusHours(1))
        val said = ScheduleWords.describe(listOf(over, at(11, 0, "Review")), now)
        assertTrue(said, said.contains("Review"))
        assertTrue(said, !said.contains("Early call"))
        assertTrue(said, said.contains("One has already finished"))
    }

    @Test
    fun `a day that is entirely over says so`() {
        val over = Appointment("Early call", now.minusHours(2), now.minusHours(1))
        val said = ScheduleWords.describe(listOf(over), now)
        assertTrue(said, said.contains("Nothing left today"))
    }

    @Test
    fun `a long day is summarised rather than recited`() {
        val many = (10..20).map { at(it, 0, "Thing $it") }
        val said = ScheduleWords.describe(many, now)
        assertTrue(said, said.contains("Thing 10"))
        assertTrue(said, said.contains("Thing 13"))
        // The fifth onwards are counted. Reading eleven appointments aloud is
        // not an answer.
        assertTrue(said, !said.contains("Thing 15"))
        assertTrue(said, said.contains("7 more after that"))
    }

    @Test
    fun `an all-day thing is said first and has no time`() {
        val holiday = Appointment("Public holiday", now.withHour(0), null, allDay = true)
        val said = ScheduleWords.describe(listOf(at(11, 0, "Review"), holiday), now)
        assertTrue(said, said.startsWith("Public holiday, all day"))
    }

    @Test
    fun `a place is mentioned when there is one`() {
        val said = ScheduleWords.describe(listOf(at(11, 0, "Review", where = "the office")), now)
        assertTrue(said, said.contains("at the office"))
    }

    @Test
    fun `an untitled entry is still readable`() {
        val said = ScheduleWords.describe(listOf(at(11, 0, title = "   ")), now)
        assertTrue(said, said.contains("Something untitled"))
    }
}
