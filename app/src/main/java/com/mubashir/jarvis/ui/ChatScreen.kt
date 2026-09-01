package com.mubashir.jarvis.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.mubashir.jarvis.ChatMessage
import com.mubashir.jarvis.R
import com.mubashir.jarvis.UiState
import com.mubashir.jarvis.ui.components.JarvisIcons
import androidx.compose.ui.res.stringResource

@Composable
fun ChatScreen(
    ui: UiState,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onOpenSettings: () -> Unit,
    onListen: () -> Unit,
    onStopListening: () -> Unit,
    onToggleSpeak: () -> Unit,
    onExitVoice: () -> Unit,
    micUsable: Boolean,
    modifier: Modifier = Modifier,
) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(ui.messages.size, ui.messages.lastOrNull()?.text) {
        if (ui.messages.isNotEmpty()) listState.animateScrollToItem(ui.messages.lastIndex)
    }

    val reactorState = when {
        ui.listening -> ReactorState.Listening
        ui.speaking -> ReactorState.Speaking
        ui.generating -> ReactorState.Thinking
        else -> ReactorState.Idle
    }

    // Whatever is actually answering — the phone's model or a server — so it is
    // never a guess which one a reply came from.
    val modelLabel = ui.installed.firstOrNull { it.file.name == ui.loadedModel }?.displayName
        ?: ui.loadedModel?.let(::prettyModelName)
        ?: ui.brainLabel

    Box(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                // Insets are handled here rather than at the root, so the voice
                // overlay below can cover the status and navigation bars instead
                // of stopping short of them in a different shade.
                .statusBarsPadding()
                .imePadding(),
        ) {
            ChatHeader(
                reactorState = reactorState,
                micLevel = ui.micLevel,
                modelLabel = modelLabel,
                speakReplies = ui.speakReplies,
                onToggleSpeak = onToggleSpeak,
                onOpenSettings = onOpenSettings,
            )

            if (ui.messages.isEmpty()) {
                EmptyChat(
                    reactorState = reactorState,
                    micLevel = ui.micLevel,
                    onSuggestion = onSend,
                    modifier = Modifier.weight(1f),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(ui.messages) { message -> Bubble(message) }
                }
            }

            VoiceStrip(ui = ui)

            Composer(
                input = input,
                onInputChange = { input = it },
                ui = ui,
                micUsable = micUsable,
                onSend = { onSend(input); input = "" },
                onStop = onStop,
                onListen = onListen,
                onStopListening = onStopListening,
            )
        }

        if (ui.voiceMode) {
            VoiceOverlay(
                state = reactorState,
                level = ui.micLevel,
                heard = ui.heardSoFar,
                note = ui.voiceNote,
                reply = ui.messages.lastOrNull()?.takeIf { !it.fromUser }?.text.orEmpty(),
                onDismiss = onExitVoice,
            )
        }
    }
}

@Composable
private fun ChatHeader(
    reactorState: ReactorState,
    micLevel: Float,
    modelLabel: String,
    speakReplies: Boolean,
    onToggleSpeak: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArcReactor(state = reactorState, level = micLevel, modifier = Modifier.size(34.dp))
        Spacer(Modifier.width(10.dp))
        // weight(1f) is what stops the model name pushing into the buttons; it
        // used to be unconstrained and wrapped the header onto three lines.
        Column(Modifier.weight(1f)) {
            Text(
                text = "JARVIS",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            if (modelLabel.isNotBlank()) {
                Text(
                    text = modelLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onToggleSpeak) {
            Icon(
                imageVector = if (speakReplies) JarvisIcons.VolumeUp else JarvisIcons.VolumeOff,
                contentDescription = stringResource(
                    if (speakReplies) R.string.action_mute else R.string.action_unmute,
                ),
                tint = if (speakReplies) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        IconButton(onClick = onOpenSettings) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = stringResource(R.string.action_settings),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The first thing a new user sees. It used to be two lines of grey text pinned
 * to the middle of nowhere; the system prompt already knows exactly what Jarvis
 * can do, so say it and offer a way in.
 */
@Composable
private fun EmptyChat(
    reactorState: ReactorState,
    micLevel: Float,
    onSuggestion: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val suggestions = listOf(
        stringResource(R.string.suggestion_one),
        stringResource(R.string.suggestion_two),
        stringResource(R.string.suggestion_three),
    )
    Column(
        modifier.fillMaxWidth().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ArcReactor(state = reactorState, level = micLevel, modifier = Modifier.size(120.dp))
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        suggestions.forEach { suggestion ->
            TextButton(onClick = { onSuggestion(suggestion) }) {
                Text(suggestion, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * Status and failure told apart. Both used to be printed in the same amber, so
 * "Listening…" and "Jarvis cannot use the microphone" looked identical.
 */
@Composable
private fun VoiceStrip(ui: UiState) {
    if (!ui.listening && ui.voiceNote == null && ui.heardSoFar.isBlank()) return
    val failed = !ui.listening && ui.voiceNote != null
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        ui.voiceNote?.let { note ->
            Text(
                text = note,
                style = MaterialTheme.typography.labelMedium,
                color = if (failed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        }
        if (ui.heardSoFar.isNotBlank()) {
            Text(
                text = ui.heardSoFar,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Composer(
    input: String,
    onInputChange: (String) -> Unit,
    ui: UiState,
    micUsable: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onListen: () -> Unit,
    onStopListening: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (micUsable) {
            FilledIconButton(
                onClick = if (ui.listening) onStopListening else onListen,
                enabled = ui.listening || (!ui.busy && !ui.generating),
                colors = if (ui.listening) {
                    IconButtonDefaults.filledIconButtonColors()
                } else {
                    IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.primary,
                    )
                },
            ) {
                Icon(
                    imageVector = if (ui.listening) JarvisIcons.Stop else JarvisIcons.Mic,
                    contentDescription = stringResource(
                        if (ui.listening) R.string.action_stop_listening else R.string.action_listen,
                    ),
                )
            }
        }

        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            modifier = Modifier.weight(1f),
            placeholder = {
                Text(
                    stringResource(R.string.composer_hint),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            // Typing during generation used to be accepted and then silently
            // thrown away, because send refuses while an answer is streaming.
            enabled = !ui.busy && !ui.generating,
            maxLines = 4,
            shape = MaterialTheme.shapes.large,
            textStyle = MaterialTheme.typography.bodyLarge,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
        )

        if (ui.generating) {
            FilledIconButton(onClick = onStop) {
                Icon(
                    imageVector = JarvisIcons.Stop,
                    contentDescription = stringResource(R.string.action_stop),
                )
            }
        } else {
            FilledIconButton(
                onClick = onSend,
                enabled = input.isNotBlank() && !ui.busy,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.action_send),
                )
            }
        }
    }
}

/**
 * The reactor taking over the screen for the length of a spoken exchange —
 * what the user asked for: talk to it and this is what you look at.
 */
@Composable
private fun VoiceOverlay(
    state: ReactorState,
    level: Float,
    heard: String,
    note: String?,
    reply: String,
    onDismiss: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // A close button rather than a full-screen tap target: the whole screen
        // being clickable gave no hint it was, and rippled on every touch.
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.action_close_voice),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            ArcReactor(state = state, level = level, modifier = Modifier.size(240.dp))
            Spacer(Modifier.height(24.dp))
            val caption = when (state) {
                ReactorState.Listening -> heard.ifBlank { note ?: stringResource(R.string.voice_listening) }
                ReactorState.Thinking -> stringResource(R.string.voice_thinking)
                ReactorState.Speaking -> reply
                ReactorState.Idle -> note ?: reply.ifBlank { stringResource(R.string.voice_idle) }
            }
            // Scrollable: a long answer used to run off the bottom of the screen
            // with no way to read the rest of it.
            Box(Modifier.fillMaxWidth().weight(1f, fill = false)) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                )
            }
        }
    }
}

@Composable
private fun Bubble(message: ChatMessage) {
    val fromUser = message.fromUser
    // BoxWithConstraints rather than fillMaxWidth(fraction): a fraction is a
    // width, not a limit, so every bubble was forced to exactly 86% of the
    // screen whatever it held. One word came out as a full-width slab.
    BoxWithConstraints(
        Modifier.fillMaxWidth(),
        contentAlignment = if (fromUser) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        val widest = maxWidth * 0.86f
        Surface(
            modifier = Modifier.widthIn(max = widest),
            // Not a filled amber block. A large saturated slab of AmberDeep on
            // a near-black ground is the heaviest thing on the screen, which is
            // the wrong weight for "hi" — the words are the content, the colour
            // is only there to say who said them. A tinted panel with a lit
            // edge says the same thing and lets the reactor stay the brightest
            // thing in the room.
            color = if (fromUser) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            border = if (fromUser) {
                BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
            } else {
                null
            },
            shape = MaterialTheme.shapes.medium,
        ) {
            Row(Modifier.height(IntrinsicSize.Min)) {
                // A lit edge on Jarvis's own words, so his side of the
                // conversation is identifiable at a glance without a fill.
                if (!fromUser) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .width(2.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                    )
                }
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    if (message.streaming && message.text.isEmpty()) {
                        // Was a static "…", which is indistinguishable from an
                        // app that has stopped. Waiting is the normal state on a
                        // 3B model, so it has to look like waiting.
                        ThinkingDots()
                    } else {
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (fromUser) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Three dots breathing in turn, while an answer is on its way.
 *
 * The point is only that it moves. A still screen during a thirty second wait
 * is the single most common reason someone decides an app has hung, and on a
 * phone-sized model that wait is ordinary rather than exceptional.
 */
@Composable
private fun ThinkingDots() {
    val transition = rememberInfiniteTransition(label = "thinking")
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(20.dp),
    ) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 600, delayMillis = index * 160),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot$index",
            )
            Box(
                Modifier
                    .size(6.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                        shape = CircleShape,
                    ),
            )
        }
    }
}
