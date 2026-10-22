package app.nocturne.ui.kit

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.RadioButtonColors
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.SelectableChipElevation
import androidx.compose.material3.SheetState
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.nocturne.data.DesignStyle
import app.nocturne.ui.LocalGlass
import app.nocturne.ui.LocalInGlassSource
import app.nocturne.ui.glassSheen
import app.nocturne.ui.liquidGlass
import app.nocturne.ui.theme.LocalDarkTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// Which look the kit renders: Material 3, iOS Liquid Glass or Windows Fluent.
val LocalDesignStyle = staticCompositionLocalOf { DesignStyle.MATERIAL }

// 0 = not in an alert, 1 = alert dismiss cell, 2 = alert confirm cell.
private val LocalAlertCell = staticCompositionLocalOf { 0 }

private val IosGreen = Color(0xFF34C759)

// WinUI control fills and strokes.
private class Fluent(dark: Boolean) {
    val fill = if (dark) Color.White.copy(alpha = 0.061f) else Color.White.copy(alpha = 0.7f)
    val fillHover = if (dark) Color.White.copy(alpha = 0.084f) else Color(0xFFF9F9F9).copy(alpha = 0.5f)
    val fillPressed = if (dark) Color.White.copy(alpha = 0.033f) else Color(0xFFF9F9F9).copy(alpha = 0.3f)
    val subtleHover = if (dark) Color.White.copy(alpha = 0.061f) else Color.Black.copy(alpha = 0.037f)
    val subtlePressed = if (dark) Color.White.copy(alpha = 0.042f) else Color.Black.copy(alpha = 0.024f)
    val strokeTop = if (dark) Color.White.copy(alpha = 0.07f) else Color.Black.copy(alpha = 0.057f)
    val strokeBottom = if (dark) Color.White.copy(alpha = 0.035f) else Color.Black.copy(alpha = 0.16f)
    val strong = if (dark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.45f)
}

@Composable
private fun fluent() = Fluent(LocalDarkTheme.current)

// 1px elevation border: [bottom] colors the last 3dp, like WinUI's ControlElevationBorder.
private fun Modifier.fluentBorder(shape: Shape, top: Color, bottom: Color): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val brush = Brush.verticalGradient(listOf(top, bottom), startY = size.height - 3.dp.toPx(), endY = size.height)
    val w = 1.dp.toPx() * 2 // half is clipped by the shape
    onDrawWithContent {
        drawContent()
        drawOutline(outline, brush, style = Stroke(w))
    }
}

// Glass fill for in-content controls; overlays use [liquidGlass].
@Composable
private fun glassFill(): Color = if (LocalDarkTheme.current) Color(0xFF2C2C2E).copy(alpha = 0.8f) else Color.White.copy(alpha = 0.82f)

@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    thumbContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    colors: SwitchColors = SwitchDefaults.colors(),
    interactionSource: MutableInteractionSource? = null,
) {
    when (LocalDesignStyle.current) {
        DesignStyle.MATERIAL -> androidx.compose.material3.Switch(
            checked = checked, onCheckedChange = onCheckedChange, modifier = modifier, thumbContent = thumbContent,
            enabled = enabled, colors = colors, interactionSource = interactionSource,
        )
        DesignStyle.WINDOWS -> FluentSwitch(checked, onCheckedChange, modifier, enabled, interactionSource)
        DesignStyle.GLASS -> IosSwitch(checked, onCheckedChange, modifier, enabled, interactionSource)
    }
}

@Composable
private fun IosSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier, enabled: Boolean, interactionSource: MutableInteractionSource?) {
    val dark = LocalDarkTheme.current
    val src = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val pos by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = 0.68f, stiffness = 500f), label = "knob")
    val track by animateColorAsState(if (checked) IosGreen else if (dark) Color(0xFF39393D) else Color(0xFFE9E9EA), label = "track")
    val knobW by animateDpAsState(if (pressed) 34.dp else 27.dp, label = "knobW")
    Box(
        modifier
            .then(
                if (onCheckedChange != null) Modifier.minimumInteractiveComponentSize()
                    .toggleable(checked, src, null, enabled, Role.Switch, onCheckedChange)
                else Modifier,
            )
            .alpha(if (enabled) 1f else 0.45f)
            .size(51.dp, 31.dp)
            .clip(CircleShape)
            .background(track)
            .padding(2.dp),
    ) {
        Box(
            Modifier
                .offset { IntOffset(((47.dp - knobW).toPx() * pos).roundToInt(), 0) }
                .size(knobW, 27.dp)
                .shadow(2.dp, CircleShape)
                .background(Color.White, CircleShape),
        )
    }
}

// WinUI ToggleSwitch: 40x20 pill, outlined when off, accent with a white knob when on.
@Composable
private fun FluentSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier, enabled: Boolean, interactionSource: MutableInteractionSource?) {
    val cs = MaterialTheme.colorScheme
    val f = fluent()
    val src = interactionSource ?: remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    val pressed by src.collectIsPressedAsState()
    val pos by animateFloatAsState(if (checked) 1f else 0f, tween(160), label = "knob")
    val knob by animateDpAsState(if (hovered || pressed) 14.dp else 12.dp, tween(120), label = "knobSize")
    val stretch by animateDpAsState(if (pressed) 3.dp else 0.dp, tween(120), label = "knobStretch")
    val accent = cs.primary
    val onAccent = cs.onPrimary
    val stroke = f.strong
    val hoverFill = f.subtleHover
    Box(
        modifier
            .then(
                if (onCheckedChange != null) Modifier.minimumInteractiveComponentSize()
                    .hoverable(src, enabled)
                    .toggleable(checked, src, null, enabled, Role.Switch, onCheckedChange)
                else Modifier,
            )
            .alpha(if (enabled) 1f else 0.4f)
            .size(40.dp, 20.dp)
            .drawBehind {
                val r = size.height / 2
                if (pos > 0f) drawRoundRect(accent.copy(alpha = accent.alpha * pos), cornerRadius = CornerRadius(r))
                if (pos < 1f) {
                    val a = 1f - pos
                    if (hovered) drawRoundRect(hoverFill.copy(alpha = hoverFill.alpha * a), cornerRadius = CornerRadius(r))
                    val w = 1.dp.toPx()
                    drawRoundRect(stroke.copy(alpha = stroke.alpha * a), Offset(w / 2, w / 2), Size(size.width - w, size.height - w), CornerRadius(r - w / 2), style = Stroke(w))
                }
                val kh = knob.toPx()
                val kw = kh + stretch.toPx()
                val cx = r + (size.width - 2 * r) * pos
                drawRoundRect(lerp(stroke, onAccent, pos), Offset(cx - kw / 2, r - kh / 2), Size(kw, kh), CornerRadius(kh / 2))
            },
    )
}

@Composable
fun Slider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val style = LocalDesignStyle.current
    if (style == DesignStyle.MATERIAL) {
        androidx.compose.material3.Slider(
            value = value, onValueChange = onValueChange, modifier = modifier, enabled = enabled, valueRange = valueRange,
            steps = steps, onValueChangeFinished = onValueChangeFinished, colors = colors, interactionSource = interactionSource,
        )
        return
    }
    val fluent = style == DesignStyle.WINDOWS
    val cs = MaterialTheme.colorScheme
    val dark = LocalDarkTheme.current
    val f = fluent()
    val active = if (enabled) colors.activeTrackColor else colors.disabledActiveTrackColor
    val inactive = if (fluent) f.strong else cs.onSurface.copy(alpha = 0.2f)
    val span = valueRange.endInclusive - valueRange.start
    val fraction = if (span <= 0f) 0f else ((value - valueRange.start) / span).coerceIn(0f, 1f)
    var dragging by remember { mutableStateOf(false) }
    val hovered by interactionSource.collectIsHoveredAsState()
    // iOS 26: capsule thumb that swells into a translucent lens while dragged. Fluent: inner accent dot.
    val thumbW by animateDpAsState(if (dragging) 46.dp else 38.dp, label = "thumbW")
    val thumbH by animateDpAsState(if (dragging) 30.dp else 24.dp, label = "thumbH")
    val lens by animateFloatAsState(if (dragging) 1f else 0f, label = "lens")
    val dot by animateDpAsState(if (dragging) 5.dp else if (hovered) 7.dp else 6.dp, tween(120), label = "dot")
    val change by rememberUpdatedState(onValueChange)
    val finished by rememberUpdatedState(onValueChangeFinished)
    val pad = if (fluent) 10.dp else 19.dp
    val thumbFill = if (dark) Color(0xFF454545) else Color.White
    val thumbStroke = f.strokeTop
    fun valueAt(x: Float, width: Float, padPx: Float): Float {
        var p = ((x - padPx) / (width - 2 * padPx).coerceAtLeast(1f)).coerceIn(0f, 1f)
        if (steps > 0) p = (p * (steps + 1)).roundToInt() / (steps + 1).toFloat()
        return valueRange.start + p * span
    }
    Box(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = if (fluent) 40.dp else 44.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .hoverable(interactionSource, enabled)
            .pointerInput(enabled, valueRange, steps) {
                if (!enabled) return@pointerInput
                detectTapGestures { o ->
                    change(valueAt(o.x, size.width.toFloat(), pad.toPx()))
                    finished?.invoke()
                }
            }
            .pointerInput(enabled, valueRange, steps) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { o ->
                        dragging = true
                        change(valueAt(o.x, size.width.toFloat(), pad.toPx()))
                    },
                    onDragEnd = { dragging = false; finished?.invoke() },
                    onDragCancel = { dragging = false; finished?.invoke() },
                ) { c, _ ->
                    c.consume()
                    change(valueAt(c.position.x, size.width.toFloat(), pad.toPx()))
                }
            }
            .drawBehind {
                val p = pad.toPx()
                val y = size.height / 2
                val w = size.width - 2 * p
                val x = p + w * fraction
                val h = if (fluent) 4.dp.toPx() else 6.dp.toPx()
                drawRoundRect(inactive, Offset(p, y - h / 2), Size(w, h), CornerRadius(h / 2))
                drawRoundRect(active, Offset(p - h / 2, y - h / 2), Size(x - p + h / 2, h), CornerRadius(h / 2))
                if (fluent) {
                    val r = 10.dp.toPx()
                    drawCircle(Color.Black.copy(alpha = 0.12f), r + 0.5.dp.toPx(), Offset(x, y + 1.dp.toPx()))
                    drawCircle(thumbFill, r, Offset(x, y))
                    drawCircle(thumbStroke, r - 0.5.dp.toPx(), Offset(x, y), style = Stroke(1.dp.toPx()))
                    drawCircle(active, dot.toPx(), Offset(x, y))
                } else {
                    val tw = thumbW.toPx()
                    val th = thumbH.toPx()
                    val tl = Offset(x - tw / 2, y - th / 2)
                    val cr = CornerRadius(th / 2)
                    drawRoundRect(Color.Black.copy(alpha = 0.14f * (1f - lens * 0.5f)), tl + Offset(0f, 1.5.dp.toPx()), Size(tw, th), cr)
                    drawRoundRect(Color.White.copy(alpha = 1f - lens * 0.55f), tl, Size(tw, th), cr)
                    if (lens > 0f) {
                        drawRoundRect(
                            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.7f * lens), Color.White.copy(alpha = 0.1f * lens)), tl.y, tl.y + th),
                            tl, Size(tw, th), cr, style = Stroke(1.dp.toPx()),
                        )
                    }
                }
            },
    )
}

@Composable
fun FilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    shape: Shape = FilterChipDefaults.shape,
    colors: SelectableChipColors = FilterChipDefaults.filterChipColors(),
    elevation: SelectableChipElevation? = FilterChipDefaults.filterChipElevation(),
    border: BorderStroke? = FilterChipDefaults.filterChipBorder(enabled, selected),
    interactionSource: MutableInteractionSource? = null,
) {
    val style = LocalDesignStyle.current
    if (style == DesignStyle.MATERIAL) {
        androidx.compose.material3.FilterChip(
            selected = selected, onClick = onClick, label = label, modifier = modifier, enabled = enabled,
            leadingIcon = leadingIcon, trailingIcon = trailingIcon, shape = shape, colors = colors,
            elevation = elevation, border = border, interactionSource = interactionSource,
        )
        return
    }
    val cs = MaterialTheme.colorScheme
    val dark = LocalDarkTheme.current
    val src = interactionSource ?: remember { MutableInteractionSource() }
    val fg = if (selected) cs.onPrimary else cs.onSurface
    val surface = if (style == DesignStyle.WINDOWS) {
        // Fluent pill toggle button.
        val f = fluent()
        val hovered by src.collectIsHoveredAsState()
        val pressed by src.collectIsPressedAsState()
        val bg = when {
            selected -> cs.primary.copy(alpha = if (pressed) 0.8f else if (hovered) 0.9f else 1f)
            pressed -> f.fillPressed
            hovered -> f.fillHover
            else -> f.fill
        }
        Modifier.clip(CircleShape).background(bg)
            .fluentBorder(CircleShape, if (selected) Color.White.copy(alpha = 0.08f) else f.strokeTop, if (selected) Color.Black.copy(alpha = 0.3f) else f.strokeBottom)
            .hoverable(src, enabled)
    } else {
        // Liquid Glass pill: frosted when idle, tinted glass when selected.
        val bg by animateColorAsState(if (selected) cs.primary else glassFill(), label = "chip")
        Modifier.shadow(if (selected) 4.dp else 3.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.12f), spotColor = Color.Black.copy(alpha = 0.16f))
            .clip(CircleShape).background(bg).glassSheen(CircleShape, dark)
    }
    Row(
        modifier
            .minimumInteractiveComponentSize()
            .alpha(if (enabled) 1f else 0.4f)
            .heightIn(min = 32.dp)
            .then(surface)
            .selectable(selected, src, null, enabled, Role.Checkbox, onClick)
            .padding(horizontal = if (style == DesignStyle.WINDOWS) 12.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides fg,
            LocalTextStyle provides MaterialTheme.typography.bodyMedium.copy(fontWeight = if (style == DesignStyle.WINDOWS) FontWeight.Normal else FontWeight.Medium, color = fg),
        ) {
            leadingIcon?.let { it(); Spacer(Modifier.width(6.dp)) }
            label()
            trailingIcon?.let { Spacer(Modifier.width(6.dp)); it() }
        }
    }
}

@Composable
fun RadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: RadioButtonColors = RadioButtonDefaults.colors(),
    interactionSource: MutableInteractionSource? = null,
) {
    val style = LocalDesignStyle.current
    if (style == DesignStyle.MATERIAL) {
        androidx.compose.material3.RadioButton(
            selected = selected, onClick = onClick, modifier = modifier, enabled = enabled,
            colors = colors, interactionSource = interactionSource,
        )
        return
    }
    val cs = MaterialTheme.colorScheme
    val src = interactionSource ?: remember { MutableInteractionSource() }
    val base = modifier.then(
        if (onClick != null) Modifier.minimumInteractiveComponentSize()
            .hoverable(src, enabled)
            .selectable(selected, src, null, enabled, Role.RadioButton, onClick)
        else Modifier,
    )
    if (style == DesignStyle.GLASS) {
        Box(base.size(24.dp), contentAlignment = Alignment.Center) {
            if (selected) Icon(Icons.Rounded.Check, null, Modifier.size(22.dp), tint = cs.primary.copy(alpha = if (enabled) 1f else 0.4f))
        }
        return
    }
    // WinUI radio: outlined ring, accent disc with a white dot when selected.
    val f = fluent()
    val hovered by src.collectIsHoveredAsState()
    val pressed by src.collectIsPressedAsState()
    val dot by animateDpAsState(if (pressed) 3.dp else if (hovered) 5.dp else 4.dp, tween(120), label = "dot")
    val accent = cs.primary
    val onAccent = cs.onPrimary
    val stroke = f.strong
    val hoverFill = f.subtleHover
    Box(
        base.alpha(if (enabled) 1f else 0.4f).size(20.dp).drawBehind {
            val r = size.minDimension / 2
            if (selected) {
                drawCircle(accent, r)
                drawCircle(onAccent, dot.toPx())
            } else {
                if (hovered) drawCircle(hoverFill, r)
                drawCircle(stroke, r - 0.5.dp.toPx(), style = Stroke(1.dp.toPx()))
            }
        },
    )
}

@Composable
private fun IosButton(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    container: Color,
    content: Color,
    border: BorderStroke?,
    contentPadding: PaddingValues,
    interactionSource: MutableInteractionSource?,
    style: TextStyle,
    minHeight: Dp = 40.dp,
    glass: Boolean = false,
    body: @Composable RowScope.() -> Unit,
) {
    val src = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val a by animateFloatAsState(if (!enabled) 0.4f else if (pressed) 0.5f else 1f, label = "press")
    val dark = LocalDarkTheme.current
    Row(
        modifier
            .minimumInteractiveComponentSize()
            .graphicsLayer { alpha = a }
            .defaultMinSize(minWidth = 58.dp, minHeight = minHeight)
            .then(if (glass) Modifier.shadow(4.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.12f), spotColor = Color.Black.copy(alpha = 0.18f)) else Modifier)
            .clip(CircleShape)
            .background(container)
            .then(if (glass) Modifier.glassSheen(CircleShape, dark) else Modifier)
            .then(if (border != null) Modifier.border(border, CircleShape) else Modifier)
            .clickable(interactionSource = src, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(contentPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides content, LocalTextStyle provides style.copy(color = content)) { body() }
    }
}

// WinUI Button: 4dp corners, accent or subtle fill, 1px elevation border.
@Composable
private fun FluentButton(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    accent: Boolean,
    colors: ButtonColors?,
    border: BorderStroke?,
    contentPadding: PaddingValues,
    interactionSource: MutableInteractionSource?,
    body: @Composable RowScope.() -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val f = fluent()
    val src = interactionSource ?: remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    val pressed by src.collectIsPressedAsState()
    val shape = RoundedCornerShape(4.dp)
    val custom = colors?.containerColor?.takeIf { it.alpha > 0f }
    val bg = when {
        accent || custom != null -> (custom ?: cs.primary).let { c -> c.copy(alpha = c.alpha * if (pressed) 0.8f else if (hovered) 0.9f else 1f) }
        pressed -> f.fillPressed
        hovered -> f.fillHover
        else -> f.fill
    }
    val fg = (colors?.contentColor ?: if (accent) cs.onPrimary else cs.onSurface)
        .let { if (pressed && !accent && custom == null) it.copy(alpha = it.alpha * 0.8f) else it }
    Row(
        modifier
            .alpha(if (enabled) 1f else 0.4f)
            .defaultMinSize(minWidth = 56.dp, minHeight = 32.dp)
            .clip(shape)
            .background(bg)
            .then(
                if (border != null) Modifier.border(border, shape)
                else if (accent || custom != null) Modifier.fluentBorder(shape, Color.White.copy(alpha = 0.08f), Color.Black.copy(alpha = 0.32f))
                else Modifier.fluentBorder(shape, f.strokeTop, f.strokeBottom),
            )
            .hoverable(src, enabled)
            .clickable(interactionSource = src, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(contentPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides fg, LocalTextStyle provides MaterialTheme.typography.bodyMedium.copy(color = fg)) { body() }
    }
}

// Fluent buttons keep the caller's padding unless it's the Material default.
private fun fluentPadding(p: PaddingValues) = if (p == ButtonDefaults.ContentPadding) PaddingValues(horizontal = 16.dp, vertical = 6.dp) else p

@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors? = null,
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    when (LocalDesignStyle.current) {
        DesignStyle.MATERIAL -> androidx.compose.material3.Button(
            onClick = onClick, modifier = modifier, enabled = enabled, shape = shape, colors = colors ?: ButtonDefaults.buttonColors(),
            elevation = elevation, border = border, contentPadding = contentPadding, interactionSource = interactionSource, content = content,
        )
        DesignStyle.WINDOWS -> FluentButton(onClick, modifier, enabled, true, colors, border, fluentPadding(contentPadding), interactionSource, content)
        DesignStyle.GLASS -> IosButton(
            onClick, modifier, enabled, colors?.containerColor ?: cs.primary, colors?.contentColor ?: cs.onPrimary,
            border, contentPadding, interactionSource, MaterialTheme.typography.titleSmall, glass = true, body = content,
        )
    }
}

@Composable
fun FilledTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.filledTonalShape,
    colors: ButtonColors? = null,
    elevation: ButtonElevation? = ButtonDefaults.filledTonalButtonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    when (LocalDesignStyle.current) {
        DesignStyle.MATERIAL -> androidx.compose.material3.FilledTonalButton(
            onClick = onClick, modifier = modifier, enabled = enabled, shape = shape, colors = colors ?: ButtonDefaults.filledTonalButtonColors(),
            elevation = elevation, border = border, contentPadding = contentPadding, interactionSource = interactionSource, content = content,
        )
        DesignStyle.WINDOWS -> FluentButton(onClick, modifier, enabled, false, colors, border, fluentPadding(contentPadding), interactionSource, content)
        DesignStyle.GLASS -> {
            val tint = lerp(glassFill(), cs.primary, if (LocalDarkTheme.current) 0.22f else 0.12f)
            IosButton(
                onClick, modifier, enabled, colors?.containerColor ?: tint, colors?.contentColor ?: cs.primary,
                border, contentPadding, interactionSource, MaterialTheme.typography.titleSmall, glass = true, body = content,
            )
        }
    }
}

@Composable
fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.outlinedShape,
    colors: ButtonColors? = null,
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    when (LocalDesignStyle.current) {
        DesignStyle.MATERIAL -> androidx.compose.material3.OutlinedButton(
            onClick = onClick, modifier = modifier, enabled = enabled, shape = shape, colors = colors ?: ButtonDefaults.outlinedButtonColors(),
            elevation = elevation, border = border ?: ButtonDefaults.outlinedButtonBorder(enabled), contentPadding = contentPadding,
            interactionSource = interactionSource, content = content,
        )
        DesignStyle.WINDOWS -> FluentButton(onClick, modifier, enabled, false, colors, border, fluentPadding(contentPadding), interactionSource, content)
        DesignStyle.GLASS -> IosButton(
            onClick, modifier, enabled, colors?.containerColor?.takeIf { it.alpha > 0f } ?: glassFill(), colors?.contentColor ?: cs.primary,
            border, contentPadding, interactionSource, MaterialTheme.typography.titleSmall, glass = true, body = content,
        )
    }
}

@Composable
fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.textShape,
    colors: ButtonColors? = null,
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val style = LocalDesignStyle.current
    if (style == DesignStyle.MATERIAL) {
        androidx.compose.material3.TextButton(
            onClick = onClick, modifier = modifier, enabled = enabled, shape = shape, colors = colors ?: ButtonDefaults.textButtonColors(),
            elevation = elevation, border = border, contentPadding = contentPadding, interactionSource = interactionSource, content = content,
        )
        return
    }
    val cell = LocalAlertCell.current
    val fg = colors?.contentColor ?: MaterialTheme.colorScheme.primary
    if (style == DesignStyle.WINDOWS) {
        // ContentDialog footer: accent primary button, standard secondary. Elsewhere a HyperlinkButton.
        if (cell != 0) {
            FluentButton(
                onClick, modifier.widthIn(min = 96.dp), enabled, cell == 2, colors?.takeIf { it.containerColor.alpha > 0f }, border,
                PaddingValues(horizontal = 16.dp, vertical = 6.dp), interactionSource, content,
            )
            return
        }
        val f = fluent()
        val src = interactionSource ?: remember { MutableInteractionSource() }
        val hovered by src.collectIsHoveredAsState()
        val pressed by src.collectIsPressedAsState()
        val shape4 = RoundedCornerShape(4.dp)
        Row(
            modifier
                .alpha(if (enabled) 1f else 0.4f)
                .defaultMinSize(minWidth = 40.dp, minHeight = 32.dp)
                .clip(shape4)
                .background(if (pressed) f.subtlePressed else if (hovered) f.subtleHover else Color.Transparent)
                .then(if (border != null) Modifier.border(border, shape4) else Modifier)
                .hoverable(src, enabled)
                .clickable(interactionSource = src, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = 11.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val c = if (pressed) fg.copy(alpha = fg.alpha * 0.8f) else fg
            CompositionLocalProvider(LocalContentColor provides c, LocalTextStyle provides MaterialTheme.typography.bodyMedium.copy(color = c)) { content() }
        }
        return
    }
    val body = MaterialTheme.typography.bodyLarge
    if (cell != 0) {
        IosButton(
            onClick, modifier, enabled, Color.Transparent, fg, null, PaddingValues(horizontal = 12.dp), interactionSource,
            body.copy(fontWeight = if (cell == 2) FontWeight.SemiBold else FontWeight.Normal), minHeight = 44.dp, body = content,
        )
    } else {
        IosButton(onClick, modifier, enabled, Color.Transparent, fg, border, contentPadding, interactionSource, body, body = content)
    }
}

@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
) {
    val style = LocalDesignStyle.current
    if (style == DesignStyle.MATERIAL) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = onDismissRequest, confirmButton = confirmButton, modifier = modifier, dismissButton = dismissButton,
            icon = icon, title = title, text = text, properties = properties,
        )
        return
    }
    if (style == DesignStyle.WINDOWS) {
        FluentDialog(onDismissRequest, confirmButton, modifier, dismissButton, icon, title, text, properties)
        return
    }
    val cs = MaterialTheme.colorScheme
    val dark = LocalDarkTheme.current
    val shape = RoundedCornerShape(28.dp)
    val hair = cs.onSurface.copy(alpha = if (dark) 0.16f else 0.14f)
    val real = LocalGlass.current != null
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        CompositionLocalProvider(LocalInGlassSource provides true) {
            Column(
                modifier
                    .width(300.dp)
                    .then(
                        if (real) Modifier.liquidGlass(shape, 30.dp, overlay = true)
                        else Modifier
                            .shadow(30.dp, shape, ambientColor = Color.Black.copy(alpha = 0.35f), spotColor = Color.Black.copy(alpha = 0.35f))
                            .clip(shape)
                            .background(if (dark) Color(0xFF232325) else Color(0xFFF6F6F8))
                            .glassSheen(shape, dark),
                    ),
            ) {
                Column(
                    Modifier.weight(1f, fill = false).fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    icon?.let {
                        CompositionLocalProvider(LocalContentColor provides cs.primary) { Box(Modifier.padding(bottom = 10.dp)) { it() } }
                    }
                    title?.let {
                        CompositionLocalProvider(
                            LocalContentColor provides cs.onSurface,
                            LocalTextStyle provides MaterialTheme.typography.titleMedium.copy(color = cs.onSurface, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
                        ) { Box(Modifier.padding(bottom = 4.dp)) { it() } }
                    }
                    text?.let {
                        CompositionLocalProvider(
                            LocalContentColor provides cs.onSurface,
                            LocalTextStyle provides MaterialTheme.typography.bodySmall.copy(color = cs.onSurface, fontSize = 13.sp, textAlign = TextAlign.Center),
                        ) { Box(Modifier.weight(1f, fill = false).padding(top = 2.dp)) { it() } }
                    }
                }
                AlertButtons(dismissButton, confirmButton, hair)
            }
        }
    }
}

// WinUI ContentDialog: left-aligned 20sp title, buttons right-aligned in a footer band.
@Composable
private fun FluentDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier,
    dismissButton: @Composable (() -> Unit)?,
    icon: @Composable (() -> Unit)?,
    title: @Composable (() -> Unit)?,
    text: @Composable (() -> Unit)?,
    properties: DialogProperties,
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(8.dp)
    val line = cs.outlineVariant
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        Column(
            modifier
                .widthIn(min = 320.dp, max = 548.dp)
                .shadow(32.dp, shape, ambientColor = Color.Black.copy(alpha = 0.3f), spotColor = Color.Black.copy(alpha = 0.3f))
                .clip(shape)
                .background(cs.surfaceContainerHigh)
                .border(1.dp, cs.outline, shape),
        ) {
            Column(Modifier.weight(1f, fill = false).padding(24.dp)) {
                icon?.let {
                    CompositionLocalProvider(LocalContentColor provides cs.primary) { Box(Modifier.padding(bottom = 12.dp)) { it() } }
                }
                title?.let {
                    CompositionLocalProvider(
                        LocalContentColor provides cs.onSurface,
                        LocalTextStyle provides MaterialTheme.typography.titleLarge.copy(color = cs.onSurface, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Start),
                    ) { Box(Modifier.padding(bottom = 12.dp)) { it() } }
                }
                text?.let {
                    CompositionLocalProvider(
                        LocalContentColor provides cs.onSurface,
                        LocalTextStyle provides MaterialTheme.typography.bodyMedium.copy(color = cs.onSurface),
                    ) { Box(Modifier.weight(1f, fill = false)) { it() } }
                }
            }
            Row(
                Modifier.fillMaxWidth().background(cs.surface).drawBehind { drawRect(line, size = Size(size.width, 1.dp.toPx())) }.padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompositionLocalProvider(LocalAlertCell provides 2) { confirmButton() }
                dismissButton?.let { CompositionLocalProvider(LocalAlertCell provides 1) { it() } }
            }
        }
    }
}

@Composable
private fun AlertButtons(dismiss: @Composable (() -> Unit)?, confirm: @Composable () -> Unit, hair: Color) {
    Layout(
        contents = listOf<@Composable () -> Unit>(
            { if (dismiss != null) CompositionLocalProvider(LocalAlertCell provides 1) { dismiss() } },
            { CompositionLocalProvider(LocalAlertCell provides 2) { confirm() } },
            { Box(Modifier.background(hair)) },
            { Box(Modifier.background(hair)) },
        ),
        modifier = Modifier.fillMaxWidth(),
    ) { (d, c, top, mid), constraints ->
        val w = constraints.maxWidth
        val groups = listOf(d, c).filter { it.isNotEmpty() }
        if (groups.isEmpty() || w == Constraints.Infinity) return@Layout layout(0, 0) {}
        val line = 0.5.dp.roundToPx().coerceAtLeast(1)
        val cellH = 44.dp.roundToPx()
        val gap = 16.dp.roundToPx()
        val sideBySide = groups.size == 2 && groups.all { g -> g.sumOf { it.maxIntrinsicWidth(cellH) } <= (w - line) / 2 - gap }
        val cellW = if (sideBySide) (w - line) / 2 else w
        val cells: List<List<Placeable>> = (if (sideBySide) groups else groups.reversed()).map { g ->
            g.map { it.measure(Constraints(maxWidth = cellW, minHeight = cellH)) }
        }
        val heights = cells.map { g -> maxOf(cellH, g.maxOf { it.height }) }
        val topLine = top.first().measure(Constraints.fixed(w, line))
        val height = line + if (sideBySide) heights.max() else heights.sum() + line * (heights.size - 1)
        val midLine = when {
            cells.size < 2 -> null
            sideBySide -> mid.first().measure(Constraints.fixed(line, height - line))
            else -> mid.first().measure(Constraints.fixed(w, line))
        }
        layout(w, height) {
            topLine.place(0, 0)
            var y = line
            cells.forEachIndexed { i, g ->
                val x0 = if (sideBySide) i * (cellW + line) else 0
                val total = g.sumOf { it.width }
                var x = x0 + (cellW - total) / 2
                g.forEach { p ->
                    p.place(x, y + (heights[i] - p.height) / 2)
                    x += p.width
                }
                if (!sideBySide) {
                    y += heights[i]
                    if (i == 0) midLine?.place(0, y)
                    y += line
                }
            }
            if (sideBySide) midLine?.place(cellW, line)
        }
    }
}

@Composable
fun Card(
    modifier: Modifier = Modifier,
    shape: Shape = CardDefaults.shape,
    colors: CardColors? = null,
    elevation: CardElevation? = null,
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    when (LocalDesignStyle.current) {
        DesignStyle.MATERIAL -> androidx.compose.material3.Card(
            modifier = modifier, shape = shape, colors = colors ?: CardDefaults.cardColors(),
            elevation = elevation ?: CardDefaults.cardElevation(), border = border, content = content,
        )
        DesignStyle.WINDOWS -> FluentCard(modifier, colors, border, null, true, null, content)
        DesignStyle.GLASS -> IosCard(modifier, colors, elevation != null, border, null, true, content)
    }
}

@Composable
fun Card(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CardDefaults.shape,
    colors: CardColors? = null,
    elevation: CardElevation? = null,
    border: BorderStroke? = null,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    when (LocalDesignStyle.current) {
        DesignStyle.MATERIAL -> androidx.compose.material3.Card(
            onClick = onClick, modifier = modifier, enabled = enabled, shape = shape, colors = colors ?: CardDefaults.cardColors(),
            elevation = elevation ?: CardDefaults.cardElevation(), border = border, interactionSource = interactionSource, content = content,
        )
        DesignStyle.WINDOWS -> FluentCard(modifier, colors, border, onClick, enabled, interactionSource, content)
        DesignStyle.GLASS -> IosCard(modifier, colors, elevation != null, border, onClick, enabled, content)
    }
}

@Composable
private fun IosCard(
    modifier: Modifier,
    colors: CardColors?,
    raised: Boolean,
    border: BorderStroke?,
    onClick: (() -> Unit)?,
    enabled: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(22.dp)
    val fg = colors?.contentColor ?: cs.onSurface
    Column(
        modifier
            .then(if (raised) Modifier.shadow(14.dp, shape, ambientColor = Color.Black.copy(alpha = 0.2f), spotColor = Color.Black.copy(alpha = 0.2f)) else Modifier)
            .clip(shape)
            .background(colors?.containerColor ?: cs.surfaceContainerLow)
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier),
    ) {
        CompositionLocalProvider(LocalContentColor provides fg) { content() }
    }
}

// WinUI card: layer fill, 1px stroke, 8dp corners, subtle hover.
@Composable
private fun FluentCard(
    modifier: Modifier,
    colors: CardColors?,
    border: BorderStroke?,
    onClick: (() -> Unit)?,
    enabled: Boolean,
    interactionSource: MutableInteractionSource?,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val f = fluent()
    val shape = RoundedCornerShape(8.dp)
    val src = interactionSource ?: remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    val pressed by src.collectIsPressedAsState()
    val fg = colors?.contentColor ?: cs.onSurface
    val over = if (onClick == null) Color.Transparent else if (pressed) f.subtlePressed else if (hovered) f.subtleHover else Color.Transparent
    Column(
        modifier
            .clip(shape)
            .background(colors?.containerColor ?: cs.surfaceContainerLow)
            .background(over)
            .border(border ?: BorderStroke(1.dp, cs.outlineVariant), shape)
            .then(
                if (onClick != null) Modifier.hoverable(src, enabled)
                    .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
                else Modifier,
            ),
    ) {
        CompositionLocalProvider(LocalContentColor provides fg) { content() }
    }
}

@Composable
fun LinearProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    trackColor: Color = Color.Unspecified,
    strokeCap: StrokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
    gapSize: Dp = Dp.Unspecified,
    drawStopIndicator: (DrawScope.() -> Unit)? = null,
) {
    val style = LocalDesignStyle.current
    if (style == DesignStyle.MATERIAL) {
        val c = color.takeOrElse { ProgressIndicatorDefaults.linearColor }
        val stop: DrawScope.() -> Unit = drawStopIndicator ?: {
            ProgressIndicatorDefaults.drawStopIndicator(this, ProgressIndicatorDefaults.LinearTrackStopIndicatorSize, c, strokeCap)
        }
        androidx.compose.material3.LinearProgressIndicator(
            progress = progress,
            modifier = modifier,
            color = c,
            trackColor = trackColor.takeOrElse { ProgressIndicatorDefaults.linearTrackColor },
            strokeCap = strokeCap,
            gapSize = if (gapSize.isSpecified) gapSize else ProgressIndicatorDefaults.LinearIndicatorTrackGapSize,
            drawStopIndicator = stop,
        )
        return
    }
    val cs = MaterialTheme.colorScheme
    val c = color.takeOrElse { cs.primary }
    if (style == DesignStyle.WINDOWS) {
        // WinUI ProgressBar: 1px track, 3px rounded indicator.
        val tc = trackColor.takeOrElse { fluent().strong }
        Canvas(modifier.size(240.dp, 4.dp)) {
            val h = minOf(size.height, 3.dp.toPx())
            val y = size.height / 2
            val t = 1.dp.toPx()
            val p = progress().coerceIn(0f, 1f)
            drawRect(tc, Offset(0f, y - t / 2), Size(size.width, t))
            if (p > 0f) drawRoundRect(c, Offset(0f, y - h / 2), Size(size.width * p, h), CornerRadius(h / 2))
        }
        return
    }
    val tc = trackColor.takeOrElse { cs.onSurface.copy(alpha = 0.15f) }
    Canvas(modifier.size(240.dp, 4.dp)) {
        val h = size.height
        val p = progress().coerceIn(0f, 1f)
        drawRoundRect(tc, cornerRadius = CornerRadius(h / 2))
        if (p > 0f) drawRoundRect(c, size = Size(size.width * p, h), cornerRadius = CornerRadius(h / 2))
    }
}

@Composable
fun CircularProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    strokeWidth: Dp = ProgressIndicatorDefaults.CircularStrokeWidth,
    trackColor: Color = Color.Unspecified,
    strokeCap: StrokeCap = ProgressIndicatorDefaults.CircularDeterminateStrokeCap,
) {
    val style = LocalDesignStyle.current
    if (style == DesignStyle.MATERIAL) {
        androidx.compose.material3.CircularProgressIndicator(
            progress = progress,
            modifier = modifier,
            color = color.takeOrElse { ProgressIndicatorDefaults.circularColor },
            strokeWidth = strokeWidth,
            trackColor = trackColor.takeOrElse { ProgressIndicatorDefaults.circularDeterminateTrackColor },
            strokeCap = strokeCap,
        )
        return
    }
    val cs = MaterialTheme.colorScheme
    val fluent = style == DesignStyle.WINDOWS
    val c = color.takeOrElse { cs.primary }
    // WinUI ProgressRing has no visible track and a thinner stroke.
    val tc = if (fluent) Color.Transparent else trackColor.takeOrElse { cs.onSurface.copy(alpha = 0.15f) }
    val sw = if (fluent) strokeWidth * 0.75f else strokeWidth
    Canvas(modifier.size(if (fluent) 32.dp else 40.dp)) {
        val w = sw.toPx()
        val d = size.minDimension - w
        val tl = Offset((size.width - d) / 2, (size.height - d) / 2)
        if (tc.alpha > 0f) drawArc(tc, 0f, 360f, false, tl, Size(d, d), style = Stroke(w))
        drawArc(c, -90f, 360f * progress().coerceIn(0f, 1f), false, tl, Size(d, d), style = Stroke(w, cap = StrokeCap.Round))
    }
}

@Composable
fun CircularProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    strokeWidth: Dp = ProgressIndicatorDefaults.CircularStrokeWidth,
    trackColor: Color = Color.Unspecified,
    strokeCap: StrokeCap = ProgressIndicatorDefaults.CircularIndeterminateStrokeCap,
) {
    val style = LocalDesignStyle.current
    if (style == DesignStyle.MATERIAL) {
        androidx.compose.material3.CircularProgressIndicator(
            modifier = modifier,
            color = color.takeOrElse { ProgressIndicatorDefaults.circularColor },
            strokeWidth = strokeWidth,
            trackColor = trackColor.takeOrElse { ProgressIndicatorDefaults.circularIndeterminateTrackColor },
            strokeCap = strokeCap,
        )
        return
    }
    if (style == DesignStyle.WINDOWS) {
        // WinUI ProgressRing: spinning accent arc that grows and shrinks.
        val c = color.takeOrElse { MaterialTheme.colorScheme.primary }
        val spin = rememberInfiniteTransition(label = "ring")
        val rot by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "rot")
        val sweep by spin.animateFloat(30f, 270f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "sweep")
        val sw = strokeWidth * 0.75f
        Canvas(modifier.size(32.dp)) {
            val w = sw.toPx()
            val d = size.minDimension - w
            val tl = Offset((size.width - d) / 2, (size.height - d) / 2)
            drawArc(c, rot - 90f, sweep, false, tl, Size(d, d), style = Stroke(w, cap = StrokeCap.Round))
        }
        return
    }
    // iOS activity indicator: 8 fading spokes.
    val c = color.takeOrElse { MaterialTheme.colorScheme.onSurfaceVariant }
    val spin = rememberInfiniteTransition(label = "spin")
    val step by spin.animateFloat(0f, 8f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "step")
    Canvas(modifier.size(40.dp)) {
        val head = step.toInt() % 8
        val r = size.minDimension / 2
        val sw = (r * 0.18f).coerceAtLeast(1.dp.toPx())
        for (i in 0 until 8) {
            val fade = 1f - ((head - i + 8) % 8) / 8f * 0.85f
            val ang = (i * 45.0 - 90.0) * PI / 180.0
            val dir = Offset(cos(ang).toFloat(), sin(ang).toFloat())
            drawLine(c.copy(alpha = c.alpha * fade), center + dir * (r * 0.55f), center + dir * (r - sw / 2), sw, StrokeCap.Round)
        }
    }
}

@Composable
fun OutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = OutlinedTextFieldDefaults.shape,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
) {
    val design = LocalDesignStyle.current
    if (design == DesignStyle.MATERIAL) {
        androidx.compose.material3.OutlinedTextField(
            value = value, onValueChange = onValueChange, modifier = modifier, enabled = enabled, readOnly = readOnly,
            textStyle = textStyle, label = label, placeholder = placeholder, leadingIcon = leadingIcon, trailingIcon = trailingIcon,
            prefix = prefix, suffix = suffix, supportingText = supportingText, isError = isError,
            visualTransformation = visualTransformation, keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
            singleLine = singleLine, maxLines = maxLines, minLines = minLines, interactionSource = interactionSource,
            shape = shape, colors = colors,
        )
        return
    }
    val fluent = design == DesignStyle.WINDOWS
    val cs = MaterialTheme.colorScheme
    val dark = LocalDarkTheme.current
    val f = fluent()
    val src = interactionSource ?: remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val hovered by src.collectIsHoveredAsState()
    val box = RoundedCornerShape(if (fluent) 4.dp else 12.dp)
    val fill = when {
        !fluent -> cs.onSurface.copy(alpha = if (dark) 0.1f else 0.06f)
        focused -> if (dark) Color(0xFF1F1F1F) else Color.White
        hovered -> f.fillHover
        else -> f.fill
    }
    // Fluent TextBox: accent underline (2dp) when focused, thin strong stroke otherwise.
    val underline = if (isError) cs.error else if (focused) cs.primary else f.strong
    val underlineW = if (focused || isError) 2.dp else 1.dp
    val base = if (fluent) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge
    val style = base.copy(color = if (enabled) cs.onSurface else cs.onSurface.copy(alpha = 0.4f), textAlign = TextAlign.Start)
    val hint = style.copy(color = cs.onSurfaceVariant)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = if (fluent) modifier.hoverable(src, enabled) else modifier,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = style,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        visualTransformation = visualTransformation,
        interactionSource = src,
        cursorBrush = SolidColor(cs.primary),
        decorationBox = { inner ->
            Column {
                if (fluent) label?.let {
                    CompositionLocalProvider(LocalContentColor provides cs.onSurface, LocalTextStyle provides style) {
                        Box(Modifier.padding(bottom = 8.dp)) { it() }
                    }
                }
                Row(
                    Modifier.clip(box).background(fill)
                        .then(
                            if (fluent) Modifier.fluentBorder(box, f.strokeTop, f.strokeTop).drawWithContent {
                                drawContent()
                                val h = underlineW.toPx()
                                drawRect(underline, Offset(0f, size.height - h), Size(size.width, h))
                            }
                            else if (isError) Modifier.border(1.dp, cs.error, box) else Modifier,
                        )
                        .defaultMinSize(minHeight = if (fluent) 32.dp else 44.dp)
                        .padding(horizontal = if (fluent) 11.dp else 12.dp, vertical = if (fluent) 6.dp else 10.dp),
                    verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
                ) {
                    CompositionLocalProvider(LocalContentColor provides cs.onSurfaceVariant, LocalTextStyle provides hint) {
                        leadingIcon?.let { it(); Spacer(Modifier.width(8.dp)) }
                        prefix?.let { it(); Spacer(Modifier.width(4.dp)) }
                    }
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty()) (if (fluent) placeholder else placeholder ?: label)?.let { p ->
                            CompositionLocalProvider(LocalContentColor provides cs.onSurfaceVariant, LocalTextStyle provides hint) { p() }
                        }
                        inner()
                    }
                    CompositionLocalProvider(LocalContentColor provides cs.onSurfaceVariant, LocalTextStyle provides hint) {
                        suffix?.let { Spacer(Modifier.width(4.dp)); it() }
                        trailingIcon?.let { Spacer(Modifier.width(8.dp)); it() }
                    }
                }
                supportingText?.let {
                    val c = if (isError) cs.error else cs.onSurfaceVariant
                    CompositionLocalProvider(LocalContentColor provides c, LocalTextStyle provides MaterialTheme.typography.bodySmall.copy(color = c)) {
                        Box(Modifier.padding(start = if (fluent) 0.dp else 12.dp, top = 4.dp)) { it() }
                    }
                }
            }
        },
    )
}

@Composable
fun ListItem(
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    overlineContent: @Composable (() -> Unit)? = null,
    supportingContent: @Composable (() -> Unit)? = null,
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    colors: ListItemColors = ListItemDefaults.colors(),
    tonalElevation: Dp = ListItemDefaults.Elevation,
    shadowElevation: Dp = ListItemDefaults.Elevation,
) {
    val style = LocalDesignStyle.current
    val mod = if (style == DesignStyle.WINDOWS) {
        // Fluent list row: subtle hover fill.
        val src = remember { MutableInteractionSource() }
        val hovered by src.collectIsHoveredAsState()
        val over = fluent().subtleHover
        Modifier.hoverable(src).drawWithContent {
            drawContent()
            if (hovered) drawRoundRect(over, cornerRadius = CornerRadius(4.dp.toPx()))
        }.then(modifier)
    } else modifier
    androidx.compose.material3.ListItem(
        headlineContent = headlineContent, modifier = mod, overlineContent = overlineContent,
        supportingContent = supportingContent, leadingContent = leadingContent, trailingContent = trailingContent,
        colors = colors, tonalElevation = if (style != DesignStyle.MATERIAL) 0.dp else tonalElevation, shadowElevation = shadowElevation,
    )
}

@Composable
fun Snackbar(
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
    dismissAction: @Composable (() -> Unit)? = null,
    actionOnNewLine: Boolean = false,
    shape: Shape = SnackbarDefaults.shape,
    containerColor: Color = SnackbarDefaults.color,
    contentColor: Color = SnackbarDefaults.contentColor,
    actionContentColor: Color = SnackbarDefaults.actionContentColor,
    dismissActionContentColor: Color = SnackbarDefaults.dismissActionContentColor,
    content: @Composable () -> Unit,
) {
    val style = LocalDesignStyle.current
    if (style == DesignStyle.MATERIAL) {
        androidx.compose.material3.Snackbar(
            modifier = modifier, action = action, dismissAction = dismissAction, actionOnNewLine = actionOnNewLine, shape = shape,
            containerColor = containerColor, contentColor = contentColor, actionContentColor = actionContentColor,
            dismissActionContentColor = dismissActionContentColor, content = content,
        )
        return
    }
    val cs = MaterialTheme.colorScheme
    val fluent = style == DesignStyle.WINDOWS
    val surface = if (fluent) {
        // WinUI InfoBar-like card.
        val s = RoundedCornerShape(8.dp)
        Modifier.shadow(8.dp, s, ambientColor = Color.Black.copy(alpha = 0.2f), spotColor = Color.Black.copy(alpha = 0.2f))
            .clip(s).background(cs.surfaceContainerHigh).border(1.dp, cs.outline, s)
    } else Modifier.liquidGlass(RoundedCornerShape(26.dp))
    Row(
        modifier.widthIn(max = 560.dp).then(surface).heightIn(min = if (fluent) 48.dp else 52.dp).padding(start = if (fluent) 16.dp else 18.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).padding(vertical = 10.dp)) {
            CompositionLocalProvider(
                LocalContentColor provides cs.onSurface,
                LocalTextStyle provides MaterialTheme.typography.bodyMedium.copy(color = cs.onSurface, fontWeight = if (fluent) FontWeight.Normal else FontWeight.Medium),
            ) { content() }
        }
        action?.invoke()
        dismissAction?.invoke()
    }
}

@Composable
fun ModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val style = LocalDesignStyle.current
    if (style == DesignStyle.MATERIAL) {
        androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismissRequest, modifier = modifier, sheetState = sheetState, content = content)
        return
    }
    val cs = MaterialTheme.colorScheme
    val dark = LocalDarkTheme.current
    if (style == DesignStyle.WINDOWS) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
            containerColor = cs.surfaceContainerHigh,
            contentColor = cs.onSurface,
            tonalElevation = 0.dp,
            scrimColor = Color.Black.copy(alpha = 0.3f),
            dragHandle = {
                Box(Modifier.padding(top = 8.dp, bottom = 12.dp).size(32.dp, 4.dp).clip(CircleShape).background(cs.onSurfaceVariant.copy(alpha = 0.45f)))
            },
            content = content,
        )
        return
    }
    // Liquid Glass sheet. The glass sits on the content column, not on the M3 sheet modifier: that one is
    // laid out before the drag offset, so the blur/shadow ended up detached from (and taller than) the content.
    val shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp)
    val real = LocalGlass.current != null
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        sheetMaxWidth = 640.dp,
        shape = shape,
        containerColor = Color.Transparent,
        contentColor = cs.onSurface,
        tonalElevation = 0.dp,
        scrimColor = Color.Black.copy(alpha = if (dark) 0.4f else 0.25f),
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .maxHeightFraction(0.9f)
                .then(
                    if (real) Modifier.liquidGlass(shape, 0.dp, overlay = true)
                    else Modifier.clip(shape).background(if (dark) Color(0xF01C1C1E) else Color(0xF5F2F2F7)).glassSheen(shape, dark),
                )
                .windowInsetsPadding(BottomSheetDefaults.windowInsets),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp, bottom = 12.dp).size(36.dp, 5.dp).clip(CircleShape).background(cs.onSurfaceVariant.copy(alpha = 0.5f)))
            CompositionLocalProvider(LocalInGlassSource provides true, LocalContentColor provides cs.onSurface) { content() }
        }
    }
}

// Caps the height to [fraction] of the incoming max height (sheets never cover the whole window).
private fun Modifier.maxHeightFraction(fraction: Float): Modifier = layout { measurable, constraints ->
    val max = if (constraints.hasBoundedHeight) (constraints.maxHeight * fraction).toInt() else constraints.maxHeight
    val p = measurable.measure(constraints.copy(minHeight = constraints.minHeight.coerceAtMost(max), maxHeight = max))
    layout(p.width, p.height) { p.place(0, 0) }
}

private class IosRow(val inset: Dp)

// Marks a child of [IosGroupedColumn] as an inset-grouped row; [inset] = separator start.
fun Modifier.iosGroupRow(inset: Dp = 61.dp): Modifier = layoutId(IosRow(inset))

// Column that draws iOS inset-grouped backgrounds behind runs of consecutive [iosGroupRow] children.
@Composable
fun IosGroupedColumn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val bg = MaterialTheme.colorScheme.surfaceContainerLow
    val sep = MaterialTheme.colorScheme.outlineVariant
    SubcomposeLayout(modifier) { constraints ->
        val w = constraints.maxWidth
        val side = 16.dp.roundToPx()
        val measurables = subcompose("content", content)
        val rows = measurables.map { it.layoutId as? IosRow }
        val placeables = measurables.mapIndexed { i, m ->
            if (rows[i] != null) m.measure(Constraints.fixedWidth((w - 2 * side).coerceAtLeast(0)))
            else m.measure(Constraints(maxWidth = w))
        }
        val ys = IntArray(placeables.size)
        var total = 0
        placeables.forEachIndexed { i, p -> ys[i] = total; total += p.height }
        val runs = mutableListOf<IntRange>()
        var start = -1
        for (i in rows.indices) {
            if (rows[i] != null && start < 0) start = i
            if (rows[i] == null && start >= 0) { runs += start until i; start = -1 }
        }
        if (start >= 0) runs += start until rows.size
        val bgs = runs.mapIndexed { ri, run ->
            val top = ys[run.first]
            val h = ys[run.last] + placeables[run.last].height - top
            val seps = run.drop(1).map { (ys[it] - top).toFloat() to rows[it - 1]!!.inset.toPx() }
            subcompose("bg$ri") {
                Box(
                    Modifier.clip(RoundedCornerShape(22.dp)).background(bg).drawBehind {
                        val t = 0.5.dp.toPx().coerceAtLeast(1f)
                        seps.forEach { (y, inset) -> drawRect(sep, Offset(inset, y - t / 2), Size(size.width - inset, t)) }
                    },
                )
            }.first().measure(Constraints.fixed((w - 2 * side).coerceAtLeast(0), h))
        }
        layout(w, total) {
            runs.forEachIndexed { ri, run -> bgs[ri].place(side, ys[run.first]) }
            placeables.forEachIndexed { i, p -> p.place(if (rows[i] != null) side else 0, ys[i]) }
        }
    }
}

// Round tonal icon button: glass circle with accent glyph (Glass), WinUI standard button (Windows).
@Composable
fun FilledTonalIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val style = LocalDesignStyle.current
    if (style == DesignStyle.MATERIAL) {
        androidx.compose.material3.FilledTonalIconButton(onClick = onClick, modifier = modifier, enabled = enabled, content = content)
        return
    }
    val cs = MaterialTheme.colorScheme
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    val pressed by src.collectIsPressedAsState()
    if (style == DesignStyle.GLASS) {
        val dark = LocalDarkTheme.current
        val s by animateFloatAsState(if (pressed) 0.9f else 1f, spring(dampingRatio = 0.55f, stiffness = 600f), label = "press")
        Box(
            modifier
                .minimumInteractiveComponentSize()
                .size(40.dp)
                .graphicsLayer { scaleX = s; scaleY = s; alpha = if (enabled) 1f else 0.4f }
                .clip(CircleShape)
                .background(glassFill())
                .glassSheen(CircleShape, dark)
                .clickable(interactionSource = src, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { CompositionLocalProvider(LocalContentColor provides cs.primary, content = content) }
        return
    }
    val f = fluent()
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier
            .size(36.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(shape)
            .background(if (pressed) f.fillPressed else if (hovered) f.fillHover else f.fill)
            .fluentBorder(shape, f.strokeTop, f.strokeBottom)
            .hoverable(src, enabled)
            .clickable(interactionSource = src, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { CompositionLocalProvider(LocalContentColor provides if (pressed) cs.onSurface.copy(alpha = 0.8f) else cs.onSurface, content = content) }
}
