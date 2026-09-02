package com.mubashir.jarvis.routine

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import java.util.concurrent.TimeUnit

/** Keeps the routine check running, or stops it when there is nothing to check. */
object RoutineScheduler {

    private const val JOB_ID = 4203

    /**
     * Fifteen minutes is Android's floor for a periodic job, and it is why a
     * routine set for eight o'clock may not happen until a few minutes past.
     * That is a deliberate trade rather than a limitation worked around: an
     * exact alarm needs a special permission on Android 12+, and on ColorOS
     * exact alarms are throttled anyway. A briefing a few minutes late is
     * still a briefing.
     */
    fun schedule(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        val job = JobInfo.Builder(JOB_ID, ComponentName(context, RoutineCheckJob::class.java))
            .setPeriodic(TimeUnit.MINUTES.toMillis(15))
            // No network requirement. A routine that reads the calendar or the
            // battery works perfectly well in a tunnel.
            .setPersisted(true)
            .build()
        runCatching { scheduler.schedule(job) }
    }

    fun cancel(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        runCatching { scheduler.cancel(JOB_ID) }
    }
}
