package app.nocturne.ui.player

import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import app.nocturne.i18n.t
import androidx.compose.animation.AnimatedContent
import kotlinx.coroutines.delay
import androidx.compose.material.icons.rounded.Close
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material3.LocalContentColor
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.runtime.produceState
import androidx.compose.material.icons.rounded.ThumbDownOffAlt
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Airplay
import androidx.compose.material.icons.automirrored.rounded.VolumeMute
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import app.nocturne.ui.components.trackMenuOpen
import app.nocturne.data.DesignStyle
import app.nocturne.ui.kit.LocalDesignStyle
import app.nocturne.ui.kit.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import app.nocturne.ui.kit.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.nocturne.AppGraph
import app.nocturne.data.PlayerBackground
import app.nocturne.nowMillis
import app.nocturne.ui.components.Artwork
import app.nocturne.ui.components.LocalTrackMenu
import app.nocturne.ui.components.SourceBadge
import app.nocturne.ui.components.FlowingColors
import app.nocturne.ui.components.TrackMenuContext
import app.nocturne.ui.components.MenuExtra
import app.nocturne.ui.components.ArtistLinks
import app.nocturne.ui.theme.NocturneTheme
import app.nocturne.ui.theme.isDark
import app.nocturne.ui.theme.rememberArtworkSeed
import coil3.compose.AsyncImage
import kotlin.math.roundToInt

private enum class Pane { ARTWORK, LYRICS, QUEUE }

@Composable
fun FullPlayer(onCollapse: () -> Unit) {
    val graph = AppGraph.instance
    val settings by graph.settings.state.collectAsState()
    val state by graph.player.state.collectAsState()
    val track = state.current ?: return
    val seed = if (settings.playerArtworkTint) rememberArtworkSeed(track.artworkUrl) else null
    val dark = settings.playerBackground != PlayerBackground.SOLID || isDark(settings)
    var dragY by remember { mutableFloatStateOf(0f) }
    val offset by animateFloatAsState(dragY, label = "drag")

    NocturneTheme(settings, seedOverride = seed, forceDark = dark) {
        val cs = MaterialTheme.colorScheme
        Box(
            Modifier.fillMaxSize()
                .offset { IntOffset(0, offset.roundToInt().coerceAtLeast(0)) }
                .background(cs.surface)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = { if (dragY > 220) onCollapse(); dragY = 0f },
                        onDragCancel = { dragY = 0f },
                    ) { _, d -> dragY = (dragY + d).coerceAtLeast(0f) }
                },
        ) {
            when (settings.playerBackground) {
                PlayerBackground.BLUR -> {
                    AsyncImage(track.artworkUrl, null, Modifier.fillMaxSize().blur(80.dp), contentScale = ContentScale.Crop)
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
                }
                PlayerBackground.GRADIENT -> Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(cs.primaryContainer.copy(alpha = 0.95f), cs.surfaceContainerLowest, cs.surfaceContainerLowest))
                    )
                )
                PlayerBackground.SOLID -> Unit
            }
            if (settings.animatedBackground && settings.playerBackground != PlayerBackground.SOLID) {
                FlowingColors(listOf(cs.primary, cs.tertiary, cs.secondary, cs.primaryContainer), Modifier.fillMaxSize(), 0.32f)
            }
            PlayerContent(onCollapse)
        }
    }
}

// Sheets and dialogs opened from the player; the video hides while any of them is up.
private class Overlays {
    var audio by mutableStateOf(false)
    var sleep by mutableStateOf(false)
    var connect by mutableStateOf(false)
    var carMode by mutableStateOf(false)
    var comments by mutableStateOf(false)
    val any: Boolean get() = audio || sleep || connect || carMode || comments
}

private val LocalOverlays = staticCompositionLocalOf { Overlays() }

@Composable
private fun PlayerContent(onCollapse: () -> Unit) {
    val graph = AppGraph.instance
    val state by graph.player.state.collectAsState()
    val settings by graph.settings.state.collectAsState()
    val track = state.current ?: return
    var pane by remember { mutableStateOf(Pane.ARTWORK) }
    val overlays = remember { Overlays() }
    var noLyrics by remember(track.key) { mutableStateOf(false) }
    LaunchedEffect(track.key, settings.showLyrics) {
        if (settings.showLyrics) noLyrics = runCatching { graph.lyrics.lyrics(track).isEmpty }.getOrDefault(true)
    }
    LaunchedEffect(noLyrics) { if (noLyrics && pane == Pane.LYRICS) pane = Pane.QUEUE }
    val menu = LocalTrackMenu.current

    CompositionLocalProvider(LocalOverlays provides overlays) {
    BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding()) {
        val wide = maxWidth > 760.dp && maxWidth > maxHeight
        val short = maxHeight < 560.dp
        val tall = maxHeight > 680.dp
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                TransportButton(Icons.Rounded.KeyboardArrowDown, "Collapse", onCollapse, iconSize = 30.dp)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(t("PLAYING FROM"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(state.queueTitle ?: track.album ?: track.source.label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (state.instrumental) Text(t("Instrumental version"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                val castDevice by graph.cast.device.collectAsState()
                TransportButton(
                    if (castDevice != null) Icons.Rounded.CastConnected else Icons.Rounded.Devices, "Devices", { overlays.connect = true },
                    tint = if (castDevice != null) MaterialTheme.colorScheme.primary else LocalContentColor.current, glassIcon = Icons.Rounded.Airplay,
                )
                TransportButton(Icons.Rounded.MoreVert, "More", {
                    val s = graph.player.state.value
                    val sleep = s.sleepAtMs?.let { t("Sleep timer · %s min", ((it - nowMillis()) / 60_000).coerceAtLeast(0) + 1) }
                        ?: if (s.sleepAfterTrack) t("Sleep timer") + " · " + t("End of track") else t("Sleep timer")
                    menu(track, TrackMenuContext(extras = listOf(MenuExtra(Icons.Rounded.Bedtime, sleep) { overlays.sleep = true })))
                })
            }

            if (wide) {
                val auto = Pane.LYRICS.takeIf { settings.showLyrics && !noLyrics } ?: Pane.QUEUE
                val shown = if (pane == Pane.ARTWORK && !short) auto else pane
                Row(
                    Modifier.weight(1f).fillMaxWidth().padding(horizontal = if (short) 20.dp else 32.dp, vertical = if (short) 6.dp else 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (short) 24.dp else 40.dp),
                ) {
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) { ArtworkView(track, state.isPlaying) }
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        if (shown != Pane.ARTWORK) {
                            Box(Modifier.weight(1f)) { PaneContent(shown) }
                            Controls(compact = short)
                        } else {
                            Spacer(Modifier.weight(1f))
                            Controls()
                            Spacer(Modifier.weight(1f))
                        }
                        BottomActions(shown, { p -> pane = if (short && pane == p) Pane.ARTWORK else p }, hideArtworkToggle = !short)
                    }
                }
            } else {
                Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
                    AnimatedContent(pane, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "pane") { p ->
                        if (p == Pane.ARTWORK) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            ArtworkView(track, state.isPlaying) { if (settings.showLyrics) pane = if (noLyrics) Pane.QUEUE else Pane.LYRICS }
                        }
                        else PaneContent(p)
                    }
                }
                // Phone: transport and actions in two rounded cards, lifted well off the bottom edge for thumbs.
                Column(Modifier.padding(horizontal = 16.dp).padding(top = 8.dp, bottom = if (tall) 76.dp else 28.dp)) {
                    Controls(cards = true)
                    BottomActions(pane, { pane = if (pane == it) Pane.ARTWORK else it }, hideArtworkToggle = false, card = true)
                }
            }
        }
    }
    }

    if (overlays.carMode) CarModeDialog { overlays.carMode = false }
    if (overlays.connect) ConnectSheet(onDismiss = { overlays.connect = false }, onCarMode = { overlays.carMode = true })
    if (overlays.sleep) SleepTimerDialog { overlays.sleep = false }
    if (overlays.audio) AudioSheet { overlays.audio = false }
    if (overlays.comments) CommentsSheet(track) { overlays.comments = false }
}

@Composable
private fun PaneContent(pane: Pane) {
    when (pane) {
        Pane.LYRICS -> LyricsView()
        Pane.QUEUE -> QueueView()
        Pane.ARTWORK -> Unit
    }
}

@Composable
private fun ArtworkView(track: app.nocturne.model.Track, playing: Boolean, onTap: () -> Unit = {}) {
    val graph = AppGraph.instance
    val settings by graph.settings.state.collectAsState()
    val state by graph.player.state.collectAsState()
    val scale by animateFloatAsState(if (playing) 1f else 0.88f, label = "scale")
    var dx by remember { mutableFloatStateOf(0f) }
    val shift by animateFloatAsState(dx, label = "shift")
    val video by produceState<String?>(null, track.key, settings.showVideo) {
        value = null // drop the previous track's video right away, or its frames linger over the new track
        value = if (settings.showVideo) runCatching { graph.provider(track.source).videoStream(track) }.getOrNull() else null
    }
    // Render (and keep a decoder) only while the expanded player is on screen with no sheet over it.
    val overlays = LocalOverlays.current
    val onScreen = graph.navigator.playerExpanded && !overlays.any && !trackMenuOpen
    val full = video != null && settings.videoFullWidth
    val ratio by animateFloatAsState(if (full) 16f / 9f else 1f, label = "ratio")
    fun setFull(v: Boolean) { if (v != settings.videoFullWidth) graph.settings.update { it.copy(videoFullWidth = v) } }
    Box(
        Modifier
            .widthIn(max = if (full) 760.dp else 520.dp)
            .fillMaxWidth()
            .aspectRatio(ratio)
            .pointerInput(video != null) {
                if (video == null) return@pointerInput
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        if (e.type == PointerEventType.Scroll) {
                            val dy = e.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                            if (dy > 0) setFull(true) else if (dy < 0) setFull(false)
                            e.changes.forEach { it.consume() }
                        }
                    }
                }
            }
            .pointerInput(video != null) {
                if (video == null) return@pointerInput
                awaitEachGesture {
                    var start = 0f
                    var last = 0f
                    while (true) {
                        val e = awaitPointerEvent()
                        val down = e.changes.filter { it.pressed }
                        if (down.size >= 2) {
                            val d = (down[0].position - down[1].position).getDistance()
                            if (start == 0f) start = d
                            last = d
                            e.changes.forEach { it.consume() }
                        }
                        if (down.isEmpty()) break
                    }
                    if (start > 0f) {
                        if (last < start * 0.85f) setFull(true) else if (last > start * 1.15f) setFull(false)
                    }
                }
            }
            .offset { IntOffset(shift.roundToInt(), 0) }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (dx < -150) graph.player.next() else if (dx > 150) graph.player.previous()
                        dx = 0f
                    },
                    onDragCancel = { dx = 0f },
                ) { _, d -> dx += d }
            }
            .padding(120.dp * (1f - scale))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTap)
            .shadow(24.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp)),
    ) {
        Artwork(track.artworkUrl, Modifier.fillMaxSize(), RoundedCornerShape(24.dp))
        video?.takeIf { onScreen }?.let { url ->
            if (full) Box(Modifier.fillMaxSize().background(Color.Black))
            VideoSurface(url, state.positionMs, state.isPlaying, settings.playbackSpeed, Modifier.fillMaxSize(), fit = full)
        }
    }
}

@Composable
private fun Controls(compact: Boolean = false, cards: Boolean = false) {
    val graph = AppGraph.instance
    val state by graph.player.state.collectAsState()
    val library by graph.library.state.collectAsState()
    val settings by graph.settings.state.collectAsState()
    val track = state.current ?: return
    val cs = MaterialTheme.colorScheme
    val inset = if (cards) Modifier.padding(horizontal = 8.dp) else Modifier

    Row(verticalAlignment = Alignment.CenterVertically, modifier = inset.padding(top = if (compact) 6.dp else 16.dp)) {
        Column(Modifier.weight(1f)) {
            Text(track.title, style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.basicMarquee())
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (settings.showSourceBadges) { SourceBadge(track.source, size = 16.dp); Spacer(Modifier.width(6.dp)) }
                ArtistLinks(track, style = MaterialTheme.typography.titleMedium, color = cs.onSurfaceVariant)
            }
        }
        if (track.source == app.nocturne.model.Source.YOUTUBE_MUSIC) {
            val overlays = LocalOverlays.current
            TransportButton(Icons.Rounded.ChatBubbleOutline, "Comments", { overlays.comments = true }, iconSize = 24.dp, tint = cs.onSurfaceVariant, circle = true)
            Box(Modifier.padding(horizontal = 4.dp).size(1.dp, 24.dp).background(cs.onSurface.copy(alpha = 0.2f)))
        }
        TransportButton(Icons.Rounded.ThumbDownOffAlt, "Not interested", { graph.notInterested(track) }, iconSize = 26.dp, tint = cs.onSurfaceVariant, circle = true)
        val liked = library.liked.any { it.key == track.key }
        TransportButton(
            if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Like", { graph.toggleLike(track) },
            iconSize = 28.dp, tint = if (liked) cs.primary else cs.onSurface, circle = true,
        )
    }

    PlayerSeekBar(state.positionMs, state.durationMs, state.progress, { graph.player.seekTo(it) }, inset, compact = compact)

    if (cards) PlayerCard(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        TransportRow(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), compact)
    } else TransportRow(Modifier.fillMaxWidth().padding(vertical = if (compact) 2.dp else 12.dp), compact)
}

@Composable
private fun TransportRow(modifier: Modifier, compact: Boolean) {
    val graph = AppGraph.instance
    val state by graph.player.state.collectAsState()
    Row(modifier, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        ToggleGlyph(state.shuffle, { graph.player.toggleShuffle() }, Icons.Rounded.Shuffle, "Shuffle")
        TransportButton(Icons.Rounded.SkipPrevious, "Previous", graph.player::previous, size = 56.dp, iconSize = 38.dp, glassIcon = Icons.Rounded.FastRewind)
        PlayPauseButton(state.isPlaying, graph.player::toggle, buffering = state.isBuffering, hero = true, compact = compact)
        TransportButton(Icons.Rounded.SkipNext, "Next", graph.player::next, size = 56.dp, iconSize = 38.dp, glassIcon = Icons.Rounded.FastForward)
        ToggleGlyph(
            state.repeat != app.nocturne.player.RepeatMode.OFF, { graph.player.cycleRepeat() },
            if (state.repeat == app.nocturne.player.RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, "Repeat",
        )
    }
}

// Action buttons; [card] = phone layout's rounded container. Volume only on desktop (phones use hardware keys).
@Composable
private fun BottomActions(pane: Pane, onPane: (Pane) -> Unit, hideArtworkToggle: Boolean, card: Boolean = false) {
    val desktop = app.nocturne.platformKind == app.nocturne.PlatformKind.DESKTOP
    var volumeOpen by remember { mutableStateOf(false) }
    var touched by remember { mutableStateOf(0) }
    LaunchedEffect(volumeOpen, touched) {
        if (volumeOpen) { delay(3500); volumeOpen = false }
    }
    val row = if (card) Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp) else Modifier.fillMaxWidth().height(48.dp).padding(bottom = 12.dp)
    val body: @Composable () -> Unit = {
        AnimatedContent(volumeOpen && desktop, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "actions") { open ->
            if (open) VolumeRow(row, onTouch = { touched++ }, onClose = { volumeOpen = false })
            else ActionsRow(row, pane, onPane, hideArtworkToggle, onVolume = if (desktop) ({ volumeOpen = true }) else null)
        }
    }
    if (card) PlayerCard(Modifier.fillMaxWidth().padding(top = 12.dp)) { body() } else body()
}

@Composable
private fun VolumeRow(modifier: Modifier, onTouch: () -> Unit, onClose: () -> Unit) {
    val graph = AppGraph.instance
    val settings by graph.settings.state.collectAsState()
    val cs = MaterialTheme.colorScheme
    var vol by remember(settings.volume) { mutableFloatStateOf(settings.volume) }
    // Glass: Apple-style bar between a quiet and a loud speaker.
    val glass = LocalDesignStyle.current == DesignStyle.GLASS
    Row(modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        TransportButton(
            if (vol <= 0.01f) Icons.AutoMirrored.Rounded.VolumeOff else if (glass) Icons.AutoMirrored.Rounded.VolumeMute else Icons.AutoMirrored.Rounded.VolumeUp, "Mute",
            { vol = if (vol > 0.01f) 0f else 0.6f; graph.player.setVolume(vol); onTouch() },
            size = 36.dp, iconSize = 20.dp, tint = if (glass) cs.onSurfaceVariant else cs.primary,
        )
        PlayerVolumeSlider(
            vol,
            { vol = it; graph.player.setVolume(it); onTouch() },
            Modifier.weight(1f).height(24.dp),
            SliderDefaults.colors(thumbColor = cs.primary, activeTrackColor = cs.primary, inactiveTrackColor = cs.onSurface.copy(alpha = 0.15f)),
        )
        if (glass) Icon(Icons.AutoMirrored.Rounded.VolumeUp, null, Modifier.padding(start = 8.dp).size(20.dp), tint = cs.onSurfaceVariant)
        else Text("${(vol * 100).toInt()}", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, modifier = Modifier.width(32.dp).padding(start = 6.dp))
        TransportButton(Icons.Rounded.Close, "Close", onClose, size = 36.dp, iconSize = 20.dp, tint = cs.onSurfaceVariant)
    }
}

@Composable
private fun ActionsRow(modifier: Modifier, pane: Pane, onPane: (Pane) -> Unit, hideArtworkToggle: Boolean, onVolume: (() -> Unit)?) {
    val graph = AppGraph.instance
    val settings by graph.settings.state.collectAsState()
    val state by graph.player.state.collectAsState()
    val overlays = LocalOverlays.current
    Row(modifier, horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        if (onVolume != null) ToggleGlyph(
            false, onVolume,
            if (settings.volume <= 0.01f) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp, "Volume",
            modifier = Modifier.pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        if (e.type != PointerEventType.Scroll) continue
                        val dy = e.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                        val cur = graph.settings.value.volume
                        graph.player.setVolume((cur - dy * 0.05f).coerceIn(0f, 1f))
                        e.changes.forEach { it.consume() }
                    }
                }
            },
        )
        val current = state.current
        if (current != null && current.source != app.nocturne.model.Source.SOUNDCLOUD && current.source != app.nocturne.model.Source.LOCAL) {
            ToggleGlyph(
                settings.showVideo, { graph.settings.update { it.copy(showVideo = !it.showVideo) } },
                if (settings.showVideo) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff,
                if (settings.showVideo) "Show artwork" else "Show video",
            )
        }
        ToggleGlyph(settings.eqEnabled || settings.playbackSpeed != 1f, { overlays.audio = true }, "Equalizer and speed") { tint, size ->
            EqBars(state.isPlaying, Modifier.size(size), tint)
        }
        if (settings.showLyrics) {
            ToggleGlyph(pane == Pane.LYRICS || (hideArtworkToggle && pane == Pane.ARTWORK), { onPane(Pane.LYRICS) }, Icons.Rounded.Lyrics, "Lyrics")
        }
        ToggleGlyph(pane == Pane.QUEUE, { onPane(Pane.QUEUE) }, Icons.AutoMirrored.Rounded.QueueMusic, "Queue")
    }
}

@Composable
private fun SleepTimerDialog(onDismiss: () -> Unit) {
    val player = AppGraph.instance.player
    val state by player.state.collectAsState()
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Bedtime, null) },
        title = { Text(t("Sleep timer")) },
        text = {
            Column {
                listOf(5, 15, 30, 45, 60, 90).forEach { m ->
                    TextButton(onClick = { player.setSleepTimer(m); onDismiss() }, modifier = Modifier.fillMaxWidth()) { Text(t("%s minutes", m)) }
                }
                TextButton(onClick = { player.sleepAfterCurrentTrack(); onDismiss() }, modifier = Modifier.fillMaxWidth()) { Text(t("End of track")) }
            }
        },
        confirmButton = {
            if (state.sleepAtMs != null || state.sleepAfterTrack) TextButton(onClick = { player.setSleepTimer(null); onDismiss() }) { Text(t("Turn off")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Close")) } },
    )
}

