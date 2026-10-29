package app.nocturne.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import app.nocturne.AppGraph
import app.nocturne.i18n.t
import app.nocturne.ui.components.Artwork
import app.nocturne.ui.theme.NocturneTheme

/** Desktop widget: cover, title and the current lyric line, pinned to a screen corner. */
@Composable
fun LyricsWidgetWindow() {
    val graph = AppGraph.instance
    val settings by graph.settings.state.collectAsState()
    val state by graph.player.state.collectAsState()
    val track = state.current ?: return
    val w = 380
    val h = 112
    val b = remember { java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds }
    val m = 18
    val corner = settings.lyricsWidgetCorner
    val x = if (corner % 2 == 0) b.x + m else b.x + b.width - w - m
    val y = if (corner < 2) b.y + m else b.y + b.height - h - m
    val dialog = rememberDialogState(position = WindowPosition(x.dp, y.dp), size = DpSize(w.dp, h.dp))
    LaunchedEffect(x, y) { dialog.position = WindowPosition(x.dp, y.dp) }
    val lyrics by produceState<app.nocturne.source.Lyrics?>(null, track.key) {
        value = null
        value = runCatching { graph.lyrics.lyrics(track) }.getOrNull()
    }
    val synced = lyrics?.synced.orEmpty()
    val at = synced.indexOfLast { it.timeMs <= state.positionMs + 250 }
    val line = synced.getOrNull(at)?.text?.takeIf { it.isNotBlank() } ?: if (synced.isEmpty()) track.artistNames else "♪"
    val next = synced.getOrNull(at + 1)?.text.orEmpty()
    DialogWindow(
        onCloseRequest = { graph.settings.update { it.copy(lyricsWidget = false) } },
        state = dialog,
        title = "Nocturne",
        undecorated = true,
        transparent = true,
        resizable = false,
        focusable = false,
    ) {
        LaunchedEffect(Unit) { window.isAlwaysOnTop = settings.lyricsWidgetOnTop }
        LaunchedEffect(settings.lyricsWidgetOnTop) { window.isAlwaysOnTop = settings.lyricsWidgetOnTop }
        NocturneTheme(settings, forceDark = true) {
            val shape = RoundedCornerShape(22.dp)
            Box(Modifier.fillMaxSize().padding(4.dp).clip(shape).background(Color(0xE60E0E12))) {
                Artwork(track.artworkUrl, Modifier.fillMaxSize().blur(40.dp))
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
                Row(Modifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Artwork(track.artworkUrl, Modifier.size(80.dp).clip(RoundedCornerShape(14.dp)))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(track.title, style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(2.dp))
                        AnimatedContent(line, transitionSpec = {
                            (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut())
                        }, label = "line") { l ->
                            Text(l, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        if (next.isNotBlank()) Text(next, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.45f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.18f))) {
                            Box(Modifier.fillMaxWidth(state.progress.coerceIn(0f, 1f)).height(3.dp).background(Color.White.copy(alpha = 0.85f)))
                        }
                    }
                }
            }
        }
    }
}
