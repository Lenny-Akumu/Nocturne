package app.nocturne.ui.player

import androidx.compose.foundation.background
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import app.nocturne.data.PlayerBackground
import app.nocturne.ui.theme.NocturneTheme
import app.nocturne.ui.theme.isDark
import app.nocturne.ui.theme.rememberArtworkSeed
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.foundation.clickable
import app.nocturne.ui.components.onRightClick
import app.nocturne.ui.liquidGlass
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import app.nocturne.ui.kit.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nocturne.AppGraph
import app.nocturne.ui.components.Artwork
import app.nocturne.ui.components.SourceBadge
import app.nocturne.ui.theme.LocalBackdrop

@Composable
fun MiniPlayer(onExpand: () -> Unit, wide: Boolean, modifier: Modifier = Modifier) {
    if (!wide && app.nocturne.ui.kit.LocalDesignStyle.current == app.nocturne.data.DesignStyle.MATERIAL) {
        app.nocturne.ui.style.material.MaterialMiniPlayer(onExpand, modifier)
        return
    }
    val graph = AppGraph.instance
    val state by graph.player.state.collectAsState()
    val settings by graph.settings.state.collectAsState()
    val library by graph.library.state.collectAsState()
    val track = state.current ?: return
    val backdrop = LocalBackdrop.current
    val seed = if (settings.playerArtworkTint) rememberArtworkSeed(track.artworkUrl) else null
    val dark = settings.playerBackground != PlayerBackground.SOLID || isDark(settings)
    val glass = app.nocturne.ui.LocalGlass.current != null
    val shape = RoundedCornerShape(if (glass) 26.dp else if (wide) 18.dp else 16.dp)
    val menu = app.nocturne.ui.components.LocalTrackMenu.current
    val scope = rememberCoroutineScope()
    val dx = remember { Animatable(0f) }
    val dy = remember { Animatable(0f) }
    val density = LocalDensity.current
    val swipe = with(density) { 90.dp.toPx() }

    NocturneTheme(settings, seedOverride = seed, forceDark = if (glass) null else dark, backdrop = backdrop) {
    val cs = MaterialTheme.colorScheme
    val container = if (backdrop) cs.surfaceContainerHigh.copy(alpha = 0.82f) else cs.surfaceContainerHigh
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .graphicsLayer {
                translationX = dx.value
                translationY = dy.value.coerceAtLeast(0f)
                alpha = 1f - (abs(dx.value) / (swipe * 3)).coerceIn(0f, 0.5f) - (dy.value / (swipe * 1.5f)).coerceIn(0f, 0.8f)
            }
            .then(
                if (glass) Modifier.liquidGlass(shape)
                else Modifier.shadow(6.dp, shape).clip(shape).background(container),
            )
            .drawBehind {
                if (!glass && settings.playerBackground == PlayerBackground.GRADIENT) {
                    drawRect(Brush.horizontalGradient(listOf(cs.primaryContainer.copy(alpha = 0.9f), cs.surfaceContainerHigh.copy(alpha = 0f))))
                }
            }
            .onRightClick { menu(track, app.nocturne.ui.components.TrackMenuContext()) }
            .clickable(onClick = onExpand)
            .pointerInput(Unit) {
                var horizontal: Boolean? = null
                detectDragGestures(
                    onDragStart = { horizontal = null },
                    onDragEnd = {
                        val x = dx.value
                        val y = dy.value
                        scope.launch {
                            when {
                                horizontal == true && x < -swipe -> { graph.player.next(); dx.snapTo(-x); dx.animateTo(0f) }
                                horizontal == true && x > swipe -> { graph.player.previous(); dx.snapTo(-x); dx.animateTo(0f) }
                                horizontal == false && y > swipe * 0.6f -> { dy.animateTo(swipe * 1.5f); graph.player.stop(); dy.snapTo(0f) }
                                horizontal == false && y < -swipe * 0.4f -> { onExpand(); dy.snapTo(0f) }
                                else -> { launch { dx.animateTo(0f) }; dy.animateTo(0f) }
                            }
                        }
                    },
                    onDragCancel = { scope.launch { dx.animateTo(0f) }; scope.launch { dy.animateTo(0f) } },
                ) { change, d ->
                    if (horizontal == null) horizontal = abs(d.x) > abs(d.y)
                    change.consume()
                    scope.launch { if (horizontal == true) dx.snapTo(dx.value + d.x) else dy.snapTo(dy.value + d.y) }
                }
            },
    ) {
        if (!glass && settings.playerBackground == PlayerBackground.BLUR) {
            Box(Modifier.matchParentSize()) {
                AsyncImage(track.artworkUrl, null, Modifier.matchParentSize().blur(40.dp), contentScale = ContentScale.Crop)
                Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.5f)))
            }
        }
        if (!glass && settings.animatedBackground && settings.playerBackground != PlayerBackground.SOLID) {
            app.nocturne.ui.components.FlowingColors(listOf(cs.primary, cs.tertiary, cs.secondary), Modifier.matchParentSize(), 0.28f)
        }
        Column {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(track.artworkUrl, Modifier.size(if (wide) 52.dp else 46.dp), RoundedCornerShape(10.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (settings.showSourceBadges) { SourceBadge(track.source, size = 12.dp); Spacer(Modifier.width(5.dp)) }
                    // links only in the wide bar; on phones the whole mini player opens the full player
                    app.nocturne.ui.components.ArtistLinks(track, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, enabled = wide)
                }
            }
            if (wide) {
                val liked = library.liked.any { it.key == track.key }
                TransportButton(
                    if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Like", { graph.toggleLike(track) },
                    tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TransportButton(Icons.Rounded.SkipPrevious, "Previous", graph.player::previous, glassIcon = Icons.Rounded.FastRewind)
            }
            PlayPauseButton(state.isPlaying, graph.player::toggle, buffering = state.isBuffering)
            TransportButton(Icons.Rounded.SkipNext, "Next", graph.player::next, glassIcon = Icons.Rounded.FastForward)
            if (wide && app.nocturne.platformKind == app.nocturne.PlatformKind.DESKTOP) {
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Rounded.VolumeUp, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                var vol by remember(settings.volume) { mutableFloatStateOf(settings.volume) }
                PlayerVolumeSlider(vol, { vol = it; graph.player.setVolume(it) }, Modifier.widthIn(max = 140.dp).width(120.dp))
                Spacer(Modifier.width(8.dp))
            }
        }
        LinearProgressIndicator(
            progress = { state.progress },
            modifier = Modifier.fillMaxWidth().height(3.dp).padding(horizontal = 14.dp),
            strokeCap = StrokeCap.Round,
            drawStopIndicator = {},
            gapSize = 0.dp,
        )
        Spacer(Modifier.height(4.dp))
    }
}
}
}
