package com.mubashir.jarvis.core

import android.content.ComponentCallbacks2
import android.content.Context
import com.mubashir.jarvis.DeviceCapabilities
import com.mubashir.jarvis.data.ChatStore
import com.mubashir.jarvis.data.SettingsStore
import com.mubashir.jarvis.data.BrainChoice
import com.mubashir.jarvis.llm.Brain
import com.mubashir.jarvis.llm.Abilities
import com.mubashir.jarvis.llm.JarvisEngine
import com.mubashir.jarvis.llm.LocalBrain
import com.mubashir.jarvis.llm.RemoteBrain
import com.mubashir.jarvis.memory.MemoryStore
import com.mubashir.jarvis.model.DownloadStore
import com.mubashir.jarvis.routine.Moment
import com.mubashir.jarvis.routine.Routine
import com.mubashir.jarvis.routine.RoutineRules
import com.mubashir.jarvis.routine.RoutineScheduler
import com.mubashir.jarvis.routine.RoutineStore
import com.mubashir.jarvis.tools.IntentRouter
import com.mubashir.jarvis.tools.ToolOutcome
import com.mubashir.jarvis.tools.needsConfirmation
import com.mubashir.jarvis.sense.Senses
import com.mubashir.jarvis.model.ModelManager
import com.mubashir.jarvis.tools.Contacts
import com.mubashir.jarvis.tools.ToolRunner
import com.mubashir.jarvis.update.ApkInstaller
import com.mubashir.jarvis.update.UpdateNotifier
import com.mubashir.jarvis.update.UpdateRepository
import com.mubashir.jarvis.voice.Speaker
import com.mubashir.jarvis.voice.VoiceInput
import com.mubashir.jarvis.voice.WakeModelStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Everything with a lifetime longer than a screen.
 *
 * The engine holds a multi-gigabyte model in native memory and the recogniser and
 * speaker each hold a system service binding. Owned by a ViewModel, all three were
 * torn down and rebuilt whenever the Activity went away — which cost a full model
 * reload at best, and at worst left the process-wide native engine dead. They
 * belong to the process, so they live here and the ViewModel only borrows them.
 */
class JarvisRuntime(context: Context) : ComponentCallbacks2 {

    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob())

    val engine = JarvisEngine(app)
    val speaker = Speaker(app)
    val voice = VoiceInput(app)
    val wakeModel = WakeModelStore(app)
    val models = ModelManager(app)
    val downloads = DownloadStore(app)
    val settings = SettingsStore(app)
    val chats = ChatStore(app)
    val memory = MemoryStore(app)
    val tools = ToolRunner(app)
    val senses = Senses(app)
    val routines = RoutineStore(app)
    val contacts = Contacts(app)
    val updates = UpdateRepository(app)
    val installer = ApkInstaller(app)
    val notifier = UpdateNotifier(app)
    val capabilities = DeviceCapabilities.read(app)

    init {
        // Read once, up front. Every prompt consults it, and a disk read on the
        // way to the model would put SQLite in the path of every message.
        scope.launch { memory.load() }
        scope.launch { routines.load() }
    }

    private val localBrain = LocalBrain(engine)
    private val remoteBrain = RemoteBrain(
        baseUrl = { settings.settings.value.serverUrl },
        model = { settings.settings.value.serverModel },
        // Read at each request rather than captured once: a permission granted
        // mid-session changes what Jarvis can do, and a server brain has no
        // reason to wait for a model reload to hear about it.
        systemPrompt = { JarvisEngine.systemPrompt(abilities()) },
    )

    /**
     * Does whatever standing instruction is due, and tells the user.
     *
     * Called from a background job with no screen, which is what makes the
     * order here matter: the routine is marked as run *before* the work starts,
     * not after. If the work fails or the process is killed halfway, a missed
     * briefing is a shrug — one that repeats every fifteen minutes until the
     * phone is restarted is not.
     *
     * A routine does its work down the same path a typed message takes, so it
     * can do anything Jarvis can do. The exception is anything that would reach
     * the outside world: a routine never places a call or sends a message,
     * because nobody is watching to confirm it.
     */
    /** Starts the periodic check when there is something to check, and not before. */
    fun scheduleRoutinesIfAny(context: Context) {
        scope.launch {
            routines.load()
            if (routines.routines.value.any { it.enabled }) {
                RoutineScheduler.schedule(context)
            } else {
                RoutineScheduler.cancel(context)
            }
        }
    }

    suspend fun runDueRoutines() {
        val stored = routines.allNow()
        if (stored.none { it.enabled }) return

        val situation = senses.now()
        val moment = Moment(
            now = situation.now,
            batteryPercent = situation.batteryPercent,
            charging = situation.charging,
            nextAppointmentAt = situation.nextAppointment?.start,
        )

        stored.filter { RoutineRules.due(it, moment) }.forEach { routine ->
            routines.markRun(routine.id, System.currentTimeMillis())
            val said = carryOutRoutine(routine)
            notifier.routineHappened(routine.what, said)
        }
    }

    private suspend fun carryOutRoutine(routine: Routine): String {
        val command = IntentRouter.route(routine.what)
        return when {
            // Never, from a background job with nobody watching. The whole
            // reason these are confirmed on screen is that a machine should not
            // be the last thing to decide them, and at eight in the morning it
            // would be exactly that.
            command != null && command.needsConfirmation ->
                "That needs you to confirm it, so it is waiting."

            command != null -> when (val outcome = tools.run(command)) {
                is ToolOutcome.Done -> outcome.spoken
                is ToolOutcome.NotYet -> outcome.spoken
                is ToolOutcome.Failed -> outcome.spoken
            }

            else -> {
                val answer = StringBuilder()
                runCatching {
                    brain.ask(routine.what, settings.settings.value.predictLength)
                        .collect { answer.append(it) }
                }
                answer.toString().trim().ifEmpty { "Nothing came back." }
            }
        }
    }

    /**
     * What Jarvis can actually do right now.
     *
     * Read from the permissions the system has granted and the switches the
     * user has set, rather than from a list somebody remembered to update.
     */
    fun abilities(): Abilities {
        val contacts = granted(android.Manifest.permission.READ_CONTACTS)
        return Abilities(
            phoneControl = settings.settings.value.phoneControl,
            canCall = granted(android.Manifest.permission.CALL_PHONE),
            canMessage = granted(android.Manifest.permission.SEND_SMS),
            canReadContacts = contacts,
            canSeeLocation = granted(android.Manifest.permission.ACCESS_COARSE_LOCATION),
            canReadCalendar = granted(android.Manifest.permission.READ_CALENDAR),
            canReadNotifications = com.mubashir.jarvis.sense.NoticeListener.allowed(app),
            remembers = true,
            brainIsRemote = settings.settings.value.brain == BrainChoice.Server,
        )
    }

    private fun granted(permission: String) =
        androidx.core.content.ContextCompat.checkSelfPermission(app, permission) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    /** Whichever brain is selected right now. */
    val brain: Brain
        get() = when (settings.settings.value.brain) {
            BrainChoice.Phone -> localBrain
            BrainChoice.Server -> remoteBrain
        }

    /**
     * Android asks for memory back before it starts killing processes. A loaded
     * model is by far the largest thing this app holds, so give it up rather than
     * be killed outright — the file is still on disk and reloads on demand.
     *
     * Which levels count is decided by [givesUpModelAt], and is not the obvious
     * threshold: see the note there.
     */
    @Suppress("DEPRECATION") // the levels are deprecated; the callback still fires
    override fun onTrimMemory(level: Int) {
        if (!givesUpModelAt(level)) return
        scope.launch {
            speaker.stop()
            engine.unload()
        }
    }

    @Suppress("DEPRECATION")
    @Deprecated("Required by ComponentCallbacks2", ReplaceWith(""))
    override fun onLowMemory() = onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) = Unit
}
