package com.mubashir.jarvis.routine

import android.app.job.JobParameters
import android.app.job.JobService
import com.mubashir.jarvis.JarvisApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Wakes up every so often and does whatever is due.
 *
 * Nothing here decides anything: what is due is [RoutineRules], which is pure
 * and tested, and what a routine actually does is the same path a typed message
 * takes. This is only the alarm clock.
 */
class RoutineCheckJob : JobService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onStartJob(params: JobParameters?): Boolean {
        val app = application as? JarvisApplication ?: return false
        scope.launch {
            runCatching { app.runtime.runDueRoutines() }
            // false: nothing to reschedule. A missed check is picked up by the
            // next one fifteen minutes later, and asking Android to retry a
            // periodic job immediately is how a background job becomes a
            // battery complaint.
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        scope.cancel()
        return true
    }
}
