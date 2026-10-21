package app.nocturne.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import app.nocturne.data.IconBackground
import app.nocturne.data.IconPalette
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// Keep in sync with tools/icons/gen_icons.py (which renders the launcher/installer assets).
data class IconColors(val arcLight: Color, val arcDeep: Color, val wave0: Color, val wave1: Color, val wave2: Color)

private fun ic(vararg c: Long) = IconColors(Color(c[0]), Color(c[1]), Color(c[2]), Color(c[3]), Color(c[4]))

private val Standard = ic(0xFFD9C8FF, 0xFF7B4DF0, 0xFFE2D4FF, 0xFF9B6BFF, 0xFF4B2A9E)

/** Tones of an accent colour in the same light→deep layout as the standard violet. */
fun iconColorsFrom(accent: Color) = IconColors(
    arcLight = lerp(accent, Color.White, 0.72f),
    arcDeep = lerp(accent, Color.Black, 0.08f),
    wave0 = lerp(accent, Color.White, 0.8f),
    wave1 = lerp(accent, Color.White, 0.12f),
    wave2 = lerp(accent, Color.Black, 0.45f),
)

/** System accent tones (Android 12+ Material You, Windows accent), or null where there is none. */
expect fun systemIconColors(): IconColors?

/** Switches the launcher / home-screen icon where the OS allows it. */
expect fun applyAppIcon(background: IconBackground, palette: IconPalette)

fun IconPalette.colors(): IconColors = when (this) {
    IconPalette.STANDARD -> Standard
    IconPalette.SYSTEM -> systemIconColors() ?: Standard
    IconPalette.OCEAN -> ic(0xFFC9E8FF, 0xFF2F7BEB, 0xFFD6F0FF, 0xFF3DA5FF, 0xFF1B3F9E)
    IconPalette.SUNSET -> ic(0xFFFFD3B8, 0xFFE8506A, 0xFFFFE3C7, 0xFFFF7A59, 0xFFA82C6B)
    IconPalette.EMERALD -> ic(0xFFC6F3DD, 0xFF14A37F, 0xFFD4FBE8, 0xFF34D399, 0xFF0E6B5A)
    IconPalette.ROSE -> ic(0xFFFFCCDB, 0xFFE0386A, 0xFFFFDAE5, 0xFFFF5C8A, 0xFF9A1E46)
    IconPalette.GOLD -> ic(0xFFFFE7A8, 0xFFE89B1C, 0xFFFFF2C9, 0xFFFFBC3D, 0xFFA5600C)
    IconPalette.MONO -> ic(0xFFF0F1F4, 0xFF8C9099, 0xFFFFFFFF, 0xFFBFC3CC, 0xFF5A5E68)
    IconPalette.AURORA -> ic(0xFFC6FFF0, 0xFF8A5CF5, 0xFFD2FFF5, 0xFF4FD1C5, 0xFF7B4DF0)
}

// Wave "M56 100 Q70 72 84 100 T112 100 T144 100" as cubics (Q→C: control points at 2/3).
private fun wavePath() = Path().apply {
    moveTo(56f, 100f)
    cubicTo(65.333f, 81.333f, 74.667f, 81.333f, 84f, 100f)
    cubicTo(93.333f, 118.667f, 102.667f, 118.667f, 112f, 100f)
    cubicTo(121.333f, 81.333f, 132f, 81.333f, 144f, 100f)
}

// Deterministic grain for the acrylic disc.
private val grain: Pair<List<Offset>, List<Offset>> by lazy {
    val r = Random(7)
    fun pts() = List(700) {
        val a = r.nextFloat() * 6.2832f
        val d = sqrt(r.nextFloat()) * 97f
        Offset(100f + d * cos(a), 100f + d * sin(a))
    }
    pts() to pts()
}

/** Draws the app icon (200-unit geometry) scaled to fit this scope. */
fun DrawScope.drawNocturneIcon(background: IconBackground, c: IconColors) {
    val k = size.minDimension / 200f
    scale(k, k, pivot = Offset.Zero) {
        val center = Offset(100f, 100f)
        when (background) {
            IconBackground.SQUARE -> drawRoundRect(Color.Black, size = Size(200f, 200f), cornerRadius = CornerRadius(44f))
            IconBackground.CIRCLE -> drawCircle(Color.Black, 100f, center)
            IconBackground.ACRYLIC -> {
                // Native-acrylic imitation: translucent tinted gradient, palette glow, grain and a light rim.
                drawCircle(Brush.linearGradient(0f to Color(0x8C3A3450), 1f to Color(0xB8121018), start = Offset(30f, 30f), end = Offset(170f, 170f)), 100f, center)
                drawCircle(Brush.radialGradient(0f to c.wave1.copy(alpha = 0.24f), 1f to Color.Transparent, center = Offset(80f, 70f), radius = 120f), 100f, center)
                drawPoints(grain.first, PointMode.Points, Color.White.copy(alpha = 0.06f), strokeWidth = 1.2f)
                drawPoints(grain.second, PointMode.Points, Color.Black.copy(alpha = 0.08f), strokeWidth = 1.2f)
                drawCircle(
                    Brush.linearGradient(0f to Color.White.copy(alpha = 0.38f), 0.5f to Color.White.copy(alpha = 0.05f), 1f to Color.White.copy(alpha = 0.14f), start = Offset.Zero, end = Offset(200f, 200f)),
                    99f, center, style = Stroke(2f),
                )
            }
            IconBackground.NONE -> Unit
        }
        val arc = Stroke(12f, cap = StrokeCap.Round)
        drawArc(c.arcLight, startAngle = -90f, sweepAngle = 90f, useCenter = false, topLeft = Offset(32f, 32f), size = Size(136f, 136f), style = arc)
        drawArc(c.arcDeep, startAngle = 90f, sweepAngle = 90f, useCenter = false, topLeft = Offset(32f, 32f), size = Size(136f, 136f), style = arc)
        // SVG objectBoundingBox gradient (0,0)->(1,1) over the wave, converted to user space.
        drawPath(
            wavePath(),
            Brush.linearGradient(0f to c.wave0, 0.5f to c.wave1, 1f to c.wave2, start = Offset(56f, 86f), end = Offset(72.18f, 136.85f)),
            style = Stroke(14f, cap = StrokeCap.Round),
        )
    }
}

@Composable
fun NocturneIcon(background: IconBackground, palette: IconPalette, modifier: Modifier = Modifier) {
    val colors = remember(palette) { palette.colors() }
    Canvas(modifier) { drawNocturneIcon(background, colors) }
}
