package app.nocturne.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import app.nocturne.ui.kit.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nocturne.AppGraph
import app.nocturne.i18n.t
import app.nocturne.nowMillis
import app.nocturne.ui.components.Artwork
import app.nocturne.ui.components.MessageBox
import app.nocturne.ui.components.TrackRow

private val periods = listOf("Week" to 7L, "Month" to 30L, "Year" to 365L, "All time" to 0L)

@Composable
fun StatsView(padding: PaddingValues) {
    val graph = AppGraph.instance
    val library by graph.library.state.collectAsState()
    var period by remember { mutableIntStateOf(1) }
    val days = periods[period].second
    val from = if (days == 0L) 0L else nowMillis() - days * 86_400_000L
    val plays = library.plays.filter { it.t >= from }
    if (library.plays.isEmpty()) {
        MessageBox(Icons.Rounded.BarChart, "No stats yet", "Listen to some music and your stats will show up here.")
        return
    }
    val minutes = plays.sumOf { it.d } / 60_000
    val topTracks = plays.groupingBy { it.k }.eachCount().entries.sortedByDescending { it.value }.take(15)
        .mapNotNull { (k, n) -> graph.library.trackFor(k)?.let { it to n } }
    val topArtists = plays.flatMap { p -> p.a.split(", ").filter { it.isNotBlank() }.map { it to p.k } }
        .groupBy { it.first }.entries.sortedByDescending { it.value.size }.take(10)
        .map { (name, list) -> Triple(name, list.size, graph.library.trackFor(list.first().second)?.artworkUrl) }

    LazyColumn(contentPadding = padding) {
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(periods.size) { i -> FilterChip(selected = i == period, onClick = { period = i }, label = { Text(t(periods[i].first)) }) }
            }
        }
        if (days == 365L) item {
            Text(t("Your year in music"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 18.dp, top = 16.dp))
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(t("Minutes"), "$minutes", Modifier.weight(1f))
                StatCard(t("Plays"), "${plays.size}", Modifier.weight(1f))
                StatCard(t("Artists"), "${topArtists.size.coerceAtLeast(plays.map { it.a }.distinct().size)}", Modifier.weight(1f))
            }
        }
        if (topArtists.isNotEmpty()) {
            item { Text(t("Top artists"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 18.dp, top = 8.dp, bottom = 8.dp)) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(topArtists) { (name, count, art) ->
                        Column(Modifier.width(96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Artwork(art, Modifier.size(88.dp), CircleShape)
                            Spacer(Modifier.height(6.dp))
                            Text(name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(t("%s plays", count), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        if (topTracks.isNotEmpty()) {
            item { Text(t("Top tracks"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 18.dp, top = 18.dp, bottom = 4.dp)) }
            items(topTracks.size) { i ->
                val (track, n) = topTracks[i]
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TrackRow(track, onClick = { graph.player.play(topTracks.map { it.first }, i, t("Top tracks")) }, modifier = Modifier.weight(1f).padding(start = 4.dp), index = i + 1)
                    Text("×$n", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 18.dp))
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(14.dp)) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SmartMixes(onPlay: (List<app.nocturne.model.Track>, String) -> Unit) {
    val mixes = smartMixes()
    if (mixes.isEmpty()) return
    Column {
        Text(t("Your mixes"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 18.dp, top = 18.dp, bottom = 8.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(mixes) { (title, tracks) ->
                Column(Modifier.width(150.dp).clip(RoundedCornerShape(18.dp)).clickable { onPlay(tracks, t(title)) }.padding(4.dp)) {
                    Box(Modifier.size(142.dp).clip(RoundedCornerShape(16.dp))) {
                        val arts = tracks.mapNotNull { it.artworkUrl }.distinct().take(4)
                        if (arts.size >= 4) Column {
                            Row { Artwork(arts[0], Modifier.size(71.dp)); Artwork(arts[1], Modifier.size(71.dp)) }
                            Row { Artwork(arts[2], Modifier.size(71.dp)); Artwork(arts[3], Modifier.size(71.dp)) }
                        } else Artwork(arts.firstOrNull(), Modifier.size(142.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(t(title), style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    Text(t("%s songs", tracks.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// Mixes built from listening history: (untranslated title, tracks).
@Composable
fun smartMixes(): List<Pair<String, List<app.nocturne.model.Track>>> {
    val graph = AppGraph.instance
    val library by graph.library.state.collectAsState()
    val now = nowMillis()
    val month = now - 30L * 86_400_000L
    val onRepeat = library.plays.filter { it.t >= month }.groupingBy { it.k }.eachCount().entries
        .sortedByDescending { it.value }.filter { it.value >= 2 }.take(40).mapNotNull { graph.library.trackFor(it.key) }
    val forgotten = library.history.filter { it.playedAt < month && (library.playCounts[it.track.key] ?: 0) >= 3 }
        .sortedByDescending { library.playCounts[it.track.key] ?: 0 }.take(40).map { it.track }
    val likedMix = remember(library.liked.size) { library.liked.shuffled().take(50) }
    val night = remember(library.history.size) {
        library.history.filter { val h = ((it.playedAt / 3_600_000L) % 24); h >= 21 || h <= 4 }.map { it.track }.distinctBy { it.key }.take(40)
    }
    val daily by graph.dailyMixes.state.collectAsState()
    androidx.compose.runtime.LaunchedEffect(Unit) { graph.dailyMixes.refresh() }
    return daily.map { it.title to it.tracks } + listOfNotNull(
        ("On repeat" to onRepeat).takeIf { onRepeat.size >= 4 },
        ("Long time no listen" to forgotten).takeIf { forgotten.size >= 4 },
        ("Liked mix" to likedMix).takeIf { likedMix.size >= 4 },
        ("Late night" to night).takeIf { night.size >= 6 },
    )
}
