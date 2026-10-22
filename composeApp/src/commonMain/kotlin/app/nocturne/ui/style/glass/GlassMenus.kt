package app.nocturne.ui.style.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nocturne.ui.LocalInGlassSource
import app.nocturne.ui.LocalGlass
import app.nocturne.ui.PlatformBackHandler
import app.nocturne.ui.theme.LocalDarkTheme
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import kotlin.math.roundToInt

// Last pointer-down position (root coordinates), so menus can grow out of the touch point.
object GlassTouch {
    var last: Offset? = null
}

// Records every press on the way down (Initial pass, never consumes).
fun Modifier.glassTouchTracker(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val e = awaitPointerEvent(PointerEventPass.Initial)
            e.changes.firstOrNull { it.pressed && !it.previousPressed }?.let { GlassTouch.last = it.position }
        }
    }
}

// iOS context menu: blurred dim backdrop, glass panel anchored at [anchor] that springs out of it (scale + blur),
// shrinks back on dismiss and then calls [onGone]. Without an anchor it rises from the bottom like an action sheet.
@Composable
fun GlassMenuOverlay(visible: Boolean, anchor: Offset?, onDismiss: () -> Unit, onGone: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val p = remember { Animatable(0f) }
    val gone by rememberUpdatedState(onGone)
    LaunchedEffect(visible) {
        if (visible) p.animateTo(1f, spring(dampingRatio = 0.76f, stiffness = 380f))
        else { p.animateTo(0f, tween(170, easing = FastOutLinearInEasing)); gone() }
    }
    PlatformBackHandler(enabled = visible) { onDismiss() }
    val haze = LocalGlass.current
    val dark = LocalDarkTheme.current
    val cs = MaterialTheme.colorScheme
    val insets = WindowInsets.safeDrawing
    val shape = RoundedCornerShape(26.dp)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { alpha = p.value.coerceIn(0f, 1f) }
                .then(
                    if (haze != null) Modifier.hazeEffect(
                        haze,
                        HazeStyle(backgroundColor = cs.surface, tints = listOf(HazeTint(Color.Black.copy(alpha = if (dark) 0.35f else 0.15f))), blurRadius = 16.dp, noiseFactor = 0f),
                    ) else Modifier.background(Color.Black.copy(alpha = if (dark) 0.45f else 0.25f)),
                )
                .pointerInput(visible) { if (visible) detectTapGestures { onDismiss() } },
        )
        val menuW = minOf(260.dp, maxWidth - 32.dp)
        val maxH = maxHeight - 40.dp
        Layout(
            content = {
                CompositionLocalProvider(LocalInGlassSource provides false) {
                    Column(
                        Modifier.width(menuW).heightIn(max = maxH)
                            .glassPane(shape, elevation = 30.dp, overlay = true)
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 4.dp),
                        content = content,
                    )
                }
            },
        ) { measurables, constraints ->
            val pl = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
            val w = constraints.maxWidth
            val h = constraints.maxHeight
            val top = insets.getTop(this) + 12.dp.roundToPx()
            val bottom = h - insets.getBottom(this) - 12.dp.roundToPx()
            val gap = 10.dp.roundToPx()
            val side = 16.dp.roundToPx()
            val a = anchor
            val x: Int
            val y: Int
            if (a == null) {
                x = (w - pl.width) / 2
                y = bottom - pl.height
            } else {
                val ax = a.x.roundToInt()
                val ay = a.y.roundToInt()
                // Align the near edge with the finger; open downward when there is room, else upward.
                x = (if (ax > w / 2) ax + gap - pl.width else ax - gap).coerceIn(side, (w - side - pl.width).coerceAtLeast(side))
                y = (if (ay + gap + pl.height <= bottom) ay + gap else ay - gap - pl.height).coerceIn(top, (bottom - pl.height).coerceAtLeast(top))
            }
            val ox = if (a == null) 0.5f else ((a.x - x) / pl.width.coerceAtLeast(1)).coerceIn(0f, 1f)
            val oy = if (a == null) 1f else ((a.y - y) / pl.height.coerceAtLeast(1)).coerceIn(0f, 1f)
            layout(w, h) {
                pl.placeWithLayer(x, y) {
                    val v = p.value
                    val s = 0.25f + 0.75f * v
                    scaleX = s
                    scaleY = s
                    alpha = (v * 1.6f).coerceIn(0f, 1f)
                    transformOrigin = TransformOrigin(ox, oy)
                    val r = (1f - v).coerceIn(0f, 1f) * 14.dp.toPx()
                    renderEffect = if (r > 0.5f) BlurEffect(r, r, TileMode.Decal) else null
                }
            }
        }
    }
}

// Menu row: label left, symbol right, 44pt min height; red for destructive actions; glass highlight while pressed.
@Composable
fun GlassMenuRow(icon: ImageVector?, label: String, destructive: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val fg = (if (destructive) cs.error else cs.onSurface).copy(alpha = if (enabled) 1f else 0.4f)
    val hl = cs.onSurface.copy(alpha = 0.1f)
    val hair = cs.onSurface.copy(alpha = 0.1f)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 46.dp)
            .drawBehind {
                if (pressed) drawRect(hl)
                drawRect(hair, Offset(0f, 0f), Size(size.width, 0.5.dp.toPx()))
            }
            .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
            .padding(start = 16.dp, end = 14.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = fg, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (icon != null) {
            Spacer(Modifier.width(12.dp))
            Icon(icon, null, Modifier.size(20.dp), tint = fg)
        }
    }
}

// Thick group separator between menu sections.
@Composable
fun GlassMenuGap() {
    val dark = LocalDarkTheme.current
    Box(Modifier.fillMaxWidth().padding(top = 4.dp).height(8.dp).background(if (dark) Color.Black.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.06f)))
}
