package app.nocturne.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate

@Composable
fun NocturneLogo(modifier: Modifier = Modifier, background: Boolean = true) {
    Canvas(modifier) {
        val k = size.minDimension / 108f
        scale(k, k, pivot = Offset.Zero) {
            if (background) {
                drawRoundRect(
                    Brush.linearGradient(listOf(Color(0xFF3A1C78), Color(0xFF120A2E)), Offset(0f, 0f), Offset(108f, 108f)),
                    cornerRadius = CornerRadius(30f),
                    size = Size(108f, 108f),
                )
                listOf(Triple(24f, 26f, 1.4f), Triple(84f, 82f, 1.1f), Triple(30f, 80f, 0.9f), Triple(86f, 24f, 1f)).forEach { (x, y, r) ->
                    drawCircle(Color.White.copy(alpha = 0.55f), r, Offset(x, y))
                }
            }
            translate(2f, -2f) {
            val moon = Path().apply {
                op(
                    Path().apply { addOval(Rect(Offset(50f, 56f), 26f)) },
                    Path().apply { addOval(Rect(Offset(63f, 46f), 22f)) },
                    PathOperation.Difference,
                )
            }
            drawPath(moon, Brush.linearGradient(listOf(Color(0xFFF3ECFF), Color(0xFFB39BFF)), Offset(28f, 30f), Offset(70f, 82f)))
            val bars = listOf(55f to 10f, 62.5f to 20f, 70f to 28f, 77.5f to 16f)
            bars.forEach { (x, h) ->
                drawRoundRect(
                    Brush.verticalGradient(listOf(Color(0xFFFFD6F5), Color(0xFF9F7BFF)), 46f - h / 2, 46f + h / 2),
                    topLeft = Offset(x - 2.4f, 46f - h / 2),
                    size = Size(4.8f, h),
                    cornerRadius = CornerRadius(2.4f),
                )
            }
            }
        }
    }
}
