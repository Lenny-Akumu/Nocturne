package app.nocturne.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.nocturne.AppGraph
import app.nocturne.BuildInfo
import app.nocturne.data.UpdateState
import kotlinx.coroutines.launch

@Composable
fun UpdateDialog() {
    val updater = AppGraph.instance.updater
    val state by updater.state.collectAsState()
    val scope = rememberCoroutineScope()
    val info = when (val s = state) {
        is UpdateState.Available -> s.info
        is UpdateState.Downloading -> s.info
        is UpdateState.Failed -> s.info
        UpdateState.Idle -> return
    }
    val downloading = state as? UpdateState.Downloading
    AlertDialog(
        onDismissRequest = { if (downloading == null) updater.later() },
        icon = { Icon(Icons.Rounded.SystemUpdate, null) },
        title = { Text("Nocturne ${info.version} is available") },
        text = {
            Column {
                Text("You have ${BuildInfo.VERSION}.", style = MaterialTheme.typography.bodyMedium)
                if (info.notes.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        info.notes.replace(Regex("[#*`]"), "").trim(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
                    )
                }
                if (downloading != null) {
                    Spacer(Modifier.height(14.dp))
                    LinearProgressIndicator(progress = { downloading.progress }, modifier = Modifier.fillMaxWidth())
                    Text("Downloading… ${(downloading.progress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                }
                (state as? UpdateState.Failed)?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { scope.launch { updater.install(info) } }, enabled = downloading == null) { Text("Update") }
        },
        dismissButton = {
            if (downloading == null) {
                Column {
                    TextButton(onClick = { updater.later() }) { Text("Later") }
                    TextButton(onClick = { updater.skip(info) }) { Text("Skip this version") }
                }
            }
        },
    )
}
