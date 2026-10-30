package app.nocturne.ui.style.material

import app.nocturne.ui.demo
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import app.nocturne.ui.kit.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nocturne.AppGraph
import app.nocturne.data.DesignStyle
import app.nocturne.data.Settings
import app.nocturne.i18n.t
import app.nocturne.ui.Navigator
import app.nocturne.ui.Screen
import app.nocturne.ui.components.ArtistLinks
import app.nocturne.ui.components.Artwork
import app.nocturne.ui.components.LocalTrackMenu
import app.nocturne.ui.components.SourceBadge
import app.nocturne.ui.components.TrackMenuContext
import app.nocturne.ui.components.onRightClick
import app.nocturne.ui.theme.LocalBackdrop
import app.nocturne.ui.theme.NocturneTheme
import app.nocturne.ui.theme.rememberArtworkSeed
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.abs

// Material You 3 app chrome: dynamic artwork color, floating mini player, navigation bar, home search bar.

// App-wide seed: the now-playing artwork (kept across track changes until the next one resolves).
// Null = NocturneTheme falls back to system dynamic color (Android 12+) or the accent violet.
@Composable
fun rememberMaterialSeed(settings: Settings): Color? {
    val on = settings.designStyle == DesignStyle.MATERIAL && settings.dynamicColor
    val state = AppGraph.instance.player.state
    // Only the artwork URL, so position ticks don't recompose the app root.
    val art by remember(state) { state.map { it.current?.artworkUrl }.distinctUntilChanged() }.collectAsState(state.value.current?.artworkUrl)
    val url = art?.takeIf { on }
    val seed = rememberArtworkSeed(url)
    val held = remember { SeedHolder() }
    held.last = if (url == null) null else seed ?: held.last
    return held.last
}

private class SeedHolder { var last: Color? = null }

private class NavTab(val screen: Screen, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val navTabs = listOf(
    NavTab(Screen.Home, "Home", Icons.Outlined.Home, Icons.Rounded.Home),
    NavTab(Screen.Search, "Search", Icons.Outlined.Search, Icons.Rounded.Search),
    NavTab(Screen.Library, "Library", Icons.Outlined.LibraryMusic, Icons.Rounded.LibraryMusic),
)

// M3 navigation bar with the pill indicator; the selected icon pops with a spring.
@Composable
fun MaterialNavBar(nav: Navigator) {
    val cs = MaterialTheme.colorScheme
    val backdrop = LocalBackdrop.current
    NavigationBar(containerColor = cs.surfaceContainer.copy(alpha = if (backdrop) 0.7f else 0.97f)) {
        navTabs.forEach { tab ->
            val selected = nav.currentTab == tab.screen
            val pop = remember { Animatable(1f) }
            LaunchedEffect(selected) {
                if (selected) { pop.snapTo(0.78f); pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 500f)) }
            }
            NavigationBarItem(
                selected = selected,
                onClick = { nav.selectTab(tab.screen) },
                icon = { Icon(if (selected) tab.selectedIcon else tab.icon, t(tab.label), Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value }) },
                label = { Text(t(tab.label), fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium) },
            )
        }
    }
}

// Home top bar: pill search field that opens Search, with the account avatar (opens Settings).
@Composable
fun MaterialSearchBar(modifier: Modifier = Modifier) {
    val graph = AppGraph.instance
    val nav = graph.navigator
    val cs = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().height(56.dp).clip(CircleShape).background(cs.surfaceContainerHigh)
            .clickable { nav.selectTab(Screen.Search) }.padding(start = 18.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = cs.onSurface)
        Spacer(Modifier.width(14.dp))
        Text(t("Search songs, artists"), style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        ExpressiveIconButton(onClick = { nav.push(Screen.Profile) }, modifier = Modifier.demo("open_settings") { nav.push(Screen.Profile) }) {
            app.nocturne.ui.components.ProfileAvatar(34.dp)
        }
    }
}

// Floating pill mini player: round artwork, title/artists, next, and play/pause inside a progress ring.
// Swipe sideways to skip, down to stop, up (or tap) to expand.
@Composable
fun MaterialMiniPlayer(onExpand: () -> Unit, modifier: Modifier = Modifier) {
    val graph = AppGraph.instance
    val state by graph.player.state.collectAsState()
    val settings by graph.settings.state.collectAsState()
    val track = state.current ?: return
    val seed = if (settings.playerArtworkTint) rememberArtworkSeed(track.artworkUrl) else null
    val menu = LocalTrackMenu.current
    val scope = rememberCoroutineScope()
    val dx = remember { Animatable(0f) }
    val dy = remember { Animatable(0f) }
    val swipe = with(LocalDensity.current) { 90.dp.toPx() }
    val shape = RoundedCornerShape(32.dp)

    // Tint with the artwork only when there is a seed; otherwise keep the app scheme (already artwork-based with dynamic color).
    val backdrop = LocalBackdrop.current
    val themed: @Composable (@Composable () -> Unit) -> Unit = { body -> if (seed != null) NocturneTheme(settings, seedOverride = seed, backdrop = backdrop, content = body) else body() }
    themed {
        val cs = MaterialTheme.colorScheme
        Row(
            modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .graphicsLayer {
                    translationX = dx.value
                    translationY = dy.value.coerceAtLeast(0f)
                    alpha = 1f - (abs(dx.value) / (swipe * 3)).coerceIn(0f, 0.5f) - (dy.value / (swipe * 1.5f)).coerceIn(0f, 0.8f)
                }
                .shadow(8.dp, shape)
                .clip(shape)
                .background(cs.primaryContainer)
                .onRightClick { menu(track, TrackMenuContext()) }
                .combinedClickable(onClick = onExpand, onLongClick = { menu(track, TrackMenuContext()) })
                .pointerInput(Unit) {
                    var horizontal: Boolean? = null
                    detectDragGestures(
                        onDragStart = { horizontal = null },
                        onDragEnd = {
                            val x = dx.value
                            val y = dy.value
                            scope.launch {
                                when {
                                    horizontal == true && x < -swipe -> { graph.player.next(); dx.snapTo(-x); dx.animateTo(0f, spring(dampingRatio = 0.7f)) }
                                    horizontal == true && x > swipe -> { graph.player.previous(); dx.snapTo(-x); dx.animateTo(0f, spring(dampingRatio = 0.7f)) }
                                    horizontal == false && y > swipe * 0.6f -> { dy.animateTo(swipe * 1.5f); graph.player.stop(); dy.snapTo(0f) }
                                    horizontal == false && y < -swipe * 0.4f -> { onExpand(); dy.snapTo(0f) }
                                    else -> { launch { dx.animateTo(0f, spring(dampingRatio = 0.5f)) }; dy.animateTo(0f, spring(dampingRatio = 0.5f)) }
                                }
                            }
                        },
                        onDragCancel = { scope.launch { dx.animateTo(0f) }; scope.launch { dy.animateTo(0f) } },
                    ) { change, d ->
                        if (horizontal == null) horizontal = abs(d.x) > abs(d.y)
                        change.consume()
                        scope.launch { if (horizontal == true) dx.snapTo(dx.value + d.x) else dy.snapTo(dy.value + d.y) }
                    }
                }
                .padding(start = 8.dp, end = 6.dp)
                .height(64.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(track.artworkUrl, Modifier.size(46.dp), CircleShape)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, style = MaterialTheme.typography.titleSmall, color = cs.onPrimaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (settings.showSourceBadges) { SourceBadge(track.source, size = 12.dp); Spacer(Modifier.width(5.dp)) }
                    ArtistLinks(track, style = MaterialTheme.typography.bodySmall, color = cs.onPrimaryContainer.copy(alpha = 0.78f), enabled = false)
                }
            }
            ExpressiveIconButton(onClick = graph.player::next) {
                Icon(Icons.Rounded.SkipNext, t("Next"), tint = cs.onPrimaryContainer)
            }
            Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                val ring = cs.primary
                val track2 = cs.onPrimaryContainer.copy(alpha = 0.18f)
                Canvas(Modifier.fillMaxSize()) {
                    val w = 3.dp.toPx()
                    val d = size.minDimension - w
                    val tl = androidx.compose.ui.geometry.Offset((size.width - d) / 2, (size.height - d) / 2)
                    val s = androidx.compose.ui.geometry.Size(d, d)
                    drawArc(track2, 0f, 360f, false, tl, s, style = Stroke(w))
                    drawArc(ring, -90f, 360f * state.progress, false, tl, s, style = Stroke(w, cap = StrokeCap.Round))
                }
                ExpressiveIconButton(onClick = graph.player::toggle, size = 40.dp, container = cs.primary) {
                    if (state.isBuffering) LoadingIndicator(Modifier.size(24.dp), cs.onPrimary)
                    else MorphGlyph(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, t(if (state.isPlaying) "Pause" else "Play"), 22.dp, cs.onPrimary)
                }
            }
        }
    }
}
