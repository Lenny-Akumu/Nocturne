package app.nocturne.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhoneIphone
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nocturne.AppGraph
import app.nocturne.PlatformKind
import app.nocturne.i18n.t
import app.nocturne.nowMillis
import app.nocturne.sync.Peer
import app.nocturne.ui.kit.AlertDialog
import app.nocturne.ui.kit.Card
import app.nocturne.ui.kit.FilledTonalButton
import app.nocturne.ui.kit.GroupedColumn
import app.nocturne.ui.kit.OptionGroup
import app.nocturne.ui.kit.optionRow
import app.nocturne.ui.kit.OutlinedButton
import app.nocturne.ui.kit.OutlinedTextField
import app.nocturne.ui.kit.Slider
import app.nocturne.ui.kit.TextButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** In-app QR scanner (Android), or null where there is none. */
@Composable
expect fun rememberQrScanner(): (suspend () -> String?)?

@Composable
fun QrCode(text: String, modifier: Modifier = Modifier) {
    val m = remember(text) { runCatching { QrEncoder.encode(text) }.getOrNull() }
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(Color.White).padding(12.dp)) {
        if (m != null) Canvas(Modifier.fillMaxSize()) {
            val cell = size.minDimension / m.size
            for (y in m.indices) for (x in m.indices) {
                if (m[y][x]) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
            }
        }
    }
}

@Composable
private fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(nowMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = nowMillis()
        }
    }
    return now
}

private fun PlatformKind.icon(): ImageVector = when (this) {
    PlatformKind.DESKTOP -> Icons.Rounded.Computer
    PlatformKind.IOS -> Icons.Rounded.PhoneIphone
    PlatformKind.ANDROID -> Icons.Rounded.Smartphone
}

private fun ago(ms: Long): String {
    val min = ms / 60_000
    return when {
        min < 1 -> t("just now")
        min < 60 -> t("%s min ago", min)
        min < 48 * 60 -> t("%s h ago", min / 60)
        else -> t("%s days ago", min / (24 * 60))
    }
}

private fun status(p: Peer, now: Long): String {
    val s = p.state
    if (!p.online(now)) return if (p.info.lastSeen > 0) t("Offline · seen %s", ago(now - p.info.lastSeen)) else t("Offline")
    if (s == null) return t("Online")
    val tr = s.track ?: return t("Online")
    return (if (s.playing) t("Playing: %s", tr.title) else t("Paused: %s", tr.title)) + if (tr.artistNames.isNotBlank()) " — ${tr.artistNames}" else ""
}

@Composable
private fun OnlineDot(online: Boolean) {
    Box(Modifier.size(9.dp).clip(CircleShape).background(if (online) Color(0xFF34C759) else MaterialTheme.colorScheme.outline))
}

@Composable
private fun DeviceRow(p: Peer, now: Long, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    val online = p.online(now)
    Row(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(p.info.platform.icon(), null, tint = if (online) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.info.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(8.dp))
                OnlineDot(online)
            }
            Text(status(p, now), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

/** Settings → Devices & sync: paired devices, pairing and leaving. */
@Composable
fun DevicesSettings() {
    val graph = AppGraph.instance
    val sync = graph.sync
    val group by sync.group.collectAsState()
    val peers by sync.peers.collectAsState()
    val connected by sync.connected.collectAsState()
    val now = rememberNow()
    val scope = rememberCoroutineScope()
    val scanner = rememberQrScanner()
    var addOpen by remember { mutableStateOf(false) }
    var linkOpen by remember { mutableStateOf(false) }
    var leaveOpen by remember { mutableStateOf(false) }
    var unpair by remember { mutableStateOf<Peer?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            t("Pair your phone, PC and iPhone to share likes, playlists and history, see what's playing on each one and move playback between them. Everything is end-to-end encrypted."),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Row(Modifier.padding(18.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Devices, null, Modifier.size(32.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(t("This device: %s", sync.myName), style = MaterialTheme.typography.titleMedium)
                    Text(
                        when {
                            group == null -> t("Not paired with other devices yet")
                            connected -> t("Connected")
                            else -> t("Connecting…")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
        // Paired devices in one card, divided rows.
        if (peers.isNotEmpty()) GroupedColumn(side = 0.dp) {
            peers.forEach { p -> DeviceRow(p, now, Modifier.optionRow(54.dp).padding(start = 16.dp, end = 8.dp)) { TextButton(onClick = { unpair = p }) { Text(t("Unpair")) } } }
        } else if (group != null) {
            Text(t("No other devices yet."), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 8.dp))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { addOpen = true }) { Text(t("Add a device")) }
            if (scanner != null) OutlinedButton(onClick = {
                scope.launch {
                    val text = runCatching { scanner() }.getOrElse { message = it.message ?: "Scanner unavailable"; return@launch } ?: return@launch
                    message = if (sync.joinLink(text)) t("Paired. Your devices will appear in a moment.") else t("This isn't a Nocturne pairing code")
                }
            }) { Text(t("Scan QR code")) }
            OutlinedButton(onClick = { linkOpen = true }) { Text(t("Enter pairing link")) }
            if (group != null) TextButton(onClick = { leaveOpen = true }) { Text(t("Leave sync")) }
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 8.dp)) }
    }

    if (addOpen) AddDeviceDialog { addOpen = false }
    if (linkOpen) {
        var text by remember { mutableStateOf("") }
        var error by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { linkOpen = false },
            title = { Text(t("Enter pairing link")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(t("On the other device open Add a device and copy the link."), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(text, { text = it; error = false }, singleLine = true, placeholder = { Text("nocturne://pair?…") }, isError = error, modifier = Modifier.fillMaxWidth())
                    if (error) Text(t("This isn't a Nocturne pairing code"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (sync.joinLink(text)) { linkOpen = false; message = t("Paired. Your devices will appear in a moment.") } else error = true
                }) { Text(t("Pair")) }
            },
            dismissButton = { TextButton(onClick = { linkOpen = false }) { Text(t("Cancel")) } },
        )
    }
    unpair?.let { p ->
        AlertDialog(
            onDismissRequest = { unpair = null },
            title = { Text(t("Unpair %s?", p.info.name)) },
            text = { Text(t("It stops syncing and can't control your other devices. To add it back, pair it again.")) },
            confirmButton = { TextButton(onClick = { sync.unpair(p.info.id); unpair = null }) { Text(t("Unpair")) } },
            dismissButton = { TextButton(onClick = { unpair = null }) { Text(t("Cancel")) } },
        )
    }
    if (leaveOpen) AlertDialog(
        onDismissRequest = { leaveOpen = false },
        title = { Text(t("Leave sync?")) },
        text = { Text(t("This device stops syncing with your other devices. Your library here stays as it is.")) },
        confirmButton = { TextButton(onClick = { sync.leave(); leaveOpen = false }) { Text(t("Leave")) } },
        dismissButton = { TextButton(onClick = { leaveOpen = false }) { Text(t("Cancel")) } },
    )
}

@Composable
fun AddDeviceDialog(onDismiss: () -> Unit) {
    val sync = AppGraph.instance.sync
    val link = remember { sync.inviteLink() }
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        sync.paired.collect { onDismiss() } // DeviceSync shows the toast
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Add a device")) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                QrCode(link, Modifier.size(240.dp))
                Text(
                    t("On the other device open Nocturne → Settings → Devices & sync → Scan QR code. On iPhone just scan it with the Camera app."),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
                Text(
                    t("Anyone with this code can join your devices. Only scan it with your own."),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t("Close")) } },
        dismissButton = {
            TextButton(onClick = { clipboard.setText(AnnotatedString(link)); copied = true }) { Text(if (copied) t("Link copied") else t("Copy link")) }
        },
    )
}

/** Asks before joining a group from a link opened outside the app. */
@Composable
fun PairLinkDialog() {
    val sync = AppGraph.instance.sync
    val link by sync.pendingLink.collectAsState()
    val url = link ?: return
    AlertDialog(
        onDismissRequest = { sync.pendingLink.value = null },
        title = { Text(t("Pair this device?")) },
        text = {
            Text(t("It joins your other Nocturne devices: they share likes, playlists and history and can control playback here. Continue only if the code came from your own device."))
        },
        confirmButton = { TextButton(onClick = { sync.confirmLink(url) }) { Text(t("Pair")) } },
        dismissButton = { TextButton(onClick = { sync.pendingLink.value = null }) { Text(t("Cancel")) } },
    )
}

/** "Your devices" in the Connect sheet: see what plays where, take it over or send it there. */
@Composable
fun YourDevicesSection() {
    val graph = AppGraph.instance
    val sync = graph.sync
    val peers by sync.peers.collectAsState()
    val player by graph.player.state.collectAsState()
    val now = rememberNow()
    if (peers.isEmpty()) return
    OptionGroup(t("Your devices"), icon = Icons.Rounded.Devices) {
        peers.sortedByDescending { it.online(now) }.forEach { p -> key(p.info.id) {
            val online = p.online(now)
            val s = p.state
            // One row per device: status plus its remote controls.
            Column(Modifier.optionRow(54.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                DeviceRow(p, now)
                if (online && s != null) {
                    if (!s.rc) {
                        Text(t("Remote control is off on this device"), Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (s.track != null) {
                                IconButton(onClick = { sync.control(p.info.id, "prev") }) { Icon(Icons.Rounded.SkipPrevious, t("Previous")) }
                                IconButton(onClick = { sync.control(p.info.id, "toggle") }) {
                                    Icon(if (s.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, t(if (s.playing) "Pause" else "Play"))
                                }
                                IconButton(onClick = { sync.control(p.info.id, "next") }) { Icon(Icons.Rounded.SkipNext, t("Next")) }
                                FilledTonalButton(onClick = { sync.playHere(p.info.id) }) { Text(t("Play here")) }
                            }
                            if (player.current != null) OutlinedButton(onClick = { sync.playOn(p.info.id) }) { Text(t("Play on %s", p.info.name)) }
                        }
                        if (s.track != null) {
                            var vol by remember(p.info.id, s.vol) { mutableFloatStateOf(s.vol) }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.AutoMirrored.Rounded.VolumeUp, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(10.dp))
                                Slider(vol, { vol = it }, Modifier.weight(1f), onValueChangeFinished = { sync.control(p.info.id, "vol", vol = vol) })
                            }
                        }
                    }
                }
            }
        } }
    }
}

/** Thin bar above the mini player while another device plays and nothing plays here. */
@Composable
fun RemoteStrip(modifier: Modifier = Modifier) {
    val graph = AppGraph.instance
    val sync = graph.sync
    val peers by sync.peers.collectAsState()
    val player by graph.player.state.collectAsState()
    val now = rememberNow()
    if (player.current != null) return
    val p = peers.firstOrNull { pr -> pr.online(now) && pr.state?.let { it.playing && it.track != null } == true } ?: return
    val s = p.state ?: return
    Row(
        modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(enabled = s.rc) { sync.playHere(p.info.id) }
            .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(p.info.platform.icon(), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(t("Playing on %s", p.info.name), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(s.track?.title.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (s.rc) {
            IconButton(onClick = { sync.control(p.info.id, "pause") }) { Icon(Icons.Rounded.Pause, t("Pause"), tint = MaterialTheme.colorScheme.onSecondaryContainer) }
            TextButton(onClick = { sync.playHere(p.info.id) }) { Text(t("Play here")) }
        }
    }
}
