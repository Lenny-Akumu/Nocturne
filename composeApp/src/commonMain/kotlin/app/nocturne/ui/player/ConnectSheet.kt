package app.nocturne.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Speaker
import app.nocturne.ui.kit.CircularProgressIndicator
import app.nocturne.ui.kit.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import app.nocturne.ui.kit.ModalBottomSheet
import app.nocturne.ui.kit.OutlinedButton
import app.nocturne.ui.kit.OutlinedTextField
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.nocturne.AppGraph
import app.nocturne.i18n.t

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
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Header(Icons.Rounded.Cast, "Play on a device")
            if (device != null) {
                Text(t("Playing on %s", device!!.name), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge)
                castError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(onClick = { cast.disconnect() }) { Text(t("Stop casting")) }
            } else {
                Text(t("TVs, speakers and receivers with DLNA / UPnP on the same Wi-Fi."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                devices.forEach { d ->
                    Row(
                        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { cast.connect(d) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Speaker, null)
                        Spacer(Modifier.width(14.dp))
                        Text(d.name, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (scanning) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(t("Looking for devices…"), style = MaterialTheme.typography.bodyMedium)
                    } else {
                        if (devices.isEmpty()) Text(t("No devices found"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        else Spacer(Modifier.weight(1f))
                        TextButton(onClick = { cast.scan() }) { Text(t("Search again")) }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Header(Icons.Rounded.Groups, "Listen together")
            Text(t("Friends anywhere hear the same song at the same moment."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            when {
                hosting.isNotEmpty() -> {
                    partyCode?.let { code ->
                        Text(t("Share this code with friends:"), style = MaterialTheme.typography.bodyMedium)
                        Text(code.chunked(4).joinToString("-"), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    partyStatus?.let { Text(t(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Spacer(Modifier.height(6.dp))
                    val lan = hosting.filter { it.isNotBlank() }
                    if (lan.isNotEmpty()) {
                        Text(t("On the same Wi-Fi they can also use:"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        lan.forEach { Text(it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium) }
                    }
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(onClick = { party.stopHosting() }) { Text(t("Stop session")) }
                }
                joined != null -> {
                    Text(t("Listening with %s", joined!!), color = MaterialTheme.colorScheme.primary)
                    partyStatus?.let { Text(t(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(onClick = { party.leave() }) { Text(t("Leave")) }
                }
                else -> {
                    var address by remember { mutableStateOf("") }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { party.host() }) { Text(t("Start a session")) }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(address, { address = it }, singleLine = true, label = { Text(t("Session code")) }, placeholder = { Text("ABCD-1234") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(onClick = { party.join(address) }, enabled = address.count { it.isLetterOrDigit() } >= 8) { Text(t("Join")) }
                }
            }

            Spacer(Modifier.height(18.dp))
            Header(Icons.Rounded.DirectionsCar, "Car mode")
            OutlinedButton(onClick = { onDismiss(); onCarMode() }) { Text(t("Open car mode")) }
        }
    }
}

@Composable
private fun Header(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Text(t(title), style = MaterialTheme.typography.titleLarge)
    }
}
