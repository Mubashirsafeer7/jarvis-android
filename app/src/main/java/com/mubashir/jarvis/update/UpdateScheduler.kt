package com.mubashir.jarvis.update

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import java.util.concurrent.TimeUnit

/** Keeps the background update check running, or stops it. */
object UpdateScheduler {

    private const val JOB_ID = 4201

    fun schedule(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        val job = JobInfo.Builder(JOB_ID, ComponentName(context, UpdateCheckJob::class.java))
            // Twice a day is plenty for something that ships a few times a week,
            // and the request itself is a few hundred bytes.
            .setPeriodic(TimeUnit.HOURS.toMillis(12))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            // Survives a reboot; a check that only runs until the next restart
            // is one that stops running.
            .setPersisted(true)
            .build()
        runCatching { scheduler.schedule(job) }
    }

    fun cancel(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        runCatching { scheduler.cancel(JOB_ID) }
    }
}
