package app.nocturne.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.nocturne.AppGraph
import app.nocturne.i18n.t
import app.nocturne.model.MediaEntry
import app.nocturne.model.SearchFilter
import app.nocturne.model.Source
import app.nocturne.model.Track
import app.nocturne.ui.kit.CircularProgressIndicator
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** A vibe: title, emoji, gradient and what to ask YouTube Music for. */
class Mood(val title: String, val emoji: String, val a: Long, val b: Long, val query: String)

val Moods = listOf(
    Mood("Chill", "🌊", 0xFF2E8BC0, 0xFF6DD5ED, "chill"),
    Mood("Focus", "🎧", 0xFF3A3F8F, 0xFF8E7CFF, "focus instrumental"),
    Mood("Workout", "🔥", 0xFFE53935, 0xFFFF8A3D, "workout gym"),
    Mood("Party", "🎉", 0xFFD81B9A, 0xFFFF6FD8, "party hits"),
    Mood("Night drive", "🌃", 0xFF1A1A40, 0xFF5B2A86, "night drive phonk"),
    Mood("Sad", "🌧", 0xFF37474F, 0xFF78909C, "sad songs"),
    Mood("Romance", "💗", 0xFFB71C5A, 0xFFFF8FAB, "love songs"),
    Mood("Sleep", "🌙", 0xFF0F2027, 0xFF2C5364, "sleep ambient"),
)

/**
 * Mood mix from the user's own taste: the mood searched together with their most played artists,
 * the plain mood, and radio from the first hits, shuffled together.
 */
suspend fun buildMoodMix(mood: Mood): List<Track> = coroutineScope {
    val g = AppGraph.instance
    val yt = g.provider(Source.YOUTUBE_MUSIC)
    val artists = g.library.state.value.plays.flatMap { it.a.split(", ") }.filter { it.isNotBlank() }
        .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(3).map { it.key }
    val queries = artists.map { "$it ${mood.query}" } + mood.query
    val found = queries.map { q ->
        async { runCatching { yt.search(q, SearchFilter.SONGS).mapNotNull { (it as? MediaEntry.Song)?.track }.take(12) }.getOrDefault(emptyList()) }
    }.awaitAll()
    val generic = found.last()
    val radio = generic.take(2).map { seed -> async { runCatching { yt.related(seed).take(15) }.getOrDefault(emptyList()) } }.awaitAll().flatten()
    (found.dropLast(1).flatten() + generic + radio)
        .filterNot { g.library.isBlocked(it) }
        .distinctBy { it.key }
        .distinctBy { it.title.lowercase() + it.artistNames.lowercase() }
        .shuffled()
        .take(60)
}

@Composable
fun MoodRow(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }
    val player = AppGraph.instance.player
    Column(modifier) {
        Text(t("Moods"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 18.dp, top = 18.dp, bottom = 8.dp))
        val state = rememberLazyListState()
        Box {
            LazyRow(state = state, contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(Moods, key = { it.title }) { m ->
                    Box(
                        Modifier.size(132.dp, 84.dp).clip(RoundedCornerShape(18.dp))
                            .background(Brush.linearGradient(listOf(Color(m.a), Color(m.b))))
                            .clickable(enabled = busy == null) {
                                busy = m.title
                                scope.launch {
                                    val mix = runCatching { buildMoodMix(m) }.getOrDefault(emptyList())
                                    busy = null
                                    if (mix.isNotEmpty()) player.play(mix, 0, t(m.title)) else AppGraph.instance.toast(t("Couldn't build this mix right now"))
                                }
                            }
                            .padding(12.dp),
                    ) {
                        Text(m.emoji, style = MaterialTheme.typography.titleLarge, modifier = Modifier.align(Alignment.TopEnd))
                        Text(t(m.title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.align(Alignment.BottomStart))
                        if (busy == m.title) CircularProgressIndicator(Modifier.align(Alignment.TopStart).size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    }
                }
            }
            EdgeArrows(state)
        }
    }
}
