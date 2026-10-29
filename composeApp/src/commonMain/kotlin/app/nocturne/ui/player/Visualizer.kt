package app.nocturne.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nocturne.AppGraph
import app.nocturne.ui.components.Artwork
import app.nocturne.ui.theme.rememberArtworkSeed
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/** Full-screen "aura": artwork-coloured light blobs and a radial spectrum that breathe with the music. */
@Composable
fun VisualizerScreen(onClose: () -> Unit) {
    val graph = AppGraph.instance
    val state by graph.player.state.collectAsState()
    val track = state.current ?: return onClose()
    val seed = rememberArtworkSeed(track.artworkUrl) ?: MaterialTheme.colorScheme.primary
    val colors = remember(seed) {
        listOf(seed, lerp(seed, Color(0xFFFF4FD8), 0.45f), lerp(seed, Color(0xFF3DD6FF), 0.45f), lerp(seed, Color.White, 0.35f))
    }
    val bands = remember { FloatArray(5) }
    val ring = remember { FloatArray(RING) }
    val clock = remember { mutableLongStateOf(0L) }
    var bass = remember { floatArrayOf(0f) }
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(500)) }
    LaunchedEffect(Unit) {
        val synth = SynthLevels()
        var last = 0L
        while (true) withFrameNanos { now ->
            val dt = if (last == 0L) 0.016f else ((now - last) / 1e9f).coerceIn(0f, 0.1f)
            last = now
            val src = if (graph.player.state.value.isPlaying) graph.player.levels() ?: synth.next(dt) else null
            for (i in 0 until 5) {
                val want = src?.getOrNull(i) ?: 0f
                bands[i] += (want - bands[i]) * (1f - exp(-dt * if (want > bands[i]) 24f else 6f))
            }
            bass[0] += ((bands[0] + bands[1]) / 2 - bass[0]) * (1f - exp(-dt * 10f))
            // spread 5 bands around the circle, mirrored, with a little shimmer
            val t = now / 1e9f
            for (k in 0 until RING) {
                val pos = (if (k < RING / 2) k else RING - 1 - k) / (RING / 2f) * 4f
                val i = pos.toInt().coerceIn(0, 3)
                val f = pos - i
                val v = bands[i] * (1 - f) + bands[i + 1] * f
                ring[k] = (v * (0.85f + 0.15f * sin(t * 7f + k * 0.9f))).coerceIn(0f, 1f)
            }
            clock.longValue = now
        }
    }
    Box(
        Modifier.fillMaxSize().graphicsLayer { alpha = enter.value }.background(Color(0xFF05050A))
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClose),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            clock.longValue
            val t = clock.longValue / 1e9f
            val c = Offset(size.width / 2, size.height / 2)
            val base = size.minDimension
            // drifting light blobs, swelling on the bass
            colors.forEachIndexed { i, col ->
                val a = t * (0.13f + i * 0.04f) + i * 1.7f
                val p = Offset(c.x + cos(a) * base * 0.22f, c.y + sin(a * 1.3f) * base * 0.18f)
                val r = base * (0.38f + 0.14f * bass[0] + 0.03f * sin(t + i))
                drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.55f), Color.Transparent), p, r), r, p, blendMode = BlendMode.Plus)
            }
            // radial spectrum around the cover
            val inner = base * 0.19f * (1f + 0.06f * bass[0])
            for (k in 0 until RING) {
                val ang = (k / RING.toFloat()) * 2 * PI.toFloat() - PI.toFloat() / 2 + t * 0.15f
                val len = base * (0.015f + 0.16f * ring[k])
                val d = Offset(cos(ang), sin(ang))
                drawLine(
                    lerp(colors[0], Color.White, ring[k] * 0.6f).copy(alpha = 0.9f),
                    c + d * (inner + 8f), c + d * (inner + 8f + len),
                    strokeWidth = base * 0.008f, cap = StrokeCap.Round,
                )
            }
        }
        val spin = (clock.longValue / 1e9f * 9f) % 360f
        Artwork(
            track.artworkUrl,
            Modifier.align(Alignment.Center).size(220.dp)
                .graphicsLayer { rotationZ = if (state.isPlaying) spin else 0f; scaleX = 1f + 0.05f * bass[0]; scaleY = scaleX }
                .clip(CircleShape),
            CircleShape,
        )
        Column(Modifier.align(Alignment.BottomCenter).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(track.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artistNames, style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private const val RING = 72
