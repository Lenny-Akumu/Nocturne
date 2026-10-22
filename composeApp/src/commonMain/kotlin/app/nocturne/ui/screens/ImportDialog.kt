package app.nocturne.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import app.nocturne.ui.kit.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.nocturne.AppGraph
import app.nocturne.i18n.t
import app.nocturne.model.CollectionRef
import app.nocturne.model.Source
import app.nocturne.model.Track
import app.nocturne.source.SourceUnavailableException
import app.nocturne.ui.components.Artwork
import app.nocturne.ui.components.SourceBadge
import app.nocturne.ui.kit.AlertDialog
import app.nocturne.ui.kit.CircularProgressIndicator
import app.nocturne.ui.kit.LinearProgressIndicator
import app.nocturne.ui.kit.OutlinedTextField
import app.nocturne.ui.kit.TextButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

private enum class ImportStep { CHOOSE, PICK, WORKING, DONE }

/** "What do you want to import?" — liked songs into Nocturne likes, or a playlist into a local playlist. */
@Composable
fun ImportDialog(source: Source, onDismiss: () -> Unit) {
    val graph = AppGraph.instance
    val provider = graph.provider(source)
    val spotify = source == Source.SPOTIFY
    val signedIn = provider.isSignedIn
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(if (signedIn) ImportStep.CHOOSE else ImportStep.PICK) }
    var status by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf<Pair<Int, Int?>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf("") }
    var playlists by remember { mutableStateOf<List<CollectionRef>?>(null) }
    var link by remember { mutableStateOf("") }
    var job by remember { mutableStateOf<Job?>(null) }
    var retry by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun work(label: String, block: suspend () -> String) {
        retry = { work(label, block) }
        error = null
        progress = null
        status = label
        step = ImportStep.WORKING
        job?.cancel()
        job = scope.launch {
            try {
                result = withTimeout(10 * 60_000L) { block() }
                step = ImportStep.DONE
            } catch (e: TimeoutCancellationException) {
                error = t("Import took too long. Check your connection and try again.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: t("Import failed")
            }
        }
    }

    fun savePlaylist(name: String, tracks: List<Track>): String {
        if (tracks.isEmpty()) throw SourceUnavailableException(t("This playlist is empty or private"))
        graph.library.createPlaylist(name, tracks)
        return t("Imported “%s” — %s songs", name, tracks.size)
    }

    val onProgress: (Int, Int?) -> Unit = { n, total -> progress = n to total }

    fun importLiked() = work(t("Fetching your liked songs…")) {
        val tracks = if (spotify) graph.spotify.importLiked(onProgress) else provider.likedTracks()
        if (tracks.isEmpty()) throw SourceUnavailableException(t("No liked songs found"))
        status = t("Saving…")
        val before = graph.library.state.value.liked.size
        graph.library.mergeLiked(tracks)
        t("Added %s new songs to your liked songs (%s found)", graph.library.state.value.liked.size - before, tracks.size)
    }

    fun importPlaylist(ref: CollectionRef) = work(t("Fetching “%s”…", ref.title)) {
        if (spotify) graph.spotify.importCollection("playlist:${ref.id}", onProgress).let { (name, tracks) -> savePlaylist(name, tracks) }
        else provider.collection(ref).let { savePlaylist(it.ref.title, it.tracks) }
    }

    fun importLink() = work(t("Fetching playlist…")) {
        graph.spotify.importCollection(link, onProgress).let { (name, tracks) -> savePlaylist(name, tracks) }
    }

    LaunchedEffect(step) {
        if (step == ImportStep.PICK && signedIn && playlists == null) {
            playlists = try {
                withTimeout(60_000) { if (spotify) graph.spotify.myPlaylists() else provider.library()?.playlists.orEmpty() }
            } catch (e: CancellationException) {
                if (e is TimeoutCancellationException) { error = t("Couldn't load your playlists. Try again."); emptyList() } else throw e
            } catch (e: Exception) {
                error = e.message ?: t("Couldn't load your playlists. Try again.")
                emptyList()
            }
        }
    }

    val working = step == ImportStep.WORKING && error == null
    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        icon = { SourceBadge(source, size = 32.dp) },
        title = { Text(t("Import from %s", source.label)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (step) {
                    ImportStep.CHOOSE -> {
                        Text(t("What do you want to import from %s?", source.label), style = MaterialTheme.typography.bodyMedium)
                        ImportOption(Icons.Rounded.Favorite, t("Liked songs"), t("Added to your Nocturne liked songs")) { importLiked() }
                        ImportOption(Icons.AutoMirrored.Rounded.QueueMusic, t("A playlist"), t("Saved as a playlist in your library")) { error = null; step = ImportStep.PICK }
                    }
                    ImportStep.PICK -> {
                        if (spotify) {
                            Text(
                                t(if (signedIn) "Pick a playlist or paste a link to any playlist or album." else "Paste a link to a public playlist or album. It's saved to your library as a playlist — no Spotify account needed."),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            OutlinedTextField(link, { link = it.trim() }, singleLine = true, placeholder = { Text("https://open.spotify.com/playlist/…") }, modifier = Modifier.fillMaxWidth())
                        }
                        if (signedIn) {
                            val list = playlists
                            when {
                                list == null -> CircularProgressIndicator(Modifier.size(28.dp).align(Alignment.CenterHorizontally), strokeWidth = 2.dp)
                                list.isEmpty() && error == null -> Text(t("No playlists found"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                else -> Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                                    list.forEach { ref ->
                                        Row(
                                            Modifier.fillMaxWidth().clickable { importPlaylist(ref) }.padding(vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            app.nocturne.ui.components.CollectionArtwork(ref, Modifier.size(40.dp), RoundedCornerShape(6.dp))
                                            Spacer(Modifier.width(12.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(ref.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                                                ref.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    ImportStep.WORKING -> {
                        Text(status, style = MaterialTheme.typography.bodyMedium)
                        val p = progress
                        val total = p?.second?.takeIf { it > 0 }
                        if (error == null) {
                            if (p != null && total != null) LinearProgressIndicator(progress = { p.first.toFloat() / total }, modifier = Modifier.fillMaxWidth())
                            else CircularProgressIndicator(Modifier.size(28.dp).align(Alignment.CenterHorizontally), strokeWidth = 2.dp)
                        }
                        if (p != null) Text(
                            if (total != null) t("%s of %s songs", p.first, total) else t("%s songs", p.first),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    ImportStep.DONE -> Text(result, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            when {
                step == ImportStep.DONE -> TextButton(onClick = onDismiss) { Text(t("Done")) }
                step == ImportStep.WORKING && error != null -> TextButton(onClick = { retry?.invoke() }) { Text(t("Retry")) }
                step == ImportStep.PICK && spotify -> TextButton(onClick = { importLink() }, enabled = Regex("(album|playlist)[/:][A-Za-z0-9]{22}").containsMatchIn(link)) { Text(t("Import")) }
                else -> Unit
            }
        },
        dismissButton = {
            TextButton(onClick = {
                job?.cancel()
                when {
                    step == ImportStep.DONE || !signedIn && step != ImportStep.WORKING -> onDismiss()
                    step == ImportStep.WORKING && signedIn -> { error = null; step = ImportStep.CHOOSE }
                    step == ImportStep.WORKING -> { error = null; step = ImportStep.PICK }
                    step == ImportStep.PICK -> { error = null; step = ImportStep.CHOOSE }
                    else -> onDismiss()
                }
            }) { Text(t(if (step == ImportStep.CHOOSE || step == ImportStep.DONE) "Close" else if (working) "Cancel" else "Back")) }
        },
    )
}

@Composable
private fun ImportOption(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    app.nocturne.ui.kit.Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
