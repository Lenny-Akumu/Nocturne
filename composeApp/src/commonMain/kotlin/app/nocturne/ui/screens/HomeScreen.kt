package app.nocturne.ui.screens

import androidx.compose.foundation.layout.Box
import app.nocturne.ui.seasonalStop
import app.nocturne.i18n.t
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.AssistChip
import app.nocturne.ui.kit.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.nocturne.AppGraph
import app.nocturne.model.MediaEntry
import app.nocturne.model.Section
import app.nocturne.model.SectionLayout
import app.nocturne.ui.components.LoadingBox
import app.nocturne.ui.components.MessageBox
import app.nocturne.ui.components.SectionView
import app.nocturne.ui.components.SourceBadge
import app.nocturne.ui.components.brandColor
import app.nocturne.model.Source
import androidx.compose.foundation.layout.width
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private object HomeCache {
    var sections: List<Section>? = null
    var version by androidx.compose.runtime.mutableIntStateOf(0)
}

fun invalidateHomeCache() {
    HomeCache.sections = null
    HomeCache.version++
}

@Composable
fun HomeScreen(contentPadding: PaddingValues) {
    val graph = AppGraph.instance
    val library by graph.library.state.collectAsState()
    val settings by graph.settings.state.collectAsState()
    graph.accounts.state.collectAsState().value
    var sections by remember(HomeCache.version) { mutableStateOf(HomeCache.sections) }
    var errors by remember { mutableStateOf<List<String>>(emptyList()) }
    var refreshing by remember { mutableStateOf(false) }
    var recommended by remember { mutableStateOf<List<app.nocturne.model.Track>>(emptyList()) }
    var reload by rememberSaveable { mutableIntStateOf(0) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val homeRefresh = graph.navigator.homeRefresh
    var handledRefresh by remember { mutableIntStateOf(homeRefresh) }
    LaunchedEffect(homeRefresh) {
        if (homeRefresh != handledRefresh) {
            handledRefresh = homeRefresh
            listState.animateScrollToItem(0)
            reload++
        }
    }

    val logoTaps = app.nocturne.ui.LogoTaps.refresh
    var seenTaps by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(logoTaps) {
        if (logoTaps != seenTaps && app.nocturne.nowMillis() - app.nocturne.ui.LogoTaps.at < 1500) {
            seenTaps = logoTaps
            runCatching { listState.animateScrollToItem(0) }
            reload++
        }
    }
    LaunchedEffect(reload, HomeCache.version) {
        launch { recommended = runCatching { graph.recommendations(force = reload > 0) }.getOrDefault(emptyList()) }
        if (sections != null && reload == 0) return@LaunchedEffect
        refreshing = true
        val errs = mutableListOf<String>()
        val loaded = coroutineScope {
            graph.enabledProviders().map { p ->
                async {
                    runCatching { p.home() }.getOrElse {
                        errs += "${p.source.label}: ${it.message}"
                        emptyList()
                    }
                }
            }.awaitAll().flatten()
        }
        sections = loaded
        HomeCache.sections = loaded
        errors = errs
        refreshing = false
    }

    if (app.nocturne.ui.kit.LocalDesignStyle.current == app.nocturne.data.DesignStyle.MATERIAL) {
        app.nocturne.ui.style.material.MaterialHome(
            contentPadding, listState, refreshing, { reload++ }, greeting(), recommended, sections, errors,
            onPlay = { list, i, title -> playTrack(list, i, title) },
        ) {}
        return
    }
    if (app.nocturne.ui.kit.LocalDesignStyle.current == app.nocturne.data.DesignStyle.GLASS) {
        app.nocturne.ui.style.glass.GlassHome(
            contentPadding, listState, sections, recommended, errors, refreshing, { reload++ },
            play = { list, i, title -> playTrack(list, i, title) },
        ) {}
        return
    }
    app.nocturne.ui.components.RefreshBox(isRefreshing = refreshing, onRefresh = { reload++ }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = contentPadding) {
            item {
                Text(
                    greeting(),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 8.dp),
                )
            }
            val recs = recommended.filterNot { graph.library.isBlocked(it) }
            if (recs.isNotEmpty()) {
                item {
                    Box(Modifier.seasonalStop()) { SectionView(
                        Section("Recommended for you", recs.map { MediaEntry.Song(it) }, SectionLayout.QUICK_PICKS),
                        subtitle = t("Based on what you've been listening to"),
                    ) { list, i -> playTrack(list, i, t("Recommended for you")) } }
                }
            }
            item { SmartMixes { list, title -> playTrack(list, 0, title) } }
            item { app.nocturne.ui.components.MoodRow() }
            val mostPlayed = graph.library.mostPlayed(16)
            if (mostPlayed.size >= 4) {
                item {
                    SectionView(Section("Your top tracks", mostPlayed.map { MediaEntry.Song(it) })) { list, i ->
                        if (list.size == 1) playTrack(mostPlayed, mostPlayed.indexOfFirst { it.key == list[0].key }, t("Your top tracks"))
                        else playTrack(list, i)
                    }
                }
            }
            if (library.saved.isNotEmpty()) {
                item {
                    SectionView(Section("From your library", library.saved.take(20).map { MediaEntry.Collection(it) })) { _, _ -> }
                }
            }
            val s = sections
            when {
                s == null -> item { LoadingBox() }
                s.isEmpty() && library.history.isEmpty() -> item {
                    MessageBox(
                        Icons.Rounded.CloudOff,
                        "Nothing here yet",
                        errors.joinToString("\n").ifBlank { "Search for something you love to get started." },
                        actionLabel = "Retry",
                        onAction = { reload++ },
                    )
                }
                else -> s.forEach { raw ->
                    val section = raw.copy(items = raw.items.filterNot { it is MediaEntry.Song && graph.library.isBlocked(it.track) })
                    if (section.items.isNotEmpty()) item {
                        SectionView(section) { list, i ->
                            val songs = section.items.filterIsInstance<MediaEntry.Song>().map { it.track }
                            if (list.size == 1 && songs.size > 1) playTrack(songs, songs.indexOfFirst { it.key == list[0].key }, t(section.title))
                            else playTrack(list, i, t(section.title))
                        }
                    }
                }
            }
            if (library.history.isNotEmpty()) {
                item {
                    SectionView(Section("Listen again", library.history.take(20).map { MediaEntry.Song(it.track) })) { list, _ ->
                        val all = library.history.take(20).map { it.track }
                        playTrack(all, all.indexOfFirst { it.key == list[0].key }.coerceAtLeast(0), t("Listen again"))
                    }
                }
            }
            if (errors.isNotEmpty() && !sections.isNullOrEmpty()) {
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(errors) { e ->
                            AssistChip(onClick = { reload++ }, label = { Text(e, maxLines = 1) }, leadingIcon = { Icon(Icons.Rounded.History, null, Modifier.size(18.dp)) })
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

private fun playTrack(list: List<app.nocturne.model.Track>, index: Int, title: String? = null) {
    val graph = AppGraph.instance
    val i = index.coerceIn(0, list.lastIndex.coerceAtLeast(0))
    if (list.isEmpty()) return
    if (graph.settings.value.autoplayRadio) graph.player.startRadio(list[i]) else graph.player.play(list, i, title)
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

@Composable
fun SourceRow(modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        app.nocturne.model.Source.entries.filter { it != app.nocturne.model.Source.LOCAL }.forEach { SourceBadge(it, size = 14.dp) }
    }
}

@Composable
private fun ConnectSpotifyCard() {
    val nav = AppGraph.instance.navigator
    app.nocturne.ui.kit.Card(
        onClick = { nav.push(app.nocturne.ui.Screen.SettingsPage(SettingsCategory.ACCOUNTS)) },
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Source.SPOTIFY.brandColor.copy(alpha = 0.16f)),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            SourceBadge(Source.SPOTIFY, size = 36.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(t("Connect Spotify"), style = MaterialTheme.typography.titleMedium)
                Text(
                    t("Sign in to see your playlists, liked songs and top tracks here."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
