package com.mubashir.jarvis.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mubashir.jarvis.MainActivity
import com.mubashir.jarvis.R

/**
 * Tells the user a newer build exists, without them having to go looking.
 *
 * Tapping it opens settings with the update already found — not a download.
 * Nothing is fetched or installed until someone asks for it, and a notification
 * that starts a sixty megabyte download on its own would be exactly that.
 */
class UpdateNotifier(private val context: Context) {

    fun canNotify(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED

    fun notify(update: AvailableUpdate) {
        // Checked inline rather than through canNotify(). The guard belongs
        // next to the thing it guards — posting without the permission throws —
        // and a check one call away is one lint cannot follow either.
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel()

        val open = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .putExtra(EXTRA_OPEN_UPDATES, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        val pending = PendingIntent.getActivity(
            context,
            update.versionCode.toInt(),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(context.getString(R.string.update_notify_title, update.versionName))
            .setContentText(context.getString(R.string.update_notify_body))
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    update.notes.ifBlank { context.getString(R.string.update_notify_body) },
                ),
            )
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(ID, notification)
        }
    }

    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL,
            context.getString(R.string.update_channel),
            // Low: worth knowing about, not worth interrupting anything for.
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.update_channel_detail)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val EXTRA_OPEN_UPDATES = "open_updates"
        private const val CHANNEL = "updates"
        private const val ID = 4201
    }
}
