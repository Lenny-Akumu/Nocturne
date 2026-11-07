package app.nocturne.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Airplay
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.ThumbDownOffAlt
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nocturne.AppGraph
import app.nocturne.data.DesignStyle
import app.nocturne.i18n.t
import app.nocturne.model.Source
import app.nocturne.model.Track
import app.nocturne.nowMillis
import app.nocturne.player.RepeatMode
import app.nocturne.source.Lyrics
import app.nocturne.ui.Cava
import app.nocturne.ui.GlassPane
import app.nocturne.ui.SeekLine
import app.nocturne.ui.TermBox
import app.nocturne.ui.TuiBox
import app.nocturne.ui.Wave
import app.nocturne.ui.components.Artwork
import app.nocturne.ui.components.LocalTrackMenu
import app.nocturne.ui.components.MenuExtra
import app.nocturne.ui.components.TrackMenuContext
import app.nocturne.ui.kit.Icon
import coil3.compose.AsyncImage

private val TPink = Color(0xFFF5C2E7)
private val TMauve = Color(0xFFCBA6F7)
private val TBlue = Color(0xFF89B4FA)
private val TSky = Color(0xFF89DCEB)
private val TGreen = Color(0xFFA6E3A1)
private val TPeach = Color(0xFFFAB387)
private val TRed = Color(0xFFF38BA8)
private val TYellow = Color(0xFFF9E2AF)
private val GYellow = Color(0xFFFABD2F)
private val GAqua = Color(0xFF8EC07C)
private val GGreen = Color(0xFFB8BB26)
private val GOrange = Color(0xFFFE8019)
private val GRed = Color(0xFFFB4934)
private val LPink = Color(0xFFFF5C7A)
private val LViolet = Color(0xFFB36BFF)

internal fun DesignStyle.hasOwnPlayer() = this == DesignStyle.LINUX || this == DesignStyle.ARCH || this == DesignStyle.TERMINAL

/** Backdrop for the styled players (drawn under the player content). */
@Composable
internal fun StyledPlayerBackground(style: DesignStyle, artwork: String?) {
    val cs = MaterialTheme.colorScheme
    when (style) {
        DesignStyle.LINUX -> {
            app.nocturne.ui.LinuxWallpaper(Modifier.fillMaxSize())
            AsyncImage(artwork, null, Modifier.fillMaxSize().blur(90.dp), contentScale = ContentScale.Crop, alpha = 0.45f)
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
        }
        else -> Box(Modifier.fillMaxSize().background(cs.background))
    }
}

@Composable
internal fun StyledPlayer(style: DesignStyle, onCollapse: () -> Unit) {
    when (style) {
        DesignStyle.LINUX -> LinuxPlayer(onCollapse)
        DesignStyle.ARCH -> ArchPlayer(onCollapse)
        else -> TerminalPlayer(onCollapse)
    }
}

// ---------- shared bits ----------

/** Tap, drag or scroll anywhere on the element to set a 0..1 value. */
private fun Modifier.dragValue(value: () -> Float, onChange: (Float) -> Unit): Modifier = this
    .pointerInput(Unit) { detectTapGestures { o -> onChange((o.x / size.width).coerceIn(0f, 1f)) } }
    .pointerInput(Unit) { detectHorizontalDragGestures { c, _ -> onChange((c.position.x / size.width).coerceIn(0f, 1f)) } }
    .pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val e = awaitPointerEvent()
                if (e.type != PointerEventType.Scroll) continue
                val dy = e.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                onChange((value() - dy * 0.05f).coerceIn(0f, 1f))
                e.changes.forEach { it.consume() }
            }
        }
    }

private fun Modifier.scrollVolume(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val e = awaitPointerEvent()
            if (e.type != PointerEventType.Scroll) continue
            val dy = e.changes.firstOrNull()?.scrollDelta?.y ?: 0f
            setVolume((AppGraph.instance.settings.value.volume - dy * 0.05f).coerceIn(0f, 1f))
            e.changes.forEach { it.consume() }
        }
    }
}

/** ■■■■□□ cells you can click, drag or scroll. */
@Composable
internal fun BlockMeter(value: Float, onChange: (Float) -> Unit, on: Color, off: Color, modifier: Modifier = Modifier, cells: Int = 20) {
    Canvas(modifier.height(14.dp).dragValue({ value }, onChange)) {
        val cell = size.width / cells
        val filled = (value.coerceIn(0f, 1f) * cells + 0.001f).toInt()
        for (i in 0 until cells) drawRect(if (i < filled) on else off, Offset(i * cell, 1.dp.toPx()), Size(cell - 2.dp.toPx(), size.height - 2.dp.toPx()))
    }
}

@Composable
private fun LineMeter(value: Float, onChange: (Float) -> Unit, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.height(22.dp).dragValue({ value }, onChange)) {
        val y = size.height / 2
        val h = 5.dp.toPx()
        val x = size.width * value.coerceIn(0f, 1f)
        drawRoundRect(Color.White.copy(alpha = 0.18f), Offset(0f, y - h / 2), Size(size.width, h), CornerRadius(h / 2))
        drawRoundRect(color, Offset(0f, y - h / 2), Size(x, h), CornerRadius(h / 2))
        drawCircle(Color.White, 7.dp.toPx(), Offset(x, y))
    }
}

private fun setVolume(v: Float) = AppGraph.instance.player.setVolume(v)

private fun clock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

private fun canComment(track: Track) = track.source == Source.YOUTUBE_MUSIC || track.source == Source.SOUNDCLOUD

/** Same ⋮ menu as the regular player: track actions plus sleep timer and visualizer. */
@Composable
private fun rememberMore(track: Track): () -> Unit {
    val menu = LocalTrackMenu.current
    val overlays = LocalOverlays.current
    return {
        val s = AppGraph.instance.player.state.value
        val sleep = s.sleepAtMs?.let { t("Sleep timer · %s min", ((it - nowMillis()) / 60_000).coerceAtLeast(0) + 1) }
            ?: if (s.sleepAfterTrack) t("Sleep timer") + " · " + t("End of track") else t("Sleep timer")
        menu(track, TrackMenuContext(extras = listOf(
            MenuExtra(Icons.Rounded.Bedtime, sleep) { overlays.sleep = true },
            MenuExtra(Icons.Rounded.BlurOn, t("Visualizer")) { overlays.visualizer = true },
        )))
    }
}

private class LyricState(val lines: List<String>, val translated: List<String>?, val current: Int, val synced: Boolean)

@Composable
private fun rememberLyricState(track: Track, pos: Long): LyricState? {
    val graph = AppGraph.instance
    val settings by graph.settings.state.collectAsState()
    var lyrics by remember(track.key) { mutableStateOf<Lyrics?>(null) }
    LaunchedEffect(track.key) { lyrics = runCatching { graph.lyrics.lyrics(track) }.getOrNull() }
    val target = app.nocturne.i18n.I18n.lang
    var translated by remember(track.key) { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(lyrics, settings.lyricsTranslate, target) {
        translated = null
        val l = lyrics ?: return@LaunchedEffect
        if (!settings.lyricsTranslate) return@LaunchedEffect
        val src = l.synced?.takeIf { it.isNotEmpty() }?.map { it.text } ?: l.plain?.lines() ?: return@LaunchedEffect
        translated = runCatching { graph.translator.lines(src, target) }.getOrNull()
    }
    val l = lyrics ?: return null
    val synced = l.synced?.takeIf { it.isNotEmpty() }
    return when {
        synced != null -> LyricState(synced.map { it.text }, translated, synced.indexOfLast { it.timeMs <= pos + 300 }.coerceAtLeast(0), true)
        !l.plain.isNullOrBlank() -> LyricState(l.plain.lines(), translated, -1, false)
        else -> null
    }
}

/** Lines around the current one, each with its translation under it when that's on. */
@Composable
private fun LyricList(track: Track, pos: Long, now: Color, idle: Color, sub: Color, marker: String, modifier: Modifier = Modifier) {
    val st = rememberLyricState(track, pos)
    val list = rememberLazyListState()
    if (st == null) {
        if (wantsLiveText(track)) LiveTranscriptView(track, modifier)
        else Text(t("No lyrics"), color = idle, style = MaterialTheme.typography.bodyMedium, modifier = modifier)
        return
    }
    LaunchedEffect(st.current) { if (st.current > 1) list.animateScrollToItem(st.current - 1) }
    LazyColumn(modifier, state = list) {
        items(st.lines.size) { i ->
            val on = i == st.current
            Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Text(
                    (if (on) marker else " ".repeat(marker.length)) + st.lines[i],
                    color = if (on) now else idle, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (on) FontWeight.Bold else FontWeight.Normal),
                )
                st.translated?.getOrNull(i)?.takeIf { it.isNotBlank() && it != st.lines[i] }?.let {
                    Text(" ".repeat(marker.length) + it, color = sub, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

// ---------- Linux: glass split ----------

private enum class LTab { QUEUE, LYRICS }

@Composable
private fun LinuxPlayer(onCollapse: () -> Unit) {
    val graph = AppGraph.instance
    val p = graph.player
    val state by p.state.collectAsState()
    val track = state.current ?: return
    val overlays = LocalOverlays.current
    var tab by remember { mutableStateOf(LTab.QUEUE) }
    var artOnly by remember { mutableStateOf(true) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > 900.dp && maxWidth > maxHeight
        Column(Modifier.fillMaxSize().padding(horizontal = if (wide) 26.dp else 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            LinuxTop(track, onCollapse)
            val tabs: @Composable (Modifier) -> Unit = { m ->
                GlassPane(m) {
                    Column(Modifier.fillMaxSize()) {
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.06f)).padding(4.dp)) {
                            LinuxTabChip(t("Queue"), tab == LTab.QUEUE && !(!wide && artOnly)) { tab = LTab.QUEUE; artOnly = false }
                            LinuxTabChip(t("Lyrics"), tab == LTab.LYRICS && !(!wide && artOnly)) { tab = LTab.LYRICS; artOnly = false }
                            if (canComment(track)) LinuxTabChip(t("Comments"), false) { overlays.comments = true }
                        }
                        Spacer(Modifier.height(10.dp))
                        Box(Modifier.weight(1f).fillMaxWidth()) { if (tab == LTab.QUEUE) QueueView() else LyricsView() }
                    }
                }
            }
            if (wide) {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    GlassPane(Modifier.weight(1.1f).fillMaxHeight(), pad = false) { LinuxArt(track) }
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        tabs(Modifier.weight(1f).fillMaxWidth())
                        GlassPane(Modifier.fillMaxWidth()) { LinuxControls(track) }
                    }
                }
            } else {
                if (artOnly) GlassPane(Modifier.weight(1f).fillMaxWidth(), pad = false) { LinuxArt(track) { artOnly = false } }
                else tabs(Modifier.weight(1f).fillMaxWidth())
                if (!artOnly) Text(
                    "← " + t("Artwork"), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { artOnly = true }.padding(6.dp),
                )
                GlassPane(Modifier.fillMaxWidth()) { LinuxControls(track) }
            }
        }
    }
}

@Composable
private fun LinuxTop(track: Track, onCollapse: () -> Unit) {
    val graph = AppGraph.instance
    val state by graph.player.state.collectAsState()
    val overlays = LocalOverlays.current
    val more = rememberMore(track)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.KeyboardArrowDown, t("Collapse"), Modifier.clip(CircleShape).clickable(onClick = onCollapse).padding(6.dp).size(26.dp), tint = Color.White)
        Text(
            t("PLAYING FROM").lowercase() + "  " + (state.queueTitle ?: track.album ?: track.source.label),
            color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 6.dp),
        )
        Icon(Icons.Rounded.Devices, t("Devices"), Modifier.clip(CircleShape).clickable { overlays.connect = true }.padding(8.dp).size(22.dp), tint = Color.White.copy(alpha = 0.85f))
        Icon(Icons.Rounded.MoreVert, t("More"), Modifier.clip(CircleShape).clickable(onClick = more).padding(8.dp).size(22.dp), tint = Color.White.copy(alpha = 0.85f))
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.LinuxTabChip(label: String, on: Boolean, onClick: () -> Unit) {
    Text(
        label, maxLines = 1, color = if (on) Color(0xFF1A0710) else Color.White.copy(alpha = 0.75f),
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (on) FontWeight.Bold else FontWeight.Medium),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (on) LPink else Color.Transparent).clickable(onClick = onClick).padding(vertical = 8.dp),
    )
}

@Composable
private fun LinuxArt(track: Track, onTap: () -> Unit = {}) {
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp))) {
        AsyncImage(track.artworkUrl, null, Modifier.fillMaxSize().blur(60.dp), contentScale = ContentScale.Crop, alpha = 0.5f)
        BoxWithConstraints(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            val side = minOf(maxWidth, maxHeight - 90.dp).coerceAtLeast(120.dp)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(side).clip(RoundedCornerShape(22.dp)).clickable(onClick = onTap)) {
                    Artwork(track.artworkUrl, Modifier.fillMaxSize(), RoundedCornerShape(22.dp))
                }
                Spacer(Modifier.height(18.dp))
                Text(track.title, color = Color.White, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artistNames + " · " + track.source.label, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun LinuxControls(track: Track) {
    val graph = AppGraph.instance
    val p = graph.player
    val state by p.state.collectAsState()
    val settings by graph.settings.state.collectAsState()
    val library by graph.library.state.collectAsState()
    val overlays = LocalOverlays.current
    var volume by remember { mutableStateOf(false) }
    LaunchedEffect(volume, settings.volume) { if (volume) { kotlinx.coroutines.delay(3500); volume = false } }
    Column {
        Wave(track.key, state.positionMs, state.durationMs) { p.seekTo(it) }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(clock(state.positionMs), color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelMedium)
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                if (volume) {
                    Icon(Icons.AutoMirrored.Rounded.VolumeUp, null, Modifier.size(20.dp), tint = Color.White)
                    LineMeter(settings.volume, ::setVolume, LPink, Modifier.width(220.dp).padding(horizontal = 10.dp))
                    Text("${(settings.volume * 100).toInt()}", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium, modifier = Modifier.clickable { volume = false })
                } else {
                    val liked = library.liked.any { it.key == track.key }
                    SmallIcon(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, t("Like"), if (liked) LPink else Color.White) { graph.toggleLike(track) }
                    if (canComment(track)) SmallIcon(Icons.Rounded.ChatBubbleOutline, t("Comments"), Color.White) { overlays.comments = true }
                    SmallIcon(Icons.Rounded.ThumbDownOffAlt, t("Not interested"), Color.White) { graph.notInterested(track) }
                    SmallIcon(Icons.Rounded.GraphicEq, t("Equalizer and speed"), if (settings.eqEnabled) LPink else Color.White) { overlays.audio = true }
                    SmallIcon(if (settings.volume <= 0.01f) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp, t("Volume"), Color.White, Modifier.scrollVolume()) { volume = true }
                }
            }
            Text(clock(state.durationMs), color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelMedium)
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            ShuffleButton(state, { p.toggleShuffle() })
            Spacer(Modifier.width(26.dp))
            SmallIcon(Icons.Rounded.SkipPrevious, t("Previous"), Color.White, size = 32.dp) { p.previous() }
            Spacer(Modifier.width(26.dp))
            Box(
                Modifier.size(66.dp).clip(CircleShape).background(Brush.linearGradient(listOf(LPink, LViolet))).clickable { p.toggle() },
                contentAlignment = Alignment.Center,
            ) {
                if (state.isBuffering) androidx.compose.material3.CircularProgressIndicator(Modifier.size(30.dp), color = Color.White, strokeWidth = 3.dp)
                else Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, t("Play"), Modifier.size(34.dp), tint = Color.White)
            }
            Spacer(Modifier.width(26.dp))
            SmallIcon(Icons.Rounded.SkipNext, t("Next"), Color.White, size = 32.dp) { p.next() }
            Spacer(Modifier.width(26.dp))
            SmallIcon(if (state.repeat == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, t("Repeat"), if (state.repeat != RepeatMode.OFF) LPink else Color.White.copy(alpha = 0.7f)) { p.cycleRepeat() }
        }
    }
}

@Composable
private fun SmallIcon(icon: ImageVector, desc: String, tint: Color, modifier: Modifier = Modifier, size: Dp = 22.dp, onClick: () -> Unit) {
    Icon(icon, desc, modifier.clip(CircleShape).clickable(onClick = onClick).padding(8.dp).size(size), tint = tint)
}

// ---------- Arch: ncmpcpp-style ----------

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ArchPlayer(onCollapse: () -> Unit) {
    val graph = AppGraph.instance
    val p = graph.player
    val cs = MaterialTheme.colorScheme
    val state by p.state.collectAsState()
    val settings by graph.settings.state.collectAsState()
    val library by graph.library.state.collectAsState()
    val track = state.current ?: return
    val overlays = LocalOverlays.current
    val more = rememberMore(track)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > 900.dp
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⌄ ", color = cs.onSurface, style = MaterialTheme.typography.titleLarge, modifier = Modifier.clickable(onClick = onCollapse))
                Text("nocturne", color = GGreen, style = MaterialTheme.typography.bodyMedium)
                Text(" :: player  ", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                Text(if (state.isPlaying) "▶ playing" else "‖ paused", color = GOrange, style = MaterialTheme.typography.bodyMedium)
                if (wide) Text("   src ", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                if (wide) Text(track.source.label.lowercase(), color = GOrange, style = MaterialTheme.typography.bodyMedium)
                Text("   " + t("from") + " ", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                Text(state.queueTitle ?: track.album ?: "—", color = cs.secondary, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("[" + t("devices") + "]", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.clickable { overlays.connect = true }.padding(horizontal = 6.dp))
                Text("[⋮]", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.clickable(onClick = more).padding(horizontal = 6.dp))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (wide) Column(Modifier.weight(0.42f).fillMaxHeight()) {
                    TuiBox("[1] ${t("cover")}") { Artwork(track.artworkUrl, Modifier.fillMaxWidth().aspectRatio(1f), RectangleShape) }
                    TuiBox("[4] ${t("lyrics")}", Modifier.weight(1f).fillMaxWidth()) {
                        LyricList(track, state.positionMs, GYellow, cs.onSurface.copy(alpha = 0.6f), cs.onSurfaceVariant, "» ", Modifier.fillMaxSize())
                    }
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    TuiBox("[2] ${t("now playing")}") {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!wide) { Artwork(track.artworkUrl, Modifier.size(84.dp), RectangleShape); Spacer(Modifier.width(12.dp)) }
                                Column(Modifier.weight(1f)) {
                                    Text(track.title, color = GYellow, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(listOfNotNull(track.artistNames, track.album, track.source.label).joinToString(" · "), color = GAqua, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(clock(state.positionMs) + " ", style = MaterialTheme.typography.bodyMedium)
                                SeekLine(state.positionMs, state.durationMs, Modifier.weight(1f)) { p.seekTo(it) }
                                Text(" " + clock(state.durationMs), style = MaterialTheme.typography.bodyMedium)
                            }
                            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                ArchKey("⏮") { p.previous() }
                                ArchKey(if (state.isBuffering) "⋯" else if (state.isPlaying) "⏸" else "▶", GGreen) { p.toggle() }
                                ArchKey("⏭") { p.next() }
                                Spacer(Modifier.width(14.dp))
                                Text("vol ", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                                BlockMeter(settings.volume, ::setVolume, GYellow, cs.outline.copy(alpha = 0.5f), Modifier.width(150.dp), cells = 15)
                                Text(" ${(settings.volume * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                            }
                            val liked = library.liked.any { it.key == track.key }
                            androidx.compose.foundation.layout.FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                ArchToggle("z", if (state.smartShuffle) "✦ " + t("smart shuffle") else t("shuffle"), state.shuffle) { p.toggleShuffle() }
                                ArchToggle("r", t("repeat") + when (state.repeat) { RepeatMode.ONE -> ":one"; RepeatMode.OFF -> ":off"; else -> ":all" }, state.repeat != RepeatMode.OFF) { p.cycleRepeat() }
                                ArchToggle("♥", if (liked) t("liked") else t("like"), liked, GRed) { graph.toggleLike(track) }
                                ArchToggle("e", t("eq") + if (settings.playbackSpeed != 1f) " ${settings.playbackSpeed}x" else "", settings.eqEnabled) { overlays.audio = true }
                                if (canComment(track)) ArchToggle("c", t("comments"), false) { overlays.comments = true }
                                ArchToggle("-", t("not interested"), false) { graph.notInterested(track) }
                            }
                        }
                    }
                    TuiBox("[3] ${t("queue")} — ${state.queue.size}", Modifier.weight(1f).fillMaxWidth()) {
                        val list = rememberLazyListState()
                        LaunchedEffect(Unit) { if (state.index > 2) list.scrollToItem(state.index - 2) }
                        LazyColumn(state = list) {
                            items(state.queue.size) { i ->
                                val q = state.queue[i]
                                val on = i == state.index
                                Row(Modifier.fillMaxWidth().background(if (on) cs.surfaceContainerHigh else Color.Transparent).clickable { p.skipTo(i) }.padding(vertical = 1.dp)) {
                                    Text((if (on) "▶ " else "  ") + (i + 1).toString().padStart(2, '0') + "  ", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                                    Text(q.title, color = if (on) GYellow else cs.onSurface, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1.4f))
                                    Text("  " + q.artistNames, color = GAqua, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Text("  " + clock(q.durationMs), color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
            androidx.compose.foundation.layout.FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf<Triple<String, String, () -> Unit>>(
                    Triple("space", t("pause"), { p.toggle() }),
                    Triple("n", t("next"), { p.next() }),
                    Triple("p", t("prev"), { p.previous() }),
                    Triple("l", t("like"), { graph.toggleLike(track) }),
                    Triple("↑↓", t("vol"), { setVolume((settings.volume + 0.1f).coerceAtMost(1f)) }),
                    Triple("esc", t("close"), onCollapse),
                ).forEach { (k, v, a) ->
                    Row(Modifier.clickable(onClick = a), verticalAlignment = Alignment.CenterVertically) {
                        Text(" $k ", color = cs.background, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.background(GYellow))
                        Text(" $v", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun ArchKey(label: String, color: Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) {
    Text(label, color = color, style = MaterialTheme.typography.titleLarge, modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp))
}

@Composable
private fun ArchToggle(key: String, label: String, on: Boolean, color: Color = GGreen, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.clickable(onClick = onClick)) {
        Text("[", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(key, color = if (on) color else cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text("] ", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(label, color = if (on) cs.onSurface else cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

// ---------- Terminal: btop-style ----------

@Composable
private fun TerminalPlayer(onCollapse: () -> Unit) {
    val graph = AppGraph.instance
    val p = graph.player
    val cs = MaterialTheme.colorScheme
    val state by p.state.collectAsState()
    val settings by graph.settings.state.collectAsState()
    val library by graph.library.state.collectAsState()
    val track = state.current ?: return
    val overlays = LocalOverlays.current
    val more = rememberMore(track)
    var showLyrics by remember { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > 900.dp
        val art = if (wide) 190.dp else 110.dp
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⌄ ", color = cs.onSurface, style = MaterialTheme.typography.titleLarge, modifier = Modifier.clickable(onClick = onCollapse))
                Text("lenny@nocturne", color = TSky, style = MaterialTheme.typography.bodyMedium)
                Text(" ~ ❯ play ", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                Text(state.queueTitle ?: track.album ?: "", color = TMauve, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("[" + t("devices") + "]", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.clickable { overlays.connect = true }.padding(horizontal = 6.dp))
                Text("[⋮]", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.clickable(onClick = more).padding(horizontal = 6.dp))
            }
            TermBox("¹" + t("now"), TMauve, Modifier.fillMaxWidth()) {
                Row {
                    Artwork(track.artworkUrl, Modifier.size(art), RoundedCornerShape(6.dp))
                    Spacer(Modifier.width(20.dp))
                    Column(Modifier.weight(1f).height(art)) {
                        Text(track.title, color = TPink, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = if (wide) 30.sp else 20.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(track.artistNames, track.source.label.lowercase()).joinToString(" · "), color = TBlue, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.weight(1f))
                        app.nocturne.ui.BlockBar(state.positionMs, state.durationMs, state.isPlaying, onToggle = { p.toggle() }) { p.seekTo(it) }
                        val liked = library.liked.any { it.key == track.key }
                        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            ShuffleButton(state, { p.toggleShuffle() })
                            SmallIcon(Icons.Rounded.SkipPrevious, t("Previous"), cs.onSurface) { p.previous() }
                            if (state.isBuffering) androidx.compose.material3.CircularProgressIndicator(Modifier.padding(8.dp).size(22.dp), color = TGreen, strokeWidth = 2.dp)
                            else SmallIcon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, t("Play"), TGreen) { p.toggle() }
                            SmallIcon(Icons.Rounded.SkipNext, t("Next"), cs.onSurface) { p.next() }
                            SmallIcon(if (state.repeat == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, t("Repeat"), if (state.repeat != RepeatMode.OFF) TGreen else cs.onSurfaceVariant) { p.cycleRepeat() }
                            Spacer(Modifier.width(10.dp))
                            SmallIcon(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, t("Like"), if (liked) TRed else cs.onSurfaceVariant) { graph.toggleLike(track) }
                            if (canComment(track)) SmallIcon(Icons.Rounded.ChatBubbleOutline, t("Comments"), cs.onSurfaceVariant) { overlays.comments = true }
                            if (wide) SmallIcon(Icons.Rounded.ThumbDownOffAlt, t("Not interested"), cs.onSurfaceVariant) { graph.notInterested(track) }
                            SmallIcon(Icons.Rounded.GraphicEq, t("Equalizer and speed"), if (settings.eqEnabled) TGreen else cs.onSurfaceVariant) { overlays.audio = true }
                        }
                    }
                }
            }
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TermBox("²cava", TPink, Modifier.weight(1.25f).fillMaxHeight()) { Cava(state.isPlaying, Modifier.fillMaxSize()) }
                TermBox(
                    "³" + t("stream"), TSky, Modifier.weight(1f).fillMaxHeight(),
                    header = { Text("[e] " + t("equalizer"), color = cs.onSurfaceVariant, style = MaterialTheme.typography.labelLarge, modifier = Modifier.clickable { overlays.audio = true }) },
                ) { TermStream() }
            }
            if (wide) Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TermBox("⁴" + t("queue"), TMauve, Modifier.weight(1.25f).fillMaxHeight()) { TermQueue() }
                TermBox("⁵" + t("lyrics"), TYellow, Modifier.weight(1f).fillMaxHeight()) {
                    LyricList(track, state.positionMs, TPink, cs.onSurfaceVariant, cs.outline, "› ", Modifier.fillMaxSize())
                }
            } else {
                TermBox(
                    if (showLyrics) "⁵" + t("lyrics") else "⁴" + t("queue"), if (showLyrics) TYellow else TMauve, Modifier.weight(1.4f).fillMaxWidth(),
                    header = { Text(if (showLyrics) "[q] " + t("queue") else "[l] " + t("lyrics"), color = cs.onSurfaceVariant, style = MaterialTheme.typography.labelLarge, modifier = Modifier.clickable { showLyrics = !showLyrics }) },
                ) {
                    if (showLyrics) LyricList(track, state.positionMs, TPink, cs.onSurfaceVariant, cs.outline, "› ", Modifier.fillMaxSize()) else TermQueue()
                }
            }
        }
    }
}

@Composable
private fun TermStream() {
    val graph = AppGraph.instance
    val p = graph.player
    val cs = MaterialTheme.colorScheme
    val state by p.state.collectAsState()
    val settings by graph.settings.state.collectAsState()
    val overlays = LocalOverlays.current
    // loudness history from the engine's levels; flat when the engine can't report them
    var history by remember { mutableStateOf(List(60) { 0f }) }
    LaunchedEffect(state.isPlaying) {
        while (true) {
            kotlinx.coroutines.delay(120)
            val lv = if (state.isPlaying) p.levels() else null
            val v = lv?.takeIf { it.isNotEmpty() }?.let { (it.maxOrNull() ?: 0f) * 0.6f + it.average().toFloat() * 0.4f } ?: 0f
            history = (history + v.coerceIn(0f, 1f)).takeLast(60)
        }
    }
    val off = cs.outline.copy(alpha = 0.45f)
    Column {
        MeterRow(t("volume"), "${(settings.volume * 100).toInt()}%") { BlockMeter(settings.volume, ::setVolume, TGreen, off, it, cells = 24) }
        MeterRow(t("speed"), "${settings.playbackSpeed}x", onClick = { overlays.audio = true }) { BlockMeter(((settings.playbackSpeed - 0.5f) / 1.5f).coerceIn(0f, 1f), { overlays.audio = true }, TPeach, off, it, cells = 24) }
        val frac = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f
        MeterRow(t("track"), "${(frac * 100).toInt()}%") { BlockMeter(frac, { f -> if (state.durationMs > 0) p.seekTo((f * state.durationMs).toLong()) }, TMauve, off, it, cells = 24) }
        val q = if (state.queue.size > 1) state.index.toFloat() / (state.queue.size - 1) else 1f
        MeterRow(t("queue"), "${state.index + 1}/${state.queue.size}") { BlockMeter(q, { f -> p.skipTo((f * (state.queue.size - 1)).toInt()) }, TBlue, off, it, cells = 24) }
        Text(
            listOfNotNull(state.current?.source?.label?.lowercase(), if (settings.eqEnabled) "eq on" else "eq off", if (settings.crossfadeSec > 0) "crossfade ${settings.crossfadeSec}s" else null).joinToString(" · "),
            color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp).clickable { overlays.audio = true },
        )
        Canvas(Modifier.fillMaxWidth().weight(1f, fill = false).height(70.dp).padding(top = 8.dp)) {
            val step = size.width / (history.size - 1)
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, size.height)
                history.forEachIndexed { i, v -> lineTo(i * step, size.height - v * size.height * 0.9f) }
                lineTo(size.width, size.height)
                close()
            }
            drawPath(path, TSky.copy(alpha = 0.2f))
            for (i in 1 until history.size) drawLine(TSky, Offset((i - 1) * step, size.height - history[i - 1] * size.height * 0.9f), Offset(i * step, size.height - history[i] * size.height * 0.9f), 2.dp.toPx())
        }
    }
}

@Composable
private fun MeterRow(label: String, value: String, onClick: (() -> Unit)? = null, meter: @Composable (Modifier) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = cs.onSurface, style = MaterialTheme.typography.bodyMedium, maxLines = 1, modifier = Modifier.width(80.dp))
        meter(Modifier.weight(1f))
        Text(value, color = cs.onSurface, style = MaterialTheme.typography.bodyMedium, maxLines = 1, modifier = Modifier.width(64.dp).padding(start = 10.dp))
    }
}

@Composable
private fun TermQueue() {
    val p = AppGraph.instance.player
    val cs = MaterialTheme.colorScheme
    val state by p.state.collectAsState()
    val list = rememberLazyListState()
    LaunchedEffect(Unit) { if (state.index > 1) list.scrollToItem(state.index - 1) }
    LazyColumn(state = list) {
        items(state.queue.size) { i ->
            val q = state.queue[i]
            val on = i == state.index
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(if (on) cs.surfaceContainerHigh else Color.Transparent).clickable { p.skipTo(i) }.padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(q.artworkUrl, Modifier.size(30.dp), RoundedCornerShape(4.dp))
                Spacer(Modifier.width(12.dp))
                Text(q.title, color = if (on) TPink else cs.onSurface, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(clock(q.durationMs), color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
