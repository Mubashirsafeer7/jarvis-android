package com.mubashir.jarvis

import com.mubashir.jarvis.sense.Appointment
import com.mubashir.jarvis.sense.Situation
import com.mubashir.jarvis.sense.SituationWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * Mostly about restraint. This rides in front of every single message on a
 * model with a small context, so anything included has to earn its tokens.
 */
class SituationWordsTest {

    /** A Wednesday afternoon, which the day-name assertions below rely on. */
    private val now: LocalDateTime = LocalDateTime.of(2026, 9, 2, 15, 42)

    @Test
    fun `the time alone is not worth saying`() {
        // The model does not need to be told it is Tuesday to answer most
        // questions, and this costs tokens on every message.
        assertNull(SituationWords.describe(Situation(now = now, batteryPercent = 80)))
    }

    @Test
    fun `a low battery is worth saying`() {
        val said = SituationWords.describe(Situation(now = now, batteryPercent = 12))
        assertTrue(said!!, said.contains("battery 12 percent"))
        assertTrue(said, said.contains("Wednesday"))
        assertTrue(said, said.contains("3:42 pm"))
    }

    @Test
    fun `a low battery on the charger is not a problem`() {
        assertNull(
            SituationWords.describe(Situation(now = now, batteryPercent = 12, charging = true)),
        )
    }

    @Test
    fun `something soon is worth saying, in minutes`() {
        val soon = Appointment("Review", now.plusMinutes(18), now.plusHours(1))
        val said = SituationWords.describe(Situation(now = now, nextAppointment = soon))
        assertTrue(said!!, said.contains("Review in 18 minutes"))
    }

    @Test
    fun `something later today is given as a time`() {
        val later = Appointment("Review", now.plusHours(2), null)
        val said = SituationWords.describe(Situation(now = now, nextAppointment = later))
        assertTrue(said!!, said.contains("Review at 5:42 pm"))
    }

    @Test
    fun `something far off is not coming up`() {
        // Beyond a few hours it is not "next up", it is just today, and the
        // calendar command is the place to ask about that.
        val far = Appointment("Dinner", now.plusHours(6), null)
        assertNull(SituationWords.describe(Situation(now = now, nextAppointment = far)))
    }

    @Test
    fun `something that already started is not next up`() {
        val started = Appointment("Standup", now.minusMinutes(10), now.plusMinutes(20))
        assertNull(SituationWords.describe(Situation(now = now, nextAppointment = started)))
    }

    @Test
    fun `unread messages are counted, never quoted`() {
        // The count is context. The contents are not, and putting them in front
        // of every message would send the whole shade to the model.
        val said = SituationWords.describe(Situation(now = now, unreadCount = 4))
        assertTrue(said!!, said.contains("4 unread"))
        // The count and nothing else. No sender, no subject, no body.
        assertTrue(said, said.endsWith("4 unread."))
    }

    @Test
    fun `location is never included`() {
        // It costs tokens on every message to answer a question almost never
        // asked, and it is the most private thing here. Asking outright still
        // works; volunteering it does not.
        val everything = Situation(
            now = now,
            batteryPercent = 5,
            nextAppointment = Appointment("Review", now.plusMinutes(10), null),
            unreadCount = 3,
        )
        val said = SituationWords.describe(everything)!!
        assertTrue(said, !said.contains("north"))
        assertTrue(said, !said.contains("Karachi"))
    }

    @Test
    fun `nothing worth saying leaves the prompt exactly as it was`() {
        val quiet = Situation(now = now, batteryPercent = 90)
        assertEquals("what is two plus two", SituationWords.withContext("what is two plus two", quiet))
    }

    @Test
    fun `the question survives at the end, where the model will read it`() {
        val urgent = Situation(now = now, batteryPercent = 4)
        val sent = SituationWords.withContext("should I go out", urgent)
        assertTrue(sent, sent.trimEnd().endsWith("should I go out"))
        assertTrue(sent, sent.contains("battery 4 percent"))
    }
}
