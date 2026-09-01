package com.mubashir.jarvis

import android.Manifest
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mubashir.jarvis.ui.ChatScreen
import com.mubashir.jarvis.ui.SetupScreen
import com.mubashir.jarvis.ui.theme.JarvisTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            JarvisTheme {
                Scaffold { inner -> JarvisApp(Modifier.padding(inner)) }
            }
        }
    }
}

@Composable
private fun JarvisApp(modifier: Modifier = Modifier) {
    val vm: ChatViewModel = viewModel()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Shown while a model is loaded; also the way back to the picker.
    var showSetup by remember { mutableStateOf(true) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) vm.import(uri, context.displayNameOf(uri))
    }

    // The mic button asks for permission the first time, then listens. Granting
    // it mid-session has to flip this back on without a restart.
    var micGranted by remember { mutableStateOf(vm.hasMicPermission()) }
    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        micGranted = granted
        if (granted) vm.startListening()
    }

    // Leave the picker once a model is actually loaded. As an effect, not a
    // write during composition.
    LaunchedEffect(ui.loadedModel, ui.busy) {
        if (ui.loadedModel != null && !ui.busy) showSetup = false
    }

    Box(modifier.fillMaxSize()) {
        if (ui.loadedModel == null || showSetup) {
            SetupScreen(
                ui = ui,
                caps = vm.capabilities,
                freeSpaceGb = vm.usableSpaceGb(),
                onDownload = vm::download,
                onCancelDownload = vm::cancelDownload,
                onImport = { picker.launch(arrayOf("*/*")) },
                onLoad = { vm.load(it.file) },
                onDelete = vm::delete,
            )
        } else {
            ChatScreen(
                ui = ui,
                onSend = vm::send,
                onStop = vm::stopGenerating,
                onBenchmark = vm::benchmark,
                onChangeModel = { showSetup = true },
                onListen = {
                    if (micGranted) vm.startListening()
                    else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                },
                onStopListening = vm::stopListening,
                onToggleSpeak = vm::toggleSpeakReplies,
                micUsable = vm.micAvailable(),
            )
        }

        if (ui.busy) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Model load ho raha hai — pehli baar mein thoda waqt lagta hai",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    ui.error?.let { message ->
        AlertDialog(
            onDismissRequest = vm::dismissError,
            confirmButton = { TextButton(onClick = vm::dismissError) { Text("Theek hai") } },
            title = { Text("Masla") },
            text = { Text(message) },
        )
    }
}

private fun android.content.Context.displayNameOf(uri: Uri): String =
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "imported.gguf"
