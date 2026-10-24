package app.nocturne.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.nocturne.AppGraph
import app.nocturne.BuildInfo
import app.nocturne.data.Changelog
import app.nocturne.data.versionNewer
import app.nocturne.i18n.I18n
import app.nocturne.i18n.t
import app.nocturne.ui.kit.AlertDialog
import app.nocturne.ui.kit.TextButton

/** Once after an update: what changed since the version the user last opened. Fresh installs skip it. */
@Composable
fun WhatsNewDialog() {
    val graph = AppGraph.instance
    val settings by graph.settings.state.collectAsState()
    val seen = settings.lastSeenVersion
    if (seen == BuildInfo.VERSION || Demo.enabled) return
    // older builds didn't record this: if there's listening data it's an update, not a fresh install
    val lib = graph.library.state.value
    val updated = seen.isNotEmpty() || lib.history.isNotEmpty() || lib.liked.isNotEmpty()
    if (!updated) {
        LaunchedEffect(Unit) { graph.settings.update { it.copy(lastSeenVersion = BuildInfo.VERSION) } }
        return
    }
    val entries = if (seen.isEmpty()) Changelog.filter { it.version == BuildInfo.VERSION }
    else Changelog.filter { versionNewer(it.version, seen) && !versionNewer(it.version, BuildInfo.VERSION) }
    val close = { graph.settings.update { it.copy(lastSeenVersion = BuildInfo.VERSION) } }
    if (entries.isEmpty()) {
        LaunchedEffect(Unit) { close() }
        return
    }
    val uk = I18n.lang == "uk"
    AlertDialog(
        onDismissRequest = close,
        title = { Text(t("What's new in %s", BuildInfo.VERSION)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                entries.forEach { e ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (entries.size > 1) Text(e.version, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        (if (uk) e.uk else e.en).forEach { line ->
                            Row {
                                Text("•", color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(10.dp))
                                Text(line, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = close) { Text(t("Got it")) } },
    )
}
