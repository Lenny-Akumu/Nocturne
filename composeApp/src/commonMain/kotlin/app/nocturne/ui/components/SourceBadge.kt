package app.nocturne.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nocturne.model.Source

val Source.brandColor: Color
    get() = when (this) {
        Source.YOUTUBE_MUSIC -> Color(0xFFFF0033)
        Source.SPOTIFY -> Color(0xFF1ED760)
        Source.SOUNDCLOUD -> Color(0xFFFF5500)
    }

@Composable
fun SourceBadge(source: Source, modifier: Modifier = Modifier, size: Dp = 16.dp) {
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2
        val c = center
        drawCircle(source.brandColor, r, c)
        when (source) {
            Source.YOUTUBE_MUSIC -> {
                drawCircle(Color.White, r * 0.58f, c, style = Stroke(width = r * 0.12f))
                val t = Path().apply {
                    moveTo(c.x - r * 0.18f, c.y - r * 0.28f)
                    lineTo(c.x + r * 0.30f, c.y)
                    lineTo(c.x - r * 0.18f, c.y + r * 0.28f)
                    close()
                }
                drawPath(t, Color.White)
            }
            Source.SPOTIFY -> {
                val widths = floatArrayOf(0.16f, 0.13f, 0.10f)
                val spans = floatArrayOf(1.10f, 0.92f, 0.72f)
                val ys = floatArrayOf(-0.28f, 0.04f, 0.32f)
                for (i in 0..2) {
                    val w = r * spans[i]
                    val y = c.y + r * ys[i]
                    drawArc(
                        color = Color.Black,
                        startAngle = 200f,
                        sweepAngle = 140f,
                        useCenter = false,
                        topLeft = Offset(c.x - w / 2, y),
                        size = Size(w, w * 0.62f),
                        style = Stroke(width = r * widths[i], cap = StrokeCap.Round),
                    )
                }
            }
            Source.SOUNDCLOUD -> {
                val base = c.y + r * 0.28f
                drawCircle(Color.White, r * 0.26f, Offset(c.x + r * 0.12f, base - r * 0.20f))
                drawCircle(Color.White, r * 0.20f, Offset(c.x + r * 0.42f, base - r * 0.10f))
                drawRect(Color.White, Offset(c.x - r * 0.12f, base - r * 0.10f), Size(r * 0.62f, r * 0.20f))
                for (i in 0..3) {
                    val x = c.x - r * (0.62f - i * 0.14f)
                    val h = r * (0.22f + i * 0.08f)
                    drawLine(Color.White, Offset(x, base + r * 0.08f), Offset(x, base + r * 0.08f - h), strokeWidth = r * 0.07f, cap = StrokeCap.Round)
                }
            }
        }
    }
}
