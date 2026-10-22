package app.nocturne.ui.style.glass

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nocturne.AppGraph
import app.nocturne.i18n.t
import app.nocturne.model.CollectionType
import app.nocturne.model.MediaEntry
import app.nocturne.model.Section
import app.nocturne.model.SectionLayout
import app.nocturne.model.Source
import app.nocturne.model.Track
import app.nocturne.nowMillis
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
import app.nocturne.ui.kit.TextButton
import app.nocturne.ui.player.EqBars
import app.nocturne.ui.player.pressAmount

private class Mix(val title: String, val kicker: String, val tracks: List<Track>)

// Home for DesignStyle.GLASS (iPhone "Tinted glass"): large title, mix hero carousel, grouped 2-column list, shelves.
@Composable
fun GlassHome(
    contentPadding: PaddingValues,
    listState: LazyListState,
    sections: List<Section>?,
    recommended: List<Track>,
    errors: List<String>,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    play: (List<Track>, Int, String?) -> Unit,
    extras: @Composable () -> Unit = {},
) {
    val graph = AppGraph.instance
    val library by graph.library.state.collectAsState()
    val greeting = greeting()
    val threshold = with(LocalDensity.current) { 44.dp.toPx() }
    // Large title collapses into the glass inline bar once it scrolls away.
    LaunchedEffect(listState, greeting) {
        snapshotFlow { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > threshold }
            .collect { GlassChrome.inlineTitle = if (it) greeting else null }
    }
    DisposableEffect(Unit) { onDispose { GlassChrome.inlineTitle = null } }

    val recs = recommended.filterNot { graph.library.isBlocked(it) }
    val mostPlayed = graph.library.mostPlayed(16)
    val mixes = rememberMixes().ifEmpty {
        listOfNotNull(
            Mix(t("Recommended for you"), t("Recommended for you"), recs).takeIf { recs.size >= 4 },
            Mix(t("Your top tracks"), t("Your top tracks"), mostPlayed).takeIf { mostPlayed.size >= 4 },
        )
    }

    val ptr = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize(), state = ptr,
        // Below the status bar: the glass home has no top bar above it.
        indicator = { PullToRefreshDefaults.Indicator(state = ptr, isRefreshing = refreshing, modifier = Modifier.align(Alignment.TopCenter).padding(top = contentPadding.calculateTopPadding())) },
    ) {
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = contentPadding) {
            item(key = "title") {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 14.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(greeting, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    GlassAvatar({ graph.navigator.push(Screen.Settings) })
                }
            }
            if (mixes.isNotEmpty()) item(key = "mixes") { MixCarousel(mixes) { m -> play(m.tracks, 0, m.title) } }
            if (recs.isNotEmpty()) item(key = "recs") {
                var all by rememberSaveable { mutableStateOf(false) }
                ShelfHeader(t("Recommended for you"), chevron = recs.size > 6, open = all, onClick = if (recs.size > 6) ({ all = !all }) else null)
                GroupedGrid(if (all) recs.take(20) else recs.take(6)) { i -> play(recs, i, t("Recommended for you")) }
            }
            if (mostPlayed.size >= 4) item(key = "top") {
                ShelfHeader(t("Your top tracks"))
                Shelf(mostPlayed.map { MediaEntry.Song(it) }) { i -> play(mostPlayed, i, t("Your top tracks")) }
            }
            if (library.saved.isNotEmpty()) item(key = "saved") {
                ShelfHeader(t("From your library"))
                Shelf(library.saved.take(20).map { MediaEntry.Collection(it) }) { _ -> }
            }
            item(key = "extras") { extras() }
            val s = sections
            when {
                s == null -> item(key = "loading") { LoadingBox() }
                s.isEmpty() && library.history.isEmpty() -> item(key = "empty") {
                    MessageBox(
                        Icons.Rounded.CloudOff, "Nothing here yet",
                        errors.joinToString("\n").ifBlank { "Search for something you love to get started." },
                        actionLabel = "Retry", onAction = onRefresh,
                    )
                }
                else -> s.forEachIndexed { idx, raw ->
                    val section = raw.copy(items = raw.items.filterNot { it is MediaEntry.Song && graph.library.isBlocked(it.track) })
                    if (section.items.isNotEmpty()) item(key = "s$idx:${section.title}") {
                        val songs = section.items.filterIsInstance<MediaEntry.Song>().map { it.track }
                        ShelfHeader(t(section.title), source = section.source)
                        if (section.layout == SectionLayout.QUICK_PICKS && songs.size >= 2) {
                            GroupedGrid(songs.take(8)) { i -> play(songs, i, t(section.title)) }
                        } else Shelf(section.items) { i ->
                            val e = section.items[i]
                            if (e is MediaEntry.Song) play(songs, songs.indexOfFirst { it.key == e.track.key }.coerceAtLeast(0), t(section.title))
                        }
                    }
                }
            }
            if (library.history.isNotEmpty()) item(key = "again") {
                val again = library.history.take(20).map { it.track }
                ShelfHeader(t("Listen again"))
                Shelf(again.map { MediaEntry.Song(it) }) { i -> play(again, i, t("Listen again")) }
            }
            if (errors.isNotEmpty() && !sections.isNullOrEmpty()) item(key = "errors") {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    errors.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2) }
                    TextButton(onClick = onRefresh) { Text(t("Retry")) }
                }
            }
            item(key = "end") { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun greeting(): String {
    val hour = remember { app.nocturne.currentHour() }
    return when (hour) {
        in 5..11 -> t("Good morning")
        in 12..17 -> t("Good afternoon")
        in 18..22 -> t("Good evening")
        else -> t("Good night")
    }
}

// Same smart mixes as the Material home (StatsScreen.SmartMixes).
@Composable
private fun rememberMixes(): List<Mix> {
    val graph = AppGraph.instance
    val library by graph.library.state.collectAsState()
    val month = nowMillis() - 30L * 86_400_000L
    val onRepeat = remember(library.plays.size) {
        library.plays.filter { it.t >= month }.groupingBy { it.k }.eachCount().entries
            .sortedByDescending { it.value }.filter { it.value >= 2 }.take(40).mapNotNull { graph.library.trackFor(it.key) }
    }
    val forgotten = remember(library.history.size) {
        library.history.filter { it.playedAt < month && (library.playCounts[it.track.key] ?: 0) >= 3 }
            .sortedByDescending { library.playCounts[it.track.key] ?: 0 }.take(40).map { it.track }
    }
    val likedMix = remember(library.liked.size) { library.liked.shuffled().take(50) }
    val night = remember(library.history.size) {
        library.history.filter { val h = ((it.playedAt / 3_600_000L) % 24); h >= 21 || h <= 4 }.map { it.track }.distinctBy { it.key }.take(40)
    }
    val k = t("Your mixes")
    return listOfNotNull(
        Mix(t("On repeat"), k, onRepeat).takeIf { onRepeat.size >= 4 },
        Mix(t("Long time no listen"), k, forgotten).takeIf { forgotten.size >= 4 },
        Mix(t("Liked mix"), k, likedMix).takeIf { likedMix.size >= 4 },
        Mix(t("Late night"), k, night).takeIf { night.size >= 6 },
    )
}

@Composable
private fun MixCarousel(mixes: List<Mix>, onPlay: (Mix) -> Unit) {
    val pager = rememberPagerState(pageCount = { mixes.size })
    Column(Modifier.padding(top = 14.dp)) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxWidth().height(196.dp),
            contentPadding = PaddingValues(horizontal = 20.dp),
            pageSpacing = 10.dp,
            key = { mixes[it].title },
        ) { page -> MixCard(mixes[page], onPlay) }
        if (mixes.size > 1) Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.Center) {
            val cs = MaterialTheme.colorScheme
            mixes.indices.forEach { i ->
                val on = pager.currentPage == i
                val w by animateDpAsState(if (on) 18.dp else 6.dp, spring(dampingRatio = 0.7f, stiffness = 500f), label = "dot")
                Box(Modifier.padding(horizontal = 3.dp).size(w, 6.dp).clip(CircleShape).background(if (on) cs.onSurface else cs.onSurface.copy(alpha = 0.25f)))
            }
        }
    }
}

@Composable
private fun MixCard(mix: Mix, onPlay: (Mix) -> Unit) {
    val src = remember { MutableInteractionSource() }
    val press = pressAmount(src)
    val shape = RoundedCornerShape(26.dp)
    val arts = remember(mix.tracks) { mix.tracks.mapNotNull { it.artworkUrl }.distinct().take(4) }
    val artists = remember(mix.tracks) { mix.tracks.flatMap { it.artists.map { a -> a.name } }.distinct().take(3).joinToString(", ") }
    Box(
        Modifier.fillMaxSize()
            .graphicsLayer { val s = 1f - 0.03f * press.value; scaleX = s; scaleY = s }
            .clip(shape)
            .combinedClickable(interactionSource = src, indication = null, role = Role.Button, onClick = { onPlay(mix) }),
    ) {
        if (arts.size >= 4) Row(Modifier.fillMaxSize()) {
            arts.chunked(2).forEach { pair ->
                Column(Modifier.weight(1f).fillMaxSize()) { pair.forEach { Artwork(it, Modifier.weight(1f).fillMaxWidth(), RoundedCornerShape(0.dp)) } }
            }
        } else Artwork(arts.firstOrNull(), Modifier.fillMaxSize(), RoundedCornerShape(0.dp))
        // Scrim keeps the white text readable on any artwork.
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, 0.4f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.75f))))
        Text(
            mix.kicker.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp),
            color = Color.White,
            modifier = Modifier.padding(12.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.4f)).padding(horizontal = 10.dp, vertical = 6.dp),
        )
        Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 16.dp, end = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(mix.title, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (artists.isNotBlank()) Text(artists, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.78f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(10.dp))
            GlassCircle(Icons.Rounded.PlayArrow, t("Play"), { onPlay(mix) }, size = 46.dp, tint = Color.Black.copy(alpha = 0.22f), iconTint = Color.White)
        }
    }
}

@Composable
private fun ShelfHeader(title: String, source: Source? = null, chevron: Boolean = false, open: Boolean = false, onClick: (() -> Unit)? = null) {
    val cs = MaterialTheme.colorScheme
    val rot by animateFloatAsState(if (open) 90f else 0f, spring(dampingRatio = 0.7f, stiffness = 500f), label = "chev")
    Row(
        Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 10.dp)
            .then(if (onClick != null) Modifier.clip(RoundedCornerShape(8.dp)).combinedClickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (source != null) { SourceBadge(source, size = 18.dp); Spacer(Modifier.width(8.dp)) }
        Text(title, style = MaterialTheme.typography.titleLarge.copy(fontSize = 21.sp, fontWeight = FontWeight.Bold), color = cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (chevron) Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
            Modifier.padding(start = 2.dp).size(22.dp).graphicsLayer { rotationZ = rot }, tint = cs.onSurface.copy(alpha = 0.38f),
        )
    }
}

// Inset grouped container (iOS grouped list) holding tracks in two columns.
@Composable
private fun GroupedGrid(tracks: List<Track>, onPlay: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().animateContentSize(spring(dampingRatio = 0.85f, stiffness = 380f))
            .clip(RoundedCornerShape(22.dp)).background(cs.surfaceContainerLow).padding(vertical = 4.dp),
    ) {
        tracks.indices.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { i -> GridCell(tracks[i], Modifier.weight(1f)) { onPlay(i) } }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun GridCell(track: Track, modifier: Modifier, onClick: () -> Unit) {
    val graph = AppGraph.instance
    val settings by graph.settings.state.collectAsState()
    val player by graph.player.state.collectAsState()
    val menu = LocalTrackMenu.current
    val cs = MaterialTheme.colorScheme
    val current = player.current?.key == track.key
    val src = remember { MutableInteractionSource() }
    val press = pressAmount(src)
    val hl = cs.onSurface.copy(alpha = 0.08f)
    Row(
        modifier.height(60.dp)
            .drawBehind { if (press.value > 0f) drawRect(hl, alpha = press.value.coerceIn(0f, 1f)) }
            .onRightClick { menu(track, TrackMenuContext()) }
            .combinedClickable(interactionSource = src, indication = null, onLongClick = { menu(track, TrackMenuContext()) }, onClick = onClick)
            .padding(start = 12.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp)) {
            Artwork(track.artworkUrl, Modifier.fillMaxSize(), RoundedCornerShape(8.dp))
            if (current) Box(Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                EqBars(player.isPlaying, Modifier.size(18.dp), Color.White)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), color = if (current) cs.primary else cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (settings.showSourceBadges) { SourceBadge(track.source, size = 11.dp); Spacer(Modifier.width(5.dp)) }
                Text(track.artistNames, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// Horizontal shelf of 122dp cards (songs, albums, playlists, artists).
@Composable
private fun Shelf(entries: List<MediaEntry>, onPlay: (Int) -> Unit) {
    val nav = AppGraph.instance.navigator
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(entries.size) { i ->
            when (val e = entries[i]) {
                is MediaEntry.Song -> ShelfCard(e.track.title, e.track.artistNames, track = e.track, onClick = { onPlay(i) }) {
                    Artwork(e.track.artworkUrl, Modifier.fillMaxSize(), RoundedCornerShape(14.dp))
                }
                is MediaEntry.Collection -> {
                    val artist = e.ref.type == CollectionType.ARTIST
                    val shape = if (artist) CircleShape else RoundedCornerShape(14.dp)
                    ShelfCard(e.ref.displayTitle(), e.ref.subtitle, centered = artist, onClick = { nav.push(Screen.Collection(e.ref)) }) {
                        CollectionArtwork(e.ref, Modifier.fillMaxSize(), shape)
                    }
                }
            }
        }
    }
}

@Composable
private fun ShelfCard(title: String, subtitle: String?, track: Track? = null, centered: Boolean = false, onClick: () -> Unit, art: @Composable () -> Unit) {
    val menu = LocalTrackMenu.current
    val cs = MaterialTheme.colorScheme
    val src = remember { MutableInteractionSource() }
    val press = pressAmount(src)
    Column(
        Modifier.width(122.dp)
            .graphicsLayer { val s = 1f - 0.05f * press.value; scaleX = s; scaleY = s }
            .then(if (track != null) Modifier.onRightClick { menu(track, TrackMenuContext()) } else Modifier)
            .combinedClickable(
                interactionSource = src, indication = null, onClick = onClick,
                onLongClick = if (track != null) ({ menu(track, TrackMenuContext()) }) else null,
            ),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Box(Modifier.size(122.dp)) { art() }
        Text(title, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium), color = cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp))
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}
