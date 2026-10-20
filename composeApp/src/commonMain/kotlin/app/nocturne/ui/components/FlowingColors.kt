package app.nocturne.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun FlowingColors(colors: List<Color>, modifier: Modifier = Modifier, intensity: Float = 0.55f) {
    val t by rememberInfiniteTransition(label = "flow").animateFloat(
        0f, 1f, infiniteRepeatable(tween(36_000, easing = LinearEasing), RepeatMode.Restart), label = "t",
    )
    Canvas(modifier) {
        val a = (t * 2 * PI).toFloat()
        val r = size.maxDimension * 0.62f
        colors.take(4).forEachIndexed { i, c ->
            val k = i + 1
            val center = Offset(
                size.width * (0.5f + 0.38f * sin(a * k * 0.7f + i * 1.9f)),
                size.height * (0.45f + 0.35f * cos(a * (k % 3 + 1) * 0.5f + i * 2.6f)),
            )
            val radius = r * (0.75f + 0.25f * sin(a * 1.3f + i))
            drawCircle(Brush.radialGradient(listOf(c.copy(alpha = intensity), c.copy(alpha = 0f)), center, radius), radius, center)
        }
    }
}
