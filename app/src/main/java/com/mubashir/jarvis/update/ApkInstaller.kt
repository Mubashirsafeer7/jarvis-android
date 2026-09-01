package com.mubashir.jarvis.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Installs a downloaded APK over the running one.
 *
 * The session API is used rather than an ACTION_VIEW intent because it takes the
 * bytes directly — no content URI, so no FileProvider to declare and no
 * FileUriExposedException to get wrong. Android still shows its own
 * confirmation; nothing here installs anything silently.
 *
 * This only ever succeeds if the new APK carries the same signing key as the
 * installed one, which is what the fingerprint assertion in CI exists to
 * guarantee before a build ever reaches the phone.
 */
class ApkInstaller(private val context: Context) {

    /** Android will not let an app install anything until the user allows it. */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun allowInstallsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            .setData(Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    suspend fun install(apk: File): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(apk.exists() && apk.length() > 0) { "The downloaded update is missing." }

            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL,
            ).apply {
                setAppPackageName(context.packageName)
            }

            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                session.openWrite(NAME, 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }

                val intent = Intent(context, InstallResultReceiver::class.java)
                val pending = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                session.commit(pending.intentSender)
            }
        }
    }

    private companion object {
        const val NAME = "jarvis-update"
    }
}
