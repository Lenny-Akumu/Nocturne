package app.nocturne.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.WindowDraggableArea
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import app.nocturne.AppGraph
import app.nocturne.MainWindow
import app.nocturne.i18n.t
import app.nocturne.model.Track
import app.nocturne.model.formatDuration
import app.nocturne.ui.components.Artwork
import app.nocturne.ui.theme.NocturneTheme
import app.nocturne.ui.theme.rememberArtworkSeed
import coil3.compose.AsyncImage

private val BASE = DpSize(820.dp, 132.dp)
private val BTN = 40.dp

@Composable
fun FloatingPlayerWindow() {
    val graph = AppGraph.instance
    val settings by graph.settings.state.collectAsState()
    val scale = settings.floatingScale.coerceIn(0.5f, 2f)
    val size = DpSize(BASE.width * scale, BASE.height * scale)
    val bounds = remember { java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds }
    fun position(): WindowPosition =
        if (settings.floatingX < 0) WindowPosition((bounds.x + (bounds.width - size.width.value) / 2).dp, (bounds.y + 4).dp)
        else WindowPosition(settings.floatingX.dp, settings.floatingY.dp)
    val state = rememberDialogState(position = position(), size = size)
    LaunchedEffect(scale, settings.floatingX, settings.floatingY) {
        state.size = size
        state.position = position()
    }
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
        val base = LocalDensity.current
        NocturneTheme(settings, forceDark = true) {
            CompositionLocalProvider(LocalDensity provides Density(base.density * scale, base.fontScale)) {
                val align = when (settings.floatingAnchor) {
                    0 -> Alignment.TopStart
                    2 -> Alignment.TopEnd
                    else -> Alignment.TopCenter
                }
                Box(Modifier.fillMaxSize().padding(6.dp), contentAlignment = align) {
                    if (FloatingEdit.editing) {
                        WindowDraggableArea(Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp)).background(Color(0x55000000))) {
                            Box(Modifier.fillMaxSize(), contentAlignment = align) { Pill(interactive = false) }
                        }
                        FilledTonalButton(
                            onClick = {
                                val loc = window.location
                                graph.settings.update { it.copy(floatingX = loc.x, floatingY = loc.y) }
                                FloatingEdit.editing = false
                            },
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
                        ) { Text(t("Done")) }
                    } else {
                        Pill(interactive = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun Pill(interactive: Boolean) {
    val graph = AppGraph.instance
    val state by graph.player.state.collectAsState()
    val settings by graph.settings.state.collectAsState()
    val library by graph.library.state.collectAsState()
    val track = state.current
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val open = interactive && hovered
    val accent = rememberArtworkSeed(track?.artworkUrl) ?: MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(30.dp)
    val b = settings.floatingButtons
    val anchor = settings.floatingAnchor

    val extras = buildList<@Composable () -> Unit> {
        if (track != null && "like" in b) add {
            val liked = library.liked.any { it.key == track.key }
            PillButton(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (liked) accent else Color.White) { graph.toggleLike(track) }
        }
        if (track != null && "dislike" in b) add { PillButton(Icons.Rounded.ThumbDown, Color.White) { graph.notInterested(track) } }
        if ("volume" in b) add { Volume(accent) }
        if ("open" in b) add { PillButton(Icons.AutoMirrored.Rounded.OpenInNew, Color.White) { MainWindow.show() } }
    }
    val leftExtras = when (anchor) {
        0 -> emptyList()
        2 -> extras.reversed()
        else -> extras.take(extras.size / 2)
    }
    val rightExtras = when (anchor) {
        0 -> extras
        2 -> emptyList()
        else -> extras.drop(extras.size / 2)
    }
    val volW = if ("volume" in b) 120.dp else 0.dp
    fun widthOf(list: List<@Composable () -> Unit>, hasVolume: Boolean): Dp = BTN * (list.size - if (hasVolume) 1 else 0) + if (hasVolume) volW else 0.dp
    val leftHasVol = anchor == 2 && "volume" in b
    val rightHasVol = (anchor == 0 || anchor == 1) && "volume" in b && rightExtras.isNotEmpty()
    val sideW = if (anchor == 1) maxOf(widthOf(leftExtras, false), widthOf(rightExtras, rightHasVol)) else 0.dp

    Box(
        Modifier
            .shadow(14.dp, shape)
            .clip(shape)
            .background(Color(0xCC0E0E12))
            .hoverable(hover),
    ) {
        if (track != null) {
            AsyncImage(track.artworkUrl, null, Modifier.matchParentSize().blur(36.dp).alpha(0.75f), contentScale = ContentScale.Crop)
        }
        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.45f)))
        Column(Modifier.width(IntrinsicSize.Max)) {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Side(open, Alignment.End, if (anchor == 1) sideW else null, leftExtras)
                Artwork(track?.artworkUrl, Modifier.size(40.dp), CircleShape, Icons.Rounded.MusicNote)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.width(170.dp).clickable(enabled = interactive) { MainWindow.show() }) {
                    Text(track?.title ?: "Nocturne", color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, maxLines = 1, modifier = Modifier.basicMarquee())
                    Text(track?.artistNames.orEmpty(), color = Color.White.copy(alpha = 0.65f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if ("prev" in b) PillButton(Icons.Rounded.SkipPrevious, Color.White, interactive) { graph.player.previous() }
                PillButton(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, accent, interactive) { graph.player.toggle() }
                if ("next" in b) PillButton(Icons.Rounded.SkipNext, Color.White, interactive) { graph.player.next() }
                Side(open, Alignment.Start, if (anchor == 1) sideW else null, rightExtras)
            }
            if ("seek" in b) {
                AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
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
}

@Composable
private fun Side(open: Boolean, from: Alignment.Horizontal, width: Dp?, items: List<@Composable () -> Unit>) {
    if (items.isEmpty() && width == null) return
    AnimatedVisibility(
        open,
        enter = expandHorizontally(expandFrom = from) + fadeIn(),
        exit = shrinkHorizontally(shrinkTowards = from) + fadeOut(),
    ) {
        Row(
            (if (width != null) Modifier.width(width) else Modifier),
            horizontalArrangement = if (from == Alignment.End) androidx.compose.foundation.layout.Arrangement.End else androidx.compose.foundation.layout.Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) { items.forEach { it() } }
    }
}

@Composable
private fun PillButton(icon: ImageVector, tint: Color, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(BTN)) { Icon(icon, null, tint = tint) }
}

@Composable
private fun Volume(accent: Color) {
    val graph = AppGraph.instance
    val settings by graph.settings.state.collectAsState()
    Row(Modifier.width(120.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (settings.volume <= 0.01f) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp, null,
            Modifier.size(18.dp), tint = Color.White.copy(alpha = 0.8f),
        )
        Spacer(Modifier.width(6.dp))
        var vol by remember(settings.volume) { mutableFloatStateOf(settings.volume) }
        PillBar(vol, accent, Modifier.weight(1f), live = true) { vol = it; graph.player.setVolume(it) }
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
            .pointerInput(live) {
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
