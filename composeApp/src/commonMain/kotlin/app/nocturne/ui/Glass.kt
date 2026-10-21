package app.nocturne.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nocturne.ui.theme.LocalDarkTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

// Root haze state; non-null only in the Liquid Glass style.
val LocalGlass = staticCompositionLocalOf<HazeState?> { null }

// True inside a glass source: effects there can't sample their own source, so they fake the blur.
val LocalInGlassSource = staticCompositionLocalOf { false }

// Content that glass overlays blur; higher [zIndex] layers draw on top.
@Composable
fun GlassSource(modifier: Modifier = Modifier, zIndex: Float = 0f, content: @Composable () -> Unit) {
    val state = LocalGlass.current
    Box(if (state != null) modifier.hazeSource(state, zIndex) else modifier) {
        CompositionLocalProvider(LocalInGlassSource provides (state != null), content = content)
    }
}

// iOS 26 Liquid Glass: background blur + tint + noise, inner refraction sheen, specular rim, soft shadow.
// [overlay] = drawn in another window (dialog / sheet), so it can always sample the source.
@Composable
fun Modifier.liquidGlass(shape: Shape, elevation: Dp = 16.dp, overlay: Boolean = false): Modifier {
    val state = LocalGlass.current
    val dark = LocalDarkTheme.current
    val surface = MaterialTheme.colorScheme.surface
    val base = this
        .shadow(elevation, shape, ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.3f))
        .clip(shape)
    val fill = if (state != null && (overlay || !LocalInGlassSource.current)) {
        base.hazeEffect(
            state,
            HazeStyle(
                backgroundColor = surface,
                tints = listOf(HazeTint(surface.copy(alpha = if (dark) 0.36f else 0.42f))),
                blurRadius = 24.dp,
                noiseFactor = 0.05f,
            ),
        )
    } else {
        // Frosted fill without a sample (iOS secondary system fill tones).
        base.background(if (dark) Color(0xFF2C2C2E).copy(alpha = 0.8f) else Color.White.copy(alpha = 0.82f))
    }
    return fill.glassSheen(shape, dark)
}

// Refraction gradient behind content and a thin white rim (35% → 0%) lit from the top.
fun Modifier.glassSheen(shape: Shape, dark: Boolean): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val sheen = Brush.verticalGradient(
        0f to Color.White.copy(alpha = if (dark) 0.09f else 0.24f),
        0.45f to Color.Transparent,
        1f to Color.White.copy(alpha = if (dark) 0.03f else 0.08f),
    )
    val rim = Brush.linearGradient(
        0f to Color.White.copy(alpha = 0.35f),
        0.5f to Color.White.copy(alpha = 0f),
        1f to Color.White.copy(alpha = if (dark) 0.1f else 0.18f),
        start = Offset.Zero,
        end = Offset(size.width * 0.35f, size.height),
    )
    val w = 1.dp.toPx() * 2 // half of it is clipped by the shape
    onDrawWithContent {
        drawOutline(outline, sheen)
        drawContent()
        drawOutline(outline, rim, style = Stroke(w))
    }
}
