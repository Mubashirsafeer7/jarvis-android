package com.mubashir.jarvis.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mubashir.jarvis.DeviceCapabilities
import com.mubashir.jarvis.UiState
import com.mubashir.jarvis.model.DownloadState
import com.mubashir.jarvis.model.InstalledModel
import com.mubashir.jarvis.model.ModelCatalog
import com.mubashir.jarvis.model.ModelSpec

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
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Text("JARVIS", fontSize = 34.sp, fontWeight = FontWeight.Light,
            color = MaterialTheme.colorScheme.primary)
        Text(
            "Ek dimaag chunein. Model phone ke andar rehta hai — uske baad internet ki " +
                "zaroorat nahi.",
            fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        ui.downloadingSpec?.let { spec ->
            DownloadCard(spec, ui.download, onCancelDownload)
        }

        if (ui.installed.isNotEmpty()) {
            SectionCard("PHONE MEIN MOJOOD") {
                ui.installed.forEachIndexed { i, model ->
                    if (i > 0) HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(model.displayName, fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface)
                            Text("%.2f GB".format(model.sizeGb), fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { onDelete(model) }) { Text("Delete") }
                        Button(onClick = { onLoad(model) }, enabled = !ui.busy) { Text("Load") }
                    }
                }
            }
        }

        SectionCard("DOWNLOAD KAREIN") {
            Text(
                "Aapke phone: %.1f GB RAM · %.1f GB free".format(caps.totalRamGb, freeSpaceGb),
                fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            // Downloads are WiFi-only on purpose — these files are gigabytes.
            // Without saying so, a download queued on mobile data just sits
            // there and looks broken.
            Text(
                "Download sirf WiFi par chalta hai — mobile data par intezaar karega.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            ModelCatalog.all.forEach { spec ->
                val fits = caps.totalRamGb >= spec.minRamGb
                val have = ui.installed.any { it.file.name == spec.fileName }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${spec.displayName}  ·  ${spec.role.name}",
                            fontSize = 15.sp,
                            color = if (fits) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            if (fits) "%.1f GB · %s".format(
                                spec.approxBytes / 1_073_741_824.0, spec.notes
                            ) else "Is phone ke liye bahut bada (%.1f GB RAM chahiye)"
                                .format(spec.minRamGb),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(0.dp))
                    if (have) {
                        Text("✓", color = MaterialTheme.colorScheme.primary, fontSize = 18.sp)
                    } else {
                        OutlinedButton(
                            onClick = { onDownload(spec) },
                            enabled = fits && ui.downloadingSpec == null && !ui.busy,
                        ) { Text("Get") }
                    }
                }
            }
        }

        SectionCard("YA APNI FILE LAGAYEIN") {
            Text(
                "Koi bhi GGUF file import karein — download link kaam na kare, ya aap koi " +
                    "aur model chalana chahein to yeh rasta hamesha khula hai.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onImport, enabled = !ui.busy) {
                Text("GGUF file chunein")
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DownloadCard(spec: ModelSpec, state: DownloadState?, onCancel: () -> Unit) {
    SectionCard("DOWNLOAD HO RAHA HAI") {
        Text(spec.displayName, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(12.dp))
        val running = state as? DownloadState.Running
        val fraction = running?.fraction
        if (fraction != null) {
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(8.dp))
        Text(
            running?.let {
                "%.2f GB / %.2f GB".format(
                    it.downloadedBytes / 1_073_741_824.0,
                    it.totalBytes / 1_073_741_824.0,
                )
            } ?: "Shuru ho raha hai…",
            fontSize = 12.sp, fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "App band kar sakte hain — download background mein chalta rahega.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onCancel) { Text("Cancel") }
    }
}

@Composable
internal fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp)) {
            Text(title, fontSize = 11.sp, letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}
