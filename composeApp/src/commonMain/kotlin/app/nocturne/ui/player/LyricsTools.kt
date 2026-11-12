package app.nocturne.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import coil3.compose.LocalPlatformContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.nocturne.AppGraph
import app.nocturne.i18n.t
import app.nocturne.model.Track
import app.nocturne.source.LyricLine
import app.nocturne.source.Lyrics
import app.nocturne.model.formatDuration
import app.nocturne.ui.encodePng
import app.nocturne.ui.kit.Icon
import app.nocturne.ui.shareImage
import app.nocturne.ui.theme.softwareOnly
import app.nocturne.ui.textInput
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import kotlinx.coroutines.launch

/** Tap along with the song to stamp when each line starts. */
@Composable
fun LyricsTimingDialog(track: Track, lyrics: Lyrics?, onDismiss: () -> Unit) {
    val graph = AppGraph.instance
    val state by graph.player.state.collectAsState()
    val source = remember(lyrics) {
        lyrics?.synced?.takeIf { it.isNotEmpty() }?.map { it.text }
            ?: lyrics?.plain?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?: emptyList()
    }
    var paste by remember { mutableStateOf("") }
    val lines = remember { mutableStateListOf<String>().apply { addAll(source) } }
    val times = remember { mutableStateListOf<Long?>().apply { repeat(source.size) { add(null) } } }
    var next by remember { mutableIntStateOf(0) }
    val list = rememberLazyListState()
    LaunchedEffect(next) { if (lines.isNotEmpty()) list.animateScrollToItem((next - 2).coerceAtLeast(0)) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(0.94f).heightIn(max = 720.dp),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(t("Sync lyrics"), style = MaterialTheme.typography.titleLarge)
                Text(
                    t("Play the song and tap the button the moment each line starts. Your timing replaces what was found."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                if (lines.isEmpty()) {
                    OutlinedTextField(
                        paste, { paste = it },
                        label = { Text(t("Paste the lyrics, one line per row")) },
                        modifier = Modifier.fillMaxWidth().height(260.dp).textInput(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text(t("Cancel")) }
                        Button(enabled = paste.isNotBlank(), onClick = {
                            val l = paste.lines().map { it.trim() }.filter { it.isNotEmpty() }
                            lines.addAll(l); repeat(l.size) { times.add(null) }
                        }) { Text(t("Next")) }
                    }
                    return@Column
                }
                LazyColumn(state = list, modifier = Modifier.weight(1f, fill = false).heightIn(max = 380.dp)) {
                    itemsIndexed(lines) { i, line ->
                        val stamped = times.getOrNull(i)
                        Row(
                            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                                .background(if (i == next) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent)
                                .clickable(enabled = stamped != null) { stamped?.let { graph.player.seekTo(it) } }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stamped?.formatDuration() ?: "--:--",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (stamped != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(52.dp),
                            )
                            Text(
                                line, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                fontWeight = if (i == next) FontWeight.Bold else FontWeight.Normal,
                                color = if (i < next) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (state.isPlaying) graph.player.pause() else graph.player.resume() }) {
                        Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, t("Play"))
                    }
                    IconButton(enabled = next > 0, onClick = {
                        next--
                        times[next] = null
                        graph.player.seekTo(((times.getOrNull(next - 1) ?: 0L)).coerceAtLeast(0))
                    }) { Icon(Icons.AutoMirrored.Rounded.Undo, t("Undo")) }
                    IconButton(onClick = {
                        for (i in times.indices) times[i] = null
                        next = 0
                        graph.player.seekTo(0)
                        graph.player.resume()
                    }) { Icon(Icons.Rounded.Replay, t("Start over")) }
                    Text(state.positionMs.formatDuration(), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp))
                    Spacer(Modifier.weight(1f))
                    Button(enabled = next < lines.size, onClick = {
                        times[next] = state.positionMs
                        next++
                    }, modifier = Modifier.height(52.dp)) {
                        Text(if (next < lines.size) t("Line starts") else t("All set"), fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    if (graph.lyrics.hasOwnTiming(track)) TextButton(onClick = {
                        graph.lyrics.saveOwnTiming(track, null)
                        graph.toast(t("Back to the found lyrics"))
                        onDismiss()
                    }) { Text(t("Remove my timing")) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text(t("Cancel")) }
                    Button(enabled = next > 0, onClick = {
                        val out = lines.indices.mapNotNull { i -> times[i]?.let { LyricLine(it, lines[i]) } }.sortedBy { it.timeMs }
                        graph.lyrics.saveOwnTiming(track, out)
                        graph.toast(t("Timing saved"))
                        onDismiss()
                    }) { Text(t("Save")) }
                }
            }
        }
    }
}

private enum class CardLook(val label: String) { COVER("Cover"), ACCENT("Accent"), NIGHT("Night") }

/** A story-sized picture with the chosen lines, the cover and the song. */
@Composable
fun LyricCardDialog(track: Track, lines: List<String>, initial: Int, onDismiss: () -> Unit) {
    val graph = AppGraph.instance
    val scope = rememberCoroutineScope()
    val picked = remember { mutableStateListOf<Int>().apply { if (initial in lines.indices) add(initial) } }
    var look by remember { mutableStateOf(CardLook.COVER) }
    var busy by remember { mutableStateOf(false) }
    val layer = rememberGraphicsLayer()
    val cs = MaterialTheme.colorScheme
    val ctx = LocalPlatformContext.current
    val cover = remember(track.artworkUrl) { track.artworkUrl?.let { ImageRequest.Builder(ctx).data(it).softwareOnly().build() } }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = cs.surfaceContainerHigh,
            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(0.94f).heightIn(max = 760.dp),
        ) {
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.padding(16.dp)) {
            val wide = maxWidth > 560.dp
            val preview: @Composable () -> Unit = {
                Box(
                    Modifier.width(if (wide) 250.dp else 170.dp).aspectRatio(9f / 16f)
                        .drawWithContent { layer.record { this@drawWithContent.drawContent() }; drawLayer(layer) },
                ) {
                    val bg = when (look) {
                        CardLook.COVER -> Brush.verticalGradient(listOf(Color(0xFF15121C), Color(0xFF07060B)))
                        CardLook.ACCENT -> Brush.linearGradient(listOf(cs.primary, cs.tertiary))
                        CardLook.NIGHT -> Brush.verticalGradient(listOf(Color(0xFF0B0B10), Color(0xFF1C1A26)))
                    }
                    Box(Modifier.fillMaxSize().background(bg))
                    if (look == CardLook.COVER && cover != null) {
                        AsyncImage(cover, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(), alpha = 0.55f)
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.8f)))))
                    }
                    val ink = if (look == CardLook.ACCENT) cs.onPrimary else Color.White
                    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.Center) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.15f))) {
                                if (cover != null) AsyncImage(cover, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            }
                            Column(Modifier.padding(start = 10.dp)) {
                                Text(track.title, color = ink, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(track.artistNames, color = ink.copy(alpha = 0.7f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        val chosen = picked.sorted().map { lines[it] }.ifEmpty { listOf("♪") }
                        val size = when (chosen.sumOf { it.length }) { in 0..60 -> 22; in 61..130 -> 18; else -> 15 }.sp
                        chosen.forEach {
                            Text(it, color = ink, fontWeight = FontWeight.ExtraBold, fontSize = size, lineHeight = size * 1.2f, modifier = Modifier.padding(bottom = 6.dp))
                        }
                    }
                    Text(
                        "Nocturne", color = ink.copy(alpha = 0.6f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                    )
                }
            }
            val controls: @Composable (Modifier) -> Unit = { mod -> Column(mod) {
                    Text(t("Lyrics card"), style = MaterialTheme.typography.titleLarge)
                    Text(t("Pick up to 4 lines"), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                        CardLook.entries.forEach { l -> FilterChip(look == l, { look = l }, label = { Text(t(l.label)) }) }
                    }
                    LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = if (wide) 420.dp else 220.dp)) {
                        itemsIndexed(lines) { i, line ->
                            val on = i in picked
                            Text(
                                line,
                                color = if (on) cs.primary else cs.onSurface,
                                fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                                    .then(if (on) Modifier.border(1.dp, cs.primary, MaterialTheme.shapes.small) else Modifier)
                                    .clickable {
                                        if (on) picked.remove(i)
                                        else if (picked.size < 4) picked.add(i)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text(t("Cancel")) }
                        Button(enabled = !busy && picked.isNotEmpty(), onClick = {
                            busy = true
                            scope.launch {
                                val name = (track.title + " lyrics").replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80)
                                val where = runCatching { shareImage(layer.toImageBitmap().encodePng(), name) }
                                    .onFailure { graph.toast(t("Couldn't make the picture")) }.getOrNull()
                                where?.let { graph.toast(t("Saved to %s", it)) }
                                busy = false
                                if (where != null || app.nocturne.platformKind != app.nocturne.PlatformKind.DESKTOP) onDismiss()
                            }
                        }) { Text(t("Share")) }
                    }
                }
            }
            if (wide) Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { preview(); controls(Modifier.weight(1f)) }
            else Column(horizontalAlignment = Alignment.CenterHorizontally) { preview(); Spacer(Modifier.height(10.dp)); controls(Modifier.fillMaxWidth()) }
            }
        }
    }
}
