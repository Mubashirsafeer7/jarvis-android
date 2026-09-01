package com.mubashir.jarvis.tools

/**
 * A command with everything resolved — a real number rather than a name.
 *
 * Separate from [Command] on purpose: a Command is what was understood, an
 * Action is what will happen. Only an Action can be carried out, so nothing can
 * place a call without having first resolved exactly who to.
 */
sealed interface Action {
    data class Call(val contact: Contact) : Action
    data class Sms(val contact: Contact, val message: String) : Action
}

/** What the app needs from the user before it can go on. */
sealed interface Ask {
    /** Irreversible, so it is spelled out and confirmed first. */
    data class Confirm(val action: Action) : Ask

    /** More than one person matched. Never resolved by guessing. */
    data class Choose(val contacts: List<Contact>, val message: String?) : Ask

    /** Android has to grant something before this can be tried again. */
    data class NeedPermission(val permissions: List<String>, val reason: String) : Ask
}
