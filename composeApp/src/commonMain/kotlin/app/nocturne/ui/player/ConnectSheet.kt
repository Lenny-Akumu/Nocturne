package app.nocturne.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Speaker
import app.nocturne.ui.kit.CircularProgressIndicator
import app.nocturne.ui.kit.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import app.nocturne.ui.kit.Switch
import androidx.compose.material3.MaterialTheme
import app.nocturne.ui.kit.ModalBottomSheet
import app.nocturne.ui.kit.OutlinedButton
import app.nocturne.ui.kit.OutlinedTextField
import app.nocturne.ui.kit.OptionGroup
import app.nocturne.ui.kit.OptionRow
import app.nocturne.ui.kit.optionRow
import androidx.compose.material3.Text
import app.nocturne.ui.kit.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.nocturne.AppGraph
import app.nocturne.i18n.t
import kotlinx.coroutines.delay

@Composable
fun ConnectSheet(onDismiss: () -> Unit, onCarMode: () -> Unit) {
    val graph = AppGraph.instance
    val cast = graph.cast
    val party = graph.party
    val devices by cast.devices.collectAsState()
    val scanning by cast.scanning.collectAsState()
    val device by cast.device.collectAsState()
    val castError by cast.error.collectAsState()
    val hosting by party.hosting.collectAsState()
    val joined by party.joined.collectAsState()
    val partyStatus by party.status.collectAsState()
    val partyCode by party.code.collectAsState()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    LaunchedEffect(Unit) { if (device == null) cast.scan() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            app.nocturne.ui.YourDevicesSection()
            OptionGroup(t("Play on a device"), icon = Icons.Rounded.Cast) {
                if (device != null) {
                    Column(Modifier.optionRow().fillMaxWidth().padding(16.dp)) {
                        Text(t("Playing on %s", device!!.name), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge)
                        castError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        Spacer(Modifier.height(8.dp))
                        FilledTonalButton(onClick = { cast.disconnect() }) { Text(t("Stop casting")) }
                    }
                } else {
                    Note(t("TVs, speakers and receivers with DLNA / UPnP on the same Wi-Fi."))
                    devices.forEach { d ->
                        OptionRow(Modifier.clickable { cast.connect(d) }, inset = 54.dp) {
                            Icon(Icons.Rounded.Speaker, null)
                            Spacer(Modifier.width(14.dp))
                            Text(d.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    OptionRow(padding = ButtonRowPadding) {
                        if (scanning) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(t("Looking for devices…"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 12.dp))
                        } else {
                            if (devices.isEmpty()) Text(t("No devices found"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            else Spacer(Modifier.weight(1f))
                            TextButton(onClick = { cast.scan() }) { Text(t("Search again")) }
                        }
                    }
                }
            }

            OptionGroup(t("Listen together"), icon = Icons.Rounded.Groups) {
                Note(t("Friends anywhere hear the same song at the same moment."))
                when {
                    hosting.isNotEmpty() -> {
                        partyCode?.let { code ->
                            val clipboard = LocalClipboardManager.current
                            var copied by remember { mutableStateOf(false) }
                            LaunchedEffect(copied) { if (copied) { delay(1800); copied = false } }
                            val copy: () -> Unit = { clipboard.setText(AnnotatedString(code)); copied = true }
                            Column(Modifier.optionRow().fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp)) {
                                Text(t("Share this code with friends:"), style = MaterialTheme.typography.bodyMedium)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        code.chunked(4).joinToString("-"),
                                        Modifier.weight(1f, fill = false).clip(MaterialTheme.shapes.small).clickable(onClick = copy).padding(horizontal = 4.dp),
                                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary,
                                    )
                                    IconButton(onClick = copy) { Icon(if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy, t("Copy code")) }
                                }
                                if (copied) Text(t("Code copied"), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                            }
                            if (app.nocturne.platformKind == app.nocturne.PlatformKind.DESKTOP) {
                                val inDiscord by party.showInDiscord.collectAsState()
                                OptionRow(Modifier.clickable { party.setShowInDiscord(!inDiscord) }) {
                                    Column(Modifier.weight(1f)) {
                                        Text(t("Show in Discord"), style = MaterialTheme.typography.bodyLarge)
                                        Text(t("Adds a “Listen together” button to your Discord status"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Switch(inDiscord, { party.setShowInDiscord(it) })
                                }
                            }
                        }
                        partyStatus?.let { Note(t(it), MaterialTheme.colorScheme.error) }
                        val lan = hosting.filter { it.isNotBlank() }
                        if (lan.isNotEmpty()) {
                            Column(Modifier.optionRow().fillMaxWidth().padding(16.dp)) {
                                Text(t("On the same Wi-Fi they can also use:"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                lan.forEach { Text(it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium) }
                            }
                        }
                        OptionRow { FilledTonalButton(onClick = { party.stopHosting() }) { Text(t("Stop session")) } }
                    }
                    joined != null -> {
                        Column(Modifier.optionRow().fillMaxWidth().padding(16.dp)) {
                            Text(t("Listening with %s", joined!!), color = MaterialTheme.colorScheme.primary)
                            partyStatus?.let { Text(t(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                            Spacer(Modifier.height(8.dp))
                            FilledTonalButton(onClick = { party.leave() }) { Text(t("Leave")) }
                        }
                    }
                    else -> {
                        var address by remember { mutableStateOf("") }
                        OptionRow { FilledTonalButton(onClick = { party.host() }) { Text(t("Start a session")) } }
                        Column(Modifier.optionRow().fillMaxWidth().padding(16.dp)) {
                            OutlinedTextField(address, { address = it }, singleLine = true, label = { Text(t("Session code")) }, placeholder = { Text("ABCD-1234") }, modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = { party.join(address) }, enabled = address.count { it.isLetterOrDigit() } >= 8) { Text(t("Join")) }
                        }
                    }
                }
                RelayDiagnostics(party)
            }

            OptionGroup(t("Car mode"), icon = Icons.Rounded.DirectionsCar) {
                OptionRow(Modifier.clickable { onDismiss(); onCarMode() }, padding = PaddingValues(16.dp)) {
                    Text(t("Open car mode"), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private val ButtonRowPadding = PaddingValues(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)

// Secondary text as its own grouped row.
@Composable
private fun Note(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, Modifier.optionRow().fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), style = MaterialTheme.typography.bodySmall, color = color)
}

// Per-relay state of the session plus an on-demand round-trip check; selectable and copyable for bug reports.
@Composable
private fun RelayDiagnostics(party: app.nocturne.party.Party) {
    val details by party.details.collectAsState()
    val checks by party.checks.collectAsState()
    val checking by party.checking.collectAsState()
    val clipboard = LocalClipboardManager.current
    var open by remember { mutableStateOf(false) }
    val text = listOfNotNull(
        details.takeIf { it.isNotBlank() },
        checks?.let { c -> t("Connection check:") + "\n" + c.joinToString("\n") },
    ).joinToString("\n\n")
    OptionRow(padding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
        TextButton(onClick = { open = !open }) { Text(t(if (open) "Hide details" else "Connection details")) }
        TextButton(onClick = { open = true; party.checkConnection() }, enabled = !checking) { Text(t("Check connection")) }
        if (checking) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
    }
    if (open && text.isNotBlank()) {
        Row(Modifier.optionRow().fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.Top) {
            androidx.compose.foundation.text.selection.SelectionContainer(Modifier.weight(1f)) {
                Text(text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { clipboard.setText(AnnotatedString(text)) }) { Icon(Icons.Rounded.ContentCopy, t("Copy")) }
        }
    }
}
