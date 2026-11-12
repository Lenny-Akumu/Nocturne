package app.nocturne.ui.screens

import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.foundation.clickable
import androidx.compose.animation.animateContentSize
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.Search
import app.nocturne.ui.components.rowMotion
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
import app.nocturne.ui.kit.Icon
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
import app.nocturne.ui.components.displayTitle
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
    var sharing by remember { mutableStateOf(false) }
    var covering by remember { mutableStateOf(false) }
    var showAll by remember(ref.key) { mutableStateOf(false) }
    val foldScope = rememberCoroutineScope()

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
                .onFailure { error = it.message ?: t("Failed to load") }
        }
    }

    val p = localPage ?: page
    val header = p?.ref ?: ref
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val playing by graph.player.state.collectAsState()
    val active = p?.let { pg -> playing.current?.key?.let { k -> pg.tracks.indexOfFirst { it.key == k } } }?.takeIf { it >= 0 }
        ?.plus(if (header.type == CollectionType.ARTIST) 3 else 2)
    val topBar: @Composable () -> Unit = {
            Row(Modifier.padding(top = contentPadding.calculateTopPadding()).padding(horizontal = 4.dp)) {
                IconButton(onClick = { graph.navigator.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                Spacer(Modifier.weight(1f))
                if (local && ref.id != LikedRef.id) {
                    IconButton(onClick = { covering = true }) { Icon(Icons.Rounded.Palette, t("Cover")) }
                    IconButton(onClick = { sharing = true }) { Icon(androidx.compose.material.icons.Icons.Rounded.Groups, t("Edit together")) }
                    IconButton(onClick = { renaming = true }) { Icon(Icons.Rounded.Edit, "Rename") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, "Delete") }
                }
            }
    }
    val headerInfo: @Composable () -> Unit = {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                app.nocturne.ui.components.CollectionArtwork(header, Modifier.size(220.dp), header.type.shape())
                Spacer(Modifier.height(18.dp))
                Text(header.displayTitle(), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    if (!local) { SourceBadge(header.source, size = 16.dp); Spacer(Modifier.width(6.dp)) }
                    Text(
                        listOfNotNull(header.subtitle, p?.tracks?.size?.takeIf { it > 0 && !local }?.let { t("%s songs", it) }).joinToString(" • "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                p?.stats?.takeIf { it.isNotEmpty() }?.let { st ->
                    Text(st.joinToString("  •  "), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
                }
                p?.description?.let {
                    var open by remember(header.key) { mutableStateOf(false) }
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = if (open) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp).widthIn(max = 520.dp).clip(MaterialTheme.shapes.small).clickable { open = !open }.animateContentSize())
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
                    Button(onClick = { graph.player.play(tracks, 0, header.displayTitle()) }, enabled = tracks.isNotEmpty(), modifier = Modifier.height(52.dp)) {
                        Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text(t("Play"))
                    }
                    FilledTonalIconButton(onClick = { graph.player.play(tracks, tracks.indices.random(), header.displayTitle(), shuffle = true) }, enabled = tracks.isNotEmpty()) {
                        Icon(Icons.Rounded.Shuffle, "Shuffle")
                    }
                    if (header.source != app.nocturne.model.Source.LOCAL) {
                        FilledTonalIconButton(onClick = { graph.downloads.enqueue(tracks) }, enabled = tracks.isNotEmpty()) {
                            Icon(androidx.compose.material.icons.Icons.Rounded.Download, "Download")
                        }
                    }
                    FilledTonalIconButton(onClick = { val had = graph.player.state.value.current != null; if (graph.player.addToQueue(tracks) && had) graph.toast(app.nocturne.i18n.t("Added to queue")) }, enabled = tracks.isNotEmpty()) {
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
    val splitStyle = app.nocturne.ui.kit.LocalDesignStyle.current.let { it == app.nocturne.data.DesignStyle.LINUX || it == app.nocturne.data.DesignStyle.TERMINAL }
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
    if (splitStyle && maxWidth >= 900.dp) SplitCollection(p, header, error, local, editable, ref, contentPadding, topBar, headerInfo) { attempt++ }
    else {
    app.nocturne.ui.components.JumpBox(listState, active) {
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 16.dp)) {
        item { topBar() }
        item { headerInfo() }
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
                val keys = app.nocturne.ui.components.occurrenceKeys(p.tracks)
                val folds = header.type == CollectionType.ARTIST && p.tracks.size > ARTIST_PREVIEW
                items(if (folds && !showAll) ARTIST_PREVIEW else p.tracks.size, key = { keys[it] }) { i ->
                    Box(rowMotion().padding(horizontal = 4.dp)) {
                        TrackRow(
                            p.tracks[i],
                            onClick = { graph.player.play(p.tracks, i, header.displayTitle()) },
                            index = if (header.type == CollectionType.ALBUM) i + 1 else null,
                            menuContext = TrackMenuContext(playlistId = if (local && ref.id != LikedRef.id) ref.id else null, remotePlaylist = if (editable) ref.copy(title = header.title) else null),
                        )
                    }
                }
                if (folds) item(key = "fold") {
                    TextButton(
                        onClick = {
                            showAll = !showAll
                            if (!showAll) foldScope.launch { listState.animateScrollToItem(2) }
                        },
                        modifier = Modifier.padding(start = 10.dp),
                    ) {
                        Text(if (showAll) t("Collapse") else t("Show all") + " (${p.tracks.size})")
                        Icon(if (showAll) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
                    }
                }
                p.sections.forEach { section ->
                    item { SectionView(section) { list, i -> graph.player.play(list, i) } }
                }
            }
        }
    }
    }
    }
    }

    if (sharing) CoEditDialog(ref.id) { sharing = false }
    if (covering) CoverDialog(ref.id) { covering = false }
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

@Composable
private fun SplitCollection(
    p: CollectionPage?, header: CollectionRef, error: String?, local: Boolean, editable: Boolean, ref: CollectionRef,
    contentPadding: PaddingValues, topBar: @Composable () -> Unit, headerInfo: @Composable () -> Unit, retry: () -> Unit,
) {
    val graph = AppGraph.instance
    var filter by remember(ref.key) { mutableStateOf("") }
    Row(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding()), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        Column(Modifier.width(340.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(bottom = contentPadding.calculateBottomPadding() + 16.dp)) {
            topBar()
            headerInfo()
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            val all = p?.tracks.orEmpty()
            if (all.size > 8) OutlinedTextField(
                filter, { filter = it }, singleLine = true, placeholder = { Text(t("Find in playlist")) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                modifier = Modifier.fillMaxWidth().padding(end = 12.dp, top = 8.dp, bottom = 6.dp),
            )
            val q = filter.trim()
            val shown = if (q.isEmpty()) all.indices.toList() else all.indices.filter { all[it].title.contains(q, true) || all[it].artistNames.contains(q, true) }
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
            val playing by graph.player.state.collectAsState()
            val active = playing.current?.key?.let { k -> shown.indexOfFirst { all[it].key == k } }?.takeIf { it >= 0 }
            when {
                p == null && error != null -> MessageBox(Icons.Rounded.ErrorOutline, "Couldn't load", error, actionLabel = "Retry", onAction = retry)
                p == null -> LoadingBox()
                all.isEmpty() && p.sections.isEmpty() -> MessageBox(Icons.Rounded.MusicOff, "No songs here yet")
                else -> app.nocturne.ui.components.JumpBox(listState, active) {
                    val keys = app.nocturne.ui.components.occurrenceKeys(all)
                    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 16.dp)) {
                        items(shown.size, key = { keys[shown[it]] }) { n ->
                            val i = shown[n]
                            TrackRow(
                                all[i],
                                onClick = { graph.player.play(all, i, header.displayTitle()) },
                                index = if (header.type == CollectionType.ALBUM) i + 1 else null,
                                menuContext = TrackMenuContext(playlistId = if (local && ref.id != LikedRef.id) ref.id else null, remotePlaylist = if (editable) ref.copy(title = header.title) else null),
                            )
                        }
                        if (q.isEmpty()) p.sections.forEach { section ->
                            item { SectionView(section) { list, i -> graph.player.play(list, i) } }
                        }
                    }
                }
            }
        }
    }
}

/** Makes a cover from the songs or a gradient with the playlist's name. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CoverDialog(playlistId: String, onDismiss: () -> Unit) {
    val graph = AppGraph.instance
    val library by graph.library.state.collectAsState()
    val p = library.playlists.firstOrNull { it.id == playlistId } ?: return
    var seed by remember { mutableStateOf(kotlin.random.Random.nextInt(0, 1000)) }
    val options = remember(p.name, p.tracks.size, seed) {
        val r = kotlin.random.Random(seed)
        listOfNotNull(app.nocturne.ui.components.mosaicCover(p.tracks.map { it.artworkUrl })) +
            (0 until 24).shuffled(r).take(5).map { app.nocturne.ui.components.gradientCover(it, p.name) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Palette, null) },
        title = { Text(t("Playlist cover")) },
        text = {
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                options.forEach { spec ->
                    val on = p.cover == spec
                    app.nocturne.ui.components.Artwork(
                        spec,
                        Modifier.size(96.dp)
                            .then(if (on) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium) else Modifier)
                            .clip(MaterialTheme.shapes.medium)
                            .clickable { graph.library.setPlaylistCover(playlistId, spec) },
                        MaterialTheme.shapes.medium,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t("Done")) } },
        dismissButton = {
            Row {
                if (p.cover != null) TextButton(onClick = { graph.library.setPlaylistCover(playlistId, null) }) { Text(t("First song")) }
                TextButton(onClick = { seed++ }) { Text(t("More")) }
            }
        },
    )
}

/** Share a playlist for editing together: the code, and a way to stop. */
@Composable
private fun CoEditDialog(playlistId: String, onDismiss: () -> Unit) {
    val graph = AppGraph.instance
    val library by graph.library.state.collectAsState()
    val code = library.playlists.firstOrNull { it.id == playlistId }?.shareCode
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(androidx.compose.material.icons.Icons.Rounded.Groups, null) },
        title = { Text(t("Edit together")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (code == null) Text(t("Friends with the code see this playlist and can add or remove songs. Changes show up for everyone right away."))
                else {
                    Text(t("Share this code. Whoever joins with it edits the same playlist."))
                    Text(
                        code.chunked(4).joinToString("-"), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        modifier = Modifier.clickable { clipboard.setText(androidx.compose.ui.text.AnnotatedString(code)); graph.toast(t("Code copied")) },
                    )
                }
            }
        },
        confirmButton = {
            if (code == null) TextButton(onClick = { graph.coPlaylists.share(playlistId) }) { Text(t("Share")) }
            else TextButton(onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(code)); graph.toast(t("Code copied")) }) { Text(t("Copy code")) }
        },
        dismissButton = {
            if (code != null) TextButton(onClick = { graph.coPlaylists.stopSharing(playlistId); onDismiss() }) { Text(t("Stop sharing")) }
            else TextButton(onClick = onDismiss) { Text(t("Close")) }
        },
    )
}

private const val ARTIST_PREVIEW = 5
