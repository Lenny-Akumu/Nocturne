package app.nocturne.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin

// covers made in the app are stored as a recipe, not a file, so they travel with shared playlists
private const val GEN = "gen:"
private const val SEP = "\u0001"

fun isGeneratedCover(url: String?) = url?.startsWith(GEN) == true

fun mosaicCover(artworks: List<String?>): String? {
    val urls = artworks.filterNotNull().distinct().take(4)
    if (urls.isEmpty()) return null
    return GEN + "mosaic" + SEP + urls.joinToString(SEP)
}

fun gradientCover(style: Int, name: String) = GEN + "grad" + SEP + style + SEP + name.take(40)

val coverPalettes = listOf(
    listOf(Color(0xFF7B2FF7), Color(0xFFF107A3)),
    listOf(Color(0xFF0F9B8E), Color(0xFF2E3192)),
    listOf(Color(0xFFFF8008), Color(0xFFFFC837)),
    listOf(Color(0xFF1D2671), Color(0xFFC33764)),
    listOf(Color(0xFF00C9FF), Color(0xFF92FE9D)),
    listOf(Color(0xFF232526), Color(0xFF6A6A7A)),
    listOf(Color(0xFFE53935), Color(0xFF3B0A45)),
    listOf(Color(0xFF654EA3), Color(0xFFEAAFC8)),
)

@Composable
fun GeneratedCover(spec: String, modifier: Modifier = Modifier) {
    val parts = spec.removePrefix(GEN).split(SEP)
    when (parts.firstOrNull()) {
        "mosaic" -> Mosaic(parts.drop(1), modifier)
        else -> Gradient(parts.getOrNull(1)?.toIntOrNull() ?: 0, parts.getOrNull(2).orEmpty(), modifier)
    }
}

@Composable
private fun Mosaic(urls: List<String>, modifier: Modifier) {
    if (urls.size < 4) { Artwork(urls.firstOrNull(), modifier, androidx.compose.ui.graphics.RectangleShape); return }
    Column(modifier) {
        for (r in 0..1) Row(Modifier.weight(1f)) {
            for (c in 0..1) Artwork(urls[r * 2 + c], Modifier.weight(1f).fillMaxSize(), androidx.compose.ui.graphics.RectangleShape)
        }
    }
}

@Composable
private fun Gradient(style: Int, name: String, modifier: Modifier) {
    val pal = coverPalettes[style.mod(coverPalettes.size)]
    val pattern = (style / coverPalettes.size).mod(3)
    BoxWithConstraints(modifier) {
        val side = maxWidth
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.linearGradient(pal, Offset.Zero, Offset(size.width, size.height)))
            val w = size.width; val h = size.height
            when (pattern) {
                0 -> for (i in 0..5) {
                    val path = Path()
                    val y0 = h * (0.35f + i * 0.11f)
                    path.moveTo(0f, y0)
                    var x = 0f
                    while (x <= w) { path.lineTo(x, y0 + sin(x / w * 2 * PI + i).toFloat() * h * 0.06f); x += w / 40 }
                    drawPath(path, Color.White.copy(alpha = 0.10f + i * 0.02f), style = Stroke(width = w * 0.012f))
                }
                1 -> for (i in 1..6) drawCircle(Color.White.copy(alpha = 0.07f), radius = w * 0.13f * i, center = Offset(w * 0.85f, h * 0.15f), style = Stroke(w * 0.02f))
                else -> for (i in -6..12) drawLine(Color.Black.copy(alpha = 0.10f), Offset(i * w / 6, h), Offset(i * w / 6 + w * 0.6f, 0f), strokeWidth = w * 0.04f)
            }
        }
        if (name.isNotBlank()) Box(Modifier.fillMaxSize().padding(side * 0.08f), contentAlignment = Alignment.BottomStart) {
            Text(
                name, color = Color.White, fontWeight = FontWeight.Black, maxLines = 3, overflow = TextOverflow.Ellipsis,
                fontSize = (side.value * 0.13f).sp, lineHeight = (side.value * 0.145f).sp,
            )
        }
    }
}
