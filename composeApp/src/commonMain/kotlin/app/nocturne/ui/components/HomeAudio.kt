package app.nocturne.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nocturne.AppGraph
import app.nocturne.data.PodcastSub
import app.nocturne.data.Podcasts
import app.nocturne.data.Radio
import app.nocturne.data.RadioStation
import app.nocturne.i18n.t
import app.nocturne.ui.Screen

/** A podcast to open when the Podcasts screen shows up next. */
object PodcastJump {
    var sub by mutableStateOf<PodcastSub?>(null)
    fun take(): PodcastSub? = sub.also { sub = null }
}

// computed once per launch; the home screen recomposes a lot
private object AudioPicks {
    var radio by mutableStateOf<List<RadioStation>?>(null)
    var podcasts by mutableStateOf<List<PodcastSub>?>(null)
}

private fun words(s: String) = s.lowercase().split(" · ", ",", "/").map { it.trim() }.filter { it.length in 2..20 }

/** Stations that match the tags of what you favourite and listen to, from your country. */
private suspend fun radioFor(): List<RadioStation> {
    val s = AppGraph.instance.settings.value
    val country = s.radioCountry.ifBlank { s.contentCountry }.uppercase()
    val known = (s.radioFavorites + s.radioRecent)
    val taste = known.flatMap { words(it.tags) }.groupingBy { it }.eachCount()
    val pool = runCatching { Radio.stations(country) }.getOrDefault(emptyList())
    val fresh = pool.filter { st -> known.none { it.id == st.id } }
    val ranked = if (taste.isEmpty()) fresh else fresh.withIndex().sortedByDescending { (i, st) ->
        words(st.tags).sumOf { taste[it] ?: 0 } * 10 - i / 30
    }.map { it.value }
    return (s.radioFavorites + s.radioRecent).distinctBy { it.id }.take(4) + ranked.take(16)
}

/** Shows about the artists you play most, plus whatever you already follow. */
internal suspend fun podcastsFor(): List<PodcastSub> {
    val graph = AppGraph.instance
    val subs = graph.settings.value.podcasts
    val artists = graph.library.mostPlayed(40).flatMap { it.artistNames.split(",").map { a -> a.trim() } }
        .filter { it.isNotEmpty() }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(3).map { it.key }
    val terms = artists.ifEmpty { listOf("music") } + "music"
    val found = terms.flatMap { runCatching { Podcasts.search(it) }.getOrDefault(emptyList()).take(8) }
    return (subs + found).distinctBy { it.feedUrl }.take(20)
}

@Composable
fun RadioPodcastRows() {
    val graph = AppGraph.instance
    val settings by graph.settings.state.collectAsState()
    LaunchedEffect(settings.radioCountry) { if (AudioPicks.radio == null) AudioPicks.radio = radioFor() }
    LaunchedEffect(Unit) { if (AudioPicks.podcasts == null) AudioPicks.podcasts = podcastsFor() }
    val radio = AudioPicks.radio.orEmpty()
    val pods = AudioPicks.podcasts.orEmpty()
    Column {
        if (radio.isNotEmpty()) {
            RowHeader(t("Radio for you")) { graph.navigator.push(Screen.Radio) }
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(radio, key = { _, st -> st.id }) { i, st ->
                    Tile(st.favicon, st.name, st.tags.split(" · ").take(2).joinToString(" · ").ifBlank { st.country }, true) { Radio.play(radio, i) }
                }
            }
        }
        if (pods.isNotEmpty()) {
            RowHeader(t("Podcasts for you")) { graph.navigator.push(Screen.Podcasts) }
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(pods, key = { _, p -> p.feedUrl }) { _, p ->
                    Tile(p.artworkUrl, p.title, p.author, false) { PodcastJump.sub = p; graph.navigator.push(Screen.Podcasts) }
                }
            }
        }
    }
}

@Composable
private fun RowHeader(title: String, onAll: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(t("See all"), color = cs.primary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAll).padding(8.dp))
    }
}

@Composable
private fun Tile(art: String?, title: String, sub: String, radio: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.width(140.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(4.dp)) {
        Artwork(art, Modifier.size(132.dp), RoundedCornerShape(if (radio) 66.dp else 14.dp), fallback = if (radio) Icons.Rounded.Radio else Icons.Rounded.Podcasts)
        Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
        Text(sub, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
