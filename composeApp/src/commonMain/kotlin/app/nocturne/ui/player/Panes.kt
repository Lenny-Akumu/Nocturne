package app.nocturne.ui.player

import androidx.compose.ui.zIndex
import androidx.compose.material.icons.rounded.Fullscreen
import app.nocturne.i18n.t
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.Lyrics
import app.nocturne.ui.kit.CircularProgressIndicator
import app.nocturne.ui.kit.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import app.nocturne.ui.kit.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nocturne.AppGraph
import app.nocturne.data.LyricsAlign
import app.nocturne.source.Lyrics
import app.nocturne.ui.components.MessageBox
import app.nocturne.ui.components.TrackMenuContext
import app.nocturne.ui.components.TrackRow

@Composable
fun LyricsView() {
    val graph = AppGraph.instance
    val state by graph.player.state.collectAsState()
    val settings by graph.settings.state.collectAsState()
    val track = state.current ?: return
    var lyrics by remember(track.key) { mutableStateOf<Lyrics?>(null) }
    var loading by remember(track.key) { mutableStateOf(true) }

    LaunchedEffect(track.key) {
        loading = true
        lyrics = runCatching { graph.lyrics.lyrics(track) }.getOrNull()
        loading = false
    }

    val align = if (settings.lyricsAlign == LyricsAlign.CENTER) TextAlign.Center else TextAlign.Start
    val l = lyrics
    when {
        loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        l == null || l.isEmpty -> MessageBox(Icons.Rounded.Lyrics, "No lyrics found", "We couldn't find lyrics for this song.")
        !l.synced.isNullOrEmpty() -> {
            val lines = l.synced
            val listState = rememberLazyListState()
            val current = lines.indexOfLast { it.timeMs <= state.positionMs + 300 }.coerceAtLeast(0)
            LaunchedEffect(current) {
                if (listState.layoutInfo.visibleItemsInfo.none { it.index == current }) listState.scrollToItem(current)
                val info = listState.layoutInfo
                val item = info.visibleItemsInfo.firstOrNull { it.index == current } ?: return@LaunchedEffect
                val target = info.viewportStartOffset + info.viewportSize.height / 3
                listState.animateScrollBy((item.offset - target).toFloat())
            }
            var karaoke by remember { mutableStateOf(false) }
            if (karaoke) KaraokeDialog(lines) { karaoke = false }
            BoxWithConstraints(Modifier.fillMaxSize()) {
            val edge = maxHeight / 2
            IconButton(onClick = { karaoke = true }, modifier = Modifier.align(Alignment.TopEnd).zIndex(1f)) {
                Icon(Icons.Rounded.Fullscreen, "Karaoke", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = edge / 2, bottom = edge)) {
                itemsIndexed(lines) { i, line ->
                    val active = i == current
                    val alpha by animateFloatAsState(if (active) 1f else if (i < current) 0.35f else 0.5f, label = "a")
                    val color by animateColorAsState(if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, label = "c")
                    Text(
                        line.text.ifBlank { "♪" },
                        fontSize = settings.lyricsTextSize.sp,
                        lineHeight = (settings.lyricsTextSize * 1.25f).sp,
                        fontWeight = FontWeight.Bold,
                        color = color,
                        textAlign = align,
                        modifier = Modifier.fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .clickable { graph.player.seekTo(line.timeMs) }
                            .alpha(alpha)
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                    )
                }
            }
            }
        }
        else -> Text(
            l.plain.orEmpty(),
            fontSize = (settings.lyricsTextSize * 0.75f).sp,
            lineHeight = (settings.lyricsTextSize * 1.1f).sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = align,
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 24.dp),
        )
    }
}

@Composable
fun QueueView() {
    val graph = AppGraph.instance
    val state by graph.player.state.collectAsState()
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (state.index - 1).coerceAtLeast(0))
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(t("Up next"), style = MaterialTheme.typography.titleLarge)
                Text(t("%s songs", state.queue.size) + (state.queueTitle?.let { " • $it" } ?: ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            IconButton(onClick = graph.player::clearQueue) { Icon(Icons.Rounded.ClearAll, "Clear queue") }
        }
        val settings by graph.settings.state.collectAsState()
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(state.queue, key = { i, qt -> qt.key + "#" + i }) { i, qt ->
                TrackRow(
                    qt,
                    onClick = { graph.player.skipTo(i) },
                    modifier = Modifier.alpha(if (i < state.index) 0.5f else 1f),
                    menuContext = TrackMenuContext(queueIndex = i),
                )
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.AllInclusive, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(t("Autoplay"), style = MaterialTheme.typography.titleSmall)
                        Text(
                            t("Keep playing similar songs when the queue ends"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = settings.autoplayRadio, onCheckedChange = { v ->
                        graph.settings.update { it.copy(autoplayRadio = v) }
                        if (v) graph.player.ensureAutoplay()
                    })
                }
            }
        }
    }
}
