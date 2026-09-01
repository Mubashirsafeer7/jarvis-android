package com.mubashir.jarvis.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.widget.Toast

/**
 * Where the installer reports back.
 *
 * The first thing it usually says is STATUS_PENDING_USER_ACTION — the system's
 * own install confirmation, which has to be launched from here or the install
 * simply never happens and nothing explains why.
 */
class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (android.os.Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT) as Intent?
                }
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                confirm?.let { runCatching { context.startActivity(it) } }
            }

            PackageInstaller.STATUS_SUCCESS -> Unit // the app is about to restart

            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Toast.makeText(
                    context,
                    message ?: context.getString(com.mubashir.jarvis.R.string.update_failed),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }
}
