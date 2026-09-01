package com.mubashir.jarvis

import android.app.Application
import com.mubashir.jarvis.core.JarvisRuntime

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
    }
}
