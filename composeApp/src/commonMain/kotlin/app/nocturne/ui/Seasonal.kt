package app.nocturne.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.height
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import app.nocturne.AppGraph
import app.nocturne.currentMonthDay
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

enum class Season { WINTER, SPRING, SUMMER, AUTUMN }

fun currentSeason(): Season = when (currentMonthDay().first) {
    12, 1, 2 -> Season.WINTER
    3, 4, 5 -> Season.SPRING
    6, 7, 8 -> Season.SUMMER
    else -> Season.AUTUMN
}

private class Flake(var x: Float, var y: Float, val size: Float, val speed: Float, val sway: Float, val phase: Float, val spin: Float, val tint: Int)

/** Where the ambience has to stop: top of the first track list on Home (root px). */
internal object SeasonalStop {
    var y by mutableFloatStateOf(Float.NaN)
}

fun Modifier.seasonalStop(): Modifier = onGloballyPositioned { SeasonalStop.y = it.positionInRoot().y }

/** Light, non-interactive seasonal ambience over the top of Home. */
@Composable
fun SeasonalLayer(modifier: Modifier = Modifier, maxHeight: Dp = 320.dp) {
    val settings by AppGraph.instance.settings.state.collectAsState()
    if (!settings.seasonalEffects || Demo.enabled) return
    val season = remember { currentSeason() }
    val count = when (season) { Season.WINTER -> 34; Season.SUMMER -> 0; else -> 14 }
    val rnd = remember { Random(42) }
    val items = remember {
        List(count) {
            Flake(rnd.nextFloat(), rnd.nextFloat() * 1.2f - 0.2f, 0.6f + rnd.nextFloat() * 0.8f, 0.035f + rnd.nextFloat() * 0.05f,
                0.015f + rnd.nextFloat() * 0.03f, rnd.nextFloat() * 6.28f, (rnd.nextFloat() - 0.5f) * 140f, rnd.nextInt(4))
        }
    }
    val time = remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) withFrameNanos { now ->
            val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.1f)
            last = now
            items.forEach { f ->
                f.y += f.speed * dt * if (season == Season.WINTER) 0.8f else 1f
                if (f.y > 1.1f) { f.y = -0.1f; f.x = rnd.nextFloat() }
            }
            time.longValue = now
        }
    }
    var top by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val stop = SeasonalStop.y
    val h = if (stop.isNaN()) maxHeight else with(density) { (stop - top).coerceAtLeast(0f).toDp() }.coerceAtMost(maxHeight)
    Canvas(modifier.onGloballyPositioned { top = it.positionInRoot().y }.height(h)) {
        val t = time.longValue / 1e9f
        if (season == Season.SUMMER) return@Canvas drawRays(t)
        items.forEach { f ->
            val x = (f.x + sin(t * 0.6f + f.phase) * f.sway) * size.width
            val y = f.y * size.height
            // fade in at the top, out towards the bottom of the band
            val a = (minOf(f.y + 0.1f, 1.1f - f.y) * 4f).coerceIn(0f, 1f)
            when (season) {
                Season.WINTER -> drawSnow(Offset(x, y), f, a)
                Season.AUTUMN -> drawLeaf(Offset(x, y), f, t, a, autumn[f.tint])
                Season.SPRING -> drawLeaf(Offset(x, y), f, t, a, spring[f.tint])
                Season.SUMMER -> Unit
            }
        }
    }
}

private val autumn = listOf(Color(0xFFE8833A), Color(0xFFD9542B), Color(0xFFF2B33D), Color(0xFFB5462A))
private val spring = listOf(Color(0xFF7BC86C), Color(0xFF5FB55A), Color(0xFFA6DB7A), Color(0xFF4E9F4A))

private fun DrawScope.drawSnow(c: Offset, f: Flake, a: Float) {
    val r = (1.4f + f.size * 2.2f) * density
    drawCircle(Color.White.copy(alpha = 0.75f * a), r, c)
    drawCircle(Color.White.copy(alpha = 0.18f * a), r * 2.2f, c)
}

private fun DrawScope.drawLeaf(c: Offset, f: Flake, t: Float, a: Float, color: Color) {
    val s = (7f + f.size * 6f) * density
    val leaf = Path().apply {
        moveTo(0f, -s)
        cubicTo(s * 0.8f, -s * 0.5f, s * 0.7f, s * 0.5f, 0f, s)
        cubicTo(-s * 0.7f, s * 0.5f, -s * 0.8f, -s * 0.5f, 0f, -s)
        close()
    }
    withTransform({
        translate(c.x, c.y)
        rotate(f.spin * t + f.phase * 57f, Offset.Zero)
        scale(0.55f + 0.45f * kotlin.math.abs(sin(t * 1.3f + f.phase)), 1f, Offset.Zero)
    }) {
        drawPath(leaf, color.copy(alpha = 0.8f * a))
        drawLine(Color.Black.copy(alpha = 0.18f * a), Offset(0f, -s * 0.85f), Offset(0f, s * 1.25f), strokeWidth = 1.1f * density)
    }
}

private fun DrawScope.drawRays(t: Float) {
    val origin = Offset(size.width * 0.92f, -size.height * 0.25f)
    val len = size.height * 1.8f
    for (i in 0 until 5) {
        val angle = 112f + i * 13f + sin(t * 0.18f + i * 1.7f) * 4f
        val w = (0.35f + 0.25f * sin(t * 0.25f + i)) * 70f * density
        val alpha = 0.07f + 0.05f * sin(t * 0.3f + i * 2.1f)
        rotate(angle, origin) {
            drawRect(
                Brush.horizontalGradient(0f to Color(0xFFFFE6A0).copy(alpha = alpha), 1f to Color.Transparent, startX = origin.x, endX = origin.x + len),
                topLeft = Offset(origin.x, origin.y - w / 2),
                size = androidx.compose.ui.geometry.Size(len, w),
                blendMode = BlendMode.Plus,
            )
        }
    }
    drawCircle(Brush.radialGradient(0f to Color(0x33FFE6A0), 1f to Color.Transparent, center = origin, radius = size.height * 0.9f), size.height * 0.9f, origin)
}
