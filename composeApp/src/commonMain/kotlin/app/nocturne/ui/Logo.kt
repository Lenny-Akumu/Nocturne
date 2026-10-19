package app.nocturne.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

private const val BARS = 60
private const val CENTER = 54f
private const val RING = 21.5f
private const val DISK = 19f
private const val FLARE = -PI / 4

private val Dim = Color(0xFF3A237A)
private val Mid = Color(0xFF9B6BFF)
private val High = Color(0xFFF5ECFF)

private data class Bar(val angle: Double, val length: Float, val color: Color)

private val bars: List<Bar> = List(BARS) { i ->
    val a = i * 2 * PI / BARS - PI / 2
    val l = 2.2 + 9.0 * (0.5 + 0.5 * sin(3 * a + 0.6)) * (0.55 + 0.45 * sin(7 * a + 1.3)).pow(2)
    val t = ((cos(a - FLARE) + 1) / 2).pow(1.4).toFloat()
    val c = if (t < 0.5f) lerp(Dim, Mid, t * 2) else lerp(Mid, High, (t - 0.5f) * 2)
    Bar(a, l.toFloat(), c)
}

@Composable
fun NocturneLogo(modifier: Modifier = Modifier, background: Boolean = true) {
    Canvas(modifier) {
        val k = size.minDimension / 108f
        scale(k, k, pivot = Offset.Zero) {
            val center = Offset(CENTER, CENTER)
            if (background) {
                drawRoundRect(Color.Black, cornerRadius = CornerRadius(24f), size = Size(108f, 108f))
            }
            drawCircle(
                Brush.radialGradient(
                    0.35f to Color(0x737C3AED),
                    1f to Color(0x007C3AED),
                    center = center,
                    radius = 44f,
                ),
                radius = 44f,
                center = center,
            )
            bars.forEach { b ->
                val dx = cos(b.angle).toFloat()
                val dy = sin(b.angle).toFloat()
                drawLine(
                    b.color,
                    Offset(CENTER + RING * dx, CENTER + RING * dy),
                    Offset(CENTER + (RING + b.length) * dx, CENTER + (RING + b.length) * dy),
                    strokeWidth = 1.7f,
                    cap = StrokeCap.Round,
                )
            }
            drawCircle(Color.Black, DISK, center)
            drawCircle(
                Brush.linearGradient(
                    0f to Color.White,
                    0.5f to Color(0x99A78BFA),
                    1f to Color(0x334C1D95),
                    start = Offset(80f, 28f),
                    end = Offset(30f, 80f),
                ),
                DISK,
                center,
                style = Stroke(1.1f),
            )
            val flare = Offset(CENTER + DISK * cos(FLARE).toFloat(), CENTER + DISK * sin(FLARE).toFloat())
            drawCircle(
                Brush.radialGradient(
                    0f to Color.White,
                    0.12f to Color(0xF2FFFFFF),
                    0.35f to Color(0x59DDD0FF),
                    1f to Color(0x00C4B5FD),
                    center = flare,
                    radius = 9f,
                ),
                radius = 9f,
                center = flare,
            )
        }
    }
}
