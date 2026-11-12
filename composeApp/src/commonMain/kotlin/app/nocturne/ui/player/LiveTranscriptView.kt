package app.nocturne.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.nocturne.data.isPodcast
import app.nocturne.data.isRadio
import app.nocturne.i18n.t
import app.nocturne.model.Track
import app.nocturne.source.LiveTranscript
import kotlinx.coroutines.launch

/** Radio and podcasts without a transcript get one made on the device. */
internal fun wantsLiveText(track: Track) = LiveTranscript.supported

@Composable
internal fun LiveTranscriptView(track: Track, modifier: Modifier = Modifier.fillMaxSize()) {
    val cs = MaterialTheme.colorScheme
    val song = !track.isRadio && !track.isPodcast
    var ready by remember { mutableStateOf(LiveTranscript.modelReady) }
    if (!ready) {
        val scope = rememberCoroutineScope()
        var progress by remember { mutableStateOf<Float?>(null) }
        var failed by remember { mutableStateOf(false) }
        Box(modifier, contentAlignment = Alignment.Center) {
            Column(Modifier.widthIn(max = 420.dp).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (song) t("No lyrics found") else t("Live text"), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                Text(
                    if (song) t("Nocturne can pick out the words while the song plays and keep them as its lyrics. It needs a one-time download of %s MB.", LiveTranscript.modelSizeMb)
                    else t("Speech in radio and podcasts can be turned into text right on your device, in any language. It needs a one-time download of %s MB.", LiveTranscript.modelSizeMb),
                    color = cs.onSurfaceVariant, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium,
                )
                val p = progress
                if (p != null) LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                else app.nocturne.ui.kit.Button(onClick = {
                    failed = false
                    progress = 0f
                    scope.launch {
                        val ok = LiveTranscript.download { progress = it }
                        progress = null
                        if (ok) ready = true else failed = true
                    }
                }) { Text(t("Download")) }
                if (failed) Text(t("Download failed. Check your connection and try again."), color = cs.error, style = MaterialTheme.typography.bodySmall)
            }
        }
        return
    }
    DisposableEffect(track.key) {
        LiveTranscript.start(track)
        onDispose { LiveTranscript.stop() }
    }
    val lines by LiveTranscript.lines.collectAsState()
    val busy by LiveTranscript.busy.collectAsState()
    val list = rememberLazyListState()
    val pos by app.nocturne.AppGraph.instance.player.state.collectAsState()
    val currentIdx = if (song) lines.indexOfLast { it.timeMs <= pos.positionMs + 300 }.coerceAtLeast(0) else lines.lastIndex
    LaunchedEffect(lines.size, currentIdx) { if (lines.isNotEmpty()) list.animateScrollToItem(currentIdx.coerceIn(0, lines.lastIndex)) }
    Column(modifier) {
        if (lines.isEmpty()) Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Text(if (busy) t("Listening…") else if (song) t("Listening for the words…") else t("Text will appear in a few seconds"), color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
        } else LazyColumn(Modifier.fillMaxWidth().weight(1f), state = list, contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 24.dp)) {
            items(lines.size) { i ->
                val last = i == currentIdx
                Text(
                    lines[i].text,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = if (last) FontWeight.Bold else FontWeight.SemiBold),
                    color = if (last) cs.onSurface else cs.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.then(if (song) Modifier.clickable { app.nocturne.AppGraph.instance.player.seekTo(lines[i].timeMs) } else Modifier)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }
        Text("● " + if (song) t("Words recognized as the song plays, saved as its lyrics") else t("Live text, made on this device"), color = cs.primary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(8.dp))
    }
}
