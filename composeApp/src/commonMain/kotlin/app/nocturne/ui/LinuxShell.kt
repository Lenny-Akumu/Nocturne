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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nocturne.AppGraph
import app.nocturne.i18n.t
import app.nocturne.player.RepeatMode
import app.nocturne.source.Lyrics
import app.nocturne.ui.components.Artwork
import app.nocturne.ui.kit.Icon

private val Pink = Color(0xFFEE66FF)
private val Cyan = Color(0xFF33CCFF)
private val Peach = Color(0xFFFF8A65)
private val Glow = Brush.linearGradient(listOf(Cyan, Pink, Peach))
private val PaneShape = RoundedCornerShape(14.dp)
private val Mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp)

/** Linux design (desktop, wide): Hyprland-style glass panes over a gradient wallpaper, editorial now-playing column. */
@Composable
internal fun LinuxShell(top: Dp, onExpand: () -> Unit) {
    val graph = AppGraph.instance
    val nav = graph.navigator
    val cs = MaterialTheme.colorScheme
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        // tablets: the now-playing column narrows, then folds into a player bar
        val compact = maxWidth < 980.dp
        val nowWidth = if (maxWidth < 1200.dp) 300.dp else 360.dp
        Row(Modifier.fillMaxSize().padding(start = 14.dp, end = 14.dp, top = top + 14.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    GlassPane(Modifier.width(if (compact) 190.dp else 220.dp).fillMaxHeight()) { NavPane() }
                    GlassPane(Modifier.weight(1f).fillMaxHeight(), focused = true, pad = false) {
                        ScreenHost(PaddingValues(top = 8.dp, bottom = 12.dp))
                    }
                }
                if (compact) LinuxBar(onExpand, showTabs = false) else UpNext()
            }
            if (!compact) GlassPane(Modifier.width(nowWidth).fillMaxHeight()) { NowColumn(onExpand) }
        }
    }
}

/** Hyprland-style wallpaper behind the Linux design (any width). */
@Composable
internal fun LinuxWallpaper(modifier: Modifier) {
    Box(modifier.drawBehind {
        drawRect(Color(0xFF0D0B14))
        drawRect(Brush.radialGradient(listOf(Color(0xFF5B2A86), Color.Transparent), Offset(size.width * 0.15f, size.height * 0.15f), size.minDimension * 0.6f))
        drawRect(Brush.radialGradient(listOf(Color(0xFF1F6F8B), Color.Transparent), Offset(size.width * 0.85f, size.height * 0.8f), size.minDimension * 0.65f))
        drawRect(Brush.radialGradient(listOf(Color(0xAAC2417B), Color.Transparent), Offset(size.width * 0.7f, 0f), size.minDimension * 0.45f))
    })
}

@Composable
internal fun GlassPane(modifier: Modifier, focused: Boolean = false, pad: Boolean = true, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier.clip(PaneShape).background(Color(0x9412101E))
            .then(if (focused) Modifier.border(2.dp, Glow, PaneShape) else Modifier.border(1.dp, Color.White.copy(alpha = 0.12f), PaneShape))
            .padding(if (pad) 18.dp else 2.dp),
        content = content,
    )
}

@Composable
private fun NavPane() {
    val graph = AppGraph.instance
    val nav = graph.navigator
    val cs = MaterialTheme.colorScheme
    val library by graph.library.state.collectAsState()
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NocturneLogo(Modifier.size(24.dp).clip(RoundedCornerShape(7.dp)).clickable { LogoTaps.tap() })
            Spacer(Modifier.width(8.dp))
            Text("nocturne", style = Mono.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold, brush = Brush.linearGradient(listOf(Cyan, Pink))))
        }
        Spacer(Modifier.height(18.dp))
        (sideTabs + settingsTab).forEach { tab ->
            val on = nav.currentTab == tab.screen
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(if (on) Color.White.copy(alpha = 0.09f) else Color.Transparent)
                    .clickable { nav.selectTab(tab.screen) }.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(t(tab.label), color = if (on) Color.White else cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                if (tab.screen == Screen.Search) Text("/", style = Mono, color = Color(0xFF5F5B78))
            }
        }
        Text(t("Playlists").uppercase(), style = Mono.copy(fontSize = 11.sp, letterSpacing = 1.5.sp), color = Color(0xFF7D7997), modifier = Modifier.padding(start = 12.dp, top = 20.dp, bottom = 8.dp))
        LazyColumn {
            items(library.playlists, key = { it.id }) { pl ->
                Text(
                    pl.name, color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).clickable { nav.push(Screen.Collection(pl.ref)) }.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun NowColumn(onExpand: () -> Unit) {
    val graph = AppGraph.instance
    val p = graph.player
    val cs = MaterialTheme.colorScheme
    val player by p.state.collectAsState()
    val library by graph.library.state.collectAsState()
    val track = player.current
    Column {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)).clickable(enabled = track != null, onClick = onExpand)) {
            Artwork(track?.artworkUrl, Modifier.fillMaxSize(), RoundedCornerShape(12.dp))
        }
        Spacer(Modifier.height(16.dp))
        if (track == null) {
            Text(t("Nothing playing"), color = cs.onSurfaceVariant)
            return@Column
        }
        Text(
            "● " + t("NOW PLAYING") + (player.queueTitle?.let { " · " + it.uppercase() } ?: "") + " · ${player.index + 1}/${player.queue.size}",
            style = Mono.copy(fontSize = 11.sp), color = Color(0xFF9ECE6A), maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Text(track.title, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, brush = Brush.linearGradient(listOf(Color.White, Pink, Peach))), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(listOfNotNull(track.artistNames, track.album).joinToString(" — "), style = Mono.copy(fontSize = 13.sp), color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(14.dp))
        Wave(track.key, player.positionMs, player.durationMs) { p.seekTo(it) }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(clock(player.positionMs), style = Mono, color = cs.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(clock(player.durationMs), style = Mono, color = cs.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            app.nocturne.ui.player.ShuffleButton(player, { p.toggleShuffle() })
            Icon(Icons.Rounded.SkipPrevious, t("Previous"), Modifier.size(28.dp).clickable { p.previous() }, tint = cs.onSurface)
            Box(
                Modifier.size(58.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Cyan, Pink))).clickable { p.toggle() },
                contentAlignment = Alignment.Center,
            ) { Icon(if (player.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, t("Play"), Modifier.size(30.dp), tint = Color.White) }
            Icon(Icons.Rounded.SkipNext, t("Next"), Modifier.size(28.dp).clickable { p.next() }, tint = cs.onSurface)
            Icon(if (player.repeat == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, t("Repeat"), Modifier.size(22.dp).clickable { p.cycleRepeat() }, tint = if (player.repeat != RepeatMode.OFF) Cyan else cs.onSurfaceVariant)
            val liked = library.liked.any { it.key == track.key }
            Icon(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, t("Like"), Modifier.size(22.dp).clickable { graph.toggleLike(track) }, tint = if (liked) Pink else cs.onSurfaceVariant)
        }
        Spacer(Modifier.height(18.dp))
        LyricsLines(track.key, player.positionMs)
    }
}

/** Seek bar drawn as a waveform (stable per track): played part in the gradient. */
@Composable
internal fun Wave(key: String, pos: Long, dur: Long, onSeek: (Long) -> Unit) {
    val bars = remember(key) { val r = kotlin.random.Random(key.hashCode()); List(64) { 0.18f + r.nextFloat() * 0.82f } }
    var drag by remember { mutableStateOf<Float?>(null) }
    val frac = (drag ?: if (dur > 0) pos.toFloat() / dur else 0f).coerceIn(0f, 1f)
    Canvas(
        Modifier.fillMaxWidth().height(64.dp)
            .pointerInput(dur) { detectTapGestures { o -> if (dur > 0) onSeek((o.x / size.width * dur).toLong()) } }
            .pointerInput(dur) {
                detectHorizontalDragGestures(
                    onDragStart = { drag = it.x / size.width },
                    onDragEnd = { drag?.let { f -> if (dur > 0) onSeek((f.coerceIn(0f, 1f) * dur).toLong()) }; drag = null },
                    onDragCancel = { drag = null },
                ) { c, _ -> drag = c.position.x / size.width }
            },
    ) {
        val step = size.width / bars.size
        val w = step * 0.55f
        val played = Brush.verticalGradient(listOf(Pink, Cyan))
        bars.forEachIndexed { i, h ->
            val bh = size.height * h
            val x = i * step
            val tl = Offset(x, (size.height - bh) / 2)
            if ((i + 0.5f) / bars.size <= frac) drawRoundRect(played, tl, Size(w, bh), CornerRadius(w / 2))
            else drawRoundRect(Color.White.copy(alpha = 0.13f), tl, Size(w, bh), CornerRadius(w / 2))
        }
    }
}

@Composable
private fun LyricsLines(key: String, pos: Long) {
    val graph = AppGraph.instance
    val track = graph.player.state.value.current ?: return
    var lyrics by remember(key) { mutableStateOf<Lyrics?>(null) }
    LaunchedEffect(key) { lyrics = runCatching { graph.lyrics.lyrics(track) }.getOrNull() }
    val synced = lyrics?.synced?.takeIf { it.isNotEmpty() }
    val style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
    Column {
        if (synced != null) {
            val at = synced.indexOfLast { it.timeMs <= pos }.coerceAtLeast(0)
            (maxOf(0, at - 2)..minOf(synced.lastIndex, at + 4)).forEach { i ->
                Text(synced[i].text, style = style, color = if (i == at) Color.White else Color(0xFF6F6B88), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(vertical = 2.dp))
            }
        } else lyrics?.plain?.lines()?.take(7)?.forEach {
            Text(it, style = style, color = Color(0xFF6F6B88), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(vertical = 2.dp))
        }
    }
}

@Composable
private fun UpNext() {
    val p = AppGraph.instance.player
    val player by p.state.collectAsState()
    val next = (player.index + 1..minOf(player.queue.lastIndex, player.index + 4)).map { it to player.queue[it] }
    if (next.isEmpty()) return
    val accents = listOf(Pink, Cyan, Color(0xFF9ECE6A), Peach)
    GlassPane(Modifier.fillMaxWidth()) {
        Column {
            Text(t("UP NEXT"), style = Mono.copy(fontSize = 11.sp, letterSpacing = 1.5.sp), color = Color(0xFF7D7997))
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                next.forEachIndexed { n, (i, t) ->
                    Row(
                        Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.05f))
                            .drawBehind { drawRect(accents[n % accents.size], size = Size(3.dp.toPx(), size.height)) }
                            .clickable { p.skipTo(i) }.padding(start = 12.dp, end = 10.dp, top = 9.dp, bottom = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Artwork(t.artworkUrl, Modifier.size(44.dp), RoundedCornerShape(8.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(t.artistNames, style = Mono, color = Color(0xFF8F8BA8), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                repeat(4 - next.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private fun clock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "${(s / 60).toString().padStart(2, '0')}:${(s % 60).toString().padStart(2, '0')}"
}
