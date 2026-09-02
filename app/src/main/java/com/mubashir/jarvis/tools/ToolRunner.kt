package com.mubashir.jarvis.tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.os.BatteryManager
import android.provider.AlarmClock
import com.mubashir.jarvis.R
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What happened, in words Jarvis can say back. */
sealed interface ToolOutcome {
    /** Done. [spoken] is the confirmation to read out. */
    data class Done(val spoken: String) : ToolOutcome

    /** Understood, but this one is not built yet — said plainly, never faked. */
    data class NotYet(val spoken: String) : ToolOutcome

    data class Failed(val spoken: String) : ToolOutcome
}

/**
 * Carries out the commands the router recognised.
 *
 * Only the ones that need no dangerous permission are wired here. Calls,
 * messages, location and the rest are recognised and answered honestly rather
 * than half-done: an assistant that says it sent a message it did not send is
 * worse than one that says it cannot yet.
 */
class ToolRunner(private val context: Context) {

    suspend fun run(command: Command): ToolOutcome = withContext(Dispatchers.Default) {
        when (command) {
            is Command.Torch -> torch(command.on)
            is Command.Battery -> battery()
            is Command.Timer -> timer(command.seconds)
            is Command.OpenApp -> openApp(command.name)

            // Calls and messages never arrive here as commands — they are
            // resolved to a real contact and confirmed on screen first, then
            // carried out through perform().
            is Command.Call -> notYet(R.string.tool_no_calls)
            is Command.SendSms -> notYet(R.string.tool_no_messages)
            is Command.WhereAmI -> notYet(R.string.tool_no_location)
            is Command.TodaySchedule -> notYet(R.string.tool_no_calendar)
            is Command.ReadNotifications -> notYet(R.string.tool_no_notifications)
            is Command.SetAlarm -> notYet(R.string.tool_no_alarm)

            // Memory needs the store, which belongs to the runtime rather than
            // to a stateless tool runner. Listed rather than swept into an else
            // so that adding a command here is a compile error until somebody
            // decides where it is handled — which is how these three were
            // caught.
            is Command.Remember,
            is Command.Forget,
            is Command.WhatYouKnow,
            -> error("Memory commands are handled by the runtime, not the tool runner.")
        }
    }

    private fun notYet(res: Int) = ToolOutcome.NotYet(context.getString(res))

    /** Carries out an action the user has already confirmed on screen. */
    suspend fun perform(action: Action): ToolOutcome = withContext(Dispatchers.Default) {
        when (action) {
            is Action.Call -> call(action.contact)
            is Action.Sms -> sms(action.contact, action.message)
        }
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED

    private fun call(contact: Contact): ToolOutcome = runCatching {
        // Checked here as well as before the confirmation was shown. Nothing
        // that dials a number should trust that somebody upstream did it — and
        // reaching the dialler without it arrives as a SecurityException, which
        // reads to the user as an unexplained failure.
        if (!granted(Manifest.permission.CALL_PHONE)) {
            return ToolOutcome.Failed(context.getString(R.string.tool_no_call_permission))
        }
        val intent = Intent(Intent.ACTION_CALL)
            .setData(Uri.fromParts("tel", contact.number, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        ToolOutcome.Done(context.getString(R.string.tool_calling, contact.name))
    }.getOrElse { ToolOutcome.Failed(context.getString(R.string.tool_call_failed)) }

    private fun sms(contact: Contact, message: String): ToolOutcome = runCatching {
        if (!granted(Manifest.permission.SEND_SMS)) {
            return ToolOutcome.Failed(context.getString(R.string.tool_no_sms_permission))
        }
        // SmsManager only became available through getSystemService in API 31,
        // and minSdk here is 30 — on that one release it has to come from the
        // deprecated accessor or it is simply null.
        val manager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        } ?: return ToolOutcome.Failed(context.getString(R.string.tool_sms_failed))
        // A long message has to be split or the network silently drops the tail.
        val parts = manager.divideMessage(message)
        if (parts.size == 1) {
            manager.sendTextMessage(contact.number, null, message, null, null)
        } else {
            manager.sendMultipartTextMessage(contact.number, null, parts, null, null)
        }
        ToolOutcome.Done(context.getString(R.string.tool_sms_sent, contact.name))
    }.getOrElse { ToolOutcome.Failed(context.getString(R.string.tool_sms_failed)) }

    private fun torch(on: Boolean): ToolOutcome = runCatching {
        val cameras = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        // The back camera is not always id "0", and not every camera has a
        // flash — ask which one does rather than assuming.
        val id = cameras.cameraIdList.firstOrNull { cameraId ->
            cameras.getCameraCharacteristics(cameraId)
                .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return ToolOutcome.Failed(context.getString(R.string.tool_no_torch))

        cameras.setTorchMode(id, on)
        ToolOutcome.Done(
            context.getString(if (on) R.string.tool_torch_on else R.string.tool_torch_off),
        )
    }.getOrElse { ToolOutcome.Failed(context.getString(R.string.tool_torch_failed)) }

    private fun battery(): ToolOutcome = runCatching {
        val manager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val percent = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (percent !in 0..100) {
            return ToolOutcome.Failed(context.getString(R.string.tool_battery_unknown))
        }
        val charging = manager.isCharging
        ToolOutcome.Done(
            context.getString(
                if (charging) R.string.tool_battery_charging else R.string.tool_battery,
                percent,
            ),
        )
    }.getOrElse { ToolOutcome.Failed(context.getString(R.string.tool_battery_unknown)) }

    private fun timer(seconds: Int): ToolOutcome = runCatching {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            .putExtra(AlarmClock.EXTRA_MESSAGE, context.getString(R.string.app_name))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) == null) {
            return ToolOutcome.Failed(context.getString(R.string.tool_no_clock))
        }
        context.startActivity(intent)
        ToolOutcome.Done(context.getString(R.string.tool_timer_set, describe(seconds)))
    }.getOrElse { ToolOutcome.Failed(context.getString(R.string.tool_timer_failed)) }

    private fun openApp(name: String): ToolOutcome = runCatching {
        val packages = context.packageManager
        val launchable = packages.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            0,
        )
        val byLabel = launchable.associateBy { it.loadLabel(packages).toString() }
        val chosen = AppMatcher.bestMatch(name, byLabel.keys.toList())
            ?: return ToolOutcome.Failed(context.getString(R.string.tool_no_such_app, name))

        val resolved = byLabel.getValue(chosen)
        val launch = packages.getLaunchIntentForPackage(resolved.activityInfo.packageName)
            ?: return ToolOutcome.Failed(context.getString(R.string.tool_no_such_app, name))
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launch)
        ToolOutcome.Done(context.getString(R.string.tool_opened, chosen))
    }.getOrElse { ToolOutcome.Failed(context.getString(R.string.tool_no_such_app, name)) }

    private fun describe(seconds: Int): String = when {
        seconds % 3600 == 0 -> context.resources.getQuantityString(
            R.plurals.hours, seconds / 3600, seconds / 3600,
        )

        seconds % 60 == 0 -> context.resources.getQuantityString(
            R.plurals.minutes, seconds / 60, seconds / 60,
        )

        else -> context.resources.getQuantityString(R.plurals.seconds, seconds, seconds)
    }
}
