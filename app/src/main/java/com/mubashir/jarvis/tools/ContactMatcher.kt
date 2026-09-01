package com.mubashir.jarvis.tools

import java.util.Locale

/** A name and number as the phone stores them. */
data class Contact(val name: String, val number: String)

/** What the matcher concluded. Ambiguity is a result, not a failure. */
sealed interface ContactMatch {
    data class One(val contact: Contact) : ContactMatch
    data class Several(val contacts: List<Contact>) : ContactMatch
    data object None : ContactMatch
}

/**
 * Works out which contact was meant.
 *
 * This is the most dangerous matching in the app: the cost of being wrong is a
 * call or a message to the wrong person, which cannot be taken back. So it is
 * deliberately reluctant — several plausible people is an answer ("which one?"),
 * never a guess, and a weak partial match is no match at all.
 *
 * Pure logic, so the rules can be tested without a contacts database.
 */
object ContactMatcher {

    fun match(said: String, contacts: List<Contact>): ContactMatch {
        val wanted = normalise(said)
        if (wanted.isEmpty()) return ContactMatch.None

        val scored = contacts
            .mapNotNull { contact -> score(wanted, contact)?.let { contact to it } }
        if (scored.isEmpty()) return ContactMatch.None

        val best = scored.maxOf { it.second }
        val winners = scored.filter { it.second == best }.map { it.first }

        // Same person stored twice — one name, one number, no ambiguity worth
        // asking about.
        val distinct = winners.distinctBy { normalise(it.name) to it.number.filter(Char::isDigit) }
        return when {
            distinct.size == 1 -> ContactMatch.One(distinct.single())
            else -> ContactMatch.Several(distinct)
        }
    }

    private fun normalise(text: String) =
        text.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() || it == ' ' }.trim()

    private fun score(wanted: String, contact: Contact): Int? {
        val name = normalise(contact.name)
        if (name.isEmpty()) return null
        val words = name.split(' ').filter { it.isNotEmpty() }

        return when {
            name == wanted -> 100
            // "ali" should find "Ali Raza", and the first name is the one people say.
            words.firstOrNull() == wanted -> 90
            words.contains(wanted) -> 80
            // "ali raza" against "Ali Raza Khan".
            name.startsWith("$wanted ") -> 70
            else -> null
        }
    }
}
