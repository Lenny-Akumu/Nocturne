package app.nocturne.ui.player

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import app.nocturne.data.DesignStyle
import app.nocturne.ui.kit.LocalDesignStyle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.nocturne.AppGraph
import app.nocturne.ui.components.Artwork

@Composable
fun CarModeDialog(onDismiss: () -> Unit) {
    val graph = AppGraph.instance
    val state by graph.player.state.collectAsState()
    val library by graph.library.state.collectAsState()
    val track = state.current
    val cs = MaterialTheme.colorScheme
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color(0xFF050507)).padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Artwork(track?.artworkUrl, Modifier.size(96.dp), RoundedCornerShape(20.dp))
                Column(Modifier.weight(1f).padding(horizontal = 18.dp)) {
                    Text(track?.title ?: "Nocturne", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track?.artistNames.orEmpty(), color = Color.White.copy(alpha = 0.7f), fontSize = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                BigButton(Icons.Rounded.Close, Color(0xFF2A2A30), Modifier.size(84.dp), onClick = onDismiss)
            }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BigButton(Icons.Rounded.SkipPrevious, Color(0xFF1E1E24), Modifier.weight(1f).fillMaxSize()) { graph.player.previous() }
                BigButton(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, cs.primary, Modifier.weight(1.4f).fillMaxSize(), cs.onPrimary) { graph.player.toggle() }
                BigButton(Icons.Rounded.SkipNext, Color(0xFF1E1E24), Modifier.weight(1f).fillMaxSize()) { graph.player.next() }
            }
            Spacer(Modifier.height(16.dp))
            if (track != null) {
                val liked = library.liked.any { it.key == track.key }
                BigButton(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, Color(0xFF1E1E24), Modifier.fillMaxWidth().height(96.dp), if (liked) cs.primary else Color.White) {
                    graph.toggleLike(track)
                }
            }
        }
    }
}

// Big tile: Windows uses 8dp corners, Liquid Glass springs down on press; the glyph crossfades.
@Composable
private fun BigButton(icon: ImageVector, bg: Color, modifier: Modifier, tint: Color = Color.White, onClick: () -> Unit) {
    val style = LocalDesignStyle.current
    val shape = RoundedCornerShape(if (style == DesignStyle.WINDOWS) 8.dp else 28.dp)
    val src = remember { MutableInteractionSource() }
    val press = pressAmount(src)
    Box(
        modifier
            .graphicsLayer { if (style == DesignStyle.GLASS) { val s = 1f - 0.05f * press.value; scaleX = s; scaleY = s } }
            .clip(shape)
            .background(bg)
            .clickable(interactionSource = src, indication = if (style == DesignStyle.GLASS) null else LocalIndication.current, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(icon, label = "car") { Icon(it, null, Modifier.size(64.dp), tint = tint) }
    }
}
