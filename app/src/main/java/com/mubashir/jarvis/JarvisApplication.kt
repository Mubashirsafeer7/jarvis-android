package com.mubashir.jarvis

import android.app.Application
import com.mubashir.jarvis.core.JarvisRuntime
import com.mubashir.jarvis.update.UpdateScheduler

/**
 * Owns the one [JarvisRuntime] for the process, so the engine, speaker and
 * recogniser survive the Activity going away and coming back.
 */
class JarvisApplication : Application() {

    val runtime: JarvisRuntime by lazy { JarvisRuntime(this) }

    override fun onCreate() {
        super.onCreate()
        // Built eagerly so the native library starts loading while the user is
        // still reading the first screen, and registered so the runtime hears
        // about memory pressure rather than waiting to be killed.
        registerComponentCallbacks(runtime)

        // Re-scheduling an identical job is a no-op, so this is safe on every
        // launch and is what keeps the check alive after an update replaces the
        // app.
        // Rescheduled on every launch, because an update replaces the app and
        // takes its jobs with it. Cheap: scheduling an identical job is a no-op.
        runtime.scheduleRoutinesIfAny(this)

        if (runtime.settings.settings.value.notifyUpdates) {
            UpdateScheduler.schedule(this)
        } else {
            UpdateScheduler.cancel(this)
        }
    }
}
