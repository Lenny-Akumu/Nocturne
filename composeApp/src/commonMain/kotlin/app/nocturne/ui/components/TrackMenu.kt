package app.nocturne.ui.components

import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import app.nocturne.i18n.t
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.LaunchedEffect
import app.nocturne.ui.kit.CircularProgressIndicator
import app.nocturne.ui.kit.FilterChip
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
import app.nocturne.ui.kit.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import app.nocturne.ui.kit.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import app.nocturne.ui.kit.ModalBottomSheet
import app.nocturne.ui.kit.OutlinedTextField
import androidx.compose.material3.Text
import app.nocturne.ui.kit.TextButton
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
import app.nocturne.data.DesignStyle
import app.nocturne.ui.kit.LocalDesignStyle
import app.nocturne.ui.style.glass.GlassMenuGap
import app.nocturne.ui.style.glass.GlassMenuOverlay
import app.nocturne.ui.style.glass.GlassMenuRow
import app.nocturne.ui.style.glass.GlassTouch
import app.nocturne.ui.style.glass.glassTouchTracker

data class TrackMenuContext(
    val playlistId: String? = null,
    val queueIndex: Int? = null,
    val remotePlaylist: CollectionRef? = null,
    val extras: List<MenuExtra> = emptyList(),
)

/** Caller-specific menu entry (e.g. the full player's sleep timer); [label] is shown as given. */
class MenuExtra(val icon: ImageVector, val label: String, val onClick: () -> Unit)

val LocalTrackMenu = staticCompositionLocalOf<(Track, TrackMenuContext) -> Unit> { { _, _ -> } }

// True while the track menu sheet is up (the full player hides its video under sheets).
var trackMenuOpen by mutableStateOf(false)
    private set

fun Track.shareUrl(): String = url ?: when (source) {
    Source.YOUTUBE_MUSIC -> "https://music.youtube.com/watch?v=$id"
    Source.SPOTIFY -> "https://open.spotify.com/track/$id"
    Source.SOUNDCLOUD -> id
    Source.LOCAL -> id
}

@Suppress("DEPRECATION")
@Composable
fun TrackMenuHost(content: @Composable () -> Unit) {
    var target by remember { mutableStateOf<Pair<Track, TrackMenuContext>?>(null) }
    var playlistPicker by remember { mutableStateOf<List<Track>?>(null) }
    val graph = AppGraph.instance
    val library by graph.library.state.collectAsState()
    val clipboard = LocalClipboardManager.current

    // Liquid Glass: iOS context menu growing out of the touch point instead of a bottom sheet.
    val glass = LocalDesignStyle.current == DesignStyle.GLASS
    var closing by remember { mutableStateOf(false) }
    CompositionLocalProvider(LocalTrackMenu provides { t, c -> target = t to c; closing = false }) {
        Box(Modifier.glassTouchTracker()) { content() }
    }
    LaunchedEffect(target != null) { trackMenuOpen = target != null }

    target?.let { (track, ctx) ->
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val close: () -> Unit = { if (glass) { closing = true } else { target = null } }
        val anchor = remember(track, ctx) { GlassTouch.last }
        val body: @Composable () -> Unit = {
            Column(if (glass) Modifier else Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
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
                if (glass) GlassMenuGap() else HorizontalDivider(Modifier.padding(vertical = 6.dp))
                val liked = library.liked.any { it.key == track.key }
                MenuItem(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (liked) "Remove from liked" else "Like") {
                    graph.toggleLike(track); close()
                }
                MenuItem(Icons.Rounded.ThumbDownOffAlt, "Not interested") { graph.notInterested(track); close() }
                // Actions call into AppGraph-scoped objects only: the sheet's own scope dies with it.
                MenuItem(Icons.AutoMirrored.Rounded.PlaylistPlay, "Play next") {
                    close()
                    val wasEmpty = graph.player.state.value.current == null
                    if (graph.player.playNext(listOf(track))) { if (!wasEmpty) graph.toast(t("Will play next")) }
                    else graph.toast(t("Already playing"))
                }
                MenuItem(Icons.AutoMirrored.Rounded.QueueMusic, "Add to queue") {
                    close()
                    val wasEmpty = graph.player.state.value.current == null
                    if (graph.player.addToQueue(listOf(track))) { if (!wasEmpty) graph.toast(t("Added to queue")) }
                    else graph.toast(t("Already playing"))
                }
                MenuItem(Icons.Rounded.Radio, "Start radio") { close(); graph.player.startRadio(track) }
                MenuItem(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add to playlist") { playlistPicker = listOf(track); close() }
                ctx.extras.forEach { e -> MenuItem(e.icon, e.label) { close(); e.onClick() } }
                track.artists.filter { it.id != null }.take(4).forEach { artist ->
                    MenuItem(Icons.Rounded.Person, t("Go to %s", artist.name)) {
                        close()
                        graph.navigator.push(Screen.Collection(CollectionRef(artist.id!!, track.source, CollectionType.ARTIST, artist.name)))
                    }
                }
                if (track.albumId != null && track.album != null) {
                    MenuItem(Icons.Rounded.Album, "Go to album") {
                        close()
                        graph.navigator.push(Screen.Collection(CollectionRef(track.albumId, track.source, CollectionType.ALBUM, track.album, track.artistNames, track.artworkUrl)))
                    }
                }
                if (track.source != app.nocturne.model.Source.LOCAL) {
                    val downloads by graph.downloads.items.collectAsState()
                    if (downloads.containsKey(track.key)) MenuItem(Icons.Rounded.DeleteOutline, "Remove download", destructive = true) { close(); graph.downloads.remove(track) }
                    else MenuItem(Icons.Rounded.Download, "Download") { close(); graph.downloads.enqueue(listOf(track)); graph.toast(t("Downloading “%s”", track.title)) }
                }
                MenuItem(Icons.Rounded.ContentCopy, "Copy link") { clipboard.setText(AnnotatedString(track.shareUrl())); close(); graph.toast(t("Link copied")) }
                ctx.playlistId?.let { pid ->
                    MenuItem(Icons.Rounded.Delete, "Remove from playlist", destructive = true) { graph.library.removeFromPlaylist(pid, track); close() }
                }
                ctx.remotePlaylist?.let { ref ->
                    MenuItem(Icons.Rounded.Delete, "Remove from playlist", destructive = true) { graph.remotePlaylists.remove(ref, track); close() }
                }
                ctx.queueIndex?.let { qi ->
                    MenuItem(Icons.Rounded.Delete, "Remove from queue", destructive = true) { close(); graph.player.removeFromQueue(qi, track) }
                }
            }
        }
        if (glass) GlassMenuOverlay(visible = !closing, anchor = anchor, onDismiss = close, onGone = { target = null; closing = false }) { body() }
        else ModalBottomSheet(onDismissRequest = close, sheetState = sheet) { body() }
    }

    playlistPicker?.let { tracks ->
        AddToPlaylistDialog(tracks, onDismiss = { playlistPicker = null })
    }
}

@Composable
private fun MenuItem(icon: ImageVector, label: String, destructive: Boolean = false, onClick: () -> Unit) {
    if (LocalDesignStyle.current == DesignStyle.GLASS) return GlassMenuRow(icon, t(label), destructive, onClick = onClick)
    ListItem(
        headlineContent = { Text(t(label)) },
        leadingContent = { Icon(icon, null) },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp),
    )
}

@Composable
private fun GroupHeader(source: Source?, label: String) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (source != null) { SourceBadge(source, size = 16.dp); Spacer(Modifier.width(8.dp)) }
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun AddToPlaylistDialog(tracks: List<Track>, onDismiss: () -> Unit) {
    val graph = AppGraph.instance
    val library by graph.library.state.collectAsState()
    val remote by graph.remotePlaylists.state.collectAsState()
    val services = remember { graph.remotePlaylists.services() }
    LaunchedEffect(Unit) { graph.remotePlaylists.refresh() }
    var creating by remember { mutableStateOf(library.playlists.isEmpty() && services.isEmpty()) }
    var target by remember { mutableStateOf(Source.LOCAL) }
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t(if (creating) "New playlist" else "Add to playlist")) },
        text = {
            if (creating) {
                Column {
                    OutlinedTextField(name, { name = it }, label = { Text(t("Name")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (services.isNotEmpty()) {
                        Text(t("Save to"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            (listOf(Source.LOCAL) + services).forEach { s ->
                                FilterChip(
                                    selected = target == s,
                                    onClick = { target = s },
                                    label = { Text(if (s == Source.LOCAL) "Nocturne" else s.label) },
                                    leadingIcon = if (s == Source.LOCAL) null else ({ SourceBadge(s, size = 16.dp) }),
                                )
                            }
                        }
                    }
                }
            } else {
                LazyColumn {
                    item {
                        ListItem(
                            headlineContent = { Text(t("New playlist")) },
                            leadingContent = { Icon(Icons.Rounded.Add, null) },
                            modifier = Modifier.clickable { creating = true },
                        )
                    }
                    if (services.isNotEmpty()) item(key = "h:local") { GroupHeader(null, "Nocturne") }
                    items(library.playlists, key = { "local:" + it.id }) { p ->
                        ListItem(
                            headlineContent = { Text(p.name) },
                            supportingContent = { Text(t("%s songs", p.tracks.size)) },
                            leadingContent = { Artwork(p.tracks.firstOrNull()?.artworkUrl, Modifier.size(44.dp)) },
                            modifier = Modifier.clickable { graph.library.addToPlaylist(p.id, tracks); graph.toast(t("Added to %s", p.name)); onDismiss() },
                        )
                    }
                    services.forEach { s ->
                        item(key = "h:${s.name}") { GroupHeader(s, s.label) }
                        val lists = remote.lists[s]
                        when {
                            lists == null && s in remote.loading -> item(key = "l:${s.name}") {
                                Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                                }
                            }
                            lists.isNullOrEmpty() -> item(key = "e:${s.name}") {
                                Text(
                                    remote.errors[s] ?: t("No editable playlists"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (remote.errors[s] != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                            }
                            else -> items(lists.orEmpty(), key = { it.key }) { ref ->
                                ListItem(
                                    headlineContent = { Text(ref.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    supportingContent = { Text(ref.subtitle ?: ref.source.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    leadingContent = { Artwork(ref.artworkUrl, Modifier.size(44.dp)) },
                                    modifier = Modifier.clickable { graph.remotePlaylists.add(ref, tracks); onDismiss() },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (creating) TextButton(onClick = {
                if (target == Source.LOCAL) graph.library.createPlaylist(name, tracks) else graph.remotePlaylists.create(target, name, tracks)
                onDismiss()
            }) { Text(t("Create")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Cancel")) } },
    )
}
