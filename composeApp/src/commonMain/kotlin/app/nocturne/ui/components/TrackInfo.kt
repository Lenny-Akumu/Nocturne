package app.nocturne.ui.components

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.nocturne.AppGraph
import app.nocturne.i18n.t
import app.nocturne.model.Track
import app.nocturne.model.TrackDetails
import app.nocturne.model.formatDuration
import app.nocturne.ui.kit.AlertDialog
import app.nocturne.ui.kit.TextButton

@Composable
fun TrackInfoDialog(track: Track, onDismiss: () -> Unit) {
    val graph = AppGraph.instance
    val library by graph.library.state.collectAsState()
    // null = loading; a failed lookup still shows what we know locally
    val details by produceState<Result<TrackDetails?>?>(null, track.key) {
        value = runCatching { graph.provider(track.source).details(track) }
    }
    val d = details?.getOrNull()
    val mine = library.plays.filter { it.k == track.key }
    val rows = buildList {
        add(t("Source") to track.source.label)
        (d?.album ?: track.album)?.let { add(t("Album") to it) }
        d?.trackNumber?.let { add(t("Track number") to "$it") }
        track.durationMs.takeIf { it > 0 }?.let { add(t("Duration") to it.formatDuration()) }
        d?.released?.let { add(t("Released") to releaseDate(it)) }
        d?.genre?.let { add(t("Genre") to it) }
        d?.label?.let { add(t("Label") to it) }
        if (track.explicit) add(t("Explicit") to t("Yes"))
        d?.plays?.let { add(t(if (track.source == app.nocturne.model.Source.YOUTUBE_MUSIC) "Views" else "Plays") to grouped(it)) }
        d?.likes?.let { add(t("Likes") to grouped(it)) }
        d?.reposts?.let { add(t("Reposts") to grouped(it)) }
        d?.comments?.let { add(t("Comments") to grouped(it)) }
        d?.credits?.forEach { (role, name) -> add(t(role) to name) }
    }
    val yours = buildList {
        add(t("Your plays") to "${maxOf(library.playCounts[track.key] ?: 0, mine.size)}")
        mine.minOfOrNull { it.t }?.let { add(t("First played") to dayOf(it)) }
        mine.maxOfOrNull { it.t }?.let { add(t("Last played") to dayOf(it)) }
        add(t("In Liked") to t(if (library.liked.any { it.key == track.key }) "Yes" else "No"))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Artwork(track.artworkUrl, Modifier.size(52.dp), RoundedCornerShape(10.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(track.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                    Text(track.artistNames, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
            }
        },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                rows.forEach { (k, v) -> InfoRow(k, v) }
                if (details == null) Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                } else if (d == null) Text(
                    t("The service didn't share more about this song."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(t("You"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 2.dp))
                yours.forEach { (k, v) -> InfoRow(k, v) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t("Close")) } },
    )
}

@Composable
private fun InfoRow(key: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(key, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.42f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, textAlign = TextAlign.End, modifier = Modifier.weight(0.58f))
    }
}

/** 3083808629 -> "3 083 808 629" */
internal fun grouped(n: Long): String = n.toString().reversed().chunked(3).joinToString(" ").reversed()

/** "2017-03-07" -> "07.03.2017", "2017-03" -> "03.2017", "2017" stays. */
internal fun releaseDate(s: String): String {
    val p = s.take(10).split('-')
    return when {
        p.size >= 3 && p[0].length == 4 -> "${p[2]}.${p[1]}.${p[0]}"
        p.size == 2 && p[0].length == 4 -> "${p[1]}.${p[0]}"
        else -> s
    }
}

// epoch millis -> dd.mm.yyyy (UTC; close enough for "when did I first hear it")
internal fun dayOf(ms: Long): String {
    val z = ms / 86_400_000L + 719_468
    val era = (if (z >= 0) z else z - 146_096) / 146_097
    val doe = z - era * 146_097
    val yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val day = doy - (153 * mp + 2) / 5 + 1
    val month = if (mp < 10) mp + 3 else mp - 9
    val year = yoe + era * 400 + if (month <= 2) 1 else 0
    return "${day.toString().padStart(2, '0')}.${month.toString().padStart(2, '0')}.$year"
}
