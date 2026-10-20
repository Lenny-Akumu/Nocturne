package app.nocturne.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import app.nocturne.AppGraph
import app.nocturne.MainWindow
import app.nocturne.ui.components.Artwork
import app.nocturne.model.formatDuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import app.nocturne.ui.theme.NocturneTheme
import app.nocturne.ui.theme.rememberArtworkSeed

@Composable
fun FloatingPlayerWindow() {
    val graph = AppGraph.instance
    val settings by graph.settings.state.collectAsState()
    val bounds = remember { java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds }
    val width = 620.dp
    val state = rememberDialogState(
        position = WindowPosition(((bounds.x + (bounds.width - width.value) / 2)).dp, (bounds.y + 6).dp),
        size = DpSize(width, 110.dp),
    )
    DialogWindow(
        onCloseRequest = { graph.settings.update { it.copy(floatingPlayer = false) } },
        state = state,
        title = "Nocturne",
        undecorated = true,
        transparent = true,
        resizable = false,
        focusable = false,
    ) {
        LaunchedEffect(Unit) { window.isAlwaysOnTop = true }
        NocturneTheme(settings, forceDark = true) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) { Pill() }
        }
    }
}

@Composable
private fun Pill() {
    val graph = AppGraph.instance
    val state by graph.player.state.collectAsState()
    val settings by graph.settings.state.collectAsState()
    val track = state.current ?: return
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val accent = rememberArtworkSeed(track.artworkUrl) ?: MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(30.dp)

    Box(
        Modifier
            .padding(top = 4.dp)
            .shadow(14.dp, shape)
            .clip(shape)
            .background(Color(0xCC0E0E12))
            .hoverable(hover)
            .animateContentSize()
            .widthIn(min = 260.dp, max = 600.dp),
    ) {
        AsyncImage(track.artworkUrl, null, Modifier.matchParentSize().blur(36.dp).alpha(0.75f), contentScale = ContentScale.Crop)
        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.45f)))
        Column {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(track.artworkUrl, Modifier.size(40.dp), CircleShape)
            Spacer(Modifier.width(10.dp))
            Column(
                Modifier.width(if (hovered) 210.dp else 160.dp).clickable { MainWindow.show() },
            ) {
                Text(track.title, color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, maxLines = 1, modifier = Modifier.basicMarquee())
                Text(track.artistNames, color = Color.White.copy(alpha = 0.65f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            AnimatedVisibility(hovered, enter = expandHorizontally() + fadeIn(), exit = shrinkHorizontally() + fadeOut()) {
                IconButton(onClick = graph.player::previous) { Icon(Icons.Rounded.SkipPrevious, "Previous", tint = Color.White) }
            }
            IconButton(onClick = graph.player::toggle) {
                Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play/pause", tint = accent)
            }
            AnimatedVisibility(hovered, enter = expandHorizontally() + fadeIn(), exit = shrinkHorizontally() + fadeOut()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = graph.player::next) { Icon(Icons.Rounded.SkipNext, "Next", tint = Color.White) }
                    Icon(
                        if (settings.volume <= 0.01f) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp, null,
                        Modifier.size(18.dp), tint = Color.White.copy(alpha = 0.8f),
                    )
                    Spacer(Modifier.width(6.dp))
                    var vol by remember(settings.volume) { mutableFloatStateOf(settings.volume) }
                    PillBar(vol, accent, Modifier.width(96.dp), live = true) { vol = it; graph.player.setVolume(it) }
                    Spacer(Modifier.width(12.dp))
                }
            }
        }
        AnimatedVisibility(hovered) {
            Row(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(state.positionMs.formatDuration(), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.width(8.dp))
                PillBar(state.progress, accent, Modifier.weight(1f)) { f ->
                    if (state.durationMs > 0) graph.player.seekTo((f * state.durationMs).toLong())
                }
                Spacer(Modifier.width(8.dp))
                Text(state.durationMs.formatDuration(), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
            }
        }
        }
    }
}

@Composable
private fun PillBar(value: Float, color: Color, modifier: Modifier, live: Boolean = false, onChange: (Float) -> Unit) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val shown = drag ?: value
    Canvas(
        modifier
            .height(16.dp)
            .pointerHoverIcon(PointerIcon.Hand)
            .pointerInput(Unit) {
                detectTapGestures { o -> onChange((o.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> drag = (o.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = { drag?.let(onChange); drag = null },
                    onDragCancel = { drag = null },
                ) { change, _ ->
                    drag = (change.position.x / size.width).coerceIn(0f, 1f)
                    if (live) drag?.let(onChange)
                }
            },
    ) {
        val y = size.height / 2
        val h = 3.dp.toPx()
        drawLine(Color.White.copy(alpha = 0.2f), Offset(0f, y), Offset(size.width, y), h, StrokeCap.Round)
        drawLine(color, Offset(0f, y), Offset(size.width * shown, y), h, StrokeCap.Round)
        drawCircle(Color.White, 5.dp.toPx(), Offset(size.width * shown, y))
    }
}
