package app.nocturne.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.takeOrElse
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import app.nocturne.data.DesignStyle
import app.nocturne.model.formatDuration
import app.nocturne.ui.kit.CircularProgressIndicator
import app.nocturne.ui.kit.LocalDesignStyle
import app.nocturne.ui.kit.Slider
import app.nocturne.ui.theme.LocalDarkTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Style-aware player controls: Material You 3, Liquid Glass (Apple Music) and Windows (WinUI media transport).

// Press amount 0..1 that always plays out, even for taps shorter than a frame.
@Composable
internal fun pressAmount(src: InteractionSource): State<Float> {
    val a = remember { Animatable(0f) }
    LaunchedEffect(src) {
        var job: Job? = null
        src.interactions.collect { i ->
            when (i) {
                is PressInteraction.Press -> { job?.cancel(); job = launch { a.animateTo(1f, spring(stiffness = 1400f)) } }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    val prev = job
                    job = launch { prev?.join(); a.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 380f)) }
                }
            }
        }
    }
    return a.asState()
}

// Glass highlight behind a glyph: translucent fill plus a top-lit rim.
private fun Modifier.glassHighlight(shape: Shape, dark: Boolean, amount: () -> Float): Modifier = drawBehind {
    val a = amount().coerceIn(0f, 1f)
    if (a <= 0f) return@drawBehind
    val o = shape.createOutline(size, layoutDirection, this)
    drawOutline(o, if (dark) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.06f), alpha = a)
    drawOutline(
        o, Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) 0.35f else 0.8f), Color.White.copy(alpha = 0.04f))),
        alpha = a, style = Stroke(1.dp.toPx()),
    )
}

// Liquid Glass button: no chrome at rest, springs to 0.9 with a glass shape fading in on press.
@Composable
private fun GlassButton(onClick: () -> Unit, enabled: Boolean, modifier: Modifier, shape: Shape = CircleShape, active: Float = 0f, content: @Composable BoxScope.() -> Unit) {
    val dark = LocalDarkTheme.current
    val src = remember { MutableInteractionSource() }
    val press = pressAmount(src)
    Box(
        modifier
            .graphicsLayer { val s = 1f - 0.1f * press.value; scaleX = s; scaleY = s; alpha = if (enabled) 1f else 0.4f }
            .glassHighlight(shape, dark) { maxOf(press.value, active) }
            .clickable(interactionSource = src, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

// WinUI subtle button: 4dp corners, onSurface 6% on hover, 4% pressed, tooltip on hover.
@Composable
private fun FluentGhost(onClick: () -> Unit, tooltip: String?, modifier: Modifier, box: Dp, enabled: Boolean, content: @Composable BoxScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    val pressed by src.collectIsPressedAsState()
    val bg by animateColorAsState(
        when { pressed -> cs.onSurface.copy(alpha = 0.04f); hovered -> cs.onSurface.copy(alpha = 0.06f); else -> cs.onSurface.copy(alpha = 0f) },
        spring(stiffness = 1200f), label = "ghost",
    )
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(box)
                .alpha(if (enabled) 1f else 0.4f)
                .clip(RoundedCornerShape(4.dp))
                .background(bg)
                .hoverable(src, enabled)
                .clickable(interactionSource = src, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
                .graphicsLayer { alpha = if (pressed) 0.8f else 1f },
            contentAlignment = Alignment.Center,
            content = content,
        )
        FluentTooltip(tooltip, hovered)
    }
}

@Composable
private fun FluentTooltip(text: String?, hovered: Boolean) {
    if (text == null) return
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(hovered) { if (hovered) { delay(600); show = true } else show = false }
    if (!show) return
    val cs = MaterialTheme.colorScheme
    val gap = with(LocalDensity.current) { 6.dp.roundToPx() }
    val shape = RoundedCornerShape(4.dp)
    Popup(popupPositionProvider = remember(gap) { AbovePosition(gap) }) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = cs.onSurface,
            modifier = Modifier.padding(4.dp).shadow(6.dp, shape).background(cs.surfaceContainerHigh, shape)
                .border(1.dp, cs.onSurface.copy(alpha = 0.08f), shape).padding(horizontal = 8.dp, vertical = 5.dp),
        )
    }
}

private class AbovePosition(private val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val x = (anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2).coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val y = (anchorBounds.top - popupContentSize.height - gap).let { if (it < 0) anchorBounds.bottom + gap else it }
        return IntOffset(x, y)
    }
}

// Icon that crossfades (with a small scale pop) whenever the glyph changes.
@Composable
private fun Glyph(icon: ImageVector, contentDescription: String?, size: Dp, tint: Color) {
    val color by animateColorAsState(tint, spring(stiffness = 600f), label = "tint")
    AnimatedContent(
        icon,
        transitionSpec = {
            (fadeIn(tween(160)) + scaleIn(spring(dampingRatio = 0.6f, stiffness = 500f), initialScale = 0.6f)) togetherWith
                (fadeOut(tween(110)) + scaleOut(tween(110), targetScale = 0.6f))
        },
        contentAlignment = Alignment.Center,
        label = "glyph",
    ) { Icon(it, contentDescription, Modifier.size(size), tint = color) }
}

// Plain glyph button (previous/next/like/top bar). [circle] keeps a glass circle behind it in Liquid Glass.
@Composable
fun TransportButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = Dp.Unspecified,
    iconSize: Dp = 24.dp,
    tint: Color = LocalContentColor.current,
    glassIcon: ImageVector = icon,
    enabled: Boolean = true,
    circle: Boolean = false,
) {
    val s = size.takeOrElse { 48.dp }
    when (LocalDesignStyle.current) {
        DesignStyle.MATERIAL -> IconButton(onClick = onClick, modifier = if (size.isSpecified) modifier.size(size) else modifier, enabled = enabled) {
            Icon(icon, contentDescription, Modifier.size(iconSize), tint = tint)
        }
        DesignStyle.GLASS -> GlassButton(onClick, enabled, modifier.size(s).padding(if (circle) 4.dp else 0.dp), active = if (circle) 1f else 0f) {
            Glyph(glassIcon, contentDescription, if (circle) iconSize * 0.8f else iconSize, tint)
        }
        DesignStyle.WINDOWS -> FluentGhost(onClick, contentDescription, modifier.size(s), minOf(s - 8.dp, 44.dp), enabled) {
            Glyph(icon, contentDescription, minOf(iconSize * 0.8f, 28.dp), tint)
        }
    }
}

// Secondary toggle (shuffle/repeat/lyrics/queue...). Glass: glass capsule when on. Windows: accent glyph + indicator.
@Composable
fun ToggleGlyph(checked: Boolean, onClick: () -> Unit, icon: ImageVector, contentDescription: String?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val cs = MaterialTheme.colorScheme
    when (LocalDesignStyle.current) {
        DesignStyle.MATERIAL -> IconToggleButton(checked = checked, onCheckedChange = { onClick() }, modifier = modifier, enabled = enabled) {
            Icon(icon, contentDescription, tint = if (checked) cs.primary else cs.onSurfaceVariant)
        }
        DesignStyle.GLASS -> {
            val on by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 500f), label = "on")
            Box(modifier.size(48.dp), contentAlignment = Alignment.Center) {
                GlassButton(onClick, enabled, Modifier.size(44.dp, 34.dp), RoundedCornerShape(50), on) {
                    Glyph(icon, contentDescription, 22.dp, lerp(cs.onSurfaceVariant, cs.onSurface, on))
                }
            }
        }
        DesignStyle.WINDOWS -> {
            val bar by animateDpAsState(if (checked) 14.dp else 0.dp, spring(dampingRatio = 0.6f, stiffness = 500f), label = "bar")
            FluentGhost(onClick, contentDescription, modifier.size(48.dp), 40.dp, enabled) {
                Glyph(icon, contentDescription, 20.dp, if (checked) cs.primary else cs.onSurfaceVariant)
                if (bar > 0.dp) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp).size(bar, 3.dp).background(cs.primary, CircleShape))
            }
        }
    }
}

// Play/pause. [hero] = full player's big control; otherwise an inline button of [size].
@Composable
fun PlayPauseButton(
    playing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buffering: Boolean = false,
    hero: Boolean = false,
    compact: Boolean = false,
    size: Dp = 44.dp,
    enabled: Boolean = true,
    tint: Color = LocalContentColor.current,
    accent: Color = Color.Unspecified,
) {
    val cs = MaterialTheme.colorScheme
    val icon = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow
    val label = if (playing) "Pause" else "Play"
    val heroMod = if (compact) Modifier.size(width = 68.dp, height = 52.dp) else Modifier.size(width = 88.dp, height = 72.dp)
    when (LocalDesignStyle.current) {
        DesignStyle.MATERIAL -> if (hero) {
            val corner by animateDpAsState(if (playing) 24.dp else 36.dp, label = "corner")
            Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(corner), color = cs.primary, contentColor = cs.onPrimary, modifier = modifier.then(heroMod)) {
                Box(contentAlignment = Alignment.Center) {
                    if (buffering) CircularProgressIndicator(Modifier.size(36.dp), color = cs.onPrimary, strokeWidth = 3.dp)
                    else Glyph(icon, label, 40.dp, cs.onPrimary)
                }
            }
        } else {
            Box(modifier.size(size), contentAlignment = Alignment.Center) {
                if (buffering) CircularProgressIndicator(Modifier.size(size - 4.dp), strokeWidth = 2.dp)
                IconButton(onClick = onClick, enabled = enabled) { Glyph(icon, label, 24.dp, tint) }
            }
        }
        DesignStyle.GLASS -> {
            val d = if (hero) (if (compact) 52.dp else 72.dp) else size
            Box(if (hero) modifier.then(heroMod) else modifier.size(size), contentAlignment = Alignment.Center) {
                GlassButton(onClick, enabled, Modifier.size(d)) {
                    if (buffering) CircularProgressIndicator(Modifier.size(d * 0.6f), color = tint, strokeWidth = if (hero) 3.dp else 2.dp)
                    else Glyph(icon, label, if (hero) (if (compact) 40.dp else 54.dp) else size * 0.64f, tint)
                }
            }
        }
        DesignStyle.WINDOWS -> {
            val d = if (hero) (if (compact) 48.dp else 56.dp) else size - 4.dp
            val base = accent.takeOrElse { cs.primary }
            val fg = if (accent.isSpecified) (if (base.luminance() > 0.5f) Color.Black else Color.White) else cs.onPrimary
            val src = remember { MutableInteractionSource() }
            val hovered by src.collectIsHoveredAsState()
            val pressed by src.collectIsPressedAsState()
            val bg by animateColorAsState(base.copy(alpha = base.alpha * if (pressed) 0.8f else if (hovered) 0.9f else 1f), spring(stiffness = 1200f), label = "play")
            Box(if (hero) modifier.then(heroMod) else modifier.size(size), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(d)
                        .alpha(if (enabled) 1f else 0.4f)
                        .shadow(if (hovered) 4.dp else 2.dp, CircleShape)
                        .clip(CircleShape)
                        .background(bg)
                        .hoverable(src, enabled)
                        .clickable(interactionSource = src, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
                    contentAlignment = Alignment.Center,
                ) {
                    if (buffering) CircularProgressIndicator(Modifier.size(d * 0.55f), color = fg, strokeWidth = 2.dp)
                    else Glyph(icon, label, d * 0.5f, if (pressed) fg.copy(alpha = 0.8f) else fg)
                }
                FluentTooltip(label, hovered)
            }
        }
    }
}

// Apple Music scrubber: 6dp capsule, thickens to 10dp while dragged, no thumb; drag is relative.
@Composable
private fun GlassBar(value: Float, onChange: (Float) -> Unit, onFinished: () -> Unit, modifier: Modifier, onDragging: (Boolean) -> Unit = {}) {
    val cs = MaterialTheme.colorScheme
    var dragging by remember { mutableStateOf(false) }
    val thick by animateDpAsState(if (dragging) 10.dp else 6.dp, spring(dampingRatio = 0.6f, stiffness = 500f), label = "barH")
    val glow by animateFloatAsState(if (dragging) 1f else 0f, spring(stiffness = 500f), label = "barGlow")
    val cur by rememberUpdatedState(value)
    val change by rememberUpdatedState(onChange)
    val finish by rememberUpdatedState(onFinished)
    val drag by rememberUpdatedState(onDragging)
    val active = cs.onSurface
    val track = cs.onSurface.copy(alpha = 0.2f)
    fun set(on: Boolean) { dragging = on; drag(on) }
    Box(
        modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(Unit) { detectTapGestures { o -> change((o.x / size.width).coerceIn(0f, 1f)); finish() } }
            .pointerInput(Unit) {
                var f = 0f
                detectHorizontalDragGestures(
                    onDragStart = { set(true); f = cur },
                    onDragEnd = { set(false); finish() },
                    onDragCancel = { set(false); finish() },
                ) { c, dx -> c.consume(); f = (f + dx / size.width).coerceIn(0f, 1f); change(f) }
            }
            .drawBehind {
                val h = thick.toPx()
                val top = (size.height - h) / 2
                val r = CornerRadius(h / 2)
                drawRoundRect(track, Offset(0f, top), Size(size.width, h), r)
                clipPath(Path().apply { addRoundRect(RoundRect(0f, top, size.width, top + h, r)) }) {
                    drawRect(active.copy(alpha = 0.75f + 0.25f * glow), Offset(0f, top), Size(size.width * value.coerceIn(0f, 1f), h))
                }
            },
    )
}

private fun remaining(pos: Long, dur: Long): String = if (dur <= 0) "--:--" else "-" + (dur - pos).coerceAtLeast(0).let { if (it < 1000) "0:00" else it.formatDuration() }

// Seek slider plus elapsed / total (Glass: elapsed / -remaining) labels.
@Composable
fun PlayerSeekBar(positionMs: Long, durationMs: Long, progress: Float, onSeek: (Long) -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    val style = LocalDesignStyle.current
    var seeking by remember { mutableStateOf<Float?>(null) }
    val duration = durationMs.coerceAtLeast(1)
    val shownPos = seeking?.let { (it * duration).toLong() } ?: positionMs
    val commit = { seeking?.let { onSeek((it * duration).toLong()) }; seeking = null }
    Column(modifier.fillMaxWidth()) {
        if (style == DesignStyle.GLASS) {
            var dragging by remember { mutableStateOf(false) }
            val bright by animateFloatAsState(if (dragging) 0.9f else 0.55f, spring(stiffness = 500f), label = "time")
            GlassBar(seeking ?: progress, { seeking = it }, commit, Modifier.padding(top = if (compact) 2.dp else 12.dp), onDragging = { dragging = it })
            val ts = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium)
            Row {
                Text(shownPos.formatDuration(), style = ts, color = cs.onSurface.copy(alpha = bright))
                Spacer(Modifier.weight(1f))
                Text(remaining(shownPos, durationMs), style = ts, color = cs.onSurface.copy(alpha = bright))
            }
        } else {
            Slider(
                value = seeking ?: progress,
                onValueChange = { seeking = it },
                onValueChangeFinished = commit,
                modifier = Modifier.padding(top = if (compact) 0.dp else 8.dp),
                colors = SliderDefaults.colors(thumbColor = cs.primary, activeTrackColor = cs.primary, inactiveTrackColor = cs.onSurface.copy(alpha = 0.18f)),
            )
            val ts = if (style == DesignStyle.WINDOWS) MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp) else MaterialTheme.typography.labelMedium
            Row(if (style == DesignStyle.WINDOWS) Modifier.padding(horizontal = 4.dp) else Modifier) {
                Text(shownPos.formatDuration(), style = ts, color = cs.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text(durationMs.formatDuration(), style = ts, color = cs.onSurfaceVariant)
            }
        }
    }
}

// Volume slider: Glass capsule bar, kit Slider (Material / Fluent) otherwise.
@Composable
fun PlayerVolumeSlider(value: Float, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier, colors: SliderColors = SliderDefaults.colors()) {
    if (LocalDesignStyle.current == DesignStyle.GLASS) GlassBar(value, onValueChange, {}, modifier)
    else Slider(value = value, onValueChange = onValueChange, modifier = modifier, colors = colors)
}
