package app.nocturne.ui.style.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import app.nocturne.ui.kit.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nocturne.ui.LocalGlass
import app.nocturne.ui.LocalInGlassSource
import app.nocturne.ui.glassSheen
import app.nocturne.ui.player.pressAmount
import app.nocturne.ui.theme.LocalDarkTheme
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import kotlinx.coroutines.delay

// iOS 26 Liquid Glass building blocks for DesignStyle.GLASS (iPhone "Tinted glass" design).

// Shared chrome state: tab bar minimizes on scroll down, expands on scroll up; inline nav-bar title.
object GlassChrome {
    var minimized by mutableStateOf(false)
    var inlineTitle by mutableStateOf<String?>(null)
    var thresholdPx = 60f
    private var acc = 0f

    // Observes only (consumes nothing), so lists, pagers and pull-to-refresh behave as usual.
    val scroll = object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            val d = consumed.y + available.y.coerceAtLeast(0f)
            if (d == 0f) return Offset.Zero
            if ((d < 0f) != (acc < 0f)) acc = 0f
            acc += d
            if (acc < -thresholdPx) minimized = true else if (acc > thresholdPx * 0.5f) minimized = false
            return Offset.Zero
        }
    }
}

// Glass material: real backdrop blur when it can sample (outside a glass source), else a clear translucent pane
// (over the player's gradient or artwork a blur would look the same). [tint] colors the glass (tinted glass).
@Composable
fun Modifier.glassPane(shape: Shape, tint: Color = Color.Unspecified, elevation: Dp = 10.dp, overlay: Boolean = false): Modifier {
    val state = LocalGlass.current
    val dark = LocalDarkTheme.current
    val surface = MaterialTheme.colorScheme.surface
    val inSource = LocalInGlassSource.current
    val base = this
        .then(if (elevation > 0.dp) Modifier.shadow(elevation, shape, ambientColor = Color.Black.copy(alpha = 0.2f), spotColor = Color.Black.copy(alpha = 0.32f)) else Modifier)
        .clip(shape)
    val fill = if (state != null && (overlay || !inSource)) {
        base.hazeEffect(
            state,
            HazeStyle(
                backgroundColor = surface,
                // Denser on overlays and in light mode so body text keeps its contrast over bright content.
                tints = listOf(HazeTint(surface.copy(alpha = if (overlay) (if (dark) 0.62f else 0.66f) else if (dark) 0.42f else 0.55f))),
                blurRadius = 22.dp,
                noiseFactor = 0.04f,
            ),
        )
    } else {
        base.background(
            if (dark) Brush.linearGradient(listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0.09f)))
            else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.72f), Color.White.copy(alpha = 0.5f), Color.White.copy(alpha = 0.62f))),
        )
    }
    val tinted = if (tint.isSpecified) fill.drawBehind {
        drawRect(Brush.linearGradient(listOf(tint, tint.copy(alpha = tint.alpha * 0.35f), tint.copy(alpha = tint.alpha * 0.6f)), Offset.Zero, Offset(size.width, size.height)))
    } else fill
    return tinted.glassSheen(shape, dark)
}

// Pressable glass: springs to ~0.95 and brightens while held (iOS 26 glass press response).
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    tint: Color = Color.Unspecified,
    elevation: Dp = 10.dp,
    overlay: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    contentDescription: String? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val press = pressAmount(src)
    val bright = if (LocalDarkTheme.current) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.35f)
    Box(
        modifier
            .graphicsLayer { val s = 1f - 0.05f * press.value; scaleX = s; scaleY = s }
            .glassPane(shape, tint, elevation, overlay)
            .drawBehind { val a = press.value.coerceIn(0f, 1f); if (a > 0f) drawRect(bright, alpha = a) }
            .then(
                if (onClick != null) Modifier.combinedClickable(
                    interactionSource = src, indication = null, role = Role.Button, onClickLabel = contentDescription,
                    onLongClick = onLongClick, onClick = onClick,
                ) else Modifier,
            ),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

// Round glass button (44pt touch target minimum).
@Composable
fun GlassCircle(icon: ImageVector, contentDescription: String?, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 44.dp, tint: Color = Color.Unspecified, iconTint: Color = MaterialTheme.colorScheme.onSurface) {
    GlassSurface(modifier.size(size), CircleShape, tint, onClick = onClick, contentDescription = contentDescription) {
        Icon(icon, contentDescription, Modifier.size(size * 0.5f), tint = iconTint)
    }
}

// Glyph button living inside a glass container: inner glass highlight on press or when [selected].
@Composable
fun GlassInnerButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    shape: Shape = CircleShape,
    contentDescription: String? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val press = pressAmount(src)
    val sel by androidx.compose.animation.core.animateFloatAsState(if (selected) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 500f), label = "sel")
    val dark = LocalDarkTheme.current
    val fill = if (dark) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.07f)
    val rim = Color.White.copy(alpha = if (dark) 0.3f else 0.8f)
    Box(
        modifier
            .graphicsLayer { val s = 1f - 0.08f * press.value; scaleX = s; scaleY = s }
            .drawBehind {
                val a = maxOf(sel, press.value * 0.7f).coerceIn(0f, 1f)
                if (a <= 0f) return@drawBehind
                val o = shape.createOutline(size, layoutDirection, this)
                drawOutline(o, fill, alpha = a)
                drawOutline(o, Brush.verticalGradient(listOf(rim, Color.Transparent)), alpha = a, style = Stroke(1.dp.toPx()))
            }
            .combinedClickable(interactionSource = src, indication = null, role = Role.Button, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

// Lensing entrance: glass swells in from 86% with a blur that clears (spring overshoot = liquid bounce).
@Composable
fun Modifier.glassAppear(delayMillis: Int = 0): Modifier {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (delayMillis > 0) delay(delayMillis.toLong())
        p.animateTo(1f, spring(dampingRatio = 0.68f, stiffness = 300f))
    }
    return graphicsLayer {
        val v = p.value
        val s = 0.86f + 0.14f * v
        scaleX = s
        scaleY = s
        alpha = v.coerceIn(0f, 1f)
        val r = (1f - v).coerceIn(0f, 1f) * 10.dp.toPx()
        renderEffect = if (r > 0.5f) BlurEffect(r, r, TileMode.Decal) else null
    }
}

// Avatar-style settings entry next to large titles.
@Composable
fun GlassAvatar(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val src = remember { MutableInteractionSource() }
    val press = pressAmount(src)
    Box(
        modifier
            .size(44.dp)
            .graphicsLayer { val s = 1f - 0.08f * press.value; scaleX = s; scaleY = s }
            .combinedClickable(interactionSource = src, indication = null, role = Role.Button, onClickLabel = "Settings", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        app.nocturne.ui.components.ProfileAvatar(38.dp, gradient = true)
    }
}
