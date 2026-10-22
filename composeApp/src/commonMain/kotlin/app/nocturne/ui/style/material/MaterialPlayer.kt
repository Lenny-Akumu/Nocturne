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
import androidx.compose.runtime.mutableIntStateOf
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
import app.nocturne.data.SeekBarStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

// Full-player pieces for Material You 3 (Expressive): wavy seek bar, morphing play button, toggles, segmented actions.

private val bouncy = spring<Float>(dampingRatio = 0.55f, stiffness = 420f)

// Expressive seek bar. WAVE: the played part is a slow sine that flattens when paused, bar thumb.
// BAR / DOT: flat track with a bar or round thumb. SPECTRUM: OneUI-style hills over the played part driven by the audio levels.
@Composable
fun WavySeekBar(
    progress: Float,
    playing: Boolean,
    onChange: (Float) -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    active: Color = MaterialTheme.colorScheme.primary,
    inactive: Color = MaterialTheme.colorScheme.secondaryContainer,
    style: SeekBarStyle = SeekBarStyle.WAVE,
) {
    var dragging by remember { mutableStateOf(false) }
    val moving = playing && !dragging
    val amp by animateFloatAsState(if (moving) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 120f), label = "amp")
    val thumbH by animateDpAsState(if (dragging) 30.dp else 22.dp, spring(dampingRatio = 0.5f, stiffness = 600f), label = "thumb")
    val thumbW by animateDpAsState(if (dragging) 3.dp else 4.dp, spring(stiffness = 600f), label = "thumbW")
    val dotR by animateDpAsState(if (dragging) 10.dp else 8.dp, spring(dampingRatio = 0.5f, stiffness = 600f), label = "dot")
    var phase by remember { mutableFloatStateOf(0f) }
    var drift by remember { mutableFloatStateOf(0f) }
    val bands = remember { FloatArray(5) }
    var frame by remember { mutableIntStateOf(0) }
    val waving by rememberUpdatedState(moving)
    val animated = style == SeekBarStyle.WAVE || style == SeekBarStyle.SPECTRUM
    LaunchedEffect(moving, style) {
        if (!animated) return@LaunchedEffect
        val player = AppGraph.instance.player
        val synth = app.nocturne.ui.player.SynthLevels()
        var last = 0L
        while (waving || (style == SeekBarStyle.SPECTRUM && bands.any { it > 0.01f })) withFrameNanos { now ->
            val dt = if (last == 0L) 0.016f else ((now - last) / 1e9f).coerceIn(0f, 0.1f)
            last = now
            // Pixel-like: one slow ripple roughly every two seconds
            phase = (phase + dt * 0.5f) % 1f
            drift = (drift + dt * 0.08f) % 1f
            if (style == SeekBarStyle.SPECTRUM) {
                val src = if (waving) player.levels() ?: synth.next(dt) else null
                for (i in bands.indices) {
                    val want = src?.getOrNull(i) ?: 0f
                    bands[i] += (want - bands[i]) * (1f - exp(-dt * if (want > bands[i]) 18f else 5f))
                }
                frame++
            }
        }
    }
    val change by rememberUpdatedState(onChange)
    val finish by rememberUpdatedState(onFinished)
    val path = remember { Path() }
    Box(
        modifier
            .fillMaxWidth()
            .height(if (style == SeekBarStyle.SPECTRUM) 52.dp else 44.dp)
            .pointerInput(Unit) { detectTapGestures { o -> change((o.x / size.width).coerceIn(0f, 1f)); finish() } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> dragging = true; change((o.x / size.width).coerceIn(0f, 1f)) },
                    onDragEnd = { dragging = false; finish() },
                    onDragCancel = { dragging = false; finish() },
                ) { c, _ -> c.consume(); change((c.position.x / size.width).coerceIn(0f, 1f)) }
            }
            .drawBehind {
                frame
                val stroke = 4.dp.toPx()
                val gap = 5.dp.toPx()
                val cy = if (style == SeekBarStyle.SPECTRUM) size.height * 0.68f else size.height / 2
                val left = stroke / 2
                val right = size.width - stroke / 2
                val x = left + (right - left) * progress.coerceIn(0f, 1f)
                val round = style == SeekBarStyle.DOT || style == SeekBarStyle.SPECTRUM
                val half = if (round) dotR.toPx() else thumbW.toPx() / 2
                val activeEnd = if (style == SeekBarStyle.SPECTRUM) x else x - half - gap
                val restStart = if (style == SeekBarStyle.SPECTRUM) x else x + half + gap

                if (style == SeekBarStyle.SPECTRUM && x > left) {
                    val maxH = cy - 4.dp.toPx()
                    val dx = 2.dp.toPx()
                    fun level(u: Float): Float {
                        val f = ((u % 1f) + 1f) % 1f * bands.size
                        val i = f.toInt() % bands.size
                        val k = f - f.toInt()
                        return bands[i] + (bands[(i + 1) % bands.size] - bands[i]) * k
                    }
                    for (layer in 0..1) {
                        path.reset()
                        path.moveTo(left, cy)
                        var px = left
                        while (px < x) {
                            px = minOf(px + dx, x)
                            val u = px / size.width
                            val hill = 0.5f - 0.5f * cos(2 * PI * (u * 5f + (if (layer == 0) drift else -drift * 1.4f) + layer * 0.37f)).toFloat()
                            val edge = (minOf(px - left, x - px) / (24.dp.toPx())).coerceIn(0f, 1f)
                            val h = maxH * level(u * 1.6f + layer * 0.45f) * hill * hill * edge
                            path.lineTo(px, cy - h)
                        }
                        path.lineTo(x, cy)
                        path.close()
                        drawPath(path, if (layer == 0) active.copy(alpha = 0.35f) else active.copy(alpha = 0.6f))
                    }
                }

                if (activeEnd > left) {
                    if (style == SeekBarStyle.WAVE) {
                        val a = 3.dp.toPx() * amp
                        val len = 32.dp.toPx()
                        path.reset()
                        var px = left
                        path.moveTo(px, cy + a * sin(2 * PI * (px / len - phase)).toFloat())
                        val dx = 1.5.dp.toPx()
                        while (px < activeEnd) {
                            px = minOf(px + dx, activeEnd)
                            val near = ((activeEnd - px) / len).coerceIn(0f, 1f)
                            path.lineTo(px, cy + a * near * sin(2 * PI * (px / len - phase)).toFloat())
                        }
                        drawPath(path, active, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    } else drawLine(active, Offset(left, cy), Offset(activeEnd, cy), stroke, StrokeCap.Round)
                }
                if (restStart < right) {
                    drawLine(inactive, Offset(restStart, cy), Offset(right, cy), stroke, StrokeCap.Round)
                    if (style != SeekBarStyle.SPECTRUM) drawCircle(active, stroke / 2, Offset(right, cy))
                }
                when (style) {
                    SeekBarStyle.WAVE, SeekBarStyle.BAR -> {
                        val tw = thumbW.toPx()
                        val h = thumbH.toPx()
                        drawRoundRect(active, Offset(x - tw / 2, cy - h / 2), Size(tw, h), CornerRadius(tw / 2))
                    }
                    SeekBarStyle.DOT -> drawCircle(active, half, Offset(x, cy))
                    SeekBarStyle.SPECTRUM -> {
                        drawCircle(active.copy(alpha = 0.25f), half + 4.dp.toPx(), Offset(x, cy))
                        drawCircle(Color.White, half, Offset(x, cy))
                        drawCircle(active, half - 2.dp.toPx(), Offset(x, cy))
                    }
                }
            },
    )
}

// Seek bar bound to the player's play state (waves only while playing).
@Composable
fun MaterialSeekBar(value: Float, onChange: (Float) -> Unit, onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val state by AppGraph.instance.player.state.collectAsState()
    val settings by AppGraph.instance.settings.state.collectAsState()
    WavySeekBar(value, state.isPlaying, onChange, onFinished, modifier, style = settings.seekBarStyle)
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
    Box(
        modifier.fillMaxSize()
            .graphicsLayer { alpha = if (enabled) 1f else 0.38f }
            .clip(shape)
            .drawBehind { drawOutline(shape.createOutline(size, layoutDirection, this), bg) }
            .toggleable(checked, src, ripple(), enabled, Role.Checkbox) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        glyph(fg, 26.dp)
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
            val h = minOf(if (constraints.hasBoundedHeight) constraints.maxHeight else Int.MAX_VALUE, 68.dp.roundToPx())
            val cw = ((w - gap * (n - 1)) / n).coerceAtLeast(0)
            val placeables = measurables.map { it.measure(Constraints.fixed(cw, h)) }
            val height = maxOf(h, constraints.minHeight)
            layout(w, height) {
                placeables.forEachIndexed { i, p -> p.place(i * (cw + gap), (height - h) / 2) }
            }
        }
    }
}
