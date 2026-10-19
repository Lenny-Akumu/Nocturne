package app.nocturne.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
    val graph = AppGraph.instance
    val state by graph.player.state.collectAsState()
    val settings by graph.settings.state.collectAsState()
    val library by graph.library.state.collectAsState()
    val track = state.current ?: return
    val backdrop = LocalBackdrop.current
    val container = if (backdrop) MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.82f) else MaterialTheme.colorScheme.surfaceContainerHigh
    val shape = RoundedCornerShape(if (wide) 18.dp else 16.dp)

    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .shadow(6.dp, shape)
            .clip(shape)
            .background(container)
            .clickable(onClick = onExpand)
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(onDragEnd = { if (total < -40) onExpand(); total = 0f }) { _, d -> total += d }
            },
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(track.artworkUrl, Modifier.size(if (wide) 52.dp else 46.dp), RoundedCornerShape(10.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (settings.showSourceBadges) { SourceBadge(track.source, size = 12.dp); Spacer(Modifier.width(5.dp)) }
                    Text(track.artistNames, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (wide) {
                val liked = library.liked.any { it.key == track.key }
                IconButton(onClick = { graph.toggleLike(track) }) {
                    Icon(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Like", tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = graph.player::previous) { Icon(Icons.Rounded.SkipPrevious, "Previous") }
            }
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                if (state.isBuffering) CircularProgressIndicator(Modifier.size(40.dp), strokeWidth = 2.dp)
                IconButton(onClick = graph.player::toggle) {
                    Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (state.isPlaying) "Pause" else "Play")
                }
            }
            IconButton(onClick = graph.player::next) { Icon(Icons.Rounded.SkipNext, "Next") }
            if (wide) {
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Rounded.VolumeUp, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                var vol by remember(settings.volume) { mutableFloatStateOf(settings.volume) }
                Slider(value = vol, onValueChange = { vol = it; graph.player.setVolume(it) }, modifier = Modifier.widthIn(max = 140.dp).width(120.dp))
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
