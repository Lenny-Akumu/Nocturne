package app.nocturne.ui.player

import androidx.compose.ui.draw.clip
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import app.nocturne.ui.kit.Icon
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.rotate
import app.nocturne.player.AudioFx
import androidx.compose.foundation.clickable
import app.nocturne.i18n.t
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import app.nocturne.ui.kit.FilterChip
import androidx.compose.material3.MaterialTheme
import app.nocturne.ui.kit.ModalBottomSheet
import app.nocturne.ui.kit.OutlinedButton
import app.nocturne.ui.kit.Slider
import app.nocturne.ui.kit.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.nocturne.AppGraph
import app.nocturne.player.Eq
import app.nocturne.player.TrackVersion
import kotlin.math.abs
import kotlin.math.roundToInt

private fun bandLabel(hz: Float) = if (hz >= 1000) "${(hz / 100).roundToInt() / 10.0}".removeSuffix(".0") + " kHz" else "${hz.roundToInt()} Hz"

@Composable
fun AudioSheet(onDismiss: () -> Unit) {
    val graph = AppGraph.instance
    val settings by graph.settings.state.collectAsState()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val fx = graph.player.audioFx

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text(t("Versions"), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TrackVersion.entries.forEach { v ->
                    val selected = abs(settings.playbackSpeed - v.speed) < 0.01f
                    OutlinedButton(
                        onClick = { graph.settings.update { it.copy(playbackSpeed = v.speed) } },
                        border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface)
                        else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) { Text(v.label) }
                }
            }
            var speed by remember(settings.playbackSpeed) { mutableFloatStateOf(settings.playbackSpeed) }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                Text(t("Speed"), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text("${(speed * 100).roundToInt() / 100.0}×", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = speed,
                onValueChange = { speed = (it * 20).roundToInt() / 20f },
                onValueChangeFinished = { graph.settings.update { it.copy(playbackSpeed = speed) } },
                valueRange = 0.5f..2f,
                steps = 29,
            )

            Spacer(Modifier.height(20.dp))
            var fxOpen by rememberSaveable { mutableStateOf(false) }
            val chevron by animateFloatAsState(if (fxOpen) 180f else 0f)
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { fxOpen = !fxOpen }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(t("Effects"), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.ExpandMore, null, Modifier.rotate(chevron), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AnimatedVisibility(fxOpen, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column {
                    Spacer(Modifier.height(4.dp))
                    if (AudioFx.NORMALIZE in fx) FxSwitch("Normalize volume", "Even out loud and quiet tracks", settings.normalizeVolume) { v -> graph.settings.update { it.copy(normalizeVolume = v) } }
                    if (AudioFx.SPATIAL in fx) FxSwitch("Spatial sound", "Wider, more open stereo", settings.spatialAudio) { v -> graph.settings.update { it.copy(spatialAudio = v) } }
                    if (AudioFx.EIGHT_D in fx) FxSwitch("8D audio", "Sound slowly circles around your head — best with headphones", settings.eightD) { v -> graph.settings.update { it.copy(eightD = v) } }
                    // instrumental lookup works everywhere; the DSP fallback only where the engine has it
                    FxSwitch(
                        "Vocal remover",
                        if (AudioFx.VOCAL_REMOVER in fx) "Karaoke: plays the instrumental version, or cuts the centered vocals" else "Karaoke: plays the instrumental version when there is one",
                        settings.vocalRemover,
                    ) { v -> graph.settings.update { it.copy(vocalRemover = v) } }
                    var fade by remember(settings.crossfadeSec) { mutableFloatStateOf(settings.crossfadeSec.toFloat()) }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        Text(t("Crossfade"), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(if (fade < 1f) t("Off") else t("%s s", fade.roundToInt()), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = fade,
                        onValueChange = { fade = it.roundToInt().toFloat() },
                        onValueChangeFinished = { graph.settings.update { it.copy(crossfadeSec = fade.roundToInt()) } },
                        valueRange = 0f..12f,
                        steps = 11,
                    )
                }
            }

            if (AudioFx.EQUALIZER in fx) {
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t("Equalizer"), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    Switch(checked = settings.eqEnabled, onCheckedChange = { v -> graph.settings.update { it.copy(eqEnabled = v) } })
                }
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 8.dp)) {
                    val names = listOf("Custom") + Eq.presets.keys
                    items(names) { name ->
                        FilterChip(
                            selected = settings.eqPreset == name,
                            onClick = {
                                val gains = Eq.presets[name]
                                graph.settings.update {
                                    if (gains == null) it.copy(eqPreset = name, eqEnabled = true)
                                    else it.copy(eqPreset = name, eqGains = gains, eqEnabled = true)
                                }
                            },
                            label = { Text(if (name == "Custom") t("Custom") else name) },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                EqGraph(
                    gains = settings.eqGains.takeIf { it.size == Eq.bands.size } ?: List(Eq.bands.size) { 0f },
                    modifier = Modifier.fillMaxWidth().height(200.dp).alpha(if (settings.eqEnabled) 1f else 0.45f),
                    onPreview = { gains -> graph.player.previewEqualizer(gains) },
                ) { gains -> graph.settings.update { it.copy(eqGains = gains, eqPreset = "Custom", eqEnabled = true) } }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Eq.bands.forEach { hz ->
                        Text(bandLabel(hz), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun EqGraph(gains: List<Float>, modifier: Modifier, onPreview: (List<Float>) -> Unit, onChange: (List<Float>) -> Unit) {
    val color = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    // Local values are only read while drawing, so a drag redraws the canvas without recomposing the sheet.
    val local = remember { mutableStateListOf<Float>().apply { addAll(gains) } }
    var dragIndex by remember { mutableIntStateOf(-1) }
    val preview by rememberUpdatedState(onPreview)
    val commit by rememberUpdatedState(onChange)
    LaunchedEffect(gains) {
        if (dragIndex < 0 && local.toList() != gains) { local.clear(); local.addAll(gains) }
    }

    fun columnX(i: Int, width: Float) = width * (i + 0.5f) / local.size
    fun gainFromY(y: Float, height: Float) = ((0.5f - y / height) * 2 * Eq.MAX_DB).coerceIn(-Eq.MAX_DB, Eq.MAX_DB).let { (it * 10).roundToInt() / 10f }

    Canvas(
        modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                if (local.isEmpty()) return@awaitEachGesture
                val i = ((down.position.x / size.width) * local.size).toInt().coerceIn(0, local.lastIndex)
                var sent = 0L
                fun set(y: Float) {
                    local[i] = gainFromY(y, size.height.toFloat())
                    val now = app.nocturne.nowMillis()
                    if (now - sent >= 30) { sent = now; preview(local.toList()) } // engine at most every ~30 ms
                }
                dragIndex = i
                down.consume()
                set(down.position.y)
                drag(down.id) { change -> change.consume(); set(change.position.y) }
                dragIndex = -1
                commit(local.toList()) // persist once, on release
            }
        },
    ) {
        if (local.isEmpty()) return@Canvas
        val h = size.height
        val points = local.mapIndexed { i, g -> Offset(columnX(i, size.width), h * (0.5f - g / (2 * Eq.MAX_DB))) }
        for (p in points) drawLine(grid, Offset(p.x, 0f), Offset(p.x, h), strokeWidth = 2f)
        drawLine(grid.copy(alpha = 0.5f), Offset(0f, h / 2), Offset(size.width, h / 2), strokeWidth = 1.5f)
        val line = Path().apply {
            moveTo(0f, points.first().y)
            lineTo(points.first().x, points.first().y)
            for (i in 1 until points.size) {
                val a = points[i - 1]
                val b = points[i]
                val mx = (a.x + b.x) / 2
                cubicTo(mx, a.y, mx, b.y, b.x, b.y)
            }
            lineTo(size.width, points.last().y)
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(size.width, h)
            lineTo(0f, h)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0.02f))))
        drawPath(line, color, style = Stroke(width = 5f, cap = StrokeCap.Round))
        points.forEachIndexed { i, p -> drawCircle(color, if (i == dragIndex) 14f else 11f, p) }
    }
}

@Composable
private fun FxSwitch(title: String, summary: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { onChange(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(t(title), style = MaterialTheme.typography.bodyLarge)
            Text(t(summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
