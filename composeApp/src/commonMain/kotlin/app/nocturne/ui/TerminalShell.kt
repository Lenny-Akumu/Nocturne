package app.nocturne.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nocturne.AppGraph
import app.nocturne.i18n.t
import app.nocturne.nowMillis
import app.nocturne.source.Lyrics
import app.nocturne.ui.components.Artwork
import kotlin.math.sin

private val Green = Color(0xFFA6E3A1)
private val Teal = Color(0xFF94E2D5)
private val Blue = Color(0xFF89B4FA)
private val MauveC = Color(0xFFCBA6F7)
private val PinkC = Color(0xFFF5C2E7)
private val PeachC = Color(0xFFFAB387)

/** Terminal design (desktop, wide): a Ghostty-style hero with a cava visualizer over btop-style panes. */
@Composable
internal fun TerminalShell(top: Dp, onExpand: () -> Unit) {
    val nav = AppGraph.instance.navigator
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
    val compact = maxWidth < 1000.dp
    Column(Modifier.fillMaxSize().padding(start = 22.dp, end = 22.dp, top = top + 16.dp, bottom = 12.dp)) {
        Hero(onExpand, if (compact) 130.dp else 190.dp)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            val title = (sideTabs + settingsTab).firstOrNull { it.screen == nav.currentTab }?.label ?: "Nocturne"
            TermBox("¹ " + t(title).lowercase(), PeachC, Modifier.weight(1.7f).fillMaxHeight(), pad = false, header = { TabKeys() }) {
                ScreenHost(PaddingValues(top = 4.dp, bottom = 10.dp))
            }
            TermBox("² " + t("queue"), PinkC, Modifier.weight(1f).fillMaxHeight()) { Queue() }
            if (!compact) Column(Modifier.weight(0.9f).fillMaxHeight()) {
                TermBox("³ " + t("lyrics"), MauveC, Modifier.weight(1f).fillMaxWidth()) { LyricLines() }
                TermBox("⁴ " + t("session"), Teal, Modifier.fillMaxWidth()) { Session() }
            }
        }
        Text(
            buildAnnotatedString {
                listOf("space" to "pause", "⇧←→" to "next/prev", "←→" to "seek", "↑↓" to "vol", "l" to "like", "/" to "search", "esc" to "back").forEach { (k, v) ->
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append("[$k] ") }
                    append(t(v) + "   ")
                }
            },
            style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp),
        )
    }
    }
}

@Composable
private fun TabKeys() {
    val nav = AppGraph.instance.navigator
    val cs = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        (sideTabs + settingsTab).forEach { tab ->
            val on = nav.currentTab == tab.screen
            Text(
                t(tab.label).lowercase(), style = MaterialTheme.typography.labelLarge,
                color = if (on) cs.primary else cs.onSurfaceVariant,
                modifier = Modifier.clickable { nav.selectTab(tab.screen) }.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
internal fun TermBox(
    title: String,
    color: Color,
    modifier: Modifier,
    pad: Boolean = true,
    header: (@Composable () -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Box(modifier.padding(top = 11.dp)) {
        Box(Modifier.matchParentSize().border(1.dp, cs.outline, RoundedCornerShape(10.dp)))
        Box(Modifier.fillMaxWidth().padding(if (pad) PaddingValues(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 10.dp) else PaddingValues(start = 2.dp, end = 2.dp, top = 14.dp, bottom = 2.dp)), content = content)
        Row(Modifier.fillMaxWidth().offset(y = (-11).dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = color, modifier = Modifier.background(cs.background).padding(horizontal = 6.dp))
            if (header != null) {
                Spacer(Modifier.weight(1f))
                Box(Modifier.background(cs.background).padding(horizontal = 6.dp)) { header() }
            }
        }
    }
}

@Composable
private fun Hero(onExpand: () -> Unit, art: Dp) {
    val graph = AppGraph.instance
    val p = graph.player
    val cs = MaterialTheme.colorScheme
    val player by p.state.collectAsState()
    val track = player.current
    Row(Modifier.fillMaxWidth().height(art), verticalAlignment = Alignment.Bottom) {
        Box(Modifier.size(art).clickable(enabled = track != null, onClick = onExpand)) {
            Artwork(track?.artworkUrl, Modifier.fillMaxSize(), RoundedCornerShape(10.dp))
        }
        Spacer(Modifier.width(26.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "● " + if (track == null) t("idle") else listOfNotNull(t("now playing"), player.queueTitle?.lowercase(), "${player.index + 1}/${player.queue.size}").joinToString(" · "),
                color = Green, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(track?.title ?: "nocturne", color = cs.primary, style = MaterialTheme.typography.displaySmall.copy(fontSize = if (art < 150.dp) 24.sp else 34.sp, fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track?.let { listOfNotNull(it.artistNames, it.album).joinToString(" — ") }.orEmpty(), color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Cava(player.isPlaying, Modifier.fillMaxWidth().height(if (art < 150.dp) 24.dp else 64.dp).padding(top = 6.dp))
            if (player.isBuffering) Text("● " + t("loading…"), color = PeachC, style = MaterialTheme.typography.bodyMedium)
            BlockBar(player.positionMs, player.durationMs, player.isPlaying, onToggle = { p.toggle() }) { p.seekTo(it) }
        }
    }
}

/** Segmented spectrum bars: real levels when the engine has them, a gentle synthetic motion otherwise. */
@Composable
internal fun Cava(playing: Boolean, modifier: Modifier) {
    val p = AppGraph.instance.player
    val bands = 70
    var heights by remember { mutableStateOf(FloatArray(bands) { 0.1f }) }
    LaunchedEffect(playing) {
        val smooth = FloatArray(bands) { 0.1f }
        while (true) {
            withFrameMillis { ms ->
                val lv = if (playing) p.levels() else null
                for (i in 0 until bands) {
                    val target = when {
                        !playing -> 0.06f
                        lv != null && lv.isNotEmpty() -> lv[(i * lv.size / bands).coerceIn(0, lv.lastIndex)].coerceIn(0f, 1f)
                        else -> (0.35f + 0.3f * sin(ms / 260.0 + i * 0.7).toFloat() + 0.25f * sin(ms / 170.0 + i * 1.9).toFloat()).coerceIn(0.05f, 1f)
                    }
                    smooth[i] += (target - smooth[i]) * 0.25f
                }
                heights = smooth.copyOf()
            }
        }
    }
    val grad = Brush.verticalGradient(listOf(PinkC, MauveC, Blue, Teal))
    Canvas(modifier) {
        val step = size.width / bands
        val w = step * 0.7f
        val seg = 4.dp.toPx()
        val gap = 1.5.dp.toPx()
        heights.forEachIndexed { i, h ->
            val bh = (size.height * h).coerceAtLeast(seg)
            var y = size.height
            while (y > size.height - bh) {
                drawRect(grad, Offset(i * step, y - seg), Size(w, seg))
                y -= seg + gap
            }
        }
    }
}

/** ████░░░ progress line with time stamps; click or drag to seek. */
@Composable
internal fun BlockBar(pos: Long, dur: Long, playing: Boolean, onToggle: () -> Unit, onSeek: (Long) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var drag by remember { mutableStateOf<Float?>(null) }
    val frac = (drag ?: if (dur > 0) pos.toFloat() / dur else 0f).coerceIn(0f, 1f)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Text(if (playing) "⏸ " else "▶ ", color = PinkC, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.clickable(onClick = onToggle))
        Text(clockOf(pos) + " ", style = MaterialTheme.typography.bodyMedium)
        Canvas(
            Modifier.weight(1f).height(14.dp)
                .pointerInput(dur) { detectTapGestures { o -> if (dur > 0) onSeek((o.x / size.width * dur).toLong()) } }
                .pointerInput(dur) {
                    detectHorizontalDragGestures(
                        onDragStart = { drag = it.x / size.width },
                        onDragEnd = { drag?.let { f -> if (dur > 0) onSeek((f.coerceIn(0f, 1f) * dur).toLong()) }; drag = null },
                        onDragCancel = { drag = null },
                    ) { c, _ -> drag = c.position.x / size.width }
                },
        ) {
            val cell = 9.dp.toPx()
            val n = (size.width / cell).toInt().coerceAtLeast(1)
            val filled = (n * frac).toInt()
            for (i in 0 until n) {
                val c = if (i < filled) MauveC else cs.outline.copy(alpha = 0.55f)
                val h = if (i < filled) size.height else size.height * 0.5f
                drawRect(c, Offset(i * cell, (size.height - h) / 2), Size(cell - 1.5.dp.toPx(), h))
            }
        }
        Text(" " + clockOf(dur), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Queue() {
    val p = AppGraph.instance.player
    val cs = MaterialTheme.colorScheme
    val player by p.state.collectAsState()
    val list = rememberLazyListState()
    LaunchedEffect(player.index) { if (player.index > 2) list.scrollToItem(player.index - 2) }
    LazyColumn(state = list) {
        items(player.queue.size) { i ->
            val t = player.queue[i]
            val on = i == player.index
            Row(Modifier.fillMaxWidth().clickable { p.skipTo(i) }.padding(vertical = 1.dp)) {
                Text((if (on) "▸ " else "  ") + (i + 1).toString().padStart(2, '0') + "  ", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                Text(t.title, color = if (on) PeachC else cs.onSurface, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(" " + t.artistNames, color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(0.8f))
                Text(" " + clockOf(t.durationMs), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun LyricLines() {
    val graph = AppGraph.instance
    val cs = MaterialTheme.colorScheme
    val player by graph.player.state.collectAsState()
    val track = player.current
    var lyrics by remember(track?.key) { mutableStateOf<Lyrics?>(null) }
    LaunchedEffect(track?.key) { lyrics = track?.let { runCatching { graph.lyrics.lyrics(it) }.getOrNull() } }
    val synced = lyrics?.synced?.takeIf { it.isNotEmpty() }
    Column {
        if (synced != null) {
            val at = synced.indexOfLast { it.timeMs <= player.positionMs }.coerceAtLeast(0)
            (maxOf(0, at - 2)..minOf(synced.lastIndex, at + 8)).forEach { i ->
                Text(
                    synced[i].text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (i == at) FontWeight.Bold else FontWeight.Normal),
                    color = if (i == at) PinkC else cs.onSurfaceVariant,
                )
            }
        } else (lyrics?.plain?.lines()?.take(12) ?: listOf(if (track == null) "" else t("No lyrics"))).forEach {
            Text(it, color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Session() {
    val graph = AppGraph.instance
    val cs = MaterialTheme.colorScheme
    val library by graph.library.state.collectAsState()
    val settings by graph.settings.state.collectAsState()
    val now = nowMillis()
    val today = library.plays.filter { now - it.t < 86_400_000L }
    // plays per hour over the last day, oldest first
    val hours = IntArray(24).also { h -> today.forEach { e -> h[(23 - ((now - e.t) / 3_600_000L).toInt()).coerceIn(0, 23)]++ } }
    val peak = (hours.maxOrNull() ?: 0).coerceAtLeast(1)
    Column {
        Canvas(Modifier.fillMaxWidth().height(30.dp)) {
            val step = size.width / 24
            hours.forEachIndexed { i, c ->
                val h = size.height * (c.toFloat() / peak).coerceAtLeast(0.06f)
                drawRect(Teal, Offset(i * step, size.height - h), Size(step * 0.85f, h))
            }
        }
        Spacer(Modifier.height(8.dp))
        val minutes = today.sumOf { it.d } / 60_000
        val top = today.mapNotNull { it.a.split(",").firstOrNull()?.trim()?.takeIf { a -> a.isNotEmpty() } }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        listOf(
            t("listened") to "${minutes / 60}h ${minutes % 60}m",
            t("tracks") to today.size.toString(),
            t("top") to (top ?: "—"),
        ).forEach { (k, v) ->
            Row { Text(k.padEnd(9), color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium); Text(v, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t("vol").padEnd(9), color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            app.nocturne.ui.player.BlockMeter(settings.volume, { graph.player.setVolume(it) }, Green, cs.outline.copy(alpha = 0.5f), Modifier.weight(1f), cells = 12)
            Text(" ${(settings.volume * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun clockOf(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "${(s / 60).toString().padStart(2, '0')}:${(s % 60).toString().padStart(2, '0')}"
}
