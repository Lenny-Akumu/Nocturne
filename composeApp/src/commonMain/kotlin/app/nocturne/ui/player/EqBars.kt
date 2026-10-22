package app.nocturne.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import app.nocturne.AppGraph
import app.nocturne.ui.kit.LocalPixelIcons
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private const val BARS = 5
private const val IDLE = 0.14f

// Stand-in spectrum when the engine has no PCM (iOS, cast): smoothed random targets with a shared pulse.
private class SynthLevels {
    private val v = FloatArray(BARS)
    private val target = FloatArray(BARS)
    private val left = FloatArray(BARS)
    private val out = FloatArray(BARS)
    private var t = 0f

    fun next(dt: Float): FloatArray {
        t += dt
        val pulse = 0.5f + 0.5f * sin(t * 2f * 3.1416f * 1.9f)
        for (i in 0 until BARS) {
            left[i] -= dt
            if (left[i] <= 0f) {
                left[i] = 0.09f + Random.nextFloat() * 0.16f
                target[i] = 0.2f + Random.nextFloat() * (if (i == 0) 0.8f else 0.7f - i * 0.06f)
            }
            v[i] += (target[i] - v[i]) * (1f - exp(-dt * 14f))
            out[i] = (v[i] * (0.75f + 0.25f * pulse * (if (i < 2) 1f else 0.5f))).coerceIn(0f, 1f)
        }
        return out
    }
}

/**
 * Five rounded bars mirrored around the center line, driven by [app.nocturne.player.PlayerController.levels]
 * (or a synthetic spectrum). Animated in the draw phase only; settles to a small idle height when not [playing].
 */
@Composable
fun EqBars(playing: Boolean, modifier: Modifier = Modifier, color: Color = LocalContentColor.current) {
    val player = AppGraph.instance.player
    val bars = remember { FloatArray(BARS) { IDLE } }
    var frame by remember { mutableIntStateOf(0) }
    val live by rememberUpdatedState(playing)
    LaunchedEffect(playing) {
        val synth = SynthLevels()
        var last = 0L
        while (live || bars.any { abs(it - IDLE) > 0.003f }) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0.016f else ((now - last) / 1e9f).coerceIn(0f, 0.1f)
                last = now
                val src = if (live) player.levels() ?: synth.next(dt) else null
                for (i in 0 until BARS) {
                    val want = IDLE + (1f - IDLE) * (src?.getOrNull(i) ?: 0f)
                    val speed = if (want > bars[i]) 28f else 7f // fast rise, slower fall
                    bars[i] += (want - bars[i]) * (1f - exp(-dt * speed))
                }
                frame++
            }
        }
    }
    val pixel = LocalPixelIcons.current
    Canvas(modifier) {
        frame // redraw per frame without recomposition
        if (pixel) {
            // 2-cell bars, 1-cell gaps on a 14-cell grid; heights snap to whole cells, grown evenly about the center
            val u = size.width / 14
            val rows = (size.height / u).toInt()
            val y0 = (size.height - rows * u) / 2
            if (rows >= 2) for (i in 0 until BARS) {
                var cells = (rows * 0.9f * bars[i]).roundToInt().coerceIn(2, rows)
                if ((rows - cells) % 2 != 0) cells--
                drawRect(color, Offset(i * 3 * u, y0 + (rows - cells) / 2 * u), Size(2 * u, cells * u))
            }
            return@Canvas
        }
        val w = size.width / (BARS * 1.75f - 0.75f)
        val gap = w * 0.75f
        val x0 = (size.width - (BARS * w + (BARS - 1) * gap)) / 2
        val cy = size.height / 2
        for (i in 0 until BARS) {
            val h = maxOf(w, size.height * 0.9f * bars[i])
            drawRoundRect(color, Offset(x0 + i * (w + gap), cy - h / 2), Size(w, h), CornerRadius(w / 2))
        }
    }
}
