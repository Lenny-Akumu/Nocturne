package app.nocturne.ui.player

import app.nocturne.i18n.t
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
                    ) { Text(t(v.label)) }
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
                        label = { Text(t(name)) },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            EqGraph(
                gains = settings.eqGains.takeIf { it.size == Eq.bands.size } ?: List(Eq.bands.size) { 0f },
                modifier = Modifier.fillMaxWidth().height(200.dp).alpha(if (settings.eqEnabled) 1f else 0.45f),
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

@Composable
private fun EqGraph(gains: List<Float>, modifier: Modifier, onChange: (List<Float>) -> Unit) {
    val color = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    var local by remember(gains) { androidx.compose.runtime.mutableStateOf(gains) }
    var dragIndex by remember { androidx.compose.runtime.mutableIntStateOf(-1) }

    fun columnX(i: Int, width: Float) = width * (i + 0.5f) / gains.size
    fun gainFromY(y: Float, height: Float) = ((0.5f - y / height) * 2 * Eq.MAX_DB).coerceIn(-Eq.MAX_DB, Eq.MAX_DB).let { (it * 2).roundToInt() / 2f }

    Canvas(
        modifier
            .pointerInput(Unit) {
                detectTapGestures { o ->
                    val i = ((o.x / size.width) * gains.size).toInt().coerceIn(0, gains.lastIndex)
                    local = local.toMutableList().also { it[i] = gainFromY(o.y, size.height.toFloat()) }
                    onChange(local)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { o -> dragIndex = ((o.x / size.width) * gains.size).toInt().coerceIn(0, gains.lastIndex) },
                    onDragEnd = { dragIndex = -1; onChange(local) },
                    onDragCancel = { dragIndex = -1 },
                ) { change, _ ->
                    if (dragIndex >= 0) local = local.toMutableList().also { it[dragIndex] = gainFromY(change.position.y, size.height.toFloat()) }
                }
            },
    ) {
        val h = size.height
        val points = local.mapIndexed { i, g -> Offset(columnX(i, size.width), h * (0.5f - g / (2 * Eq.MAX_DB))) }
        for (p in points) drawLine(grid, Offset(p.x, 0f), Offset(p.x, h), strokeWidth = 2f)
        drawLine(grid.copy(alpha = 0.5f), Offset(0f, h / 2), Offset(size.width, h / 2), strokeWidth = 1.5f)
        val line = Path().apply {
            moveTo(0f, points.first().y)
            points.forEach { lineTo(it.x, it.y) }
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
