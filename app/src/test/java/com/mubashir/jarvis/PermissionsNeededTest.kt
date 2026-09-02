package com.mubashir.jarvis

import android.Manifest
import com.mubashir.jarvis.tools.Command
import com.mubashir.jarvis.tools.permissionsNeeded
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every command that touches something private has to say so here.
 *
 * This is a `when` over a sealed type with no `else`, so a new command will not
 * compile until somebody decides what it needs. That is the point: the failure
 * this prevents is silent — a command that reports "I need permission to read
 * your calendar" with no way to grant it, which is the same as not having built
 * the feature at all.
 */
class PermissionsNeededTest {

    @Test
    fun `calling needs the phone book and the dialler`() {
        assertEquals(
            listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE),
            Command.Call("ali").permissionsNeeded,
        )
    }

    @Test
    fun `messaging needs the phone book and sms`() {
        assertEquals(
            listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.SEND_SMS),
            Command.SendSms("ali", "hello").permissionsNeeded,
        )
    }

    @Test
    fun `location asks for coarse, not fine`() {
        // Street-level accuracy is not needed to say which part of town you are
        // in, and asking for less is the honest default.
        assertEquals(
            listOf(Manifest.permission.ACCESS_COARSE_LOCATION),
            Command.WhereAmI.permissionsNeeded,
        )
        assertTrue(
            Manifest.permission.ACCESS_FINE_LOCATION !in Command.WhereAmI.permissionsNeeded,
        )
    }

    @Test
    fun `the calendar is read-only`() {
        assertEquals(listOf(Manifest.permission.READ_CALENDAR), Command.TodaySchedule.permissionsNeeded)
        assertTrue(
            Manifest.permission.WRITE_CALENDAR !in Command.TodaySchedule.permissionsNeeded,
        )
    }

    @Test
    fun `the harmless ones ask for nothing`() {
        listOf(
            Command.Torch(on = true),
            Command.Battery,
            Command.Timer(60),
            Command.OpenApp("youtube"),
            Command.Remember("something"),
            Command.Forget("something"),
            Command.WhatYouKnow,
        ).forEach { command ->
            assertEquals(command.toString(), emptyList<String>(), command.permissionsNeeded)
        }
    }

    @Test
    fun `memory never leaves the phone, so it never asks for anything`() {
        // Worth stating on its own. Memory is the most personal thing the app
        // holds and it is entirely local — no permission, because there is
        // nothing outside the app to reach for.
        assertTrue(Command.Remember("my brother is Ali").permissionsNeeded.isEmpty())
        assertTrue(Command.WhatYouKnow.permissionsNeeded.isEmpty())
    }
}
