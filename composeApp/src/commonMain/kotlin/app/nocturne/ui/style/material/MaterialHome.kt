package app.nocturne.ui.style.material

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.AssistChip
import app.nocturne.ui.kit.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import app.nocturne.AppGraph
import app.nocturne.i18n.t
import app.nocturne.model.CollectionRef
import app.nocturne.model.CollectionType
import app.nocturne.model.MediaEntry
import app.nocturne.model.Section
import app.nocturne.model.SectionLayout
import app.nocturne.model.Source
import app.nocturne.model.Track
import app.nocturne.ui.Screen
import app.nocturne.ui.components.Artwork
import app.nocturne.ui.components.CollectionArtwork
import app.nocturne.ui.components.LoadingBox
import app.nocturne.ui.components.LocalTrackMenu
import app.nocturne.ui.components.MessageBox
import app.nocturne.ui.components.SourceBadge
import app.nocturne.ui.components.TrackMenuContext
import app.nocturne.ui.components.displayTitle
import app.nocturne.ui.components.onRightClick
import app.nocturne.ui.player.EqBars
import app.nocturne.ui.screens.smartMixes
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// Material You 3 home ("Dynamic color" mockup): search pill, 2-column quick grid, hero carousel, round mixes, shelves.
// Same data as the default home; HomeScreen does the loading and hands it over.
@Composable
fun MaterialHome(
    contentPadding: PaddingValues,
    listState: LazyListState,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    greeting: String,
    recommended: List<Track>,
    sections: List<Section>?,
    errors: List<String>,
    onPlay: (List<Track>, Int, String?) -> Unit,
    spotifyCard: @Composable () -> Unit,
) {
    val graph = AppGraph.instance
    val library by graph.library.state.collectAsState()
    val settings by graph.settings.state.collectAsState()
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = contentPadding) {
            item {
                MaterialSearchBar(Modifier.padding(horizontal = 16.dp).padding(top = 8.dp))
                Text(greeting, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Normal, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp))
            }
            val recs = recommended.filterNot { graph.library.isBlocked(it) }
            if (recs.isNotEmpty()) item {
                QuickGrid(t("Recommended for you"), recs) { i -> onPlay(recs, i, t("Recommended for you")) }
            }
            val mostPlayed = graph.library.mostPlayed(16)
            if (mostPlayed.size >= 4) item {
                ShelfHeader(t("Your top tracks"))
                HeroCarousel(mostPlayed) { i -> onPlay(mostPlayed, i, t("Your top tracks")) }
            }
            item { MixesRow { list, title -> onPlay(list, 0, title) } }
            if (library.saved.isNotEmpty()) item {
                Shelf(t("From your library"), library.saved.take(20).map { MediaEntry.Collection(it) }) { _, _ -> }
            }
            if (Source.SPOTIFY in settings.enabledSources && !graph.spotify.isConfigured) item { spotifyCard() }
            val s = sections
            when {
                s == null -> item { LoadingBox() }
                s.isEmpty() && library.history.isEmpty() -> item {
                    MessageBox(
                        Icons.Rounded.CloudOff, "Nothing here yet",
                        errors.joinToString("\n").ifBlank { "Search for something you love to get started." },
                        actionLabel = "Retry", onAction = onRefresh,
                    )
                }
                else -> s.forEach { raw ->
                    val section = raw.copy(items = raw.items.filterNot { it is MediaEntry.Song && graph.library.isBlocked(it.track) })
                    if (section.items.isNotEmpty()) item {
                        val title = t(section.title)
                        val songs = section.items.filterIsInstance<MediaEntry.Song>().map { it.track }
                        if (section.layout == SectionLayout.QUICK_PICKS) QuickGrid(title, songs, section.source) { i -> onPlay(songs, i, title) }
                        else Shelf(title, section.items, section.source) { track, _ -> onPlay(songs, songs.indexOfFirst { it.key == track.key }.coerceAtLeast(0), title) }
                    }
                }
            }
            if (library.history.isNotEmpty()) item {
                val all = library.history.take(20).map { it.track }
                Shelf(t("Listen again"), all.map { MediaEntry.Song(it) }) { _, i -> onPlay(all, i, t("Listen again")) }
            }
            if (errors.isNotEmpty() && !sections.isNullOrEmpty()) item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(errors) { e ->
                        AssistChip(onClick = onRefresh, label = { Text(e, maxLines = 1) }, leadingIcon = { Icon(Icons.Rounded.History, null, Modifier.size(18.dp)) })
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ShelfHeader(title: String, source: Source? = null, action: String? = null, onAction: (() -> Unit)? = null) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp).height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        if (source != null) { SourceBadge(source, size = 18.dp); Spacer(Modifier.width(8.dp)) }
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (action != null && onAction != null) {
            Box(
                Modifier.height(40.dp).clip(CircleShape).combinedClickable(onClick = onAction).padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text(action, style = MaterialTheme.typography.labelLarge, color = cs.primary) }
        }
    }
}

// Pages of 4 rows scrolled sideways; the next page peeks so it's clear there's more.
@Composable
private fun QuickGrid(title: String, tracks: List<Track>, source: Source? = null, onPlay: (Int) -> Unit) {
    ShelfHeader(title, source, null) {}
    val pages = tracks.indices.chunked(4)
    val state = rememberLazyListState()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val pageW = if (pages.size > 1) maxWidth - 16.dp - 56.dp else maxWidth - 32.dp
        LazyRow(
            state = state,
            flingBehavior = rememberSnapFlingBehavior(state),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(pages.size) { p ->
                Column(Modifier.width(pageW), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    pages[p].forEach { i -> CompactRow(tracks[i], Modifier.fillMaxWidth()) { onPlay(i) } }
                }
            }
        }
    }
}

@Composable
private fun CompactRow(track: Track, modifier: Modifier, onClick: () -> Unit) {
    val graph = AppGraph.instance
    val cs = MaterialTheme.colorScheme
    val menu = LocalTrackMenu.current
    val settings by graph.settings.state.collectAsState()
    val player by graph.player.state.collectAsState()
    val current = player.current?.key == track.key
    Row(
        modifier.height(64.dp).clip(RoundedCornerShape(16.dp)).background(if (current) cs.secondaryContainer else cs.surfaceContainer)
            .onRightClick { menu(track, TrackMenuContext()) }
            .combinedClickable(onClick = onClick, onLongClick = { menu(track, TrackMenuContext()) })
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp)) {
            Artwork(track.artworkUrl, Modifier.fillMaxSize(), RoundedCornerShape(12.dp))
            if (current) Box(Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                EqBars(player.isPlaying, Modifier.size(20.dp), Color.White)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = if (current) cs.onSecondaryContainer else cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (settings.showSourceBadges) { SourceBadge(track.source, size = 11.dp); Spacer(Modifier.width(4.dp)) }
                Text(track.artistNames, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// Multi-browse hero carousel: one large item, a medium and a small peek. Items morph between keylines while
// dragging, so the artwork re-crops (parallax-like) as it grows or shrinks; snaps with a spring.
@Composable
private fun HeroCarousel(tracks: List<Track>, onPlay: (Int) -> Unit) {
    val scope = rememberCoroutineScope()
    var pos by remember { mutableFloatStateOf(0f) }
    var settle by remember { mutableStateOf<Job?>(null) }
    val menu = LocalTrackMenu.current
    BoxWithConstraints(Modifier.fillMaxWidth().height(208.dp).padding(horizontal = 16.dp)) {
        val density = LocalDensity.current
        val full = constraints.maxWidth.toFloat()
        val gap = with(density) { 8.dp.toPx() }
        val small = with(density) { 44.dp.toPx() }
        val medium = full * 0.28f
        val large = (full - medium - small - 2 * gap).coerceAtLeast(medium)
        val step = large + gap
        val maxPos = (tracks.size - 3).coerceAtLeast(0).toFloat()
        // Keylines for slot -1..3 as (x, width).
        val xs = floatArrayOf(-small - gap, 0f, large + gap, large + medium + 2 * gap, full + gap)
        val ws = floatArrayOf(small, large, medium, small, small)
        val drag = rememberDraggableState { d -> pos = (pos - d / step).coerceIn(-0.25f, maxPos + 0.25f) }
        Layout(
            content = {
                tracks.forEachIndexed { i, track ->
                    key(track.key) { HeroItem(track, large, { onPlay(i) }, { menu(track, TrackMenuContext()) }) }
                }
            },
            modifier = Modifier.fillMaxSize().clipToBounds().draggable(
                drag, Orientation.Horizontal,
                onDragStarted = { settle?.cancel() },
                onDragStopped = { v ->
                    val target = (pos - v / step * 0.22f).roundToInt().coerceIn(0, maxPos.toInt()).toFloat()
                    settle = scope.launch {
                        animate(pos, target, -v / step, spring(dampingRatio = 0.75f, stiffness = 260f)) { value, _ -> pos = value }
                    }
                },
            ),
        ) { measurables, c ->
            val h = c.maxHeight
            val placed = measurables.mapIndexedNotNull { i, m ->
                val p = i - pos
                if (p <= -2f || p >= 3f) return@mapIndexedNotNull null
                val s = (p + 1f).coerceIn(0f, 4f)
                val k = s.toInt().coerceAtMost(3)
                val f = s - k
                val x = xs[k] + (xs[k + 1] - xs[k]) * f
                val w = ws[k] + (ws[k + 1] - ws[k]) * f
                m.measure(Constraints.fixed(w.roundToInt().coerceAtLeast(1), h)) to x
            }
            layout(c.maxWidth, h) { placed.forEach { (pl, x) -> pl.place(x.roundToInt(), 0) } }
        }
    }
}

@Composable
private fun HeroItem(track: Track, large: Float, onClick: () -> Unit, onMenu: () -> Unit) {
    val settings by AppGraph.instance.settings.state.collectAsState()
    val shape = RoundedCornerShape(28.dp)
    Box(Modifier.fillMaxSize().clip(shape).onRightClick(onMenu).combinedClickable(onClick = onClick, onLongClick = onMenu)) {
        Artwork(track.artworkUrl, Modifier.fillMaxSize(), shape)
        // Caption fades in as the item reaches the large keyline.
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .graphicsLayer { alpha = ((size.width - large * 0.55f) / (large * 0.35f)).coerceIn(0f, 1f) }
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.62f))))
                .padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 14.dp),
        ) {
            Text(track.title, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (settings.showSourceBadges) { SourceBadge(track.source, size = 12.dp); Spacer(Modifier.width(5.dp)) }
                Text(track.artistNames, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// "Your mixes" as round avatars.
@Composable
private fun MixesRow(onPlay: (List<Track>, String) -> Unit) {
    val mixes = smartMixes()
    if (mixes.isEmpty()) return
    val cs = MaterialTheme.colorScheme
    ShelfHeader(t("Your mixes"))
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items(mixes) { (title, tracks) ->
            Column(
                Modifier.width(100.dp).clip(RoundedCornerShape(20.dp)).combinedClickable(onClick = { onPlay(tracks, t(title)) }).padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val arts = tracks.mapNotNull { it.artworkUrl }.distinct().take(4)
                Box(Modifier.size(84.dp).clip(CircleShape).background(cs.surfaceContainerHighest)) {
                    if (arts.size >= 4) Column {
                        Row { Artwork(arts[0], Modifier.size(42.dp), RoundedCornerShape(0.dp)); Artwork(arts[1], Modifier.size(42.dp), RoundedCornerShape(0.dp)) }
                        Row { Artwork(arts[2], Modifier.size(42.dp), RoundedCornerShape(0.dp)); Artwork(arts[3], Modifier.size(42.dp), RoundedCornerShape(0.dp)) }
                    } else Artwork(arts.firstOrNull(), Modifier.fillMaxSize(), CircleShape)
                }
                Spacer(Modifier.height(8.dp))
                Text(t(title), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                Text(t("%s songs", tracks.size), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

// Horizontal shelf of rounded cards (circles for artists); songs play, collections open.
@Composable
private fun Shelf(title: String, items: List<MediaEntry>, source: Source? = null, onSong: (Track, Int) -> Unit) {
    val nav = AppGraph.instance.navigator
    ShelfHeader(title, source)
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(items.size) { i ->
            when (val e = items[i]) {
                is MediaEntry.Song -> SongTile(e.track) { onSong(e.track, i) }
                is MediaEntry.Collection -> CollectionTile(e.ref) { nav.push(Screen.Collection(e.ref)) }
            }
        }
    }
}

@Composable
private fun SongTile(track: Track, onClick: () -> Unit) {
    val menu = LocalTrackMenu.current
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.width(144.dp).clip(RoundedCornerShape(20.dp))
            .onRightClick { menu(track, TrackMenuContext()) }
            .combinedClickable(onClick = onClick, onLongClick = { menu(track, TrackMenuContext()) }),
    ) {
        Artwork(track.artworkUrl, Modifier.fillMaxWidth().aspectRatio(1f), RoundedCornerShape(24.dp))
        Spacer(Modifier.height(8.dp))
        Text(track.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
        Text(track.artistNames, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 6.dp))
    }
}

@Composable
private fun CollectionTile(ref: CollectionRef, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val artist = ref.type == CollectionType.ARTIST
    Column(
        Modifier.width(144.dp).clip(RoundedCornerShape(20.dp)).combinedClickable(onClick = onClick),
        horizontalAlignment = if (artist) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        CollectionArtwork(ref, Modifier.fillMaxWidth().aspectRatio(1f), if (artist) CircleShape else RoundedCornerShape(24.dp))
        Spacer(Modifier.height(8.dp))
        Text(ref.displayTitle(), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
        ref.subtitle?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 6.dp))
        }
    }
}
