package app.nocturne.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nocturne.AppGraph
import app.nocturne.data.LibraryData
import app.nocturne.i18n.t
import app.nocturne.model.Track
import app.nocturne.nowMillis
import app.nocturne.ui.kit.Icon

private const val DAY = 86_400_000L

class Capsule(val label: String, val tracks: List<Track>, val artist: String?, val plays: Int)

// The furthest past week we have enough history for: a year, half a year, a season or a month ago.
fun buildCapsule(lib: LibraryData, now: Long, trackFor: (String) -> Track?): Capsule? {
    val spans = listOf(365 to "A year ago this week", 182 to "Half a year ago this week", 91 to "Three months ago this week", 30 to "A month ago this week")
    for ((days, label) in spans) {
        val centre = now - days * DAY
        val window = lib.plays.filter { it.t in (centre - 3 * DAY)..(centre + 4 * DAY) }
        if (window.size < 6) continue
        val top = window.groupingBy { it.k }.eachCount().entries.sortedByDescending { it.value }
            .mapNotNull { trackFor(it.key) }.take(12)
        if (top.size < 3) continue
        val artist = window.mapNotNull { it.a.split(",").firstOrNull()?.trim()?.takeIf { a -> a.isNotEmpty() } }
            .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        return Capsule(label, top, artist, window.size)
    }
    return null
}

/** Shows up during the first week of each month until dismissed. */
@Composable
fun TimeCapsuleCard(modifier: Modifier = Modifier) {
    val graph = AppGraph.instance
    val settings by graph.settings.state.collectAsState()
    val library by graph.library.state.collectAsState()
    val (month, day) = remember { app.nocturne.currentMonthDay() }
    val stamp = "${nowMillis() / (365 * DAY)}-$month"
    if (day > 7 || settings.capsuleDismissed == stamp) return
    val capsule = remember(library.plays.size) { buildCapsule(library, nowMillis(), graph.library::trackFor) } ?: return
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth().clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF3B1E54), Color(0xFF9B3D6B), Color(0xFFE0915A))))
            .border(1.dp, Color.White.copy(alpha = 0.14f), shape)
            .clickable { graph.player.play(capsule.tracks, 0, t("Time capsule")) }
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.HourglassBottom, null, Modifier.size(16.dp), tint = Color.White.copy(alpha = 0.85f))
                    Spacer(Modifier.width(6.dp))
                    Text(t("Time capsule").uppercase(), color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
                Spacer(Modifier.size(6.dp))
                Text(t(capsule.label), color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    listOfNotNull(capsule.artist?.let { t("You couldn't stop playing %s", it) }, t("%s plays that week", capsule.plays)).joinToString(" · "),
                    color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.size(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy((-10).dp)) {
                    capsule.tracks.take(5).forEachIndexed { i, tr ->
                        Artwork(tr.artworkUrl, Modifier.size(40.dp).border(2.dp, Color(0xFF3B1E54), RoundedCornerShape(10.dp)), RoundedCornerShape(10.dp))
                    }
                }
            }
            Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PlayArrow, t("Play"), Modifier.size(28.dp), tint = Color(0xFF3B1E54))
            }
        }
        Box(
            Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-8).dp).size(28.dp).clip(CircleShape)
                .clickable { graph.settings.update { it.copy(capsuleDismissed = stamp) } },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Close, t("Hide"), Modifier.size(16.dp), tint = Color.White.copy(alpha = 0.7f)) }
    }
}
