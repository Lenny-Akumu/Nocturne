package app.nocturne.ui.screens

import androidx.compose.material.icons.rounded.Download
import app.nocturne.i18n.t
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.MusicOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import app.nocturne.ui.kit.AlertDialog
import app.nocturne.ui.kit.Button
import app.nocturne.ui.kit.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import app.nocturne.ui.kit.OutlinedTextField
import androidx.compose.material3.Text
import app.nocturne.ui.kit.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nocturne.AppGraph
import app.nocturne.model.CollectionPage
import app.nocturne.model.CollectionType
import app.nocturne.model.CollectionRef
import app.nocturne.ui.components.AddToPlaylistDialog
import app.nocturne.ui.components.Artwork
import app.nocturne.ui.components.LoadingBox
import app.nocturne.ui.components.MessageBox
import app.nocturne.ui.components.SectionView
import app.nocturne.ui.components.SourceBadge
import app.nocturne.ui.components.TrackMenuContext
import app.nocturne.ui.components.TrackRow
import app.nocturne.ui.components.fallbackIcon
import app.nocturne.ui.components.shape

private val pageCache = mutableMapOf<String, CollectionPage>()

@Composable
fun CollectionScreen(ref: CollectionRef, contentPadding: PaddingValues) {
    val graph = AppGraph.instance
    val library by graph.library.state.collectAsState()
    var page by remember(ref.key) { mutableStateOf(pageCache[ref.key]) }
    var error by remember(ref.key) { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var addAll by remember { mutableStateOf(false) }

    val local = ref.type == CollectionType.LOCAL
    val localPage = if (!local) null
    else if (ref.id == LikedRef.id) CollectionPage(LikedRef.copy(subtitle = t("%s songs", library.liked.size), artworkUrl = library.liked.firstOrNull()?.artworkUrl), library.liked)
    else library.playlists.firstOrNull { it.id == ref.id }?.let { CollectionPage(it.ref, it.tracks) }
    val remoteRev = graph.remotePlaylists.revisions.collectAsState().value[ref.key] ?: 0
    val remoteLists by graph.remotePlaylists.state.collectAsState()
    val editable = !local && ref.type == CollectionType.PLAYLIST && remoteLists.lists[ref.source]?.any { it.id.removePrefix("VL") == ref.id.removePrefix("VL") } == true
    if (!local && ref.type == CollectionType.PLAYLIST) LaunchedEffect(ref.key) { graph.remotePlaylists.refresh() }
    var loadedRev by remember(ref.key) { mutableIntStateOf(0) }
    if (!local) {
        LaunchedEffect(ref.key, attempt, remoteRev) {
            if (page != null && loadedRev == remoteRev) return@LaunchedEffect
            loadedRev = remoteRev
            error = null
            runCatching { graph.provider(ref.source).collection(ref) }
                .onSuccess { page = it; pageCache[ref.key] = it }
                .onFailure { error = it.message ?: "Failed to load" }
        }
    }

    val p = localPage ?: page
    val header = p?.ref ?: ref
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 16.dp)) {
        item {
            Row(Modifier.padding(top = contentPadding.calculateTopPadding()).padding(horizontal = 4.dp)) {
                IconButton(onClick = { graph.navigator.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                Spacer(Modifier.weight(1f))
                if (local && ref.id != LikedRef.id) {
                    IconButton(onClick = { renaming = true }) { Icon(Icons.Rounded.Edit, "Rename") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, "Delete") }
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Artwork(
                    header.artworkUrl,
                    Modifier.size(220.dp),
                    header.type.shape(),
                    header.type.fallbackIcon(),
                )
                Spacer(Modifier.height(18.dp))
                Text(header.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    if (!local) { SourceBadge(header.source, size = 16.dp); Spacer(Modifier.width(6.dp)) }
                    Text(
                        listOfNotNull(header.subtitle, p?.tracks?.size?.takeIf { it > 0 && !local }?.let { "$it songs" }).joinToString(" • "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                p?.description?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3,
                        overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp).widthIn(max = 520.dp))
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!local) {
                        val saved = library.saved.any { it.key == header.key }
                        FilledTonalIconButton(onClick = { graph.library.toggleSaved(header) }) {
                            Icon(if (saved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, if (saved) "Remove from library" else "Save to library")
                        }
                    }
                    val tracks = p?.tracks.orEmpty()
                    Button(onClick = { graph.player.play(tracks, 0, header.title) }, enabled = tracks.isNotEmpty(), modifier = Modifier.height(52.dp)) {
                        Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text(t("Play"))
                    }
                    FilledTonalIconButton(onClick = { graph.player.play(tracks, tracks.indices.random(), header.title, shuffle = true) }, enabled = tracks.isNotEmpty()) {
                        Icon(Icons.Rounded.Shuffle, "Shuffle")
                    }
                    if (header.source != app.nocturne.model.Source.LOCAL) {
                        FilledTonalIconButton(onClick = { graph.downloads.enqueue(tracks) }, enabled = tracks.isNotEmpty()) {
                            Icon(androidx.compose.material.icons.Icons.Rounded.Download, "Download")
                        }
                    }
                    FilledTonalIconButton(onClick = { graph.player.addToQueue(tracks) }, enabled = tracks.isNotEmpty()) {
                        Icon(Icons.AutoMirrored.Rounded.QueueMusic, "Add to queue")
                    }
                    if (!local) {
                        FilledTonalIconButton(onClick = { addAll = true }, enabled = tracks.isNotEmpty()) {
                            Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add to playlist")
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        when {
            p == null && error != null -> item {
                MessageBox(Icons.Rounded.ErrorOutline, "Couldn't load", error, actionLabel = "Retry", onAction = { attempt++ })
            }
            p == null -> item { LoadingBox() }
            p.tracks.isEmpty() && p.sections.isEmpty() -> item { MessageBox(Icons.Rounded.MusicOff, "No songs here yet") }
            else -> {
                if (header.type == CollectionType.ARTIST && p.tracks.isNotEmpty()) {
                    item { Text(t("Popular"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 18.dp, top = 12.dp, bottom = 4.dp)) }
                }
                items(p.tracks.size, key = { p.tracks[it].key + "#" + it }) { i ->
                    Box(Modifier.padding(horizontal = 4.dp)) {
                        TrackRow(
                            p.tracks[i],
                            onClick = { graph.player.play(p.tracks, i, header.title) },
                            index = if (header.type == CollectionType.ALBUM) i + 1 else null,
                            menuContext = TrackMenuContext(playlistId = if (local && ref.id != LikedRef.id) ref.id else null, remotePlaylist = if (editable) ref.copy(title = header.title) else null),
                        )
                    }
                }
                p.sections.forEach { section ->
                    item { SectionView(section) { list, i -> graph.player.play(list, i) } }
                }
            }
        }
    }

    if (renaming) {
        var name by remember { mutableStateOf(header.title) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text(t("Rename playlist")) },
            text = { OutlinedTextField(name, { name = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { graph.library.renamePlaylist(ref.id, name); renaming = false }) { Text(t("Save")) } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text(t("Cancel")) } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(t("Delete “%s”?", header.title)) },
            text = { Text(t("This playlist will be removed from your library.")) },
            confirmButton = { TextButton(onClick = { graph.library.deletePlaylist(ref.id); confirmDelete = false; graph.navigator.back() }) { Text(t("Delete")) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(t("Cancel")) } },
        )
    }
    if (addAll) {
        AddToPlaylistDialog(p?.tracks.orEmpty(), onDismiss = { addAll = false })
    }
}
