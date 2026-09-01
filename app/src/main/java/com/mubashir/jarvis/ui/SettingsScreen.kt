package com.mubashir.jarvis.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mubashir.jarvis.R
import com.mubashir.jarvis.UiState
import com.mubashir.jarvis.UpdateUi
import androidx.compose.material3.OutlinedTextField
import com.mubashir.jarvis.data.BrainChoice
import com.mubashir.jarvis.data.Settings
import com.mubashir.jarvis.ui.theme.NumericStyle

/**
 * Everything that was previously either crammed into the chat header or simply
 * unreachable: the speech toggle, the model, how long an answer may run, the
 * benchmark, and the diagnostics that say why something is not working.
 */
@Composable
fun SettingsScreen(
    ui: UiState,
    predictLength: Int,
    speechProblem: String?,
    micUsable: Boolean,
    appVersion: String,
    ramGb: Double,
    onBack: () -> Unit,
    onSetSpeak: (Boolean) -> Unit,
    onSetPredictLength: (Int) -> Unit,
    onManageModels: () -> Unit,
    onBenchmark: () -> Unit,
    onDismissBenchmark: () -> Unit,
    onClearChat: () -> Unit,
    onOpenVoiceSettings: () -> Unit,
    brainChoice: BrainChoice,
    onSetBrain: (BrainChoice) -> Unit,
    serverUrl: String,
    onSetServerUrl: (String) -> Unit,
    serverModel: String,
    onSetServerModel: (String) -> Unit,
    onCheckServer: () -> Unit,
    keepRescueCopy: Boolean,
    onSetKeepRescueCopy: (Boolean) -> Unit,
    phoneControl: Boolean,
    onSetPhoneControl: (Boolean) -> Unit,
    notifyUpdates: Boolean,
    onSetNotifyUpdates: (Boolean) -> Unit,
    notificationsBlocked: Boolean,
    canInstallUpdates: Boolean,
    onCheckUpdate: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
    onAllowInstalls: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(Modifier.height(8.dp))

        SettingsCard(title = stringResource(R.string.settings_voice)) {
            SwitchRow(
                label = stringResource(R.string.settings_speak_replies),
                detail = stringResource(R.string.settings_speak_replies_detail),
                checked = ui.speakReplies,
                enabled = speechProblem == null,
                onCheckedChange = onSetSpeak,
            )
            // The reason speech does not work was computed and then never shown,
            // so "speech on" could be displayed by an app that could not speak.
            speechProblem?.let { problem ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = problem,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = onOpenVoiceSettings) {
                    Text(stringResource(R.string.action_open_settings))
                }
            }
            if (!micUsable) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.settings_no_recogniser),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = onOpenVoiceSettings) {
                    Text(stringResource(R.string.action_open_settings))
                }
            }
        }

        SettingsCard(title = stringResource(R.string.settings_control)) {
            SwitchRow(
                label = stringResource(R.string.settings_phone_control),
                detail = stringResource(R.string.settings_phone_control_detail),
                checked = phoneControl,
                enabled = true,
                onCheckedChange = onSetPhoneControl,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.settings_phone_control_list),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SettingsCard(title = stringResource(R.string.settings_brain)) {
            Text(
                text = stringResource(R.string.brain_detail),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BrainChoice.entries.forEach { choice ->
                    FilterChip(
                        selected = choice == brainChoice,
                        onClick = { onSetBrain(choice) },
                        label = {
                            Text(
                                when (choice) {
                                    BrainChoice.Phone -> stringResource(R.string.brain_phone)
                                    BrainChoice.Server -> stringResource(R.string.brain_server)
                                },
                            )
                        },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            if (brainChoice == BrainChoice.Server) {
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = onSetServerUrl,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.server_url_label)) },
                    placeholder = { Text(stringResource(R.string.server_url_hint)) },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = serverModel,
                    onValueChange = onSetServerModel,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.server_model_label)) },
                    placeholder = { Text(stringResource(R.string.server_model_hint)) },
                    singleLine = true,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onCheckServer, enabled = serverUrl.isNotBlank()) {
                    Text(stringResource(R.string.server_check))
                }
                ui.serverCheck?.let { result ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = result,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            Text(
                text = ui.loadedModel?.let(::prettyModelName)
                    ?: stringResource(R.string.settings_no_model),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.settings_ram_free, ramGb, ui.freeSpaceGb),
                style = NumericStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onManageModels, enabled = !ui.busy) {
                Text(stringResource(R.string.settings_manage_models))
            }
            Spacer(Modifier.height(16.dp))
            SwitchRow(
                label = stringResource(R.string.settings_keep_copy),
                detail = stringResource(R.string.settings_keep_copy_detail),
                checked = keepRescueCopy,
                enabled = true,
                onCheckedChange = onSetKeepRescueCopy,
            )
        }

        SettingsCard(title = stringResource(R.string.settings_answers)) {
            Text(
                text = stringResource(R.string.settings_answer_length_detail),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Settings.PREDICT_CHOICES.forEach { choice ->
                    FilterChip(
                        selected = choice == predictLength,
                        onClick = { onSetPredictLength(choice) },
                        label = { Text(choice.toString()) },
                    )
                }
            }
        }

        SettingsCard(title = stringResource(R.string.settings_speed)) {
            Text(
                text = stringResource(R.string.settings_speed_detail),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ui.benchmark?.let { result ->
                Spacer(Modifier.height(10.dp))
                Text(
                    text = result,
                    style = NumericStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onBenchmark,
                    enabled = !ui.busy && !ui.generating && ui.loadedModel != null,
                ) {
                    Text(stringResource(R.string.settings_measure))
                }
                if (ui.benchmark != null) {
                    TextButton(onClick = onDismissBenchmark) {
                        Text(stringResource(R.string.action_clear))
                    }
                }
            }
        }

        SettingsCard(title = stringResource(R.string.settings_conversation)) {
            Text(
                text = stringResource(R.string.settings_clear_chat_detail, ui.messages.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onClearChat, enabled = ui.messages.isNotEmpty()) {
                Text(stringResource(R.string.settings_clear_chat))
            }
        }

        SettingsCard(title = stringResource(R.string.settings_updates)) {
            SwitchRow(
                label = stringResource(R.string.settings_notify_updates),
                detail = stringResource(R.string.settings_notify_updates_detail),
                checked = notifyUpdates,
                enabled = true,
                onCheckedChange = onSetNotifyUpdates,
            )
            if (notifyUpdates && notificationsBlocked) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.settings_notify_blocked),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(16.dp))
            UpdateBody(
                state = ui.update,
                canInstall = canInstallUpdates,
                onCheck = onCheckUpdate,
                onDownload = onDownloadUpdate,
                onInstall = onInstallUpdate,
                onAllowInstalls = onAllowInstalls,
            )
        }

        SettingsCard(title = stringResource(R.string.settings_about)) {
            Text(
                text = stringResource(R.string.settings_version, appVersion),
                style = NumericStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.settings_offline_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(24.dp))
        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun UpdateBody(
    state: UpdateUi,
    canInstall: Boolean,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onAllowInstalls: () -> Unit,
) {
    when (state) {
        UpdateUi.Idle -> {
            Text(
                text = stringResource(R.string.update_idle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onCheck) { Text(stringResource(R.string.update_check)) }
        }

        UpdateUi.Checking -> Text(
            text = stringResource(R.string.update_checking),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        UpdateUi.UpToDate -> {
            Text(
                text = stringResource(R.string.update_current),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onCheck) { Text(stringResource(R.string.update_check_again)) }
        }

        is UpdateUi.Available -> {
            Text(
                text = stringResource(R.string.update_available, state.update.versionName),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (state.update.notes.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = state.update.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.update_size, state.update.sizeBytes / MB),
                style = NumericStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            // Android refuses to install anything until the user allows it, and
            // says nothing helpful when it does — so ask first rather than
            // downloading sixty megabytes into a dead end.
            if (canInstall) {
                OutlinedButton(onClick = onDownload) {
                    Text(stringResource(R.string.update_download))
                }
            } else {
                Text(
                    text = stringResource(R.string.update_needs_permission),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = onAllowInstalls) {
                    Text(stringResource(R.string.update_allow))
                }
            }
        }

        is UpdateUi.Downloading -> {
            val fraction = if (state.total > 0) {
                (state.downloaded.toFloat() / state.total).coerceIn(0f, 1f)
            } else {
                null
            }
            if (fraction != null) {
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(
                    R.string.update_progress,
                    state.downloaded / MB,
                    state.total / MB,
                ),
                style = NumericStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        is UpdateUi.Ready -> {
            Text(
                text = stringResource(R.string.update_ready),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onInstall) { Text(stringResource(R.string.update_install)) }
        }

        is UpdateUi.Failed -> {
            Text(
                text = state.reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onCheck) { Text(stringResource(R.string.action_retry)) }
        }
    }
}

private const val MB = 1024.0 * 1024.0

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    detail: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
