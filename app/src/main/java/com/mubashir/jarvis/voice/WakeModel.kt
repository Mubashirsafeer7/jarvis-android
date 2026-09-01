package com.mubashir.jarvis.voice

/**
 * The acoustic model that listening for a name needs, and the rules for
 * unpacking it safely.
 *
 * It is downloaded rather than shipped: at forty megabytes it would be most of
 * the APK, and someone who never turns the wake word on should never pay for
 * it. The URL is checked by CI on every push, because the host it lives on is
 * unreachable from where this app is written and a dead link would otherwise be
 * discovered as a failed download on the phone.
 *
 * Pure, so the two things most likely to go wrong here — deciding a half
 * unpacked directory is usable, and letting a zip write outside the directory
 * it is being unpacked into — can be tested without a phone.
 */
object WakeModel {

    /**
     * Vosk's small English model. The small one on purpose: the full model is
     * 1.8 GB and would be competing for memory with the language model, and all
     * this one has to recognise is a single word.
     */
    const val URL = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"

    /** The directory the zip unpacks to. */
    const val DIR_NAME = "vosk-model-small-en-us-0.15"

    /** For the free space check and the progress bar; the server reports the real size. */
    const val APPROX_BYTES = 41L * 1024 * 1024

    /**
     * Every usable Vosk model has these. Checking for them is what stops a
     * download that died halfway from being loaded as if it were finished —
     * which fails inside native code, with no message worth reading.
     */
    private val REQUIRED = listOf("am", "conf", "graph")

    fun looksComplete(entries: Collection<String>): Boolean =
        REQUIRED.all { required -> entries.any { it == required } }

    /**
     * Which of the unpacked directories is the model.
     *
     * Normally [DIR_NAME], but the archive names its own top directory and a
     * newer model would name it something else, so the name is a preference and
     * completeness is the test.
     *
     * @param children each unpacked directory, mapped to the names directly inside it
     */
    fun modelRoot(children: Map<String, Collection<String>>): String? {
        val complete = children.filterValues { looksComplete(it) }
        return complete.keys.firstOrNull { it == DIR_NAME } ?: complete.keys.firstOrNull()
    }

    /**
     * A zip entry names its own destination, so an archive can ask to be written
     * anywhere the app can write — `../../shared_prefs/settings.xml` is a valid
     * entry name. Nothing here is downloaded from a place the user chose, but a
     * check that only holds while the URL is trusted is a check that breaks the
     * first time the URL changes.
     *
     * @return the entry's path relative to the unpack directory, or null to refuse it
     */
    fun safeEntryName(raw: String): String? {
        val cleaned = raw.replace('\\', '/').trim()
        if (cleaned.isEmpty() || cleaned.startsWith("/")) return null
        val parts = cleaned.split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.isEmpty()) return null
        if (parts.any { it == ".." }) return null
        return parts.joinToString("/")
    }
}
