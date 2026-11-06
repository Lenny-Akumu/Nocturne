package app.nocturne.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.nocturne.AppGraph
import app.nocturne.data.DesignStyle
import app.nocturne.i18n.t
import app.nocturne.player.Eq
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val LPink = Color(0xFFFF5C7A)
private val LViolet = Color(0xFFB36BFF)
private val GYellow = Color(0xFFFABD2F)
private val GGreen = Color(0xFFB8BB26)
private val GAqua = Color(0xFF8EC07C)
private val GOrange = Color(0xFFFE8019)
private val GRed = Color(0xFFFB4934)
private val GBg = Color(0xFF1D2021)
private val GBg1 = Color(0xFF3C3836)
private val TBase = Color(0xFF1E1E2E)
private val TMantle = Color(0xFF181825)
private val TSurface = Color(0xFF313244)
private val TPink = Color(0xFFF5C2E7)
private val TMauve = Color(0xFFCBA6F7)
private val TBlue = Color(0xFF89B4FA)
private val TSky = Color(0xFF89DCEB)
private val TGreen = Color(0xFFA6E3A1)
private val TPeach = Color(0xFFFAB387)
private val TYellow = Color(0xFFF9E2AF)
private val TMuted = Color(0xFF7F849C)

@Composable
private fun Popup(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.padding(16.dp)) { content() }
    }
}

// ---------- shared state ----------

private class ConnectModel {
    val graph = AppGraph.instance
    val cast = graph.cast
    val party = graph.party
}

@Composable
private fun rememberCopy(): Pair<Boolean, (String) -> Unit> {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(1800); copied = false } }
    return copied to { s: String -> clipboard.setText(AnnotatedString(s)); copied = true }
}

private fun pretty(code: String) = code.chunked(4).joinToString("-")

private fun presetNames(custom: Map<String, List<Float>>) = listOf("Custom") + Eq.presets.keys + custom.keys.filter { it !in Eq.presets }

private fun selectPreset(name: String) {
    val graph = AppGraph.instance
    val gains = graph.settings.value.customEqPresets[name] ?: Eq.presets[name]
    graph.settings.update { if (gains == null) it.copy(eqPreset = name, eqEnabled = true) else it.copy(eqPreset = name, eqGains = gains, eqEnabled = true) }
}

private fun setGain(i: Int, db: Float) {
    val graph = AppGraph.instance
    val cur = graph.settings.value.eqGains.takeIf { it.size == Eq.bands.size } ?: List(Eq.bands.size) { 0f }
    val next = cur.toMutableList().also { it[i] = db.coerceIn(-Eq.MAX_DB, Eq.MAX_DB) }
    graph.settings.update { it.copy(eqGains = next, eqPreset = "Custom", eqEnabled = true) }
}

private fun bandLabel(f: Float) = if (f >= 1000f) { val k = f / 1000f; if (k == k.roundToInt().toFloat()) "${k.roundToInt()}k" else "${(k * 10).roundToInt() / 10f}k" } else "${f.roundToInt()}"

private fun presetLabel(n: String) = if (n == "Custom") t("Custom") else n

/** Vertical: tap or drag to set 0..1 (top = 1). */
private fun Modifier.vDrag(onChange: (Float) -> Unit) = this
    .pointerInput(Unit) { detectTapGestures { o -> onChange(1f - (o.y / size.height).coerceIn(0f, 1f)) } }
    .pointerInput(Unit) { detectVerticalDragGestures { c, _ -> onChange(1f - (c.position.y / size.height).coerceIn(0f, 1f)) } }

private fun db(frac: Float) = ((frac * 2f - 1f) * Eq.MAX_DB * 2).roundToInt() / 2f
private fun frac(db: Float) = ((db / Eq.MAX_DB) + 1f) / 2f

private fun speedFrac(v: Float) = ((v - 0.5f) / 1.5f).coerceIn(0f, 1f)
private fun speedOf(f: Float) = ((0.5f + f * 1.5f) * 20).roundToInt() / 20f

// ---------- entry points ----------

@Composable
internal fun StyledConnectPopup(style: DesignStyle, onDismiss: () -> Unit, onCarMode: () -> Unit) {
    val m = remember { ConnectModel() }
    val device by m.cast.device.collectAsState()
    LaunchedEffect(Unit) { if (device == null) m.cast.scan() }
    Popup(onDismiss) {
        when (style) {
            DesignStyle.LINUX -> LinuxConnect(m, onDismiss, onCarMode)
            DesignStyle.ARCH -> ArchConnect(m, onDismiss, onCarMode)
            else -> TerminalConnect(m, onDismiss, onCarMode)
        }
    }
}

@Composable
internal fun StyledAudioPopup(style: DesignStyle, onDismiss: () -> Unit) {
    Popup(onDismiss) {
        when (style) {
            DesignStyle.LINUX -> LinuxAudio(onDismiss)
            DesignStyle.ARCH -> ArchAudio(onDismiss)
            else -> TerminalAudio(onDismiss)
        }
    }
}

// ---------- Linux ----------

@Composable
private fun LinuxCard(width: Dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.width(width).shadow(30.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp)).background(Color(0xF21A1220))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(22.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = content,
    )
}

@Composable
private fun LinuxHeader(title: String, sub: String?, onDismiss: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold))
            sub?.let { Text(it, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall) }
        }
        trailing()
        Text("✕", color = Color.White.copy(alpha = 0.6f), modifier = Modifier.clip(CircleShape).clickable(onClick = onDismiss).padding(8.dp))
    }
}

@Composable
private fun LBtn(label: String, primary: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        label, textAlign = TextAlign.Center, maxLines = 1,
        color = if (primary) Color(0xFF1A0710) else Color.White,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (primary) FontWeight.Bold else FontWeight.Medium),
        modifier = modifier.clip(RoundedCornerShape(12.dp)).background(if (primary) LPink else Color.White.copy(alpha = 0.08f)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

@Composable
private fun LinuxConnect(m: ConnectModel, onDismiss: () -> Unit, onCarMode: () -> Unit) {
    val hosting by m.party.hosting.collectAsState()
    val joined by m.party.joined.collectAsState()
    val code by m.party.code.collectAsState()
    val status by m.party.status.collectAsState()
    val members by m.party.members.collectAsState()
    val (copied, copy) = rememberCopy()
    LinuxCard(440.dp) {
        LinuxHeader(t("Listen together"), t("Friends anywhere hear the same song at the same moment."), onDismiss)
        Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                hosting.isNotEmpty() -> {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(LPink.copy(alpha = 0.28f), LViolet.copy(alpha = 0.22f))))
                            .clickable { code?.let { copy(pretty(it)) } }.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(if (copied) t("Code copied") else t("Session code"), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
                        Text(code?.let(::pretty) ?: "…", color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 32.sp, letterSpacing = 4.sp)
                        if (members.isNotEmpty()) Text("● " + members.joinToString(", ") { it.name }, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LBtn("⎘ " + t("Copy"), modifier = Modifier.weight(1f)) { code?.let { copy(pretty(it)) } }
                        if (app.nocturne.platformKind == app.nocturne.PlatformKind.DESKTOP) {
                            val inDiscord by m.party.showInDiscord.collectAsState()
                            LBtn("Discord" + if (inDiscord) " ✓" else "", modifier = Modifier.weight(1f)) { m.party.setShowInDiscord(!inDiscord) }
                        }
                        LBtn(t("Stop session"), primary = true, modifier = Modifier.weight(1f)) { m.party.stopHosting() }
                    }
                }
                joined != null -> {
                    Text(t("Listening with %s", joined!!), color = LPink, style = MaterialTheme.typography.titleMedium)
                    LBtn(t("Leave"), primary = true) { m.party.leave() }
                }
                else -> {
                    LBtn(t("Start a session"), primary = true, modifier = Modifier.fillMaxWidth()) { m.party.host() }
                    var address by remember { mutableStateOf("") }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CodeField(address, { address = it }, "ABCD-1234", Color.White, Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.08f)).padding(12.dp))
                        LBtn(t("Join")) { if (address.count { it.isLetterOrDigit() } >= 8) m.party.join(address) }
                    }
                }
            }
            status?.let { Text(t(it), color = LPink, style = MaterialTheme.typography.bodySmall) }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.1f)))
            Text(t("Play on a device"), color = Color.White, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            DeviceRows(m) { name, on, sub, click ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (on) LPink.copy(alpha = 0.16f) else Color.Transparent).clickable(onClick = click).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(if (on) LPink else Color.White.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) { Text(if (sub == null) "↻" else "🔊") }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, color = Color.White, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                        sub?.let { Text(it, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall) }
                    }
                    if (on) Text("● " + t("playing"), color = LPink, style = MaterialTheme.typography.labelSmall)
                }
            }
            LBtn("🚗  " + t("Car mode"), modifier = Modifier.fillMaxWidth()) { onDismiss(); onCarMode() }
        }
    }
}

/** Device list rows: the playing device, found devices, then a "search again" row. */
@Composable
private fun DeviceRows(m: ConnectModel, row: @Composable (name: String, on: Boolean, sub: String?, onClick: () -> Unit) -> Unit) {
    val devices by m.cast.devices.collectAsState()
    val scanning by m.cast.scanning.collectAsState()
    val device by m.cast.device.collectAsState()
    val error by m.cast.error.collectAsState()
    device?.let { d -> row(d.name, true, t("Stop casting")) { m.cast.disconnect() } }
    error?.let { Text(it, color = Color(0xFFFF6B6B), style = MaterialTheme.typography.bodySmall) }
    if (device == null) devices.forEach { d -> row(d.name, false, "DLNA / UPnP") { m.cast.connect(d) } }
    if (device == null) row(if (scanning) t("Looking for devices…") else if (devices.isEmpty()) t("No devices found") + " · " + t("Search again") else t("Search again"), false, null) { if (!scanning) m.cast.scan() }
}

@Composable
private fun CodeField(value: String, onChange: (String) -> Unit, hint: String, color: Color, modifier: Modifier) {
    Box(modifier) {
        if (value.isEmpty()) Text(hint, color = color.copy(alpha = 0.4f), fontFamily = FontFamily.Monospace)
        BasicTextField(value, onChange, singleLine = true, textStyle = MaterialTheme.typography.bodyLarge.copy(color = color, fontFamily = FontFamily.Monospace), cursorBrush = SolidColor(color), modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun LinuxAudio(onDismiss: () -> Unit) {
    val graph = AppGraph.instance
    val settings by graph.settings.state.collectAsState()
    val gains = settings.eqGains.takeIf { it.size == Eq.bands.size } ?: List(Eq.bands.size) { 0f }
    LinuxCard(560.dp) {
        LinuxHeader(t("Equalizer"), null, onDismiss) {
            app.nocturne.ui.kit.Switch(checked = settings.eqEnabled, onCheckedChange = { v -> graph.settings.update { it.copy(eqEnabled = v) } })
            Spacer(Modifier.width(6.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.width(150.dp).heightIn(max = 300.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                presetNames(settings.customEqPresets).forEach { n ->
                    val on = settings.eqPreset == n
                    Text(
                        presetLabel(n), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (on) Color(0xFF1A0710) else Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (on) FontWeight.Bold else FontWeight.Normal),
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (on) LPink else Color.Transparent).clickable { selectPreset(n) }.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    Eq.bands.forEachIndexed { i, f ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${if (gains[i] > 0) "+" else ""}${gains[i].roundToInt()}", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                            Canvas(Modifier.padding(vertical = 8.dp).size(26.dp, 190.dp).vDrag { setGain(i, db(it)) }) {
                                val cx = size.width / 2
                                val w = 8.dp.toPx()
                                val y = size.height * (1f - frac(gains[i]))
                                drawRoundRect(Color.White.copy(alpha = 0.12f), Offset(cx - w / 2, 0f), Size(w, size.height), androidx.compose.ui.geometry.CornerRadius(w / 2))
                                drawRoundRect(Brush.verticalGradient(listOf(LViolet, LPink)), Offset(cx - w / 2, y), Size(w, size.height - y), androidx.compose.ui.geometry.CornerRadius(w / 2))
                                drawCircle(Color.White, 11.dp.toPx(), Offset(cx, y))
                            }
                            Text(bandLabel(f), color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                LinuxLine(t("Speed"), speedFrac(settings.playbackSpeed), "${settings.playbackSpeed}×") { f -> graph.settings.update { it.copy(playbackSpeed = speedOf(f)) } }
                LinuxLine(t("Crossfade"), settings.crossfadeSec / 12f, "${settings.crossfadeSec} s") { f -> graph.settings.update { it.copy(crossfadeSec = (f * 12).roundToInt()) } }
            }
        }
    }
}

@Composable
private fun LinuxLine(label: String, value: Float, text: String, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(84.dp))
        Canvas(Modifier.weight(1f).height(20.dp).hDrag(onChange)) {
            val h = 5.dp.toPx()
            val y = size.height / 2
            drawRoundRect(Color.White.copy(alpha = 0.15f), Offset(0f, y - h / 2), Size(size.width, h), androidx.compose.ui.geometry.CornerRadius(h))
            drawRoundRect(LPink, Offset(0f, y - h / 2), Size(size.width * value.coerceIn(0f, 1f), h), androidx.compose.ui.geometry.CornerRadius(h))
        }
        Text(text, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(48.dp), textAlign = TextAlign.End)
    }
}

private fun Modifier.hDrag(onChange: (Float) -> Unit) = this
    .pointerInput(Unit) { detectTapGestures { o -> onChange((o.x / size.width).coerceIn(0f, 1f)) } }
    .pointerInput(Unit) { androidx.compose.foundation.gestures.detectHorizontalDragGestures { c, _ -> onChange((c.position.x / size.width).coerceIn(0f, 1f)) } }

// ---------- Arch ----------

@Composable
private fun ArchFrame(title: String, width: Dp, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.width(width).padding(top = 10.dp)) {
        Column(Modifier.fillMaxWidth().background(GBg).border(1.dp, GYellow).padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
        Text("[ $title ]", color = GYellow, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 12.dp).offset(y = (-10).dp).background(GBg).padding(horizontal = 6.dp))
    }
}


@Composable
private fun ArchKeys(vararg keys: Pair<String, Pair<String, () -> Unit>>) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        keys.forEach { (k, v) ->
            Row(Modifier.clickable(onClick = v.second)) {
                Text(" $k ", color = GBg, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.background(GYellow))
                Text(" " + v.first, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ArchConnect(m: ConnectModel, onDismiss: () -> Unit, onCarMode: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val hosting by m.party.hosting.collectAsState()
    val joined by m.party.joined.collectAsState()
    val code by m.party.code.collectAsState()
    val status by m.party.status.collectAsState()
    val members by m.party.members.collectAsState()
    val (copied, copy) = rememberCopy()
    var devices by remember { mutableStateOf(false) }
    var address by remember { mutableStateOf("") }
    ArchFrame(if (devices) t("play on a device") else t("listen together"), 480.dp) {
        if (devices) {
            DeviceRows(m) { name, on, sub, click ->
                Row(Modifier.fillMaxWidth().background(if (on) GBg1 else Color.Transparent).clickable(onClick = click)) {
                    Text(if (on) "▶ " else "  ", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    Text(name, color = if (on) GYellow else cs.onSurface, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    sub?.let { Text(it, color = GAqua, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        } else {
            when {
                hosting.isNotEmpty() -> {
                    Text(if (copied) t("Code copied") else t("Share this code with friends:"), color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        code?.let(::pretty) ?: "…", color = GGreen, fontSize = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = 8.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().border(1.dp, cs.onSurfaceVariant).clickable { code?.let { copy(pretty(it)) } }.padding(vertical = 10.dp),
                    )
                    if (members.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        members.forEach { Text("● " + it.name, color = if (it.me) GYellow else GAqua, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
                joined != null -> Text(t("Listening with %s", joined!!), color = GGreen, style = MaterialTheme.typography.bodyLarge)
                else -> {
                    Text(t("Friends anywhere hear the same song at the same moment."), color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(t("Join") + ": [", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        CodeField(address, { address = it }, "________", cs.onSurface, Modifier.width(140.dp))
                        Text("] ⏎", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.clickable { if (address.count { it.isLetterOrDigit() } >= 8) m.party.join(address) })
                    }
                }
            }
            status?.let { Text(t(it), color = GRed, style = MaterialTheme.typography.bodySmall) }
        }
        val discord = app.nocturne.platformKind == app.nocturne.PlatformKind.DESKTOP
        val inDiscord by m.party.showInDiscord.collectAsState()
        val keys = buildList<Pair<String, Pair<String, () -> Unit>>> {
            if (!devices) when {
                hosting.isNotEmpty() -> {
                    add("y" to (t("copy") to { code?.let { copy(pretty(it)) }; Unit }))
                    if (discord) add("d" to (("discord" + (if (inDiscord) " ✓" else "")) to { m.party.setShowInDiscord(!inDiscord) }))
                    add("q" to (t("stop") to { m.party.stopHosting() }))
                }
                joined != null -> add("q" to (t("leave") to { m.party.leave() }))
                else -> add("s" to (t("start") to { m.party.host() }))
            }
            add("tab" to ((if (devices) t("together") else t("devices")) to { devices = !devices }))
            add("c" to (t("car mode") to { onDismiss(); onCarMode() }))
            add("esc" to (t("close") to onDismiss))
        }
        ArchKeys(*keys.toTypedArray())
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ArchAudio(onDismiss: () -> Unit) {
    val graph = AppGraph.instance
    val cs = MaterialTheme.colorScheme
    val settings by graph.settings.state.collectAsState()
    val gains = settings.eqGains.takeIf { it.size == Eq.bands.size } ?: List(Eq.bands.size) { 0f }
    var band by remember { mutableStateOf(0) }
    val names = presetNames(settings.customEqPresets)
    ArchFrame(t("mixer"), 560.dp) {
        Row {
            Text("card: ", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            Text(presetLabel(settings.eqPreset), color = GOrange, style = MaterialTheme.typography.bodyMedium)
            Text("   speed: ", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            Text("${settings.playbackSpeed}x", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.clickable { graph.settings.update { it.copy(playbackSpeed = if (it.playbackSpeed >= 2f) 0.5f else speedOf(speedFrac(it.playbackSpeed) + 1f / 30)) } })
            Text("   xfade: ", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            Text("${settings.crossfadeSec}s", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.clickable { graph.settings.update { it.copy(crossfadeSec = (it.crossfadeSec + 1) % 13) } })
            Spacer(Modifier.weight(1f))
            Text(if (settings.eqEnabled) "[on]" else "[off]", color = if (settings.eqEnabled) GGreen else cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.clickable { graph.settings.update { it.copy(eqEnabled = !it.eqEnabled) } })
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceAround) {
            Eq.bands.forEachIndexed { i, f ->
                val sel = i == band
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${if (gains[i] > 0) "+" else ""}${gains[i].roundToInt()}", color = if (sel) GYellow else cs.onSurface, style = MaterialTheme.typography.bodyMedium)
                    Canvas(
                        Modifier.padding(vertical = 4.dp).size(44.dp, 200.dp).border(1.dp, if (sel) GYellow else cs.onSurfaceVariant)
                            .vDrag { band = i; setGain(i, db(it)) },
                    ) {
                        val rows = 16
                        val cell = (size.height - 4.dp.toPx()) / rows
                        val lit = (frac(gains[i]) * rows).roundToInt()
                        for (r in 0 until rows) {
                            val c = if (r < lit) (if (r >= rows * 0.8f) GRed else if (r >= rows * 0.62f) GYellow else GGreen) else GBg1
                            drawRect(c, Offset(3.dp.toPx(), size.height - 2.dp.toPx() - (r + 1) * cell + 1f), Size(size.width - 6.dp.toPx(), cell - 2f))
                        }
                    }
                    Text(bandLabel(f), color = if (sel) GBg else cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.background(if (sel) GYellow else Color.Transparent).padding(horizontal = 4.dp))
                }
            }
        }
        val at = names.indexOf(settings.eqPreset).coerceAtLeast(0)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹ ", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.clickable { selectPreset(names[(at - 1 + names.size) % names.size]) })
            androidx.compose.foundation.lazy.LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(names.size) { k ->
                    val n = names[k]
                    val on = n == settings.eqPreset
                    Text(if (on) "[${presetLabel(n)}]" else presetLabel(n), color = if (on) GOrange else cs.onSurface, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.clickable { selectPreset(n) })
                }
            }
            Text(" ›", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.clickable { selectPreset(names[(at + 1) % names.size]) })
        }
        ArchKeys(
            "←" to (t("band") to { band = (band - 1 + Eq.bands.size) % Eq.bands.size }),
            "→" to (t("band") to { band = (band + 1) % Eq.bands.size }),
            "↑" to ("+1 dB" to { setGain(band, gains[band] + 1f) }),
            "↓" to ("-1 dB" to { setGain(band, gains[band] - 1f) }),
            "0" to (t("flat") to { selectPreset("Default") }),
            "esc" to (t("close") to onDismiss),
        )
    }
}

// ---------- Terminal ----------

@Composable
private fun TermWindow(title: String, width: Dp, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.width(width).shadow(30.dp, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp)).background(TBase).border(1.dp, TSurface, RoundedCornerShape(12.dp))) {
        Row(Modifier.fillMaxWidth().background(TMantle).padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(Color(0xFFFF5F57), Color(0xFFFEBC2E), Color(0xFF28C840)).forEach { Box(Modifier.padding(end = 7.dp).size(11.dp).clip(CircleShape).background(it)) }
            Text(title, color = TMuted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 6.dp))
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

@Composable
private fun TBox(title: String, color: Color, content: @Composable ColumnScope.() -> Unit) {
    app.nocturne.ui.TermBox(title, color, Modifier.fillMaxWidth()) { Column(verticalArrangement = Arrangement.spacedBy(4.dp), content = content) }
}

@Composable
private fun TerminalConnect(m: ConnectModel, onDismiss: () -> Unit, onCarMode: () -> Unit) {
    val hosting by m.party.hosting.collectAsState()
    val joined by m.party.joined.collectAsState()
    val code by m.party.code.collectAsState()
    val status by m.party.status.collectAsState()
    val members by m.party.members.collectAsState()
    val (copied, copy) = rememberCopy()
    TermWindow("nocturne · connect", 480.dp) {
        TBox("¹" + t("devices"), TSky) {
            DeviceRows(m) { name, on, sub, click ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(if (on) TSurface else Color.Transparent).clickable(onClick = click).padding(horizontal = 8.dp, vertical = 5.dp)) {
                    Text(if (sub == null) "↻ " else if (on) "● " else "○ ", color = if (on) TGreen else TMuted, style = MaterialTheme.typography.bodyMedium)
                    Text(name, color = if (on) TPink else Color(0xFFCDD6F4), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    sub?.let { Text(it, color = TMuted, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
        TBox("²" + t("together"), TYellow) {
            when {
                hosting.isNotEmpty() -> {
                    Text(code?.let(::pretty) ?: "…", color = TGreen, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = 5.sp, modifier = Modifier.clickable { code?.let { copy(pretty(it)) } })
                    Text(if (copied) t("Code copied") else listOf("${members.size} " + t("listeners")).plus(members.map { it.name }).joinToString(" · "), color = TMuted, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    TermKeys("y" to (t("copy") to { code?.let { copy(pretty(it)) }; Unit }), "q" to (t("stop") to { m.party.stopHosting() }))
                }
                joined != null -> {
                    Text(t("Listening with %s", joined!!), color = TGreen, style = MaterialTheme.typography.bodyMedium)
                    TermKeys("q" to (t("leave") to { m.party.leave() }))
                }
                else -> {
                    var address by remember { mutableStateOf("") }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("❯ join ", color = TMauve, style = MaterialTheme.typography.bodyMedium)
                        CodeField(address, { address = it }, "ABCD-1234", Color(0xFFCDD6F4), Modifier.weight(1f))
                    }
                    TermKeys("⏎" to (t("join") to { if (address.count { it.isLetterOrDigit() } >= 8) m.party.join(address) }), "s" to (t("start session") to { m.party.host() }))
                }
            }
            status?.let { Text(t(it), color = TPink, style = MaterialTheme.typography.bodySmall) }
        }
        TermKeys("c" to (t("car mode") to { onDismiss(); onCarMode() }), "esc" to (t("close") to onDismiss))
    }
}

@Composable
private fun TermKeys(vararg keys: Pair<String, Pair<String, () -> Unit>>) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        keys.forEach { (k, v) -> Text("[$k] ${v.first}", color = TMuted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.clickable(onClick = v.second)) }
    }
}

@Composable
private fun TerminalAudio(onDismiss: () -> Unit) {
    val graph = AppGraph.instance
    val settings by graph.settings.state.collectAsState()
    val gains = settings.eqGains.takeIf { it.size == Eq.bands.size } ?: List(Eq.bands.size) { 0f }
    var query by remember { mutableStateOf("") }
    val names = presetNames(settings.customEqPresets).filter { query.isBlank() || presetLabel(it).contains(query, ignoreCase = true) }
    Column(Modifier.width(580.dp).shadow(30.dp, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp)).background(TBase).border(1.dp, TSurface, RoundedCornerShape(12.dp))) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("❯ ", color = TMauve, style = MaterialTheme.typography.bodyMedium)
            CodeField(query, { query = it }, "eq", Color(0xFFCDD6F4), Modifier.weight(1f))
            Text(
                if (settings.eqEnabled) "on" else "off", color = if (settings.eqEnabled) TGreen else TMuted, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.clickable { graph.settings.update { it.copy(eqEnabled = !it.eqEnabled) } }.padding(horizontal = 10.dp),
            )
            Text("${names.size}/${presetNames(settings.customEqPresets).size}", color = TMuted, style = MaterialTheme.typography.bodyMedium)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(TSurface))
        Row {
            Column(Modifier.width(190.dp).heightIn(max = 300.dp).verticalScroll(rememberScrollState()).padding(8.dp)) {
                names.forEach { n ->
                    val on = n == settings.eqPreset
                    Text(
                        presetLabel(n), color = if (on) TPink else Color(0xFFCDD6F4), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(if (on) TSurface else Color.Transparent).clickable { selectPreset(n) }.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
            Box(Modifier.width(1.dp).height(300.dp).background(TSurface))
            Column(Modifier.weight(1f).padding(14.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Eq.bands.forEachIndexed { i, f ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${if (gains[i] > 0) "+" else ""}${gains[i].roundToInt()}", color = TMuted, style = MaterialTheme.typography.labelSmall)
                            Canvas(Modifier.padding(vertical = 4.dp).fillMaxWidth().height(170.dp).vDrag { setGain(i, db(it)) }) {
                                val n = 18
                                val cell = size.height / n
                                val lit = (frac(gains[i]) * n).roundToInt()
                                for (r in 0 until n) {
                                    val c = if (r < lit) (if (r > n * 0.7f) TPink else if (r > n * 0.45f) TMauve else TBlue) else TSurface
                                    drawRect(c, Offset(0f, size.height - (r + 1) * cell + 1f), Size(size.width, cell - 3f))
                                }
                            }
                            Text(bandLabel(f), color = TMuted, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                TermMeter(t("speed"), speedFrac(settings.playbackSpeed), "${settings.playbackSpeed}x", TPeach) { f -> graph.settings.update { it.copy(playbackSpeed = speedOf(f)) } }
                TermMeter(t("xfade"), settings.crossfadeSec / 12f, "${settings.crossfadeSec}s", TBlue) { f -> graph.settings.update { it.copy(crossfadeSec = (f * 12).roundToInt()) } }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(TSurface))
        Row(Modifier.padding(horizontal = 16.dp, vertical = 9.dp)) {
            TermKeys("0" to (t("flat") to { selectPreset("Default") }), "esc" to (t("close") to onDismiss))
        }
    }
}

@Composable
private fun TermMeter(label: String, value: Float, text: String, color: Color, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color(0xFFCDD6F4), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(64.dp))
        BlockMeter(value, onChange, color, TSurface, Modifier.weight(1f), cells = 22)
        Text(text, color = Color(0xFFCDD6F4), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(56.dp).padding(start = 8.dp))
    }
}
