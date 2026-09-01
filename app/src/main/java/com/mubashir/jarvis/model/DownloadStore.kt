package com.mubashir.jarvis.model

import android.content.Context

/**
 * Remembers which download belongs to which model across app restarts.
 *
 * The id used to live only in the ViewModel, so leaving the app lost track of a
 * download that was still running — the setup screen then offered to start it
 * all over again while the real one was still going.
 */
class DownloadStore(context: Context) {

    private val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /**
     * Written synchronously. apply() returns before the write lands, so a kill in
     * the moment after enqueueing left the download running with nothing
     * recording that it existed — it kept filling the model file while the app
     * had forgotten it.
     */
    fun remember(id: Long, specId: String) {
        prefs.edit().putLong(KEY_ID, id).putString(KEY_SPEC, specId).commit()
    }

    fun forget() {
        prefs.edit().remove(KEY_ID).remove(KEY_SPEC).apply()
    }

    /** The download left in flight, if there was one. */
    fun pending(): Pair<Long, ModelSpec>? {
        val id = prefs.getLong(KEY_ID, -1L).takeIf { it >= 0 } ?: return null
        val spec = prefs.getString(KEY_SPEC, null)?.let(ModelCatalog::byId) ?: return null
        return id to spec
    }

    private companion object {
        const val NAME = "downloads"
        const val KEY_ID = "id"
        const val KEY_SPEC = "spec"
    }
}
