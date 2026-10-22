package app.nocturne.ui.style.material

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import app.nocturne.ui.kit.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nocturne.AppGraph
import app.nocturne.data.DesignStyle
import app.nocturne.i18n.t
import app.nocturne.ui.kit.LocalDesignStyle
import app.nocturne.ui.player.pressAmount
import kotlin.math.PI
import kotlin.math.sin

// Full-player pieces for Material You 3 (Expressive): wavy seek bar, morphing play button, toggles, segmented actions.

private val bouncy = spring<Float>(dampingRatio = 0.55f, stiffness = 420f)

// Expressive wavy seek bar: the played part is a sine wave that flows while playing and flattens when paused;
// a vertical bar thumb with gaps, flat inactive track and an end stop dot.
@Composable
fun WavySeekBar(
    progress: Float,
    playing: Boolean,
    onChange: (Float) -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    active: Color = MaterialTheme.colorScheme.primary,
    inactive: Color = MaterialTheme.colorScheme.secondaryContainer,
) {
    var dragging by remember { mutableStateOf(false) }
    val amp by animateFloatAsState(if (playing && !dragging) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 120f), label = "amp")
    val thumbH by animateDpAsState(if (dragging) 30.dp else 22.dp, spring(dampingRatio = 0.5f, stiffness = 600f), label = "thumb")
    val thumbW by animateDpAsState(if (dragging) 3.dp else 4.dp, spring(stiffness = 600f), label = "thumbW")
    var phase by remember { mutableFloatStateOf(0f) }
    val waving by rememberUpdatedState(playing && !dragging)
    LaunchedEffect(playing, dragging) {
        var last = 0L
        while (waving) withFrameNanos { now ->
            if (last != 0L) phase = (phase + (now - last) / 1e9f * 2.2f) % 1f
            last = now
        }
    }
    val change by rememberUpdatedState(onChange)
    val finish by rememberUpdatedState(onFinished)
    val path = remember { Path() }
    Box(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .pointerInput(Unit) { detectTapGestures { o -> change((o.x / size.width).coerceIn(0f, 1f)); finish() } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> dragging = true; change((o.x / size.width).coerceIn(0f, 1f)) },
                    onDragEnd = { dragging = false; finish() },
                    onDragCancel = { dragging = false; finish() },
                ) { c, _ -> c.consume(); change((c.position.x / size.width).coerceIn(0f, 1f)) }
            }
            .drawBehind {
                val stroke = 4.dp.toPx()
                val tw = thumbW.toPx()
                val gap = 5.dp.toPx()
                val cy = size.height / 2
                val left = stroke / 2
                val right = size.width - stroke / 2
                val x = left + (right - left) * progress.coerceIn(0f, 1f)
                // Played wave, ending a gap before the thumb.
                val waveEnd = x - tw / 2 - gap
                if (waveEnd > left) {
                    val a = 3.dp.toPx() * amp
                    val len = 28.dp.toPx()
                    path.reset()
                    var px = left
                    path.moveTo(px, cy + a * sin(2 * PI * (px / len - phase)).toFloat())
                    val dx = 1.5.dp.toPx()
                    while (px < waveEnd) {
                        px = minOf(px + dx, waveEnd)
                        // Ease the wave into a flat line right next to the thumb.
                        val near = ((waveEnd - px) / len).coerceIn(0f, 1f)
                        path.lineTo(px, cy + a * near * sin(2 * PI * (px / len - phase)).toFloat())
                    }
                    drawPath(path, active, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                // Remaining track and stop dot.
                val restStart = x + tw / 2 + gap
                if (restStart < right) {
                    drawLine(inactive, Offset(restStart, cy), Offset(right, cy), stroke, StrokeCap.Round)
                    drawCircle(active, stroke / 2, Offset(right, cy))
                }
                val h = thumbH.toPx()
                drawRoundRect(active, Offset(x - tw / 2, cy - h / 2), Size(tw, h), CornerRadius(tw / 2))
            },
    )
}

// Seek bar bound to the player's play state (waves only while playing).
@Composable
fun MaterialSeekBar(value: Float, onChange: (Float) -> Unit, onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val state by AppGraph.instance.player.state.collectAsState()
    WavySeekBar(value, state.isPlaying, onChange, onFinished, modifier)
}

// Hero play/pause: a wide pill while playing that springs into a rounded square when paused; squishes on press.
@Composable
fun MorphPlayButton(playing: Boolean, buffering: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, compact: Boolean = false, enabled: Boolean = true) {
    val cs = MaterialTheme.colorScheme
    val src = remember { MutableInteractionSource() }
    val press by pressAmount(src)
    val h = if (compact) 52.dp else 68.dp
    val w by animateDpAsState(if (playing) h * 1.55f else h * 1.05f, spring(dampingRatio = 0.6f, stiffness = 380f), label = "w")
    val frac by animateFloatAsState(if (playing) 0.5f else 0.3f, bouncy, label = "corner")
    val shape = FracShape(frac - (frac - 0.16f) * press)
    Box(
        modifier
            .size(w, h)
            .graphicsLayer { val s = 1f - 0.06f * press; scaleX = s; scaleY = s; alpha = if (enabled) 1f else 0.38f }
            .clip(shape)
            .drawBehind { drawOutline(shape.createOutline(size, layoutDirection, this), cs.primary) }
            .clickable(interactionSource = src, indication = ripple(color = cs.onPrimary), enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (buffering) LoadingIndicator(Modifier.size(h * 0.55f), cs.onPrimary)
        else MorphGlyph(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pause" else "Play", h * 0.5f, cs.onPrimary)
    }
}

// Icon swap with a springy scale and a quarter turn.
@Composable
fun MorphGlyph(icon: ImageVector, contentDescription: String?, size: Dp, tint: Color) {
    AnimatedContent(
        icon,
        transitionSpec = {
            (fadeIn(tween(140)) + scaleIn(spring(dampingRatio = 0.5f, stiffness = 500f), initialScale = 0.5f)) togetherWith
                (fadeOut(tween(100)) + scaleOut(tween(100), targetScale = 0.5f))
        },
        contentAlignment = Alignment.Center,
        label = "glyph",
    ) { Icon(it, contentDescription, Modifier.size(size), tint = tint) }
}

// Inside [ActionsLayout]: toggles render as labelled segments.
val LocalSegmented = staticCompositionLocalOf { false }

// Secondary toggle: tonal circle when on (shuffle/repeat), or a segment of the action group.
@Composable
fun MaterialToggle(checked: Boolean, onClick: () -> Unit, label: String?, modifier: Modifier, enabled: Boolean, glyph: @Composable (Color, Dp) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val segmented = LocalSegmented.current
    val src = remember { MutableInteractionSource() }
    val press by pressAmount(src)
    val on by animateFloatAsState(if (checked) 1f else 0f, bouncy, label = "on")
    val bg by animateColorAsState(
        if (checked) cs.secondaryContainer else if (segmented) cs.surfaceContainerHighest.copy(alpha = 0.6f) else cs.secondaryContainer.copy(alpha = 0f),
        label = "bg",
    )
    val fg by animateColorAsState(if (checked) cs.onSecondaryContainer else cs.onSurfaceVariant, label = "fg")
    if (!segmented) {
        Box(modifier.size(48.dp), contentAlignment = Alignment.Center) {
            val shape = FracShape(0.5f - 0.2f * press)
            Box(
                Modifier.size(44.dp)
                    .graphicsLayer { val s = (1f - 0.08f * press) * (0.9f + 0.1f * on); scaleX = s; scaleY = s; alpha = if (enabled) 1f else 0.38f }
                    .clip(shape)
                    .drawBehind { drawOutline(shape.createOutline(size, layoutDirection, this), bg) }
                    .toggleable(checked, src, ripple(), enabled, Role.Checkbox) { onClick() },
                contentAlignment = Alignment.Center,
            ) { glyph(fg, 24.dp) }
        }
        return
    }
    // Segment: selected one rounds into a pill, press squishes corners.
    val corner = (0.24f + 0.26f * on) * (1f - 0.45f * press)
    val shape = FracShape(corner)
    BoxWithConstraints(
        modifier.fillMaxSize()
            .graphicsLayer { alpha = if (enabled) 1f else 0.38f }
            .clip(shape)
            .drawBehind { drawOutline(shape.createOutline(size, layoutDirection, this), bg) }
            .toggleable(checked, src, ripple(), enabled, Role.Checkbox) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        val roomy = maxWidth >= 76.dp
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            glyph(fg, 20.dp)
            if (label != null && roomy) {
                Spacer(Modifier.width(6.dp))
                Text(t(label), style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// Player action row: Material lays children out as an equal-width segmented button group.
@Composable
fun ActionsLayout(modifier: Modifier, content: @Composable () -> Unit) {
    if (LocalDesignStyle.current != DesignStyle.MATERIAL) {
        Row(modifier, horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) { content() }
        return
    }
    CompositionLocalProvider(LocalSegmented provides true) {
        Layout(content, modifier) { measurables, constraints ->
            val n = measurables.size.coerceAtLeast(1)
            val gap = 4.dp.roundToPx()
            val w = if (constraints.hasBoundedWidth) constraints.maxWidth else 360.dp.roundToPx()
            val h = minOf(if (constraints.hasBoundedHeight) constraints.maxHeight else Int.MAX_VALUE, 48.dp.roundToPx())
            val cw = ((w - gap * (n - 1)) / n).coerceAtLeast(0)
            val placeables = measurables.map { it.measure(Constraints.fixed(cw, h)) }
            val height = maxOf(h, constraints.minHeight)
            layout(w, height) {
                placeables.forEachIndexed { i, p -> p.place(i * (cw + gap), (height - h) / 2) }
            }
        }
    }
}
