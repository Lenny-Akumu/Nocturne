package app.nocturne.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.nocturne.AppGraph
import app.nocturne.data.Recognizer
import app.nocturne.data.hasMicAccess
import app.nocturne.data.requestMicAccess
import app.nocturne.i18n.t
import app.nocturne.ui.kit.Icon

object Recognize {
    var open by mutableStateOf(false)
}

@Composable
fun RecognizeHost() {
    if (Recognize.open) RecognizeDialog { Recognize.open = false }
}

@Composable
private fun RecognizeDialog(onDismiss: () -> Unit) {
    val graph = AppGraph.instance
    val settings by graph.settings.state.collectAsState()
    var outcome by remember { mutableStateOf<Recognizer.Outcome?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var token by remember { mutableStateOf(settings.auddToken) }
    LaunchedEffect(attempt) {
        outcome = null
        if (!hasMicAccess()) {
            var granted: Boolean? = null
            requestMicAccess { granted = it }
            while (granted == null) kotlinx.coroutines.delay(100)
            if (granted != true) { outcome = Recognizer.Outcome.Failed("Microphone access is off"); return@LaunchedEffect }
        }
        outcome = Recognizer.identify()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("What's playing?")) },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (val o = outcome) {
                    null -> {
                        Listening()
                        Text(t("Listening… hold your device near the music"), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                    }
                    is Recognizer.Outcome.Found -> {
                        val song = o.song
                        Artwork(song.match?.artworkUrl, Modifier.size(140.dp), RoundedCornerShape(18.dp))
                        Text(song.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Text(listOfNotNull(song.artist, song.album).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        song.match?.let { m ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                app.nocturne.ui.kit.Button(onClick = { graph.player.play(listOf(m), 0, t("Recognised")); onDismiss() }) {
                                    Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text(t("Play"))
                                }
                                app.nocturne.ui.kit.FilledTonalButton(onClick = { if (!graph.library.isLiked(m)) graph.toggleLike(m) }) {
                                    Icon(Icons.Rounded.Favorite, null); Spacer(Modifier.width(6.dp)); Text(t("Like"))
                                }
                            }
                        } ?: Text(t("Found it, but none of your sources has this song."), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                    }
                    Recognizer.Outcome.NotFound -> Text(t("Couldn't recognise this one. Try closer to the speaker."), textAlign = TextAlign.Center)
                    is Recognizer.Outcome.Failed -> {
                        Text(t(o.message), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
                        Text(t("Free recognitions are limited per day. A personal token from audd.io lifts the limit:"), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                        OutlinedTextField(token, { token = it.trim(); graph.settings.update { s -> s.copy(auddToken = it.trim()) } },
                            label = { Text("AudD token") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {
            if (outcome != null) TextButton(onClick = { attempt++ }) { Text(t("Listen again")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Close")) } },
    )
}

@Composable
private fun Listening() {
    val cs = MaterialTheme.colorScheme
    val tr = rememberInfiniteTransition(label = "listen")
    val p by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "p")
    Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(150.dp)) {
            for (i in 0 until 3) {
                val k = (p + i / 3f) % 1f
                drawCircle(cs.primary.copy(alpha = (1f - k) * 0.5f), radius = size.minDimension / 2 * (0.35f + 0.65f * k), style = Stroke(3.dp.toPx()))
            }
        }
        Box(Modifier.size(64.dp).padding(0.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(64.dp)) { drawCircle(cs.primary) }
            Icon(Icons.Rounded.GraphicEq, null, Modifier.size(30.dp), tint = cs.onPrimary)
        }
    }
}
