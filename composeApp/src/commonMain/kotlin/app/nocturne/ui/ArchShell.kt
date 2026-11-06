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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RectangleShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nocturne.AppGraph
import app.nocturne.BuildInfo
import app.nocturne.i18n.t
import app.nocturne.player.RepeatMode
import app.nocturne.source.Lyrics
import app.nocturne.ui.components.Artwork

/** Arch design (desktop, wide): a terminal-style layout of titled boxes, keyboard-first. */
@Composable
internal fun ArchShell(top: Dp, onExpand: () -> Unit) {
    val graph = AppGraph.instance
    val nav = graph.navigator
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = top + 10.dp, bottom = 12.dp)) {
        ArchHeader()
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.width(230.dp).fillMaxHeight()) {
                TuiBox("[1] ${t("browse")}") {
                    Column {
                        (tabs + settingsTab).forEach { tab ->
                            val on = nav.currentTab == tab.screen
                            val key = when (tab.screen) { Screen.Search -> "/"; else -> "" }
                            TuiRow(t(tab.label), on, key) { nav.selectTab(tab.screen) }
                        }
                    }
                }
                val library by graph.library.state.collectAsState()
                TuiBox("[2] ${t("playlists")}", Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn {
                        items(library.playlists, key = { it.id }) { pl ->
                            TuiRow(pl.name, false) { nav.push(Screen.Collection(pl.ref)) }
                        }
                        item { TuiRow("+ " + t("new"), false, dim = true) { nav.selectTab(Screen.Library) } }
                    }
                }
            }
            val title = (tabs + settingsTab).firstOrNull { it.screen == nav.currentTab }?.label ?: "Nocturne"
            TuiBox("[3] ${t(title).lowercase()}", Modifier.weight(1f).fillMaxHeight(), focused = true, pad = false) {
                ScreenHost(PaddingValues(top = 4.dp, bottom = 12.dp))
            }
            Column(Modifier.width(320.dp).fillMaxHeight()) {
                ArchNow(onExpand)
                ArchLyrics(Modifier.weight(1f).fillMaxWidth())
            }
        }
        ArchPlayback()
    }
}

@Composable
private fun ArchHeader() {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)) {
        Text("▞▚ nocturne", style = MaterialTheme.typography.titleLarge, color = cs.primary)
        Spacer(Modifier.width(16.dp))
        Text("${BuildInfo.VERSION} · tui", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = Color(0xFFB8BB26))) { append("● youtube  ") }
                withStyle(SpanStyle(color = Color(0xFF8EC07C))) { append("● spotify  ") }
                withStyle(SpanStyle(color = cs.secondary)) { append("● soundcloud") }
            },
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** Box with its title cut into the top border, like a TUI pane. */
@Composable
private fun TuiBox(
    title: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    focused: Boolean = false,
    pad: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Box(modifier.padding(top = 10.dp)) {
        Box(Modifier.matchParentSize().border(1.dp, if (focused) cs.primary.copy(alpha = 0.7f) else cs.outline))
        Box(Modifier.fillMaxWidth().padding(if (pad) PaddingValues(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 10.dp) else PaddingValues(top = 12.dp, start = 1.dp, end = 1.dp, bottom = 1.dp)), content = content)
        Text(
            title, style = MaterialTheme.typography.labelLarge, color = cs.secondary,
            modifier = Modifier.offset(x = 12.dp, y = (-10).dp).background(cs.background).padding(horizontal = 6.dp),
        )
    }
}

@Composable
private fun TuiRow(label: String, selected: Boolean, key: String = "", dim: Boolean = false, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().background(if (selected) cs.surfaceContainerHigh else Color.Transparent).clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            (if (selected) "> " else "  ") + label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = when { selected -> cs.primary; dim -> cs.onSurfaceVariant; else -> cs.onSurface }, modifier = Modifier.weight(1f),
        )
        if (key.isNotEmpty()) Text(key, style = MaterialTheme.typography.bodyMedium, color = cs.onSurface)
    }
}

@Composable
private fun ArchNow(onExpand: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val player by AppGraph.instance.player.state.collectAsState()
    val track = player.current
    TuiBox("[4] ${t("now")}") {
        Column(Modifier.clickable(enabled = track != null, onClick = onExpand)) {
            Artwork(track?.artworkUrl, Modifier.fillMaxWidth().aspectRatio(1f), RectangleShape)
            Spacer(Modifier.height(10.dp))
            if (track == null) Text(t("Nothing playing"), color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            else {
                Text(track.title, color = cs.primary, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(track.artistNames, track.album).joinToString(" — "), color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("♪ " + track.source.label, color = cs.tertiary, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ArchLyrics(modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val graph = AppGraph.instance
    val player by graph.player.state.collectAsState()
    val track = player.current
    var lyrics by remember(track?.key) { mutableStateOf<Lyrics?>(null) }
    LaunchedEffect(track?.key) { lyrics = track?.let { runCatching { graph.lyrics.lyrics(it) }.getOrNull() } }
    TuiBox("[5] ${t("lyrics")}", modifier) {
        val synced = lyrics?.synced?.takeIf { it.isNotEmpty() }
        val lines: List<Pair<String, Boolean>> = when {
            synced != null -> {
                val at = synced.indexOfLast { it.timeMs <= player.positionMs }.coerceAtLeast(0)
                (maxOf(0, at - 3)..minOf(synced.lastIndex, at + 6)).map { synced[it].text to (it == at) }
            }
            !lyrics?.plain.isNullOrBlank() -> lyrics!!.plain!!.lines().take(14).map { it to false }
            else -> listOf((if (track == null) "" else t("No lyrics")) to false)
        }
        Column {
            lines.forEach { (text, now) ->
                Text(
                    if (now) "» $text «" else text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium, color = if (now) cs.primary else cs.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ArchPlayback() {
    val cs = MaterialTheme.colorScheme
    val graph = AppGraph.instance
    val p = graph.player
    val player by p.state.collectAsState()
    val settings by graph.settings.state.collectAsState()
    TuiBox("[${t("playback")}]") {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (player.isPlaying) "⏸" else "▶", color = Color(0xFFB8BB26), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.clickable { p.toggle() }.padding(end = 6.dp))
                Text("⏮", color = cs.onSurface, modifier = Modifier.clickable { p.previous() }.padding(horizontal = 4.dp))
                Text("⏭", color = cs.onSurface, modifier = Modifier.clickable { p.next() }.padding(horizontal = 4.dp))
                Text(" " + mmss(player.positionMs) + " ", style = MaterialTheme.typography.bodyMedium)
                SeekLine(player.positionMs, player.durationMs, Modifier.weight(1f)) { p.seekTo(it) }
                Text(" " + mmss(player.durationMs) + "   ", style = MaterialTheme.typography.bodyMedium)
                val filled = (settings.volume * 10).toInt().coerceIn(0, 10)
                Text(
                    "vol " + "▮".repeat(filled) + "▯".repeat(10 - filled), color = cs.tertiary, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures { o -> p.setVolume(((o.x / size.width) * 1.25f - 0.15f).coerceIn(0f, 1f)) }
                    },
                )
                Text("  ⤮", color = if (player.shuffle) cs.secondary else cs.onSurfaceVariant, modifier = Modifier.clickable { p.toggleShuffle() })
                Text(" " + if (player.repeat == RepeatMode.ONE) "⟳¹" else "⟳", color = if (player.repeat != RepeatMode.OFF) cs.secondary else cs.onSurfaceVariant, modifier = Modifier.clickable { p.cycleRepeat() })
            }
            Text(
                buildAnnotatedString {
                    listOf("space" to "play", "←→" to "seek", "⇧←→" to "next/prev", "↑↓" to "vol", "l" to "like", "s" to "shuffle", "r" to "repeat", "/" to "search", "esc" to "back").forEach { (k, v) ->
                        withStyle(SpanStyle(color = cs.onSurfaceVariant)) { append(k) }
                        append(" ")
                        withStyle(SpanStyle(color = cs.onSurface)) { append(t(v)) }
                        append("   ")
                    }
                },
                style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun SeekLine(pos: Long, dur: Long, modifier: Modifier, onSeek: (Long) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val frac = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
    var drag by remember { mutableStateOf<Float?>(null) }
    Canvas(
        modifier.height(16.dp)
            .pointerInput(dur) { detectTapGestures { o -> if (dur > 0) onSeek((o.x / size.width * dur).toLong()) } }
            .pointerInput(dur) {
                detectHorizontalDragGestures(
                    onDragStart = { drag = it.x / size.width },
                    onDragEnd = { drag?.let { f -> if (dur > 0) onSeek((f.coerceIn(0f, 1f) * dur).toLong()) }; drag = null },
                    onDragCancel = { drag = null },
                ) { change, _ -> drag = change.position.x / size.width }
            },
    ) {
        val f = (drag ?: frac).coerceIn(0f, 1f)
        val y = size.height / 2
        val x = size.width * f
        drawLine(cs.outline, Offset(x, y), Offset(size.width, y), 1.5.dp.toPx())
        drawLine(cs.primary, Offset(0f, y), Offset(x, y), 2.dp.toPx())
        drawCircle(cs.primary, 4.dp.toPx(), Offset(x, y))
    }
}

private fun mmss(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "${(s / 60).toString().padStart(2, '0')}:${(s % 60).toString().padStart(2, '0')}"
}
