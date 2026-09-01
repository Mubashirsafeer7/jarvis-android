package com.mubashir.jarvis.voice

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.mubashir.jarvis.MainActivity
import com.mubashir.jarvis.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.vosk.LogLevel
import org.vosk.LibVosk
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService

/**
 * Listens for the name, and nothing else.
 *
 * A foreground service because the alternative is not a background service — it
 * is no service at all. Android has not allowed a background process to hold the
 * microphone since Oreo, and the persistent notification is the price of it: an
 * app that can hear you at any moment has to say so, visibly, the whole time.
 *
 * The recogniser is given a two word grammar, so it is not transcribing the room
 * and cannot: everything that is not the name comes back as an unknown token and
 * is dropped. Nothing is recorded, nothing is kept, and nothing leaves the phone.
 * It only decides, roughly ten times a second, whether the last thing it heard
 * was one particular word.
 */
class WakeWordService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var speech: SpeechService? = null

    /**
     * Set once the name is heard, and cleared when the app releases the mic.
     * Written from the recogniser's own thread and read on the main one.
     */
    @Volatile
    private var awake = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Before anything else. Android gives a service a few seconds to show its
        // notification and kills it if it does not, and loading the model takes
        // longer than that.
        val started = runCatching {
            startForeground(
                ID,
                notification(R.string.wake_notification_starting),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        }.isSuccess

        if (!started) {
            // Android 14 refuses a microphone service started from the
            // background. Nothing to report from here — the setting stays on and
            // it starts the next time the app is opened.
            stopSelf()
            return START_NOT_STICKY
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (speech == null) start()
        // START_STICKY: if Android reclaims the process, listening should come
        // back with it. The restart arrives with a null intent, which is why the
        // stop action is checked by action rather than assumed.
        return START_STICKY
    }

    private fun start() {
        scope.launch {
            val ready = runCatching {
                withContext(Dispatchers.IO) {
                    LibVosk.setLogLevel(LogLevel.WARNINGS)
                    val path = WakeModelStore(this@WakeWordService).installedPath()
                        ?: error("The wake word model is not installed.")
                    val loaded = Model(path)
                    val rec = Recognizer(loaded, SAMPLE_RATE, WakePhrase.GRAMMAR)
                    // The AudioRecord is opened by this constructor, so it has to
                    // happen after startForeground or Android refuses the mic.
                    val service = SpeechService(rec, SAMPLE_RATE)
                    Triple(loaded, rec, service)
                }
            }.getOrElse {
                stopSelf()
                return@launch
            }

            model = ready.first
            recognizer = ready.second
            speech = ready.third
            ready.third.startListening(listener)

            WakeSignal.setListening(true)
            update(R.string.wake_notification_listening)

            // One microphone, and two things in this app that want it. Not
            // setPause: that stops feeding the recogniser but keeps the
            // AudioRecord open, so the device is still held and whichever
            // consumer opened it first keeps the sound. The recording has to be
            // torn down and built again, which costs nothing worth measuring —
            // the model, which is the expensive part, stays loaded throughout.
            WakeSignal.appHoldsMic.collect { held ->
                if (held) {
                    releaseMic()
                    update(R.string.wake_notification_paused)
                } else {
                    awake = false
                    takeMic()
                    update(R.string.wake_notification_listening)
                }
            }
        }
    }

    /**
     * Suspending, and off the main thread, because stopping joins the
     * recogniser's own thread — on the main thread that is a visible stall, and
     * from inside a recogniser callback it is worse than that.
     */
    private suspend fun releaseMic() {
        val current = speech ?: return
        speech = null
        withContext(Dispatchers.IO) {
            runCatching { current.stop() }
            runCatching { current.shutdown() }
        }
    }

    private suspend fun takeMic() {
        if (speech != null) return
        val rec = recognizer ?: return
        rec.reset()
        val service = runCatching {
            withContext(Dispatchers.IO) { SpeechService(rec, SAMPLE_RATE) }
        }.getOrElse {
            // The microphone was not given back — another app took it, or the
            // permission was revoked while this ran. Stopping is honest: the
            // notification goes, rather than sitting there claiming to listen.
            stopSelf()
            return
        }
        speech = service
        service.startListening(listener)
    }

    private val listener = object : RecognitionListener {
        override fun onPartialResult(hypothesis: String?) = consider(hypothesis)

        override fun onResult(hypothesis: String?) = consider(hypothesis)

        override fun onFinalResult(hypothesis: String?) = consider(hypothesis)

        override fun onError(exception: Exception?) {
            // The recogniser is done once it errors; there is nothing to recover
            // to from here. Stopping is honest — the notification disappears, so
            // it does not claim to be listening when it is not.
            stopSelf()
        }

        override fun onTimeout() = Unit
    }

    private fun consider(hypothesis: String?) {
        // Partials repeat the same guess many times a second while a word is
        // still being said, so without this one "Jarvis" opens the microphone
        // over and over.
        if (awake) return
        if (!WakePhrase.heard(WakePhrase.spoken(hypothesis))) return
        awake = true

        scope.launch {
            // Let go of the microphone before telling anything that the name was
            // heard, rather than after. The other way round, the recogniser that
            // takes the command opens the device while this one still holds it,
            // and one of the two silently gets nothing.
            releaseMic()
            WakeSignal.wake()
            openApp()

            // The wake is normally ended by the app handing the microphone back.
            // It may never take it — the screen is locked, or the app was not
            // allowed to open — and without this the name would work exactly
            // once per restart.
            delay(FORGET_WAKE_MS)
            if (!WakeSignal.appHoldsMic.value) {
                awake = false
                takeMic()
            }
        }
    }

    /**
     * Brings the app forward so there is something to talk to.
     *
     * Android does not let a background app start a screen without the user
     * granting "appear on top", so this quietly does nothing on a locked or busy
     * phone. That is not a failure worth reporting: the wake still fires, and if
     * the app is already open it hears it directly.
     */
    private fun openApp() {
        val open = Intent(this, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .putExtra(EXTRA_WOKEN, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        runCatching { startActivity(open) }
    }

    override fun onDestroy() {
        WakeSignal.setListening(false)
        scope.cancel()
        runCatching { speech?.stop() }
        runCatching { speech?.shutdown() }
        runCatching { recognizer?.close() }
        runCatching { model?.close() }
        speech = null
        recognizer = null
        model = null
        super.onDestroy()
    }

    private fun update(message: Int) {
        // Checked here rather than through a helper: posting without the
        // permission throws, and a guard one call away is one lint cannot follow.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = getSystemService(NotificationManager::class.java) ?: return
        runCatching { manager.notify(ID, notification(message)) }
    }

    private fun notification(message: Int): android.app.Notification {
        ensureChannel()

        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, WakeWordService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(getString(R.string.wake_notification_title))
            .setContentText(getString(message))
            .setContentIntent(open)
            .addAction(0, getString(R.string.wake_notification_stop), stop)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL,
            getString(R.string.wake_channel),
            // Low, and silent: this notification sits there for hours. Anything
            // that makes a sound would be intolerable within a day.
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.wake_channel_detail)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        /** Set on the intent that opens the app because the name was heard. */
        const val EXTRA_WOKEN = "woken"

        private const val ACTION_STOP = "com.mubashir.jarvis.STOP_LISTENING"
        private const val CHANNEL = "wake_word"
        private const val ID = 4202
        private const val SAMPLE_RATE = 16000.0f

        /** How long a wake waits for the app to pick it up before it is dropped. */
        private const val FORGET_WAKE_MS = 8_000L

        fun start(context: Context) {
            val intent = Intent(context, WakeWordService::class.java)
            // Not startForegroundService from an arbitrary caller: Android 12+
            // throws if the app is not visible, and this is only ever called
            // from a screen that is.
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, WakeWordService::class.java)) }
        }
    }
}
