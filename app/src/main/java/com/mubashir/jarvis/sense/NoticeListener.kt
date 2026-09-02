package com.mubashir.jarvis.sense

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Reads the shade, so "what did I miss" has an answer.
 *
 * This is the one capability in the app that cannot be asked for with a
 * permission dialog. Android treats notification access as special: the user
 * has to find this app in a system settings screen and switch it on themselves,
 * and nothing the app does can shortcut that. So the app's job is to explain
 * why and take them to the right screen — [settingsIntent] — rather than
 * pretend the feature is broken.
 *
 * Nothing read here is written to disk. See NoticeBox for why.
 */
class NoticeListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        NoticeBox.setListening(true)
        // The shade already has things in it at the moment access is granted,
        // and none of them will arrive as events. Without this first sweep,
        // "what did I miss" answers "nothing" until something new lands.
        runCatching { activeNotifications?.mapNotNull { it.asNotice() } }
            .getOrNull()
            ?.let { NoticeBox.replaceAll(it) }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        // Access revoked, or the service was killed. Holding on to what was
        // read while no longer allowed to read is the wrong side of the line.
        NoticeBox.setListening(false)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.asNotice()?.let(NoticeBox::add)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn?.key?.let(NoticeBox::remove)
    }

    private fun StatusBarNotification.asNotice(): Notice? {
        val extras = notification?.extras ?: return null
        val flags = notification?.flags ?: 0
        return Notice(
            app = appLabel(packageName),
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
            text = (
                extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                    ?: extras.getCharSequence(Notification.EXTRA_TEXT)
                )?.toString().orEmpty(),
            postedAt = postTime,
            ongoing = flags and Notification.FLAG_ONGOING_EVENT != 0,
            groupSummary = flags and Notification.FLAG_GROUP_SUMMARY != 0,
            key = key,
        )
    }

    /** "WhatsApp", not "com.whatsapp". */
    private fun appLabel(packageName: String): String = runCatching {
        val manager = packageManager
        manager.getApplicationLabel(manager.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)

    companion object {

        /**
         * Whether the user has switched this on in system settings.
         *
         * Read from the setting rather than tracked, because access can be
         * revoked while the app is not running and the app is never told.
         */
        fun allowed(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners",
            ).orEmpty()
            val mine = ComponentName(context, NoticeListener::class.java).flattenToString()
            val mineShort = ComponentName(context, NoticeListener::class.java)
                .flattenToShortString()
            return enabled.split(':').any { it == mine || it == mineShort }
        }

        /** The screen where it is switched on. There is no dialog for this one. */
        fun settingsIntent(): Intent =
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
