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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mubashir.jarvis.DeviceCapabilities
import com.mubashir.jarvis.R
import com.mubashir.jarvis.UiState
import com.mubashir.jarvis.model.DownloadState
import com.mubashir.jarvis.model.InstalledModel
import com.mubashir.jarvis.model.ModelCatalog
import com.mubashir.jarvis.model.ModelRole
import com.mubashir.jarvis.model.ModelSpec
import com.mubashir.jarvis.ui.theme.NumericStyle

@Composable
fun SetupScreen(
    ui: UiState,
    caps: DeviceCapabilities,
    freeSpaceGb: Double,
    onDownload: (ModelSpec) -> Unit,
    onCancelDownload: () -> Unit,
    onImport: () -> Unit,
    onLoad: (InstalledModel) -> Unit,
    onDelete: (InstalledModel) -> Unit,
    onExport: (InstalledModel) -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    // Deleting a multi-gigabyte file used to happen on a single tap, with the
    // button not even disabled while something else was running.
    var pendingDelete by remember { mutableStateOf<InstalledModel?>(null) }

    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                    )
                }
                Spacer(Modifier.width(4.dp))
            } else {
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = "JARVIS",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.setup_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(16.dp))

        ui.downloadingSpec?.let { spec ->
            SectionCard(stringResource(R.string.setup_downloading)) {
                DownloadBody(spec = spec, state = ui.download, onCancel = onCancelDownload)
            }
        }

        if (ui.installed.isNotEmpty()) {
            SectionCard(stringResource(R.string.setup_installed)) {
                ui.installed.forEachIndexed { index, model ->
                    if (index > 0) {
                        HorizontalDivider(
                            Modifier.padding(vertical = 10.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                    InstalledRow(
                        model = model,
                        loaded = ui.loadedModel == model.file.name,
                        busy = ui.busy,
                        onLoad = { onLoad(model) },
                        onExport = { onExport(model) },
                        onDelete = { pendingDelete = model },
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.setup_installed_detail),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionCard(stringResource(R.string.setup_available)) {
            Text(
                text = stringResource(R.string.setup_device, caps.totalRamGb, freeSpaceGb),
                style = NumericStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.setup_metered_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            ModelCatalog.all.forEachIndexed { index, spec ->
                if (index > 0) {
                    HorizontalDivider(
                        Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
                CatalogRow(
                    spec = spec,
                    fits = caps.totalRamGb >= spec.minRamGb,
                    have = ui.installed.any { it.file.name == spec.fileName },
                    enabled = ui.downloadingSpec == null && !ui.busy,
                    onDownload = { onDownload(spec) },
                )
            }
        }

        SectionCard(stringResource(R.string.setup_import)) {
            Text(
                text = stringResource(R.string.setup_import_detail),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onImport, enabled = !ui.busy) {
                Text(stringResource(R.string.setup_choose_file))
            }
        }

        Spacer(Modifier.height(24.dp))
        Spacer(Modifier.navigationBarsPadding())
    }

    pendingDelete?.let { model ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete_title)) },
            text = {
                Text(
                    stringResource(R.string.delete_body, model.displayName, model.sizeGb),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    onDelete(model)
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun InstalledRow(
    model: InstalledModel,
    loaded: Boolean,
    busy: Boolean,
    onLoad: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = model.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.setup_size_gb, model.sizeGb),
                    style = NumericStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (loaded) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.setup_loaded_marker),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        // Spaced, and all three gated on busy. They used to sit flush against
        // one another, with Delete — the destructive one — always enabled.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!loaded) {
                Button(onClick = onLoad, enabled = !busy) {
                    Text(stringResource(R.string.action_load))
                }
            }
            OutlinedButton(onClick = onExport, enabled = !busy) {
                Text(stringResource(R.string.action_save))
            }
            TextButton(onClick = onDelete, enabled = !busy) {
                Text(
                    text = stringResource(R.string.action_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun CatalogRow(
    spec: ModelSpec,
    fits: Boolean,
    have: Boolean,
    enabled: Boolean,
    onDownload: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = spec.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    // The raw enum name — FAST, DEEP — used to be printed here.
                    text = when (spec.role) {
                        ModelRole.FAST -> stringResource(R.string.role_fast)
                        ModelRole.DEEP -> stringResource(R.string.role_deep)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            Text(
                text = if (fits) {
                    stringResource(R.string.setup_size_gb, spec.approxBytes / BYTES_PER_GB) +
                        "  ·  " + spec.notes
                } else {
                    stringResource(R.string.setup_too_big, spec.minRamGb)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        if (have) {
            Text(
                text = stringResource(R.string.setup_installed_marker),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            OutlinedButton(onClick = onDownload, enabled = fits && enabled) {
                Text(stringResource(R.string.action_get))
            }
        }
    }
}

@Composable
private fun DownloadBody(spec: ModelSpec, state: DownloadState?, onCancel: () -> Unit) {
    Text(
        text = spec.displayName,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(Modifier.height(10.dp))

    val running = state as? DownloadState.Running
    if (running?.fraction != null) {
        LinearProgressIndicator(
            progress = { running.fraction!! },
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        LinearProgressIndicator(Modifier.fillMaxWidth())
    }

    Spacer(Modifier.height(8.dp))
    Text(
        text = when (state) {
            is DownloadState.Running -> stringResource(
                R.string.setup_progress,
                state.downloadedBytes / BYTES_PER_GB,
                state.totalBytes / BYTES_PER_GB,
            )

            is DownloadState.Waiting -> state.reason
            else -> stringResource(R.string.setup_starting)
        },
        style = NumericStyle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(6.dp))
    Text(
        text = stringResource(R.string.setup_can_close),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
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
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

private const val BYTES_PER_GB = 1_073_741_824.0
