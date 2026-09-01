package com.mubashir.jarvis.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mubashir.jarvis.ChatMessage
import com.mubashir.jarvis.UiState

@Composable
fun ChatScreen(
    ui: UiState,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onBenchmark: () -> Unit,
    onChangeModel: () -> Unit,
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
        ui.generating -> ReactorState.Thinking
        ui.speaking -> ReactorState.Speaking
        else -> ReactorState.Idle
    }

    Box(modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
            ArcReactor(
                state = reactorState,
                level = ui.micLevel,
                modifier = Modifier.size(38.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text("JARVIS", fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                Text(
                    ui.loadedModel ?: "",
                    fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            }
            Row {
                TextButton(onClick = onToggleSpeak) {
                    Text(if (ui.speakReplies) "Awaaz on" else "Awaaz off")
                }
                TextButton(onClick = onBenchmark, enabled = !ui.busy && !ui.generating) {
                    Text("Speed")
                }
                TextButton(onClick = onChangeModel, enabled = !ui.generating) { Text("Model") }
            }
        }

        ui.benchmark?.let { result ->
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp)) {
                Text(
                    result,
                    fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (ui.messages.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "Kuch poochhein.\nSab kuch is phone ke andar — offline.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(ui.messages) { message -> Bubble(message) }
            }
        }

        if (ui.listening || ui.voiceNote != null) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
                ui.voiceNote?.let {
                    Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                }
                if (ui.heardSoFar.isNotBlank()) {
                    Text(
                        ui.heardSoFar,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (micUsable) {
                if (ui.listening) {
                    Button(onClick = onStopListening) { Text("Sun raha…") }
                } else {
                    OutlinedButton(
                        onClick = onListen,
                        enabled = !ui.busy && !ui.generating,
                    ) { Text("🎤") }
                }
            }
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Jarvis se baat karein…") },
                enabled = !ui.busy,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    onSend(input); input = ""
                }),
            )
            if (ui.generating) {
                Button(onClick = onStop) { Text("Stop") }
            } else {
                Button(
                    onClick = { onSend(input); input = "" },
                    enabled = input.isNotBlank() && !ui.busy,
                ) { Text("Send") }
            }
        }
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
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.97f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            ArcReactor(state = state, level = level, modifier = Modifier.size(260.dp))

            Text(
                text = when (state) {
                    ReactorState.Listening -> heard.ifBlank { note ?: "Sun raha hoon…" }
                    ReactorState.Thinking -> "Soch raha hoon…"
                    ReactorState.Speaking -> reply
                    ReactorState.Idle -> note ?: "Tap to close"
                },
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
private fun Bubble(message: ChatMessage) {
    val isUser = message.fromUser
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (isUser) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surface
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = message.text.ifEmpty { "…" },
                fontSize = 15.sp,
                color = if (isUser) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurface,
            )
            if (message.streaming) {
                Spacer(Modifier.height(4.dp))
                Text("soch raha hoon…", fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
