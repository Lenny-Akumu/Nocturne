package app.nocturne.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.OpenInFull
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nocturne.AppGraph
import app.nocturne.i18n.t
import app.nocturne.model.formatDuration
import app.nocturne.player.RepeatMode as Repeat
import app.nocturne.ui.components.Artwork
import app.nocturne.ui.components.onRightClick
import app.nocturne.ui.kit.Icon
import app.nocturne.ui.theme.LocalDarkTheme
import app.nocturne.ui.theme.rememberArtworkSeed
import coil3.compose.AsyncImage

// Aurora: the Windows design. Floating panes over a glow that takes its colours from the playing cover.

private val dockShape = RoundedCornerShape(18.dp)

@Composable
private fun paneFill(): Color = if (LocalDarkTheme.current) Color.White.copy(alpha = 0.055f) else Color.White.copy(alpha = 0.62f)

@Composable
private fun paneRim(): Color = if (LocalDarkTheme.current) Color.White.copy(alpha = 0.09f) else Color.Black.copy(alpha = 0.07f)

@Composable
private fun auroraColors(): Triple<Color, Color, Color> {
    val cs = MaterialTheme.colorScheme
    val art = AppGraph.instance.player.state.collectAsState().value.current?.artworkUrl
    val seed = rememberArtworkSeed(art)
    val a by animateColorAsState(seed ?: cs.primary, tween(1200), label = "a1")
    val b by animateColorAsState(cs.tertiary, tween(1200), label = "a2")
    val c by animateColorAsState(cs.secondary, tween(1200), label = "a3")
    return Triple(a, b, c)
}

/** Slowly drifting glow behind everything. */
@Composable
fun AuroraGlow(modifier: Modifier = Modifier) {
    val (a, b, c) = auroraColors()
    val dark = LocalDarkTheme.current
    val drift = rememberInfiniteTransition(label = "aurora")
    val t by drift.animateFloat(0f, 1f, infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Reverse), label = "t")
    val k = if (dark) 1f else 0.55f
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        fun glow(color: Color, x: Float, y: Float, r: Float, alpha: Float) =
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = alpha * k), Color.Transparent), Offset(x, y), r), r, Offset(x, y))
        glow(a, w * (0.72f + 0.08f * t), h * (0.12f + 0.06f * t), maxOf(w, h) * 0.45f, 0.38f)
        glow(b, w * (0.12f + 0.06f * t), h * (0.86f - 0.05f * t), maxOf(w, h) * 0.42f, 0.28f)
        glow(c, w * (0.48f - 0.05f * t), h * 0.5f, maxOf(w, h) * 0.32f, 0.18f)
    }
}

@Composable
internal fun AuroraDock(nav: Navigator, top: Dp, modifier: Modifier = Modifier) {
    Column(
        modifier.padding(start = 12.dp, top = top, bottom = 12.dp).width(78.dp).fillMaxHeight()
            .clip(dockShape).background(paneFill()).border(1.dp, paneRim(), dockShape)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NocturneLogo(Modifier.padding(bottom = 14.dp).size(42.dp).clip(RoundedCornerShape(12.dp)).clickable { LogoTaps.tap() })
        tabs.forEach { DockItem(it, nav) }
        Spacer(Modifier.weight(1f))
        DockItem(settingsTab, nav)
    }
}

@Composable
private fun DockItem(tab: Tab, nav: Navigator) {
    val cs = MaterialTheme.colorScheme
    val dark = LocalDarkTheme.current
    val selected = nav.currentTab == tab.screen
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    val sel by animateFloatAsState(if (selected) 1f else 0f, tween(220), label = "sel")
    val hov by animateFloatAsState(if (hovered) 1f else 0f, tween(140), label = "hov")
    val shape = RoundedCornerShape(14.dp)
    val base = if (dark) Color.White else Color.Black
    Column(
        Modifier.padding(vertical = 3.dp).size(60.dp, 56.dp)
            .then(if (sel > 0f) Modifier.shadow((10 * sel).dp, shape, ambientColor = cs.primary, spotColor = cs.primary) else Modifier)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(base.copy(alpha = 0.13f * sel + 0.05f * hov), base.copy(alpha = 0.05f * sel + 0.03f * hov))))
            .hoverable(src)
            .clickable(interactionSource = src, indication = null) { nav.selectTab(tab.screen) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(if (selected) tab.selectedIcon else tab.icon, tab.label, Modifier.size(21.dp), tint = if (selected) cs.primary else cs.onSurfaceVariant)
        Spacer(Modifier.height(3.dp))
        Text(t(tab.label), fontSize = 10.sp, color = if (selected) cs.onSurface else cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// Shared backdrop of the player islands: the cover, blurred, under a tinted scrim.
@Composable
private fun IslandBackdrop(art: String?) {
    val dark = LocalDarkTheme.current
    Box(Modifier.fillMaxSize()) {
        if (art != null) AsyncImage(art, null, Modifier.fillMaxSize().blur(44.dp), contentScale = ContentScale.Crop, alpha = 0.6f)
        Box(Modifier.fillMaxSize().background(if (dark) Color(0xFF0D0C12).copy(alpha = 0.7f) else Color.White.copy(alpha = 0.72f)))
    }
}

@Composable
private fun islandModifier(shape: RoundedCornerShape): Modifier =
    Modifier.shadow(24.dp, shape, ambientColor = Color.Black.copy(alpha = 0.5f), spotColor = Color.Black.copy(alpha = 0.6f))
        .clip(shape).border(1.dp, paneRim().copy(alpha = 0.16f), shape)

@Composable
private fun Glyph(icon: ImageVector, desc: String, tint: Color, size: Dp = 20.dp, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    Box(
        Modifier.size(36.dp).clip(CircleShape).background(tint.copy(alpha = if (hovered) 0.1f else 0f))
            .hoverable(src).clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, Modifier.size(size), tint = tint) }
}

@Composable
private fun BigPlay(playing: Boolean, size: Dp, onClick: () -> Unit) {
    val glow = MaterialTheme.colorScheme.primary
    Box(
        Modifier.size(size).shadow(16.dp, CircleShape, ambientColor = glow, spotColor = glow).clip(CircleShape)
            .background(if (LocalDarkTheme.current) Color.White else Color(0xFF15131C)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pause" else "Play", Modifier.size(size * 0.52f),
            tint = if (LocalDarkTheme.current) Color(0xFF111111) else Color.White)
    }
}

@Composable
private fun GlowBar(value: Float, modifier: Modifier, onSeek: (Float) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val (a, _, _) = auroraColors()
    var drag by remember { mutableStateOf<Float?>(null) }
    val shown = drag ?: value
    val track = cs.onSurface.copy(alpha = 0.16f)
    Canvas(
        modifier.height(16.dp)
            .pointerInput(Unit) { detectTapGestures { o -> onSeek((o.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> drag = (o.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = { drag?.let(onSeek); drag = null },
                    onDragCancel = { drag = null },
                ) { change, _ -> change.consume(); drag = (change.position.x / size.width).coerceIn(0f, 1f) }
            },
    ) {
        val y = size.height / 2
        val h = 4.dp.toPx()
        drawLine(track, Offset(0f, y), Offset(size.width, y), h, StrokeCap.Round)
        if (shown > 0f) {
            val end = Offset(size.width * shown, y)
            val brush = Brush.horizontalGradient(listOf(cs.tertiary, a), 0f, end.x.coerceAtLeast(1f))
            drawLine(brush, Offset(0f, y), end, h * 2.6f, StrokeCap.Round, alpha = 0.25f)
            drawLine(brush, Offset(0f, y), end, h, StrokeCap.Round)
            if (drag != null) drawCircle(Color.White, 6.dp.toPx(), end)
        }
    }
}

/** Desktop player: a floating island with the cover blurred behind it. */
@Composable
internal fun AuroraIsland(onExpand: () -> Unit, modifier: Modifier = Modifier) {
    val graph = AppGraph.instance
    val state by graph.player.state.collectAsState()
    val settings by graph.settings.state.collectAsState()
    val library by graph.library.state.collectAsState()
    val track = state.current ?: return
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    val menu = app.nocturne.ui.components.LocalTrackMenu.current
    Box(modifier.fillMaxWidth().height(88.dp).then(islandModifier(shape)).onRightClick { menu(track, app.nocturne.ui.components.TrackMenuContext()) }) {
        IslandBackdrop(track.artworkUrl)
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onExpand), verticalAlignment = Alignment.CenterVertically) {
                Artwork(track.artworkUrl, Modifier.size(60.dp).shadow(10.dp, RoundedCornerShape(12.dp)), RoundedCornerShape(12.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(track.title, style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    app.nocturne.ui.components.ArtistLinks(track, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
            Column(Modifier.widthIn(max = 460.dp).weight(1.3f), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Glyph(Icons.Rounded.Shuffle, "Shuffle", if (state.shuffle) cs.primary else cs.onSurfaceVariant, 18.dp) { graph.player.toggleShuffle() }
                    Glyph(Icons.Rounded.SkipPrevious, "Previous", cs.onSurface, 24.dp) { graph.player.previous() }
                    BigPlay(state.isPlaying, 44.dp) { graph.player.toggle() }
                    Glyph(Icons.Rounded.SkipNext, "Next", cs.onSurface, 24.dp) { graph.player.next() }
                    Glyph(if (state.repeat == Repeat.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, "Repeat",
                        if (state.repeat != Repeat.OFF) cs.primary else cs.onSurfaceVariant, 18.dp) { graph.player.cycleRepeat() }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(state.positionMs.formatDuration(), fontSize = 11.sp, color = cs.onSurfaceVariant, modifier = Modifier.width(40.dp))
                    GlowBar(state.progress, Modifier.weight(1f)) { f -> if (state.durationMs > 0) graph.player.seekTo((f * state.durationMs).toLong()) }
                    Text(state.durationMs.formatDuration(), fontSize = 11.sp, color = cs.onSurfaceVariant, modifier = Modifier.width(40.dp).padding(start = 8.dp))
                }
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                val liked = library.liked.any { it.key == track.key }
                Glyph(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Like", if (liked) cs.primary else cs.onSurfaceVariant) { graph.toggleLike(track) }
                if (app.nocturne.platformKind == app.nocturne.PlatformKind.DESKTOP) {
                    Icon(Icons.AutoMirrored.Rounded.VolumeUp, null, Modifier.padding(start = 6.dp, end = 6.dp).size(18.dp), tint = cs.onSurfaceVariant)
                    var vol by remember(settings.volume) { mutableFloatStateOf(settings.volume) }
                    GlowBar(vol, Modifier.width(110.dp)) { vol = it; graph.player.setVolume(it) }
                }
                Spacer(Modifier.width(4.dp))
                Glyph(Icons.Rounded.OpenInFull, "Open player", cs.onSurfaceVariant, 18.dp, onExpand)
            }
        }
    }
}

/** Phone bottom: one island holding the mini player and the tabs. */
@Composable
internal fun AuroraPhoneBar(nav: Navigator, onExpand: () -> Unit, modifier: Modifier = Modifier) {
    val graph = AppGraph.instance
    val state by graph.player.state.collectAsState()
    val library by graph.library.state.collectAsState()
    val track = state.current
    val cs = MaterialTheme.colorScheme
    val dark = LocalDarkTheme.current
    val shape = RoundedCornerShape(28.dp)
    Box(modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth().then(islandModifier(shape))) {
        Box(Modifier.matchParentSize()) { IslandBackdrop(track?.artworkUrl) }
        Column(Modifier.padding(8.dp)) {
            if (track != null) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onExpand).padding(start = 4.dp, end = 2.dp, top = 2.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(track.artworkUrl, Modifier.size(46.dp), RoundedCornerShape(12.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(track.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(track.artistNames, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    val liked = library.liked.any { it.key == track.key }
                    Glyph(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Like", if (liked) cs.primary else cs.onSurfaceVariant) { graph.toggleLike(track) }
                    Spacer(Modifier.width(4.dp))
                    BigPlay(state.isPlaying, 40.dp) { graph.player.toggle() }
                }
                Box(Modifier.padding(horizontal = 10.dp).fillMaxWidth().height(2.dp).clip(CircleShape).background(cs.onSurface.copy(alpha = 0.12f))) {
                    Box(Modifier.fillMaxWidth(state.progress).fillMaxHeight().background(cs.primary))
                }
                Spacer(Modifier.height(8.dp))
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background((if (dark) Color.White else Color.Black).copy(alpha = 0.06f)).padding(4.dp),
            ) {
                tabs.forEach { tab ->
                    val selected = nav.currentTab == tab.screen
                    val sel by animateFloatAsState(if (selected) 1f else 0f, tween(200), label = "tab")
                    Row(
                        Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(16.dp))
                            .background((if (dark) Color.White else Color.Black).copy(alpha = 0.12f * sel))
                            .clickable { nav.selectTab(tab.screen) },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (selected) tab.selectedIcon else tab.icon, tab.label, Modifier.size(18.dp), tint = if (selected) cs.onSurface else cs.onSurfaceVariant)
                        if (selected) {
                            Spacer(Modifier.width(6.dp))
                            Text(t(tab.label), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
