package com.mubashir.jarvis

import com.mubashir.jarvis.sense.Fix
import com.mubashir.jarvis.sense.PlaceWords
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceWordsTest {

    private fun fix(accuracy: Float = 12f, age: Long = 0) =
        Fix(latitude = 24.8607, longitude = 67.0011, accuracy = accuracy, ageMinutes = age)

    @Test
    fun `no fix at all is explained, not left blank`() {
        val said = PlaceWords.describe(null, null)
        assertTrue(said, said.contains("cannot get a position"))
        assertTrue(said, said.contains("switched off"))
    }

    @Test
    fun `a good fix with an address just says the place`() {
        val said = PlaceWords.describe(fix(), "Saddar, Karachi")
        assertTrue(said, said.contains("You are Saddar, Karachi"))
        assertTrue(said, !said.contains("near"))
        assertTrue(said, !said.contains("metres"))
    }

    @Test
    fun `a vague fix says near, rather than stating a street as fact`() {
        // A tower fix can be kilometres wide. Naming the street it happens to
        // land on is confidently wrong, and "near" costs one word.
        val said = PlaceWords.describe(fix(accuracy = 2400f), "Saddar, Karachi")
        assertTrue(said, said.contains("somewhere near Saddar"))
    }

    @Test
    fun `no address falls back to coordinates and admits the error`() {
        val said = PlaceWords.describe(fix(accuracy = 1800f), null)
        assertTrue(said, said.contains("24.8607 north"))
        assertTrue(said, said.contains("67.0011 east"))
        assertTrue(said, said.contains("give or take 1800 metres"))
    }

    @Test
    fun `southern and western coordinates keep their sign as a word`() {
        val south = Fix(-33.8688, -151.2093, 20f, 0)
        val said = PlaceWords.describe(south, null)
        assertTrue(said, said.contains("33.8688 south"))
        assertTrue(said, said.contains("151.2093 west"))
        // A minus sign read aloud is meaningless.
        assertTrue(said, !said.contains("-"))
    }

    @Test
    fun `a stale fix says when it was, rather than passing as now`() {
        val said = PlaceWords.describe(fix(age = 40), "Saddar, Karachi")
        assertTrue(said, said.contains("from 40 minutes ago"))
    }

    @Test
    fun `a very old fix is given in hours`() {
        val said = PlaceWords.describe(fix(age = 200), "Saddar, Karachi")
        assertTrue(said, said.contains("from 3 hours ago"))
    }

    @Test
    fun `a fresh fix does not mention time at all`() {
        val said = PlaceWords.describe(fix(age = 3), "Saddar, Karachi")
        assertTrue(said, !said.contains("ago"))
    }
}
