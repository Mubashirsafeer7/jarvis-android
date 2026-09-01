package com.mubashir.jarvis.voice

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The one line between the service that listens for the name and the app that
 * answers to it.
 *
 * A service and an Activity in the same process cannot simply call each other,
 * and going through a broadcast or a bound connection for two booleans would be
 * more machinery than the problem deserves. Both live in the same process, so a
 * shared flow is the whole of it.
 *
 * [appHoldsMic] is the part that matters. There is one microphone: the wake
 * word listener holds it continuously, and the recogniser that takes the actual
 * command needs it too. Without handing it over, the command is recorded by
 * whichever of the two happened to open it first, and the other silently gets
 * nothing.
 */
object WakeSignal {

    /** Emitted the moment the name is heard. Replay 0: a wake is now or never. */
    private val _heard = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val heard: SharedFlow<Unit> = _heard.asSharedFlow()

    /** True while the app is recording a command, so the listener must let go. */
    private val _appHoldsMic = MutableStateFlow(false)
    val appHoldsMic: StateFlow<Boolean> = _appHoldsMic.asStateFlow()

    /** True while the wake word service is actually listening. */
    private val _listening = MutableStateFlow(false)
    val listening: StateFlow<Boolean> = _listening.asStateFlow()

    fun wake() {
        _heard.tryEmit(Unit)
    }

    fun setAppHoldsMic(holds: Boolean) {
        _appHoldsMic.value = holds
    }

    fun setListening(on: Boolean) {
        _listening.value = on
    }
}
