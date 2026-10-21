package app.nocturne.ui.screens

import androidx.compose.foundation.layout.Box
import kotlinx.coroutines.launch
import androidx.compose.material.icons.rounded.Refresh
import app.nocturne.i18n.t
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.nocturne.AppGraph
import androidx.compose.runtime.produceState
import app.nocturne.model.AccountLibrary
import app.nocturne.model.CollectionRef
import app.nocturne.model.MediaEntry
import app.nocturne.model.Section
import app.nocturne.ui.components.SectionView
import app.nocturne.model.CollectionType
import app.nocturne.model.Source
import app.nocturne.model.Track
import app.nocturne.ui.Screen
import app.nocturne.ui.components.CollectionCard
import app.nocturne.ui.components.MessageBox
import app.nocturne.ui.components.TrackMenuContext
import app.nocturne.ui.components.TrackRow

enum class LibraryTab(val label: String) { ONLINE("Accounts"), SONGS("Liked"), PLAYLISTS("Playlists"), ALBUMS("Albums"), ARTISTS("Artists"), HISTORY("History"), LOCAL("On device"), STATS("Stats") }

val LikedRef = CollectionRef("liked", Source.YOUTUBE_MUSIC, CollectionType.LOCAL, "Liked songs")

@Composable
fun LibraryScreen(contentPadding: PaddingValues) {
    val graph = AppGraph.instance
    val library by graph.library.state.collectAsState()
    var tab by rememberSaveable { mutableStateOf(LibraryTab.ONLINE) }
    var creating by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { graph.syncLikes() } }

    Column(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(t("Library"), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { creating = true }) { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, "New playlist") }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(LibraryTab.entries) { lt -> FilterChip(selected = tab == lt, onClick = { tab = lt }, label = { Text(t(lt.label)) }) }
        }
        val bottom = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 16.dp)
        when (tab) {
            LibraryTab.ONLINE -> OnlineLibrary(bottom)
            LibraryTab.SONGS -> TrackList(library.liked, "Liked songs", bottom, Icons.Rounded.FavoriteBorder, "No liked songs yet", "Tap the heart in the player to save songs here.")
            LibraryTab.HISTORY -> TrackList(library.history.map { it.track }, "History", bottom, Icons.Rounded.History, "Nothing played yet", null,
                action = { if (library.history.isNotEmpty()) IconButton(onClick = { graph.library.clearHistory() }) { Icon(Icons.Rounded.Delete, "Clear history") } })
            LibraryTab.STATS -> StatsView(bottom)
            LibraryTab.LOCAL -> LocalMusic(bottom)
            LibraryTab.PLAYLISTS -> {
                val refs = listOf(LikedRef.copy(subtitle = t("%s songs", library.liked.size), artworkUrl = library.liked.firstOrNull()?.artworkUrl)) +
                    library.playlists.map { it.ref } + library.saved.filter { it.type == CollectionType.PLAYLIST }
                CollectionGrid(refs, bottom)
            }
            LibraryTab.ALBUMS -> {
                val refs = library.saved.filter { it.type == CollectionType.ALBUM }
                if (refs.isEmpty()) MessageBox(Icons.Rounded.LibraryMusic, "No saved albums", "Save albums from their page to find them here.")
                else CollectionGrid(refs, bottom)
            }
            LibraryTab.ARTISTS -> {
                val refs = library.saved.filter { it.type == CollectionType.ARTIST }
                if (refs.isEmpty()) MessageBox(Icons.Rounded.LibraryMusic, "No followed artists", "Follow artists from their page.")
                else CollectionGrid(refs, bottom)
            }
        }
    }

    if (creating) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text(t("New playlist")) },
            text = { OutlinedTextField(name, { name = it }, label = { Text(t("Name")) }, singleLine = true) },
            confirmButton = { TextButton(onClick = { graph.library.createPlaylist(name); creating = false; tab = LibraryTab.PLAYLISTS }) { Text(t("Create")) } },
            dismissButton = { TextButton(onClick = { creating = false }) { Text(t("Cancel")) } },
        )
    }
}

@Composable
private fun TrackList(
    tracks: List<Track>,
    title: String,
    padding: PaddingValues,
    emptyIcon: androidx.compose.ui.graphics.vector.ImageVector,
    emptyTitle: String,
    emptyText: String?,
    action: @Composable () -> Unit = {},
) {
    val player = AppGraph.instance.player
    if (tracks.isEmpty()) {
        MessageBox(emptyIcon, emptyTitle, emptyText)
        return
    }
    LazyColumn(contentPadding = padding) {
        item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = { player.play(tracks, 0, title) }) {
                    Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text(t("Play"))
                }
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = { player.play(tracks, tracks.indices.random(), title, shuffle = true) }) {
                    Icon(Icons.Rounded.Shuffle, null); Spacer(Modifier.width(6.dp)); Text(t("Shuffle"))
                }
                Spacer(Modifier.weight(1f))
                Text("${tracks.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                action()
            }
        }
        items(tracks.size, key = { tracks[it].key }) { i ->
            TrackRow(tracks[i], onClick = { player.play(tracks, i, title) }, modifier = Modifier.padding(horizontal = 4.dp), menuContext = TrackMenuContext())
        }
    }
}

@Composable
private fun CollectionGrid(refs: List<CollectionRef>, padding: PaddingValues) {
    val nav = AppGraph.instance.navigator
    LazyVerticalGrid(columns = GridCells.Adaptive(150.dp), contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = padding.calculateBottomPadding())) {
        items(refs, key = { it.key }) { ref -> CollectionCard(ref, onClick = { nav.push(Screen.Collection(ref)) }, width = null) }
        item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(8.dp)) }
    }
}

private val onlineCache = mutableMapOf<Source, AccountLibrary>()

@Composable
private fun OnlineLibrary(padding: PaddingValues) {
    val graph = AppGraph.instance
    val accounts by graph.accounts.state.collectAsState()
    val providers = graph.enabledProviders().filter { it.isSignedIn }
    if (providers.isEmpty()) {
        MessageBox(
            Icons.Rounded.LibraryMusic,
            "No accounts connected",
            "Sign in to YouTube Music, Spotify or SoundCloud to see your playlists and liked songs here.",
            actionLabel = "Connect accounts",
            onAction = { graph.navigator.push(Screen.SettingsPage(SettingsCategory.ACCOUNTS)) },
        )
        return
    }
    val libs by produceState(providers.mapNotNull { onlineCache[it.source] }, accounts) {
        value = providers.mapNotNull { p ->
            runCatching { p.library() }.getOrNull()?.also { onlineCache[p.source] = it }
        }
    }
    LazyColumn(contentPadding = padding) {
        if (libs.isEmpty()) item { app.nocturne.ui.components.LoadingBox() }
        libs.forEach { lib ->
            val top = listOfNotNull(lib.liked) + lib.playlists
            if (top.isNotEmpty()) item {
                SectionView(Section("${lib.source.label} • ${t("Playlists")}", top.map { MediaEntry.Collection(it) }, source = lib.source)) { _, _ -> }
            }
            if (lib.albums.isNotEmpty()) item {
                SectionView(Section("${lib.source.label} • ${t("Albums")}", lib.albums.map { MediaEntry.Collection(it) }, source = lib.source)) { _, _ -> }
            }
            if (lib.artists.isNotEmpty()) item {
                SectionView(Section("${lib.source.label} • ${t("Artists")}", lib.artists.map { MediaEntry.Collection(it) }, source = lib.source)) { _, _ -> }
            }
        }
    }
}

@Composable
private fun LocalMusic(padding: PaddingValues) {
    val graph = AppGraph.instance
    val tracks by graph.local.tracks.collectAsState()
    var allowed by remember { mutableStateOf(app.nocturne.source.hasLocalAudioAccess()) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    androidx.compose.runtime.LaunchedEffect(allowed) { if (allowed && tracks == null) graph.local.rescan() }
    when {
        !allowed -> Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            MessageBox(Icons.Rounded.LibraryMusic, "Music on this device", "Allow access to play the songs stored on your phone.")
            FilledTonalButton(onClick = { app.nocturne.source.requestLocalAudioAccess { ok -> allowed = ok } }) { Text(t("Allow access")) }
        }
        tracks == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { androidx.compose.material3.CircularProgressIndicator() }
        else -> TrackList(tracks.orEmpty(), t("On device"), padding, Icons.Rounded.LibraryMusic, "No music found",
            if (app.nocturne.platformKind == app.nocturne.PlatformKind.DESKTOP) "Put music into your Music folder or add a folder in Settings → Storage." else "Songs you download or copy to your phone will show up here.",
            action = { IconButton(onClick = { scope.launch { graph.local.rescan() } }) { Icon(Icons.Rounded.Refresh, "Rescan") } })
    }
}
