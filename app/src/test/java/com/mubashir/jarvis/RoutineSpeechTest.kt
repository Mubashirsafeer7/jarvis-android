package com.mubashir.jarvis

import com.mubashir.jarvis.routine.RoutineSpeech
import com.mubashir.jarvis.routine.Trigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class RoutineSpeechTest {

    private fun daily(said: String): Trigger.Daily {
        val routine = RoutineSpeech.parse(said)
        assertTrue("not parsed: $said", routine != null)
        return routine!!.trigger as Trigger.Daily
    }

    @Test
    fun `english every day, with and without am`() {
        assertEquals(LocalTime.of(8, 0), daily("every day at 8am tell me the schedule").at)
        assertEquals(LocalTime.of(8, 0), daily("every day at 8 tell me the schedule").at)
        assertEquals(LocalTime.of(23, 0), daily("daily at 11pm tell me tomorrow's schedule").at)
        assertEquals(LocalTime.of(7, 30), daily("at 7:30am every day, tell me the weather").at)
    }

    @Test
    fun `weekdays only when asked for weekdays`() {
        assertEquals(
            Trigger.Daily.WEEKDAYS,
            daily("every weekday at 9am tell me what is on today").days,
        )
        assertEquals(
            Trigger.Daily.EVERY_DAY,
            daily("every day at 9am tell me what is on today").days,
        )
    }

    @Test
    fun `urdu says which half of the day outright, and it is believed`() {
        assertEquals(LocalTime.of(8, 0), daily("roz subah 8 baje mujhe schedule batao").at)
        assertEquals(LocalTime.of(23, 0), daily("roz raat 11 baje mujhe kal ka schedule batao").at)
    }

    @Test
    fun `a bare number guesses the way that surprises people least`() {
        // Nobody sets a routine for four in the morning; plenty set one for
        // four in the afternoon. Seven upwards goes the other way — a seven
        // o'clock routine is a morning one.
        assertEquals(LocalTime.of(18, 0), daily("har roz 6 baje mujhe yaad dilao").at)
        assertEquals(LocalTime.of(8, 0), daily("har roz 8 baje mujhe yaad dilao").at)
    }

    @Test
    fun `what to do afterwards is kept as the words that were said`() {
        // It is carried out down the same path a typed message takes, so a
        // routine can do anything Jarvis can do and nothing is taught twice.
        assertEquals(
            "tell me tomorrow's schedule",
            RoutineSpeech.parse("daily at 11pm tell me tomorrow's schedule")?.what,
        )
        assertEquals(
            "mujhe kal ka schedule batao",
            RoutineSpeech.parse("roz raat 11 baje mujhe kal ka schedule batao")?.what,
        )
    }

    @Test
    fun `a battery routine in either language`() {
        assertEquals(
            Trigger.BatteryBelow(20),
            RoutineSpeech.parse("when the battery is below 20 percent tell me")?.trigger,
        )
        assertEquals(
            Trigger.BatteryBelow(15),
            RoutineSpeech.parse("if battery drops under 15 tell me to charge")?.trigger,
        )
        assertEquals(
            Trigger.BatteryBelow(20),
            RoutineSpeech.parse("battery 20 percent se kam ho to batao")?.trigger,
        )
    }

    @Test
    fun `an impossible battery level is not a routine`() {
        assertNull(RoutineSpeech.parse("when the battery is below 0 percent tell me"))
        assertNull(RoutineSpeech.parse("when the battery is below 150 percent tell me"))
    }

    @Test
    fun `an impossible time is not a routine`() {
        assertNull(RoutineSpeech.parse("every day at 99 tell me the schedule"))
        assertNull(RoutineSpeech.parse("every day at 8:99 tell me the schedule"))
    }

    @Test
    fun `a one-off request is not a routine`() {
        // The dangerous direction. Reading "tell me the schedule" as a standing
        // instruction sets something off every morning that was asked for once.
        assertNull(RoutineSpeech.parse("tell me the schedule"))
        assertNull(RoutineSpeech.parse("what is on today"))
        assertNull(RoutineSpeech.parse("torch on"))
        assertNull(RoutineSpeech.parse("hi"))
        assertNull(RoutineSpeech.parse(""))
    }

    @Test
    fun `a sentence that merely contains every day is not a routine`() {
        assertNull(RoutineSpeech.parse("every day is hard"))
        assertNull(RoutineSpeech.parse("I go there every day"))
    }

    @Test
    fun `a routine with nothing to do is not a routine`() {
        assertNull(RoutineSpeech.parse("every day at 8am"))
        assertNull(RoutineSpeech.parse("every day at 8am ok"))
    }

    @Test
    fun `midnight and midday are not confused with each other`() {
        assertEquals(LocalTime.of(0, 0), RoutineSpeech.timeOf("12am", null))
        assertEquals(LocalTime.of(12, 0), RoutineSpeech.timeOf("12pm", null))
        assertEquals(LocalTime.of(13, 0), RoutineSpeech.timeOf("1pm", null))
        assertEquals(LocalTime.of(1, 0), RoutineSpeech.timeOf("1am", null))
    }
}
