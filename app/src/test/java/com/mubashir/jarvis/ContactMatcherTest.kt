package com.mubashir.jarvis

import com.mubashir.jarvis.tools.Contact
import com.mubashir.jarvis.tools.ContactMatch
import com.mubashir.jarvis.tools.ContactMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactMatcherTest {

    private val book = listOf(
        Contact("Ali Raza", "03001111111"),
        Contact("Ali Hassan", "03002222222"),
        Contact("Bilal", "03003333333"),
        Contact("Ammi", "03004444444"),
        Contact("Usman Khan", "03005555555"),
    )

    private fun match(said: String, contacts: List<Contact> = book) =
        ContactMatcher.match(said, contacts)

    @Test
    fun `a full name matches exactly one person`() {
        val result = match("ali raza")
        assertEquals(ContactMatch.One(book[0]), result)
    }

    @Test
    fun `a single name shared by two people is a question, not a guess`() {
        // Calling the wrong Ali cannot be undone, so this must never pick one.
        val result = match("ali")
        assertTrue("$result", result is ContactMatch.Several)
        assertEquals(2, (result as ContactMatch.Several).contacts.size)
    }

    @Test
    fun `an unambiguous first name is enough`() {
        assertEquals(ContactMatch.One(book[2]), match("bilal"))
        assertEquals(ContactMatch.One(book[3]), match("ammi"))
    }

    @Test
    fun `a surname finds the person too`() {
        assertEquals(ContactMatch.One(book[4]), match("khan"))
    }

    @Test
    fun `someone not in the book is not invented`() {
        assertEquals(ContactMatch.None, match("saad"))
        assertEquals(ContactMatch.None, match(""))
        assertEquals(ContactMatch.None, match("   "))
    }

    @Test
    fun `the same person stored twice is still one person`() {
        val duplicated = listOf(
            Contact("Bilal", "0300 333 3333"),
            Contact("bilal", "03003333333"),
        )
        val result = match("bilal", duplicated)
        assertTrue("$result", result is ContactMatch.One)
    }

    @Test
    fun `two different numbers for one name still asks`() {
        // A work number and a personal one are a real choice.
        val two = listOf(
            Contact("Bilal", "03003333333"),
            Contact("Bilal", "03009999999"),
        )
        assertTrue(match("bilal", two) is ContactMatch.Several)
    }

    @Test
    fun `a fragment does not match a name`() {
        // "us" must not reach "Usman".
        assertEquals(ContactMatch.None, match("us"))
    }
}
