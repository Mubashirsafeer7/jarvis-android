package com.mubashir.jarvis.voice

/**
 * Turns the recogniser's microphone readings into something an animation can use.
 *
 * SpeechRecognizer reports loudness in dB through onRmsChanged, roughly -2 (a
 * quiet room) to 10 (speaking up), and the raw values jitter hard enough that
 * driving a ring straight off them looks like a glitch rather than a voice. So
 * map that range onto 0..1 and smooth it, quickly on the way up so the reactor
 * answers the first syllable, slowly on the way down so it settles instead of
 * flickering between words.
 */
class MicLevel(
    private val quietDb: Float = QUIET_DB,
    private val loudDb: Float = LOUD_DB,
    private val attack: Float = ATTACK,
    private val release: Float = RELEASE,
) {
    var value: Float = 0f
        private set

    fun update(rmsDb: Float): Float {
        val target = normalise(rmsDb)
        val rate = if (target > value) attack else release
        value = (value + (target - value) * rate).coerceIn(0f, 1f)
        return value
    }

    /** Called when listening stops, so the next session starts from silence. */
    fun reset() {
        value = 0f
    }

    private fun normalise(rmsDb: Float): Float {
        if (rmsDb.isNaN()) return 0f
        return ((rmsDb - quietDb) / (loudDb - quietDb)).coerceIn(0f, 1f)
    }

    private companion object {
        const val QUIET_DB = -2f
        const val LOUD_DB = 10f
        const val ATTACK = 0.55f
        const val RELEASE = 0.12f
    }
}
