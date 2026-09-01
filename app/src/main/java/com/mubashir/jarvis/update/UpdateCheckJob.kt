package com.mubashir.jarvis.update

import android.app.job.JobParameters
import android.app.job.JobService
import com.mubashir.jarvis.JarvisApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Asks GitHub, in the background, whether there is anything newer.
 *
 * JobScheduler rather than WorkManager: it is a platform API, so it adds no
 * dependency that could not be resolved and checked before shipping — which
 * matters more here than convenience, because this project is built somewhere
 * that cannot reach Google's Maven host at all.
 */
class UpdateCheckJob : JobService() {

    private val scope = CoroutineScope(SupervisorJob())
    private var work: Job? = null

    override fun onStartJob(params: JobParameters?): Boolean {
        val app = application as? JarvisApplication ?: return false
        val runtime = app.runtime
        val settings = runtime.settings

        if (!settings.settings.value.notifyUpdates) {
            return false
        }

        work = scope.launch {
            runtime.updates.latest()
                .onSuccess { found ->
                    // Only once per release. Without this the same version is
                    // announced again every time the job runs, which trains
                    // people to ignore it.
                    if (found != null &&
                        found.versionCode > settings.settings.value.lastNotifiedVersion
                    ) {
                        UpdateNotifier(applicationContext).notify(found)
                        settings.setLastNotifiedVersion(found.versionCode)
                    }
                }
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        work?.cancel()
        // Worth retrying: the usual reason for being stopped is losing the
        // network, and the check costs almost nothing.
        return true
    }
}
