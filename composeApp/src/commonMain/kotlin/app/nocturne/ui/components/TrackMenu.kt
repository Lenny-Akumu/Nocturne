package app.nocturne.ui.components

import app.nocturne.i18n.t
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.ThumbDownOffAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nocturne.AppGraph
import app.nocturne.model.CollectionRef
import app.nocturne.model.CollectionType
import app.nocturne.model.Source
import app.nocturne.model.Track
import app.nocturne.ui.Screen

data class TrackMenuContext(val playlistId: String? = null, val queueIndex: Int? = null)

val LocalTrackMenu = staticCompositionLocalOf<(Track, TrackMenuContext) -> Unit> { { _, _ -> } }

fun Track.shareUrl(): String = url ?: when (source) {
    Source.YOUTUBE_MUSIC -> "https://music.youtube.com/watch?v=$id"
    Source.SPOTIFY -> "https://open.spotify.com/track/$id"
    Source.SOUNDCLOUD -> id
}

@Suppress("DEPRECATION")
@Composable
fun TrackMenuHost(content: @Composable () -> Unit) {
    var target by remember { mutableStateOf<Pair<Track, TrackMenuContext>?>(null) }
    var playlistPicker by remember { mutableStateOf<List<Track>?>(null) }
    val graph = AppGraph.instance
    val library by graph.library.state.collectAsState()
    val clipboard = LocalClipboardManager.current

    CompositionLocalProvider(LocalTrackMenu provides { t, c -> target = t to c }) { content() }

    target?.let { (track, ctx) ->
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val close = { target = null }
        ModalBottomSheet(onDismissRequest = close, sheetState = sheet) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Artwork(track.artworkUrl, Modifier.size(56.dp), RoundedCornerShape(10.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(track.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SourceBadge(track.source, size = 14.dp)
                            Spacer(Modifier.width(6.dp))
                            Text(track.artistNames, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                val liked = library.liked.any { it.key == track.key }
                MenuItem(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (liked) "Remove from liked" else "Like") {
                    graph.toggleLike(track); close()
                }
                MenuItem(Icons.Rounded.ThumbDownOffAlt, "Not interested") { graph.notInterested(track); close() }
                MenuItem(Icons.AutoMirrored.Rounded.PlaylistPlay, "Play next") { graph.player.playNext(listOf(track)); close() }
                MenuItem(Icons.AutoMirrored.Rounded.QueueMusic, "Add to queue") { graph.player.addToQueue(listOf(track)); close() }
                MenuItem(Icons.Rounded.Radio, "Start radio") { graph.player.startRadio(track); close() }
                MenuItem(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add to playlist") { playlistPicker = listOf(track); close() }
                track.artists.firstOrNull { it.id != null }?.let { artist ->
                    MenuItem(Icons.Rounded.Person, t("Go to %s", artist.name)) {
                        graph.navigator.push(Screen.Collection(CollectionRef(artist.id!!, track.source, CollectionType.ARTIST, artist.name)))
                        close()
                    }
                }
                if (track.albumId != null && track.album != null) {
                    MenuItem(Icons.Rounded.Album, "Go to album") {
                        graph.navigator.push(Screen.Collection(CollectionRef(track.albumId, track.source, CollectionType.ALBUM, track.album, track.artistNames, track.artworkUrl)))
                        close()
                    }
                }
                MenuItem(Icons.Rounded.ContentCopy, "Copy link") { clipboard.setText(AnnotatedString(track.shareUrl())); close() }
                ctx.playlistId?.let { pid ->
                    MenuItem(Icons.Rounded.Delete, "Remove from playlist") { graph.library.removeFromPlaylist(pid, track); close() }
                }
                ctx.queueIndex?.let { qi ->
                    MenuItem(Icons.Rounded.Delete, "Remove from queue") { graph.player.removeAt(qi); close() }
                }
            }
        }
    }

    playlistPicker?.let { tracks ->
        AddToPlaylistDialog(tracks, onDismiss = { playlistPicker = null })
    }
}

@Composable
private fun MenuItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(t(label)) },
        leadingContent = { Icon(icon, null) },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp),
    )
}

@Composable
fun AddToPlaylistDialog(tracks: List<Track>, onDismiss: () -> Unit) {
    val graph = AppGraph.instance
    val library by graph.library.state.collectAsState()
    var creating by remember { mutableStateOf(library.playlists.isEmpty()) }
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t(if (creating) "New playlist" else "Add to playlist")) },
        text = {
            if (creating) {
                OutlinedTextField(name, { name = it }, label = { Text(t("Name")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            } else {
                LazyColumn {
                    item {
                        ListItem(
                            headlineContent = { Text(t("New playlist")) },
                            leadingContent = { Icon(Icons.Rounded.Add, null) },
                            modifier = Modifier.clickable { creating = true },
                        )
                    }
                    items(library.playlists, key = { it.id }) { p ->
                        ListItem(
                            headlineContent = { Text(p.name) },
                            supportingContent = { Text(t("%s songs", p.tracks.size)) },
                            leadingContent = { Artwork(p.tracks.firstOrNull()?.artworkUrl, Modifier.size(44.dp)) },
                            modifier = Modifier.clickable { graph.library.addToPlaylist(p.id, tracks); onDismiss() },
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (creating) TextButton(onClick = { graph.library.createPlaylist(name, tracks); onDismiss() }) { Text(t("Create")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Cancel")) } },
    )
}
