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

    /** Set up a standing instruction. */
    data class AddRoutine(val said: String) : Command

    /** Say what the standing instructions are. */
    data object ListRoutines : Command
}

/**
 * What Android must have granted before this command can be carried out.
 *
 * Kept next to the commands rather than inside whatever runs them, so that
 * adding a command with a permission and forgetting to ask for it is a visible
 * omission in one place instead of a dead end the user meets at the moment they
 * ask for something. "I need permission to read your calendar", with no way to
 * grant it, is the same as not having built the feature.
 */
val Command.permissionsNeeded: List<String>
    get() = when (this) {
        is Command.Call -> listOf(
            android.Manifest.permission.READ_CONTACTS,
            android.Manifest.permission.CALL_PHONE,
        )

        is Command.SendSms -> listOf(
            android.Manifest.permission.READ_CONTACTS,
            android.Manifest.permission.SEND_SMS,
        )

        is Command.WhereAmI -> listOf(android.Manifest.permission.ACCESS_COARSE_LOCATION)
        is Command.TodaySchedule -> listOf(android.Manifest.permission.READ_CALENDAR)

        is Command.Torch,
        is Command.Battery,
        is Command.Timer,
        is Command.OpenApp,
        is Command.SetAlarm,
        is Command.ReadNotifications,
        is Command.Remember,
        is Command.Forget,
        is Command.WhatYouKnow,
        is Command.AddRoutine,
        is Command.ListRoutines,
        -> emptyList()
    }

/** True for anything that reaches the outside world and cannot be taken back. */
val Command.needsConfirmation: Boolean
    get() = when (this) {
        is Command.Call, is Command.SendSms -> true
        else -> false
    }
