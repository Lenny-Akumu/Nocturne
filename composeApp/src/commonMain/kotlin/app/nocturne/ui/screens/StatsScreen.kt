package app.nocturne.ui.screens

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.draw.blur
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.sp
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
fun StatsView(padding: PaddingValues, header: androidx.compose.foundation.lazy.LazyListScope.() -> Unit = {}) {
    val graph = AppGraph.instance
    val library by graph.library.state.collectAsState()
    var period by remember { mutableIntStateOf(1) }
    val days = periods[period].second
    val from = if (days == 0L) 0L else nowMillis() - days * 86_400_000L
    val plays = library.plays.filter { it.t >= from }
    if (library.plays.isEmpty()) {
        LazyColumn(contentPadding = padding) {
            header()
            item { MessageBox(Icons.Rounded.BarChart, "No stats yet", "Listen to some music and your stats will show up here.") }
        }
        return
    }
    val minutes = plays.sumOf { it.d } / 60_000
    val topTracks = plays.groupingBy { it.k }.eachCount().entries.sortedByDescending { it.value }.take(15)
        .mapNotNull { (k, n) -> graph.library.trackFor(k)?.let { it to n } }
    val topArtists = plays.flatMap { p -> p.a.split(", ").filter { it.isNotBlank() }.map { it to p.k } }
        .groupBy { it.first }.entries.sortedByDescending { it.value.size }.take(10)
        .map { (name, list) -> Triple(name, list.size, graph.library.trackFor(list.first().second)?.artworkUrl) }

    LazyColumn(contentPadding = padding) {
        header()
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(periods.size) { i -> FilterChip(selected = i == period, onClick = { period = i }, label = { Text(t(periods[i].first)) }) }
            }
        }
        item { HeroCard(periods[period].first, minutes, plays.size, topTracks.firstOrNull()) }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(t("Day streak"), "${streak(library.plays)}", Modifier.weight(1f))
                StatCard(t("Artists"), "${plays.flatMap { it.a.split(", ") }.filter { it.isNotBlank() }.distinct().size}", Modifier.weight(1f))
                StatCard(t("New artists"), "${newArtists(library.plays, from)}", Modifier.weight(1f))
            }
        }
        item { ActivityChart(plays, days) }
        item { ListeningClock(plays) }
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

// Local offset from the device clock (minutes), so hours and days follow the user's time zone.
private fun tzOffsetMin(): Long {
    val utc = (nowMillis() / 60_000L) % 1440
    var d = app.nocturne.minuteOfDay() - utc
    if (d > 840) d -= 1440
    if (d < -720) d += 1440
    return d
}

private fun localDay(t: Long, off: Long) = (t / 60_000L + off) / 1440
private fun localHour(t: Long, off: Long) = (((t / 60_000L + off) % 1440 + 1440) % 1440 / 60).toInt()

private fun streak(plays: List<app.nocturne.data.PlayEvent>): Int {
    val off = tzOffsetMin()
    val days = plays.map { localDay(it.t, off) }.toSet()
    var d = localDay(nowMillis(), off)
    if (d !in days) d -= 1
    var n = 0
    while (d in days) { n++; d-- }
    return n
}

private fun newArtists(plays: List<app.nocturne.data.PlayEvent>, from: Long): Int {
    if (from == 0L) return 0
    val before = plays.filter { it.t < from }.flatMap { it.a.split(", ") }.toSet()
    return plays.filter { it.t >= from }.flatMap { it.a.split(", ") }.filter { it.isNotBlank() && it !in before }.distinct().size
}

@Composable
private fun HeroCard(period: String, minutes: Long, plays: Int, top: Pair<app.nocturne.model.Track, Int>?) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp).height(190.dp).clip(RoundedCornerShape(26.dp)).background(cs.primaryContainer)) {
        top?.first?.artworkUrl?.let { art ->
            Artwork(art, Modifier.matchParentSize().blur(28.dp))
        }
        Box(Modifier.matchParentSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.15f), androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.65f)))))
        Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
            Text(t(period).uppercase(), style = MaterialTheme.typography.labelMedium, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.75f))
            Text(t("%s minutes of music", minutes), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = androidx.compose.ui.graphics.Color.White)
            Text(t("%s plays", plays) + (top?.let { " · " + t("#1: %s", it.first.title) } ?: ""), style = MaterialTheme.typography.bodyMedium, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        top?.first?.artworkUrl?.let { art ->
            Artwork(art, Modifier.align(Alignment.TopEnd).padding(16.dp).size(84.dp).clip(RoundedCornerShape(16.dp)))
        }
    }
}

@Composable
private fun Bars(values: List<Float>, labels: List<String>, highlight: Int, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val max = values.maxOrNull()?.takeIf { it > 0f } ?: 1f
    Column(modifier) {
        Row(Modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
            values.forEachIndexed { i, v ->
                val h by androidx.compose.animation.core.animateFloatAsState((v / max).coerceIn(0.02f, 1f), androidx.compose.animation.core.tween(600, delayMillis = i * 12), label = "bar")
                Box(Modifier.weight(1f).fillMaxHeight(h).clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                    .background(if (i == highlight) cs.primary else cs.primary.copy(alpha = 0.35f)))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            labels.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1) }
        }
    }
}

@Composable
private fun ActivityChart(plays: List<app.nocturne.data.PlayEvent>, days: Long) {
    if (plays.isEmpty()) return
    val off = tzOffsetMin()
    val today = localDay(nowMillis(), off)
    val n = when (days) { 7L -> 7; 30L -> 30; else -> 12 }
    val values: List<Float>
    val labels: List<String>
    if (n == 12) {
        // last 12 months, 30-day buckets
        values = (11 downTo 0).map { k -> plays.filter { (today - localDay(it.t, off)) / 30 == k.toLong() }.sumOf { it.d }.toFloat() }
        labels = (11 downTo 0).map { if (it % 3 == 0) "-${it}" else "" }
    } else {
        values = (n - 1 downTo 0).map { k -> plays.filter { today - localDay(it.t, off) == k.toLong() }.sumOf { it.d }.toFloat() }
        labels = (n - 1 downTo 0).map { if (n == 7 || it % 5 == 0) (if (it == 0) t("Today") else "-$it") else "" }
    }
    val best = values.indices.maxByOrNull { values[it] } ?: -1
    Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
        Text(t("Activity"), style = MaterialTheme.typography.titleLarge)
        Text(t("Minutes listened"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Bars(values, labels, best, Modifier.fillMaxWidth().padding(top = 10.dp))
    }
}

@Composable
private fun ListeningClock(plays: List<app.nocturne.data.PlayEvent>) {
    if (plays.size < 5) return
    val off = tzOffsetMin()
    val hours = IntArray(24)
    plays.forEach { hours[localHour(it.t, off)]++ }
    val peak = hours.indices.maxByOrNull { hours[it] } ?: 0
    val persona = when (peak) {
        in 5..10 -> "Early bird"
        in 11..16 -> "Daytime listener"
        in 17..21 -> "Evening viber"
        else -> "Night owl"
    }
    Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
        Text(t("When you listen"), style = MaterialTheme.typography.titleLarge)
        Text(t("You're a %s · most plays around %s:00", t(persona), peak), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Bars(hours.map { it.toFloat() }, (0 until 24).map { if (it % 6 == 0) "$it" else "" }, peak, Modifier.fillMaxWidth().padding(top = 10.dp))
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

@Composable
internal fun AchievementsRow() {
    val settings by AppGraph.instance.settings.state.collectAsState()
    val got = settings.achievements
    val list = app.nocturne.data.Achievements.all.sortedByDescending { got[it.id] ?: -1L }
    val cs = MaterialTheme.colorScheme
    Text(
        t("Achievements") + "  ${got.keys.count { k -> list.any { it.id == k } }}/${list.size}",
        style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 18.dp, top = 18.dp, bottom = 8.dp),
    )
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(list, key = { it.id }) { a ->
            val on = a.id in got
            Column(
                Modifier.width(132.dp).height(150.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                    .background(if (on) cs.primaryContainer else cs.surfaceContainerHigh.copy(alpha = 0.6f))
                    .padding(12.dp),
            ) {
                Text(if (on || !a.secret) a.emoji else "❔", fontSize = 30.sp, modifier = Modifier.alpha(if (on) 1f else 0.35f))
                Spacer(Modifier.height(8.dp))
                Text(if (on || !a.secret) t(a.title) else t("Secret"), style = MaterialTheme.typography.titleSmall,
                    color = if (on) cs.onPrimaryContainer else cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(if (a.secret && !on) t("Find it yourself") else t(a.hint), style = MaterialTheme.typography.bodySmall,
                    color = (if (on) cs.onPrimaryContainer else cs.onSurfaceVariant).copy(alpha = 0.8f), maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
