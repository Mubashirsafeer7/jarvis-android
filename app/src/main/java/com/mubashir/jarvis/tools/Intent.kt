package com.mubashir.jarvis.tools

/**
 * Something Jarvis can actually do, once the words have been understood.
 *
 * Deliberately a closed set rather than free-form strings: the router and the
 * model both produce these, and a typo in a tool name should not compile.
 */
sealed interface Command {
    data class Call(val who: String) : Command
    data class SendSms(val who: String, val message: String) : Command
    data class OpenApp(val name: String) : Command
    data class Torch(val on: Boolean) : Command
    data class SetAlarm(val hour: Int, val minute: Int) : Command
    data class Timer(val seconds: Int) : Command
    data object Battery : Command
    data object WhereAmI : Command
    data object TodaySchedule : Command
    data object ReadNotifications : Command

    /** Commit something to memory, because the user said so outright. */
    data class Remember(val what: String) : Command

    /** Drop what is known about something. */
    data class Forget(val what: String) : Command

    /** Say what is remembered. */
    data object WhatYouKnow : Command
}

/** True for anything that reaches the outside world and cannot be taken back. */
val Command.needsConfirmation: Boolean
    get() = when (this) {
        is Command.Call, is Command.SendSms -> true
        else -> false
    }
