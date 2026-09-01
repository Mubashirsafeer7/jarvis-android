package com.mubashir.jarvis.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mubashir.jarvis.R

/**
 * What the app opens on while it picks the model back up.
 *
 * Cold-starting used to land on the model picker every single time, because a
 * loaded model was only ever remembered inside a process Android had since
 * killed — and a screen offering to download a model you already have reads as
 * though the download never worked.
 */
@Composable
fun WakingScreen(modelName: String?, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ArcReactor(
            state = ReactorState.Thinking,
            level = 0f,
            modifier = Modifier.size(160.dp),
        )
        Spacer(Modifier.height(28.dp))
        Text(
            text = stringResource(R.string.waking_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = modelName?.let { prettyModelName(it) }
                ?: stringResource(R.string.waking_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
