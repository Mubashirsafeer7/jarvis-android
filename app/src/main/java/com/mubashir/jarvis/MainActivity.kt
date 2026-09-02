package com.mubashir.jarvis

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mubashir.jarvis.ui.ChatScreen
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import com.mubashir.jarvis.tools.Action
import com.mubashir.jarvis.tools.Ask
import com.mubashir.jarvis.ui.SettingsScreen
import com.mubashir.jarvis.ui.SetupScreen
import com.mubashir.jarvis.ui.WakingScreen
import com.mubashir.jarvis.ui.theme.JarvisTheme
import com.mubashir.jarvis.update.UpdateNotifier
import com.mubashir.jarvis.voice.WakeWordService

class MainActivity : ComponentActivity() {

    /** Bumped when an update notification opens the app, so Compose reacts. */
    private val openUpdates = mutableIntStateOf(0)

    /**
     * Bumped when the wake word service opened the app because it heard the name.
     *
     * The service also signals through WakeSignal, which is instant and is what
     * carries a wake while the app is already on screen. This is for the other
     * case: the app was not running at all, so there was nothing listening to
     * that signal when it fired, and only the intent survives.
     */
    private val woken = mutableIntStateOf(0)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(UpdateNotifier.EXTRA_OPEN_UPDATES, false)) {
            openUpdates.intValue++
        }
        if (intent.getBooleanExtra(WakeWordService.EXTRA_WOKEN, false)) {
            woken.intValue++
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // The app is dark whatever the phone is set to, so say so explicitly.
        // The no-argument form follows the system setting, which on a phone in
        // light mode painted dark status-bar icons onto a near-black background
        // and left the clock invisible.
        val bars = SystemBarStyle.dark(NAVY_ARGB)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        super.onCreate(savedInstanceState)
        if (intent?.getBooleanExtra(UpdateNotifier.EXTRA_OPEN_UPDATES, false) == true) {
            openUpdates.intValue++
        }
        if (intent?.getBooleanExtra(WakeWordService.EXTRA_WOKEN, false) == true) {
            woken.intValue++
        }
        setContent {
            JarvisTheme {
                // No Scaffold inset padding at the root: it stopped the voice
                // overlay short of the system bars, so the "immersive" reactor
                // had a differently shaded band above and below it. Each screen
                // applies the insets it actually wants.
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    JarvisApp(openUpdates = openUpdates.intValue, woken = woken.intValue)
                }
            }
        }
    }
}

/** Kept in step with JarvisColors.Navy. */
private const val NAVY_ARGB = 0xFF060B14.toInt()

/** Where the user is. Saved, so a rotation does not move them. */
private enum class Screen { Chat, Models, Settings }

@Composable
private fun JarvisApp(openUpdates: Int = 0, woken: Int = 0, modifier: Modifier = Modifier) {
    val vm: ChatViewModel = viewModel()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var screen by rememberSaveable { mutableStateOf(Screen.Chat) }

    // Notifications are asked for the first time the app is opened rather than
    // buried in settings — the model download reports its progress through one
    // too, and without this that notification was being dropped in silence.
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Declining is a fine answer; the app works either way. */ }

    LaunchedEffect(Unit) {
        if (!vm.canNotify()) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Arriving from an update notification lands on the update, already checked,
    // rather than on a settings screen with a button to press.
    LaunchedEffect(openUpdates) {
        if (openUpdates > 0) {
            screen = Screen.Settings
            vm.checkForUpdate()
        }
    }

    // Opened by the wake word service. Starting to listen from here rather than
    // inside the service keeps every path into the microphone the same one.
    LaunchedEffect(woken) {
        if (woken > 0) vm.startListening()
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            vm.import(uri, context.displayNameOf(uri))
            screen = Screen.Chat
        }
    }

    // Contacts, calling and messaging are asked for only at the moment one is
    // actually needed, never up front — an assistant that demands the phone book
    // on first launch has not earned it yet.
    val toolPermissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        // Deliberately not retried automatically. Saying it again is one
        // sentence, and acting on a request the user may have moved on from —
        // by placing a call — is not a thing to do on their behalf.
        vm.permissionResult(granted = results.values.all { it })
    }

    // The mic button asks for permission the first time, then listens. Granting
    // it mid-session has to flip this back on without a restart.
    var micGranted by rememberSaveable { mutableStateOf(vm.hasMicPermission()) }
    var micDenied by rememberSaveable { mutableStateOf(false) }
    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        micGranted = granted
        micDenied = !granted
        if (granted) vm.startListening()
    }

    // A launcher of its own, because granting the microphone from the wake word
    // switch has to end up turning the wake word on — not opening the mic for a
    // command, which is what the button's launcher does.
    val wakeMicPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        micGranted = granted
        micDenied = !granted
        if (granted) vm.setWakeWord(true)
    }

    // With no model there is nowhere to go back to.
    val canLeaveModels = ui.loadedModel != null

    BackHandler(enabled = ui.voiceMode) { vm.exitVoiceMode() }
    BackHandler(enabled = !ui.voiceMode && screen == Screen.Settings) { screen = Screen.Chat }
    BackHandler(enabled = !ui.voiceMode && screen == Screen.Models && canLeaveModels) {
        screen = Screen.Settings
    }

    Box(modifier.fillMaxSize()) {
        when {
            // Picking the last model back up. Not the picker, and not a modal
            // spinner over it — nothing the user did is waiting on this.
            ui.startingUp -> WakingScreen(modelName = vm.lastModelName())

            ui.loadedModel == null || screen == Screen.Models -> SetupScreen(
                ui = ui,
                caps = vm.capabilities,
                freeSpaceGb = ui.freeSpaceGb,
                onDownload = vm::download,
                onCancelDownload = vm::cancelDownload,
                onImport = { picker.launch(arrayOf("*/*")) },
                onLoad = { vm.load(it.file); screen = Screen.Chat },
                onDelete = vm::delete,
                onExport = vm::export,
                onBack = if (canLeaveModels) ({ screen = Screen.Settings }) else null,
            )

            screen == Screen.Settings -> SettingsScreen(
                ui = ui,
                predictLength = vm.predictLength(),
                speechProblem = vm.speechUnavailableReason(),
                micUsable = vm.micAvailable(),
                appVersion = context.appVersion(),
                ramGb = vm.capabilities.totalRamGb,
                onBack = { screen = Screen.Chat },
                onSetSpeak = vm::setSpeakReplies,
                onSetPredictLength = vm::setPredictLength,
                onManageModels = { screen = Screen.Models },
                onBenchmark = vm::benchmark,
                onDismissBenchmark = vm::clearBenchmark,
                onClearChat = vm::clearChat,
                onOpenVoiceSettings = { context.openVoiceSettings() },
                brainChoice = vm.brainChoice(),
                onSetBrain = vm::setBrain,
                serverUrl = vm.serverUrl(),
                onSetServerUrl = vm::setServerUrl,
                serverModel = vm.serverModel(),
                onSetServerModel = vm::setServerModel,
                onCheckServer = vm::checkServer,
                keepRescueCopy = vm.keepRescueCopy(),
                onSetKeepRescueCopy = vm::setKeepRescueCopy,
                phoneControl = vm.phoneControl(),
                onSetPhoneControl = vm::setPhoneControl,
                onForgetFact = vm::forgetFact,
                onForgetEverything = vm::forgetEverything,
                wakeWord = vm.wakeWord(),
                onSetWakeWord = { on ->
                    if (on && !micGranted) {
                        wakeMicPermission.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        vm.setWakeWord(on)
                    }
                },
                onInstallWakeModel = vm::installWakeModel,
                onRemoveWakeModel = vm::removeWakeModel,
                micGranted = micGranted,
                onOpenBatterySettings = { context.openBatterySettings() },
                onOpenAutostart = { context.openAutostartSettings() },
                onOpenOverlaySettings = { context.openOverlaySettings() },
                notifyUpdates = vm.notifyUpdates(),
                onSetNotifyUpdates = vm::setNotifyUpdates,
                notificationsBlocked = !vm.canNotify(),
                canInstallUpdates = vm.canInstallUpdates(),
                onCheckUpdate = vm::checkForUpdate,
                onDownloadUpdate = vm::downloadUpdate,
                onInstallUpdate = vm::installUpdate,
                onAllowInstalls = {
                    runCatching { context.startActivity(vm.allowInstallsIntent()) }
                },
            )

            else -> ChatScreen(
                ui = ui,
                onSend = vm::send,
                onStop = vm::stopGenerating,
                onOpenSettings = { screen = Screen.Settings },
                onListen = {
                    if (micGranted) vm.startListening()
                    else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                },
                onStopListening = vm::stopListening,
                onToggleSpeak = vm::toggleSpeakReplies,
                onExitVoice = vm::exitVoiceMode,
                micUsable = vm.micAvailable(),
            )
        }

        if (ui.busy) {
            BusyOverlay(message = stringResource(ui.busyMessage))
        }

        val problem = ui.error
        val notice = ui.notice
        if (problem != null || notice != null) {
            AlertDialog(
                onDismissRequest = vm::dismissError,
                title = {
                    Text(
                        stringResource(
                            if (problem != null) R.string.dialog_problem else R.string.dialog_done,
                        ),
                    )
                },
                text = { Text(problem ?: notice.orEmpty()) },
                confirmButton = {
                    TextButton(onClick = vm::dismissError) {
                        Text(stringResource(R.string.action_ok))
                    }
                },
            )
        }

        when (val ask = ui.ask) {
            is Ask.Confirm -> ConfirmActionDialog(
                action = ask.action,
                onConfirm = vm::confirmAsk,
                onDismiss = vm::dismissAsk,
            )

            is Ask.Choose -> AlertDialog(
                onDismissRequest = vm::dismissAsk,
                title = { Text(stringResource(R.string.choose_contact_title)) },
                text = {
                    Column {
                        ask.contacts.forEach { contact ->
                            TextButton(onClick = { vm.chooseContact(contact) }) {
                                Text("${contact.name} · ${contact.number}")
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = vm::dismissAsk) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
            )

            is Ask.NeedPermission -> AlertDialog(
                onDismissRequest = vm::dismissAsk,
                title = { Text(stringResource(R.string.permission_title)) },
                text = { Text(ask.reason) },
                confirmButton = {
                    TextButton(onClick = {
                        toolPermissions.launch(ask.permissions.toTypedArray())
                    }) { Text(stringResource(R.string.permission_allow)) }
                },
                dismissButton = {
                    TextButton(onClick = vm::dismissAsk) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
            )

            null -> Unit
        }

        // A permanently denied microphone made the button do nothing at all,
        // with no way for the user to find out why.
        if (micDenied) {
            AlertDialog(
                onDismissRequest = { micDenied = false },
                title = { Text(stringResource(R.string.mic_denied_title)) },
                text = { Text(stringResource(R.string.mic_denied_body)) },
                confirmButton = {
                    TextButton(onClick = {
                        micDenied = false
                        context.openAppSettings()
                    }) { Text(stringResource(R.string.action_open_settings)) }
                },
                dismissButton = {
                    TextButton(onClick = { micDenied = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
            )
        }
    }
}

/**
 * Spells out exactly who is about to be called or messaged, and with what.
 *
 * The whole point is that the number is on screen: a misheard name that
 * resolved to the wrong person is caught here, by a human, rather than after
 * the call connects.
 */
@Composable
private fun ConfirmActionDialog(
    action: Action,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val title = when (action) {
        is Action.Call -> stringResource(R.string.confirm_call_title, action.contact.name)
        is Action.Sms -> stringResource(R.string.confirm_sms_title, action.contact.name)
    }
    val body = when (action) {
        is Action.Call -> stringResource(
            R.string.confirm_call_body, action.contact.name, action.contact.number,
        )

        is Action.Sms -> stringResource(
            R.string.confirm_sms_body,
            action.contact.name,
            action.contact.number,
            action.message,
        )
    }
    val confirmLabel = when (action) {
        is Action.Call -> stringResource(R.string.confirm_call)
        is Action.Sms -> stringResource(R.string.confirm_send)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * Blocks what is underneath. It used to be a transparent box drawn over the
 * setup cards, so the spinner sat on top of text while every button below it
 * stayed tappable — including Delete, during a load.
 */
@Composable
private fun BusyOverlay(message: String) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.86f))
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 40.dp),
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun Context.displayNameOf(uri: Uri): String {
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index) ?: "imported.gguf"
            }
        }
    return "imported.gguf"
}

private fun Context.appVersion(): String = runCatching {
    val info = packageManager.getPackageInfo(packageName, 0)
    "${info.versionName} (${info.longVersionCode})"
}.getOrDefault("unknown")

/** The phone's own speech settings, for a missing language pack or voice. */
private fun Context.openVoiceSettings() {
    val candidates = listOf(
        Intent("com.android.settings.TTS_SETTINGS"),
        Intent(Settings.ACTION_VOICE_INPUT_SETTINGS),
        Intent(Settings.ACTION_SETTINGS),
    )
    for (intent in candidates) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { startActivity(intent) }.isSuccess) return
    }
}

private fun Context.openAppSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.fromParts("package", packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
}

/**
 * The list of apps excluded from battery optimisation.
 *
 * Deliberately the list rather than the direct request. Asking outright needs
 * REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, which is a permission Android treats as
 * exceptional; the list is one more tap and needs nothing.
 */
private fun Context.openBatterySettings() {
    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { startActivity(intent) }.isSuccess) return
    openAppSettings()
}

/**
 * ColorOS and its relatives keep their own list of apps allowed to start
 * themselves, separate from anything in Android, and an app not on it is stopped
 * without appeal. There is no public intent for it, so these are the activities
 * the manufacturers actually ship, tried in turn.
 */
private fun Context.openAutostartSettings() {
    val candidates = listOf(
        "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
        "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
        "com.oplus.safecenter" to "com.oplus.safecenter.permission.startup.StartupAppListActivity",
        "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
        "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
        "com.samsung.android.lool" to "com.samsung.android.sm.ui.battery.BatteryActivity",
    )
    for ((pkg, activity) in candidates) {
        val intent = Intent()
            .setClassName(pkg, activity)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { startActivity(intent) }.isSuccess) return
    }
    // Every phone has this one, and the auto-start switch is usually on it.
    openAppSettings()
}

/** "Appear on top", which is what lets a wake open the app from the background. */
private fun Context.openOverlaySettings() {
    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        .setData(Uri.fromParts("package", packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { startActivity(intent) }.isSuccess) return
    openAppSettings()
}
