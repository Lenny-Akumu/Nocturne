package app.nocturne.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import app.nocturne.ui.kit.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.nocturne.AppGraph
import app.nocturne.nowMillis
import app.nocturne.source.LyricLine
import app.nocturne.ui.components.Artwork
import app.nocturne.ui.components.FlowingColors

@Composable
fun KaraokeDialog(lines: List<LyricLine>, onDismiss: () -> Unit) {
    val graph = AppGraph.instance
    val state by graph.player.state.collectAsState()
    var pos by remember { mutableLongStateOf(state.positionMs) }
    LaunchedEffect(state.positionMs, state.isPlaying) {
        val base = state.positionMs
        val start = nowMillis()
        pos = base
        while (state.isPlaying) {
            withFrameMillis { pos = base + (nowMillis() - start) }
        }
    }
    val cs = MaterialTheme.colorScheme
    val current = lines.indexOfLast { it.timeMs <= pos + 150 }.coerceAtLeast(0)
    val line = lines.getOrNull(current)
    val next = lines.getOrNull(current + 1)
    val progress = if (line != null && next != null && next.timeMs > line.timeMs)
        ((pos - line.timeMs).toFloat() / (next.timeMs - line.timeMs)).coerceIn(0f, 1f) else 1f

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xFF07060B))) {
            Artwork(state.current?.artworkUrl, Modifier.fillMaxSize().background(Color.Black))
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.78f)))
            FlowingColors(listOf(cs.primary, cs.tertiary, cs.secondary), Modifier.fillMaxSize(), 0.35f)
            Column(
                Modifier.fillMaxSize().padding(horizontal = 28.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    lines.getOrNull(current - 1)?.text.orEmpty(),
                    color = Color.White.copy(alpha = 0.35f), fontSize = 22.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(26.dp))
                AnimatedContent(
                    current,
                    transitionSpec = { (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut()) },
                    label = "line",
                ) { idx ->
                    val text = lines.getOrNull(idx)?.text?.ifBlank { "♪ ♪ ♪" } ?: ""
                    val words = text.split(" ")
                    val lit = if (idx == current) progress * words.size else if (idx < current) words.size.toFloat() else 0f
                    Text(
                        buildAnnotatedString {
                            words.forEachIndexed { i, w ->
                                val c = when {
                                    i < lit.toInt() -> Color.White
                                    i == lit.toInt() -> cs.primary
                                    else -> Color.White.copy(alpha = 0.4f)
                                }
                                withStyle(SpanStyle(color = c)) { append(w) }
                                if (i < words.lastIndex) append(" ")
                            }
                        },
                        fontSize = 40.sp, lineHeight = 50.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(26.dp))
                Text(
                    next?.text.orEmpty(),
                    color = Color.White.copy(alpha = 0.5f), fontSize = 24.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold,
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                Icon(Icons.Rounded.Close, "Close", tint = Color.White)
            }
        }
    }
}
