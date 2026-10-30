package app.nocturne.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nocturne.AppGraph
import app.nocturne.data.Episode
import app.nocturne.data.PodcastSub
import app.nocturne.data.Podcasts
import app.nocturne.i18n.t
import app.nocturne.model.formatDuration
import app.nocturne.ui.components.Artwork
import app.nocturne.ui.components.LoadingBox
import app.nocturne.ui.components.MessageBox
import app.nocturne.ui.kit.CircularProgressIndicator
import app.nocturne.ui.kit.Icon
import kotlinx.coroutines.launch

@Composable
fun PodcastsView(padding: PaddingValues) {
    val graph = AppGraph.instance
    val settings by graph.settings.state.collectAsState()
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf<PodcastSub?>(null) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<PodcastSub>?>(null) }
    var searching by remember { mutableStateOf(false) }

    open?.let { sub -> PodcastPage(sub, padding) { open = null }; return }

    LazyColumn(contentPadding = padding) {
        item {
            OutlinedTextField(
                query, { query = it }, singleLine = true, placeholder = { Text(t("Find a podcast")) },
                trailingIcon = {
                    IconButton(onClick = {
                        if (query.isBlank()) return@IconButton
                        searching = true
                        scope.launch { results = Podcasts.search(query.trim()); searching = false }
                    }) { Icon(Icons.Rounded.Search, t("Search")) }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(t("Podcast speed"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 8.dp))
                LazyRow {
                    items(listOf(0.8f, 1f, 1.25f, 1.5f, 1.75f, 2f)) { v ->
                        FilterChip(settings.podcastSpeed == v, { graph.settings.update { it.copy(podcastSpeed = v) }; graph.player.applyAudioSettings() },
                            label = { Text("${v}x".replace(".0x", "x")) }, modifier = Modifier.padding(end = 6.dp))
                    }
                }
            }
        }
        if (searching) item { LoadingBox() }
        results?.let { list ->
            item { Text(t("Results"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 18.dp, top = 12.dp, bottom = 4.dp)) }
            if (list.isEmpty()) item { MessageBox(Icons.Rounded.Podcasts, "Nothing found", null) }
            items(list, key = { "r" + it.feedUrl }) { p -> ShowRow(p, settings.podcasts.any { it.feedUrl == p.feedUrl }) { open = p } }
        }
        item { Text(t("Subscriptions"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 18.dp, top = 16.dp, bottom = 4.dp)) }
        if (settings.podcasts.isEmpty()) item { MessageBox(Icons.Rounded.Podcasts, "No podcasts yet", "Search for a show and subscribe to it.") }
        items(settings.podcasts, key = { "s" + it.feedUrl }) { p -> ShowRow(p, true) { open = p } }
    }
}

@Composable
private fun ShowRow(p: PodcastSub, subscribed: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Artwork(p.artworkUrl, Modifier.size(56.dp), RoundedCornerShape(12.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(p.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(p.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (subscribed) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun PodcastPage(sub: PodcastSub, padding: PaddingValues, onBack: () -> Unit) {
    val graph = AppGraph.instance
    val settings by graph.settings.state.collectAsState()
    val player by graph.player.state.collectAsState()
    val scope = rememberCoroutineScope()
    var episodes by remember(sub.feedUrl) { mutableStateOf<List<Episode>?>(null) }
    LaunchedEffect(sub.feedUrl) { episodes = Podcasts.episodes(sub) }
    val subscribed = settings.podcasts.any { it.feedUrl == sub.feedUrl }
    LazyColumn(contentPadding = padding) {
        item {
            Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t("Back")) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { scope.launch { episodes = null; episodes = Podcasts.episodes(sub, refresh = true) } }) { Icon(Icons.Rounded.Refresh, t("Refresh")) }
            }
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(sub.artworkUrl, Modifier.size(104.dp), RoundedCornerShape(18.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(sub.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(sub.author, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    Spacer(Modifier.height(8.dp))
                    FilterChip(subscribed, {
                        graph.settings.update { s -> s.copy(podcasts = if (subscribed) s.podcasts.filterNot { it.feedUrl == sub.feedUrl } else s.podcasts + sub) }
                    }, label = { Text(if (subscribed) t("Subscribed") else t("Subscribe")) }, leadingIcon = { Icon(if (subscribed) Icons.Rounded.Check else Icons.Rounded.Add, null, Modifier.size(18.dp)) })
                }
            }
        }
        val list = episodes
        when {
            list == null -> item { LoadingBox() }
            list.isEmpty() -> item { MessageBox(Icons.Rounded.Podcasts, "No episodes", "This feed has no playable episodes.") }
            else -> items(list.size, key = { list[it].guid }) { i ->
                val e = list[i]
                val playing = player.current?.id == e.url
                val done = Podcasts.progress(e)
                Column(Modifier.fillMaxWidth().clickable { Podcasts.play(sub, list, i) }.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(e.published, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(e.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    if (e.summary.isNotBlank()) Text(e.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (e.durationMs > 0) Text(e.durationMs.formatDuration(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (done > 0f) {
                            Spacer(Modifier.width(10.dp))
                            LinearProgressIndicator(progress = { done }, modifier = Modifier.width(80.dp).height(3.dp).clip(RoundedCornerShape(2.dp)))
                        }
                    }
                }
            }
        }
    }
}
