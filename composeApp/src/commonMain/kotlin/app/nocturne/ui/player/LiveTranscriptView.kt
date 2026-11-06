package app.nocturne.ui.player

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
internal fun wantsLiveText(track: Track) = (track.isRadio || track.isPodcast) && LiveTranscript.supported

@Composable
internal fun LiveTranscriptView(track: Track, modifier: Modifier = Modifier.fillMaxSize()) {
    val cs = MaterialTheme.colorScheme
    var ready by remember { mutableStateOf(LiveTranscript.modelReady) }
    if (!ready) {
        val scope = rememberCoroutineScope()
        var progress by remember { mutableStateOf<Float?>(null) }
        var failed by remember { mutableStateOf(false) }
        Box(modifier, contentAlignment = Alignment.Center) {
            Column(Modifier.widthIn(max = 420.dp).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(t("Live text"), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                Text(
                    t("Speech in radio and podcasts can be turned into text right on your device, in any language. It needs a one-time download of %s MB.", LiveTranscript.modelSizeMb),
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
    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) list.animateScrollToItem(lines.lastIndex) }
    Column(modifier) {
        if (lines.isEmpty()) Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Text(if (busy) t("Listening…") else t("Text will appear in a few seconds"), color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
        } else LazyColumn(Modifier.fillMaxWidth().weight(1f), state = list, contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 24.dp)) {
            items(lines.size) { i ->
                val last = i == lines.lastIndex
                Text(
                    lines[i].text,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = if (last) FontWeight.Bold else FontWeight.SemiBold),
                    color = if (last) cs.onSurface else cs.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }
        Text("● " + t("Live text, made on this device"), color = cs.primary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(8.dp))
    }
}
