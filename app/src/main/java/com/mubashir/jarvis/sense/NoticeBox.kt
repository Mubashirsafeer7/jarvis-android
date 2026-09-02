package com.mubashir.jarvis.sense

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What is currently in the shade, in memory and nowhere else.
 *
 * Deliberately not persisted. Notifications are the most revealing thing this
 * app can see — every message, every bank alert, every code sent by SMS — and
 * the only version of this that is safe to build is one where there is nothing
 * on disk to leak. It is rebuilt from the live notifications the moment the
 * listener connects, so persisting it would buy nothing anyway.
 *
 * Process-wide because a listener service and the screen are different objects
 * with different lifetimes, and one small ring is less machinery than binding
 * them together.
 */
object NoticeBox {

    /**
     * Enough to answer "what did I miss" and not enough to be a record of the
     * day. The formatter drops most of these before anything is said.
     */
    private const val KEEP = 60

    private val _notices = MutableStateFlow<List<Notice>>(emptyList())
    val notices: StateFlow<List<Notice>> = _notices.asStateFlow()

    /** True once Android has actually connected the listener. */
    private val _listening = MutableStateFlow(false)
    val listening: StateFlow<Boolean> = _listening.asStateFlow()

    fun setListening(on: Boolean) {
        _listening.value = on
        if (!on) _notices.value = emptyList()
    }

    fun replaceAll(current: List<Notice>) {
        _notices.value = current.takeLast(KEEP)
    }

    fun add(notice: Notice) {
        _notices.value = (_notices.value + notice).takeLast(KEEP)
    }

    fun remove(key: String) {
        _notices.value = _notices.value.filterNot { it.key == key }
    }

    fun clear() {
        _notices.value = emptyList()
    }
}
