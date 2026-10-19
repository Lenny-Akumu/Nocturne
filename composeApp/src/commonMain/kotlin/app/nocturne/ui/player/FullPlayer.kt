package app.nocturne.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.ThumbDownOffAlt
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import app.nocturne.model.CollectionRef
import app.nocturne.model.CollectionType
import app.nocturne.model.formatDuration
import app.nocturne.nowMillis
import app.nocturne.ui.Screen
import app.nocturne.ui.components.Artwork
import app.nocturne.ui.components.LocalTrackMenu
import app.nocturne.ui.components.SourceBadge
import app.nocturne.ui.components.TrackMenuContext
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
            PlayerContent(onCollapse)
        }
    }
}

@Composable
private fun PlayerContent(onCollapse: () -> Unit) {
    val graph = AppGraph.instance
    val state by graph.player.state.collectAsState()
    val settings by graph.settings.state.collectAsState()
    val track = state.current ?: return
    var pane by remember { mutableStateOf(Pane.ARTWORK) }
    var sleepDialog by remember { mutableStateOf(false) }
    val menu = LocalTrackMenu.current

    BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding()) {
        val wide = maxWidth > 760.dp && maxWidth > maxHeight
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCollapse) { Icon(Icons.Rounded.KeyboardArrowDown, "Collapse", Modifier.size(30.dp)) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("PLAYING FROM", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(state.queueTitle ?: track.album ?: track.source.label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { menu(track, TrackMenuContext()) }) { Icon(Icons.Rounded.MoreVert, "More") }
            }

            if (wide) {
                Row(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 32.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) { ArtworkView(track.artworkUrl, state.isPlaying) }
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        Box(Modifier.weight(1f)) {
                            PaneContent(if (pane == Pane.ARTWORK) Pane.LYRICS.takeIf { settings.showLyrics } ?: Pane.QUEUE else pane)
                        }
                        Controls()
                        BottomActions(pane, { pane = it }, { sleepDialog = true }, hideArtworkToggle = true)
                    }
                }
            } else {
                Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
                    AnimatedContent(pane, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "pane") { p ->
                        if (p == Pane.ARTWORK) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            ArtworkView(track.artworkUrl, state.isPlaying) { if (settings.showLyrics) pane = Pane.LYRICS }
                        }
                        else PaneContent(p)
                    }
                }
                Column(Modifier.padding(horizontal = 24.dp)) {
                    Controls()
                    BottomActions(pane, { pane = if (pane == it) Pane.ARTWORK else it }, { sleepDialog = true }, hideArtworkToggle = false)
                }
            }
        }
    }

    if (sleepDialog) SleepTimerDialog { sleepDialog = false }
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
private fun ArtworkView(url: String?, playing: Boolean, onTap: () -> Unit = {}) {
    val graph = AppGraph.instance
    val scale by animateFloatAsState(if (playing) 1f else 0.88f, label = "scale")
    var dx by remember { mutableFloatStateOf(0f) }
    val shift by animateFloatAsState(dx, label = "shift")
    Artwork(
        url,
        Modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .aspectRatio(1f)
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
        RoundedCornerShape(24.dp),
    )
}

@Composable
private fun Controls() {
    val graph = AppGraph.instance
    val state by graph.player.state.collectAsState()
    val library by graph.library.state.collectAsState()
    val settings by graph.settings.state.collectAsState()
    val track = state.current ?: return
    val cs = MaterialTheme.colorScheme

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 16.dp)) {
        Column(Modifier.weight(1f)) {
            Text(track.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.basicMarquee())
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (settings.showSourceBadges) { SourceBadge(track.source, size = 16.dp); Spacer(Modifier.width(6.dp)) }
                val artist = track.artists.firstOrNull()
                Text(
                    track.artistNames,
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(enabled = artist?.id != null) {
                        val a = artist ?: return@clickable
                        val id = a.id ?: return@clickable
                        graph.navigator.push(Screen.Collection(CollectionRef(id, track.source, CollectionType.ARTIST, a.name)))
                    },
                )
            }
        }
        IconButton(onClick = { graph.notInterested(track) }) {
            Icon(Icons.Rounded.ThumbDownOffAlt, "Not interested", Modifier.size(26.dp), tint = cs.onSurfaceVariant)
        }
        val liked = library.liked.any { it.key == track.key }
        IconButton(onClick = { graph.toggleLike(track) }) {
            Icon(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Like", Modifier.size(28.dp), tint = if (liked) cs.primary else cs.onSurface)
        }
    }

    var seeking by remember { mutableStateOf<Float?>(null) }
    val duration = state.durationMs.coerceAtLeast(1)
    Slider(
        value = seeking ?: state.progress,
        onValueChange = { seeking = it },
        onValueChangeFinished = { seeking?.let { graph.player.seekTo((it * duration).toLong()) }; seeking = null },
        modifier = Modifier.padding(top = 8.dp),
        colors = SliderDefaults.colors(thumbColor = cs.primary, activeTrackColor = cs.primary, inactiveTrackColor = cs.onSurface.copy(alpha = 0.18f)),
    )
    Row {
        Text(((seeking?.let { (it * duration).toLong() }) ?: state.positionMs).formatDuration(), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(state.durationMs.formatDuration(), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
    }

    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        IconToggleButton(checked = state.shuffle, onCheckedChange = { graph.player.toggleShuffle() }) {
            Icon(Icons.Rounded.Shuffle, "Shuffle", tint = if (state.shuffle) cs.primary else cs.onSurfaceVariant)
        }
        IconButton(onClick = graph.player::previous, modifier = Modifier.size(56.dp)) { Icon(Icons.Rounded.SkipPrevious, "Previous", Modifier.size(38.dp)) }
        val corner by animateDpAsState(if (state.isPlaying) 24.dp else 36.dp, label = "corner")
        Surface(
            onClick = graph.player::toggle,
            shape = RoundedCornerShape(corner),
            color = cs.primary,
            contentColor = cs.onPrimary,
            modifier = Modifier.size(width = 88.dp, height = 72.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (state.isBuffering) CircularProgressIndicator(Modifier.size(36.dp), color = cs.onPrimary, strokeWidth = 3.dp)
                else Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (state.isPlaying) "Pause" else "Play", Modifier.size(40.dp))
            }
        }
        IconButton(onClick = graph.player::next, modifier = Modifier.size(56.dp)) { Icon(Icons.Rounded.SkipNext, "Next", Modifier.size(38.dp)) }
        IconToggleButton(checked = state.repeat != app.nocturne.player.RepeatMode.OFF, onCheckedChange = { graph.player.cycleRepeat() }) {
            Icon(
                if (state.repeat == app.nocturne.player.RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                "Repeat",
                tint = if (state.repeat != app.nocturne.player.RepeatMode.OFF) cs.primary else cs.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BottomActions(pane: Pane, onPane: (Pane) -> Unit, onSleep: () -> Unit, hideArtworkToggle: Boolean) {
    val graph = AppGraph.instance
    val settings by graph.settings.state.collectAsState()
    val state by graph.player.state.collectAsState()
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        if (settings.showLyrics) {
            IconToggleButton(checked = pane == Pane.LYRICS, onCheckedChange = { onPane(Pane.LYRICS) }) {
                Icon(Icons.Rounded.Lyrics, "Lyrics", tint = if (pane == Pane.LYRICS || (hideArtworkToggle && pane == Pane.ARTWORK)) cs.primary else cs.onSurfaceVariant)
            }
        }
        val sleeping = state.sleepAtMs != null || state.sleepAfterTrack
        IconButton(onClick = onSleep) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Bedtime, "Sleep timer", tint = if (sleeping) cs.primary else cs.onSurfaceVariant)
            }
        }
        state.sleepAtMs?.let {
            val left = ((it - nowMillis()) / 60_000).coerceAtLeast(0) + 1
            Text("${left}m", style = MaterialTheme.typography.labelMedium, color = cs.primary)
        }
        IconToggleButton(checked = pane == Pane.QUEUE, onCheckedChange = { onPane(Pane.QUEUE) }) {
            Icon(Icons.AutoMirrored.Rounded.QueueMusic, "Queue", tint = if (pane == Pane.QUEUE) cs.primary else cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun SleepTimerDialog(onDismiss: () -> Unit) {
    val player = AppGraph.instance.player
    val state by player.state.collectAsState()
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Bedtime, null) },
        title = { Text("Sleep timer") },
        text = {
            Column {
                listOf(5, 15, 30, 45, 60, 90).forEach { m ->
                    TextButton(onClick = { player.setSleepTimer(m); onDismiss() }, modifier = Modifier.fillMaxWidth()) { Text("$m minutes") }
                }
                TextButton(onClick = { player.sleepAfterCurrentTrack(); onDismiss() }, modifier = Modifier.fillMaxWidth()) { Text("End of track") }
            }
        },
        confirmButton = {
            if (state.sleepAtMs != null || state.sleepAfterTrack) TextButton(onClick = { player.setSleepTimer(null); onDismiss() }) { Text("Turn off") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

