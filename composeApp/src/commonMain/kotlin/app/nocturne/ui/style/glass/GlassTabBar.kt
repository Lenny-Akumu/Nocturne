package app.nocturne.ui.style.glass

import app.nocturne.ui.demo
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import app.nocturne.ui.kit.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import app.nocturne.AppGraph
import app.nocturne.i18n.t
import app.nocturne.model.Track
import app.nocturne.ui.Navigator
import app.nocturne.ui.RemoteStrip
import app.nocturne.ui.Screen
import app.nocturne.ui.components.Artwork
import app.nocturne.ui.components.LocalTrackMenu
import app.nocturne.ui.components.SourceBadge
import app.nocturne.ui.components.TrackMenuContext
import app.nocturne.ui.kit.CircularProgressIndicator
import app.nocturne.ui.theme.LocalDarkTheme
import kotlinx.coroutines.launch

private val Spring = spring<Float>(dampingRatio = 0.78f, stiffness = 320f)

// iOS 26 tab bar: [Home | Library] capsule + Search circle, now-playing accessory above. Scrolling down morphs it into
// [tab circle] [inline player capsule] [Search circle]; scrolling up (or tapping the circle) springs it back.
@Composable
fun GlassBottomBar(nav: Navigator, onExpand: () -> Unit, modifier: Modifier = Modifier) {
    val graph = AppGraph.instance
    val player by graph.player.state.collectAsState()
    val density = LocalDensity.current
    LaunchedEffect(density) { GlassChrome.thresholdPx = with(density) { 28.dp.toPx() } }
    LaunchedEffect(nav.current) { GlassChrome.minimized = false }
    val track = player.current
    // Keep the last track so the accessory can animate out.
    val held = remember { arrayOfNulls<Track>(1) }
    if (track != null) held[0] = track
    val shown by animateFloatAsState(if (track != null) 1f else 0f, Spring, label = "acc")
    val p by animateFloatAsState(if (GlassChrome.minimized) 1f else 0f, Spring, label = "min")
    val cs = MaterialTheme.colorScheme
    val dark = LocalDarkTheme.current
    val accessory = shown > 0.01f

    Column(modifier.fillMaxWidth()) {
        if (track == null) AnimatedVisibility(!GlassChrome.minimized) { RemoteStrip(Modifier.padding(horizontal = 8.dp)) }
        BoxWithConstraints(
            Modifier.fillMaxWidth()
                // Scroll-edge fade so content does not clash with the floating glass.
                .drawBehind {
                    drawRect(Brush.verticalGradient(listOf(Color.Transparent, cs.background.copy(alpha = if (dark) 0.75f else 0.6f)), startY = 0f, endY = size.height))
                }
                .navigationBarsPadding()
                .padding(bottom = 6.dp)
                .height(if (accessory) 128.dp else 64.dp),
        ) {
            val w = maxWidth
            val m = if (w < 360.dp) 14.dp else 20.dp
            val g = 10.dp
            val q = p.coerceIn(-0.05f, 1.05f)
            val circle = lerp(62.dp, 56.dp, q)
            val barH = lerp(62.dp, 56.dp, q)
            val tabsFull = minOf(200.dp, w - m * 2 - circle - g)
            val tabsW = lerp(tabsFull, 56.dp, q.coerceIn(0f, 1f))
            val selected = when (nav.currentTab) { Screen.Library -> 1; Screen.Home -> 0; else -> -1 }
            val searchOn = nav.currentTab == Screen.Search

            // Tabs capsule (shrinks into the selected tab's circle).
            GlassSurface(
                Modifier.align(Alignment.BottomStart).offset(x = m).size(tabsW, barH),
                RoundedCornerShape(50),
                onClick = if (p > 0.5f) ({ GlassChrome.minimized = false }) else null,
                contentDescription = if (p > 0.5f) t("Show tab bar") else null,
            ) {
                val sel by animateFloatAsState(selected.coerceAtLeast(0).toFloat(), spring(dampingRatio = 0.72f, stiffness = 420f), label = "sel")
                val hl = if (dark) Color.White.copy(alpha = 0.13f) else Color.Black.copy(alpha = 0.06f)
                val itemW = (tabsFull - 8.dp) / 2
                Row(
                    Modifier.fillMaxSize().padding(4.dp).drawBehind {
                        // Liquid selection lens sliding between tabs; hidden once minimized.
                        val a = (1f - q * 2f).coerceIn(0f, 1f)
                        if (a > 0f && selected >= 0 && !searchOn) {
                            val iw = itemW.toPx()
                            drawRoundRect(hl, Offset(iw * sel, 0f), Size(iw, size.height), CornerRadius(size.height / 2), alpha = a)
                        }
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (q > 0.5f) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(if (selected == 1) Icons.Rounded.LibraryMusic else Icons.Rounded.Home, null, Modifier.size(24.dp), tint = cs.primary)
                        }
                    } else {
                        TabItem(Icons.Rounded.Home, "Home", selected == 0 && !searchOn, Modifier.width(itemW).graphicsLayer { alpha = 1f - q * 2f }) { nav.selectTab(Screen.Home) }
                        TabItem(Icons.Rounded.LibraryMusic, "Library", selected == 1 && !searchOn, Modifier.width(itemW).graphicsLayer { alpha = 1f - q * 2f }) { nav.selectTab(Screen.Library) }
                    }
                }
            }

            // Search: its own circle (iOS 26 search tab).
            GlassSurface(
                Modifier.align(Alignment.BottomEnd).offset(x = -m).size(circle),
                CircleShape,
                onClick = { nav.selectTab(Screen.Search) },
                contentDescription = t("Search"),
            ) {
                Icon(Icons.Rounded.Search, t("Search"), Modifier.size(24.dp), tint = if (searchOn) cs.primary else cs.onSurface)
            }

            // Now playing: above the bar when expanded, inline between the circles when minimized.
            val shownTrack = track ?: held[0]
            if (accessory && shownTrack != null) {
                val expX = m
                val expW = w - m * 2
                val minX = m + 56.dp + 8.dp
                val minW = w - minX * 2
                val x = lerp(expX, minX, q)
                val aw = lerp(expW, minW, q)
                val ah = lerp(54.dp, 56.dp, q)
                val lift = lerp(barH + 10.dp, 0.dp, q)
                NowPlayingCapsule(
                    shownTrack, player.isPlaying, player.isBuffering, compact = q > 0.5f, onExpand = onExpand,
                    modifier = Modifier.align(Alignment.BottomStart)
                        .offset { IntOffset(x.roundToPx(), -lift.roundToPx()) }
                        .size(aw, ah)
                        .graphicsLayer { val s = 0.8f + 0.2f * shown; scaleX = s; scaleY = s; alpha = shown.coerceIn(0f, 1f) },
                )
            }
        }
    }
}

@Composable
private fun TabItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    GlassInnerButton(onClick, modifier.fillMaxHeight(), shape = RoundedCornerShape(50), contentDescription = t(label)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, null, Modifier.size(24.dp), tint = if (selected) cs.primary else cs.onSurface)
            Text(t(label), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold), color = if (selected) cs.primary else cs.onSurface, maxLines = 1)
        }
    }
}

// Glass capsule mini player: artwork, title / artist, play-pause (and next when expanded). Swipe for next / previous.
@Composable
private fun NowPlayingCapsule(track: Track, playing: Boolean, buffering: Boolean, compact: Boolean, onExpand: () -> Unit, modifier: Modifier) {
    val graph = AppGraph.instance
    val settings by graph.settings.state.collectAsState()
    val menu = LocalTrackMenu.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val dx = remember { Animatable(0f) }
    val swipe = with(LocalDensity.current) { 70.dp.toPx() }
    GlassSurface(
        modifier.demo("mini", onExpand).pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragEnd = {
                    val x = dx.value
                    if (x < -swipe) graph.player.next() else if (x > swipe) graph.player.previous()
                    scope.launch { dx.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 400f)) }
                },
                onDragCancel = { scope.launch { dx.animateTo(0f) } },
            ) { c, d -> c.consume(); scope.launch { dx.snapTo(dx.value + d) } }
        },
        RoundedCornerShape(50),
        onClick = onExpand,
        onLongClick = { menu(track, TrackMenuContext()) },
        contentDescription = t("Now playing"),
    ) {
        Row(
            Modifier.fillMaxSize().graphicsLayer { translationX = dx.value * 0.35f }.padding(start = 6.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(track.artworkUrl, Modifier.size(42.dp), CircleShape)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                AnimatedContent(track, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) }, contentKey = { it.key }, label = "np") { track ->
                    Column {
                        Text(track.title, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), color = cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (settings.showSourceBadges) { SourceBadge(track.source, size = 11.dp); Spacer(Modifier.width(5.dp)) }
                            Text(track.artistNames, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = cs.onSurface.copy(alpha = 0.62f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            GlassInnerButton(graph.player::toggle, Modifier.size(44.dp), contentDescription = t(if (playing) "Pause" else "Play")) {
                if (buffering) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                else AnimatedContent(
                    playing,
                    transitionSpec = { (fadeIn(tween(140)) + scaleIn(spring(dampingRatio = 0.6f, stiffness = 500f), 0.6f)) togetherWith (fadeOut(tween(100)) + scaleOut(tween(100), 0.6f)) },
                    label = "pp",
                ) { on -> Icon(if (on) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, Modifier.size(26.dp), tint = cs.onSurface) }
            }
            if (!compact) GlassInnerButton(graph.player::next, Modifier.size(44.dp), contentDescription = t("Next")) {
                Icon(Icons.Rounded.SkipNext, null, Modifier.size(26.dp), tint = cs.onSurface)
            }
        }
    }
}

// Inline navigation bar that fades in (glass) once a large title scrolls away.
@Composable
fun GlassInlineBar(modifier: Modifier = Modifier) {
    val title = GlassChrome.inlineTitle
    val held = remember { arrayOf("") }
    if (title != null) held[0] = title
    AnimatedVisibility(title != null, modifier, enter = fadeIn(tween(180)), exit = fadeOut(tween(160))) {
        val cs = MaterialTheme.colorScheme
        val hair = cs.onSurface.copy(alpha = 0.12f)
        Box(
            Modifier.fillMaxWidth()
                .glassPane(RectangleShape, elevation = 0.dp)
                .drawBehind { drawRect(hair, Offset(0f, size.height - 0.5.dp.toPx()), Size(size.width, 0.5.dp.toPx())) }
                .statusBarsPadding()
                .height(44.dp)
                .padding(horizontal = 56.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(held[0], style = MaterialTheme.typography.titleMedium, color = cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    }
}

// Large title header for tab roots (Search, Library) with the settings avatar.
@Composable
fun GlassLargeTitle(title: String, padding: PaddingValues, onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = padding.calculateTopPadding()).padding(start = 20.dp, end = 14.dp, top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(t(title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        GlassAvatar(onSettings)
    }
}
