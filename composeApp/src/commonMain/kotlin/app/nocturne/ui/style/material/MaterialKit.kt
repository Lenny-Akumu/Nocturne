package app.nocturne.ui.style.material

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.nocturne.ui.player.pressAmount
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Material 3 Expressive building blocks (the CMP material3 in use has no public expressive APIs).

// Rounded rect whose corner radius is a fraction (0..0.5) of the shorter side, so it can morph pill <-> square.
data class FracShape(val frac: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(RoundRect(size.toRect(), CornerRadius(size.minDimension * frac.coerceIn(0f, 0.5f))))
}

private val morphSpring = spring<Float>(dampingRatio = 0.55f, stiffness = 650f)

// Pill shapes squish to rounder squares while pressed; any other shape is kept as is.
@Composable
fun morphShape(shape: Shape, src: InteractionSource, pressed: Float = 0.22f): Shape {
    if (shape != CircleShape) return shape
    val down by src.collectIsPressedAsState()
    val f by animateFloatAsState(if (down) pressed else 0.5f, morphSpring, label = "morph")
    return FracShape(f)
}

// Icon button: pressed state morphs a circle into a rounded square and squishes slightly.
@Composable
fun ExpressiveIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 48.dp,
    container: Color = Color.Transparent,
    content: @Composable BoxScope.() -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val press by pressAmount(src)
    val shape = FracShape(0.5f - 0.22f * press)
    Box(
        modifier
            .size(size)
            .graphicsLayer { val s = 1f - 0.08f * press; scaleX = s; scaleY = s; alpha = if (enabled) 1f else 0.38f }
            .clip(shape)
            .drawBehind { if (container.alpha > 0f) drawOutline(shape.createOutline(this.size, layoutDirection, this), container) }
            .clickable(interactionSource = src, indication = ripple(), enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

// Lobed polar shape: radius 1 at the tips, 1 - depth between lobes.
private class Lobes(val count: Int, val depth: Float) {
    fun r(theta: Float): Float = 1f - depth * (1f - cos(count * theta)) / 2f
}

private val LoaderShapes = listOf(Lobes(8, 0.16f), Lobes(4, 0.26f), Lobes(5, 0.2f), Lobes(2, 0.3f), Lobes(12, 0.1f), Lobes(6, 0.2f))

// M3 Expressive LoadingIndicator look-alike: a rotating polygon that morphs through rounded shapes.
@Composable
fun LoadingIndicator(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val n = LoaderShapes.size
    val t = rememberInfiniteTransition(label = "loading")
    val rot by t.animateFloat(0f, 360f, infiniteRepeatable(tween(4600, easing = LinearEasing)), label = "rot")
    val step by t.animateFloat(0f, n.toFloat(), infiniteRepeatable(tween(650 * n, easing = LinearEasing)), label = "step")
    val path = remember { Path() }
    Canvas(modifier.progressSemantics().size(40.dp)) {
        val i = step.toInt().coerceIn(0, n - 1)
        // Hold each shape briefly, then morph with an eased curve.
        val f = FastOutSlowInEasing.transform(((step - i) * 1.4f - 0.4f).coerceIn(0f, 1f))
        val a = LoaderShapes[i]
        val b = LoaderShapes[(i + 1) % n]
        val r = size.minDimension / 2f * 0.9f
        val c = center
        path.reset()
        val steps = 120
        for (s in 0..steps) {
            val th = (2.0 * PI * s / steps).toFloat()
            val rr = r * (a.r(th) * (1f - f) + b.r(th) * f)
            val x = c.x + rr * cos(th)
            val y = c.y + rr * sin(th)
            if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        rotate(rot + (i + f) * 45f) { drawPath(path, color) }
    }
}
