package app.nocturne.ui.kit

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.layoutId
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
import app.nocturne.ui.LocalGlass
import app.nocturne.ui.liquidGlass
import app.nocturne.ui.theme.LocalDarkTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// iOS / Liquid Glass look; false = plain Material 3.
val LocalIosStyle = staticCompositionLocalOf { false }

// 0 = not in an alert, 1 = alert dismiss cell, 2 = alert confirm cell.
private val LocalAlertCell = staticCompositionLocalOf { 0 }

private val IosGreen = Color(0xFF34C759)

@Composable
fun Modifier.iosGlass(shape: Shape): Modifier {
    if (LocalGlass.current != null) return liquidGlass(shape)
    val dark = LocalDarkTheme.current
    return this
        .shadow(18.dp, shape, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.25f))
        .clip(shape)
        .background(if (dark) Color(0xF21C1C1E) else Color(0xF5FFFFFF))
        .border(
            1.dp,
            Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) 0.22f else 0.7f), Color.White.copy(alpha = 0.04f))),
            shape,
        )
}

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
    if (!LocalIosStyle.current) {
        androidx.compose.material3.Switch(
            checked = checked, onCheckedChange = onCheckedChange, modifier = modifier, thumbContent = thumbContent,
            enabled = enabled, colors = colors, interactionSource = interactionSource,
        )
        return
    }
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
    if (!LocalIosStyle.current) {
        androidx.compose.material3.Slider(
            value = value, onValueChange = onValueChange, modifier = modifier, enabled = enabled, valueRange = valueRange,
            steps = steps, onValueChangeFinished = onValueChangeFinished, colors = colors, interactionSource = interactionSource,
        )
        return
    }
    val active = if (enabled) colors.activeTrackColor else colors.disabledActiveTrackColor
    val inactive = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
    val span = valueRange.endInclusive - valueRange.start
    val fraction = if (span <= 0f) 0f else ((value - valueRange.start) / span).coerceIn(0f, 1f)
    var dragging by remember { mutableStateOf(false) }
    val radius by animateDpAsState(if (dragging) 14.dp else 11.dp, label = "thumb")
    val change by rememberUpdatedState(onValueChange)
    val finished by rememberUpdatedState(onValueChangeFinished)
    val pad = 11.dp
    fun valueAt(x: Float, width: Float, padPx: Float): Float {
        var f = ((x - padPx) / (width - 2 * padPx).coerceAtLeast(1f)).coerceIn(0f, 1f)
        if (steps > 0) f = (f * (steps + 1)).roundToInt() / (steps + 1).toFloat()
        return valueRange.start + f * span
    }
    Box(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .alpha(if (enabled) 1f else 0.5f)
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
                val h = 4.dp.toPx()
                val y = size.height / 2
                val w = size.width - 2 * p
                val x = p + w * fraction
                drawRoundRect(inactive, Offset(p, y - h / 2), Size(w, h), CornerRadius(h / 2))
                drawRoundRect(active, Offset(p, y - h / 2), Size(x - p, h), CornerRadius(h / 2))
                val r = radius.toPx()
                drawCircle(Color.Black.copy(alpha = 0.16f), r + 1.dp.toPx(), Offset(x, y + 1.dp.toPx()))
                drawCircle(Color.White, r, Offset(x, y))
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
    if (!LocalIosStyle.current) {
        androidx.compose.material3.FilterChip(
            selected = selected, onClick = onClick, label = label, modifier = modifier, enabled = enabled,
            leadingIcon = leadingIcon, trailingIcon = trailingIcon, shape = shape, colors = colors,
            elevation = elevation, border = border, interactionSource = interactionSource,
        )
        return
    }
    val cs = MaterialTheme.colorScheme
    val dark = LocalDarkTheme.current
    val bg by animateColorAsState(if (selected) cs.primary else cs.onSurface.copy(alpha = if (dark) 0.12f else 0.07f), label = "chip")
    val fg = if (selected) Color.White else cs.onSurface
    Row(
        modifier
            .minimumInteractiveComponentSize()
            .alpha(if (enabled) 1f else 0.4f)
            .heightIn(min = 32.dp)
            .clip(CircleShape)
            .background(bg)
            .selectable(selected, interactionSource, null, enabled, Role.Checkbox, onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides fg,
            LocalTextStyle provides MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium, color = fg),
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
    if (!LocalIosStyle.current) {
        androidx.compose.material3.RadioButton(
            selected = selected, onClick = onClick, modifier = modifier, enabled = enabled,
            colors = colors, interactionSource = interactionSource,
        )
        return
    }
    Box(
        modifier
            .then(
                if (onClick != null) Modifier.minimumInteractiveComponentSize()
                    .selectable(selected, interactionSource, null, enabled, Role.RadioButton, onClick)
                else Modifier,
            )
            .size(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Rounded.Check, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.4f))
    }
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
    body: @Composable RowScope.() -> Unit,
) {
    val src = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val a by animateFloatAsState(if (!enabled) 0.4f else if (pressed) 0.5f else 1f, label = "press")
    Row(
        modifier
            .minimumInteractiveComponentSize()
            .graphicsLayer { alpha = a }
            .defaultMinSize(minWidth = 58.dp, minHeight = minHeight)
            .clip(CircleShape)
            .background(container)
            .then(if (border != null) Modifier.border(border, CircleShape) else Modifier)
            .clickable(interactionSource = src, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(contentPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides content, LocalTextStyle provides style.copy(color = content)) { body() }
    }
}

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
    if (!LocalIosStyle.current) {
        androidx.compose.material3.Button(
            onClick = onClick, modifier = modifier, enabled = enabled, shape = shape, colors = colors ?: ButtonDefaults.buttonColors(),
            elevation = elevation, border = border, contentPadding = contentPadding, interactionSource = interactionSource, content = content,
        )
        return
    }
    val cs = MaterialTheme.colorScheme
    IosButton(
        onClick, modifier, enabled, colors?.containerColor ?: cs.primary, colors?.contentColor ?: cs.onPrimary,
        border, contentPadding, interactionSource, MaterialTheme.typography.titleSmall, body = content,
    )
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
    if (!LocalIosStyle.current) {
        androidx.compose.material3.FilledTonalButton(
            onClick = onClick, modifier = modifier, enabled = enabled, shape = shape, colors = colors ?: ButtonDefaults.filledTonalButtonColors(),
            elevation = elevation, border = border, contentPadding = contentPadding, interactionSource = interactionSource, content = content,
        )
        return
    }
    val cs = MaterialTheme.colorScheme
    val tint = cs.primary.copy(alpha = if (LocalDarkTheme.current) 0.2f else 0.12f)
    IosButton(
        onClick, modifier, enabled, colors?.containerColor ?: tint, colors?.contentColor ?: cs.primary,
        border, contentPadding, interactionSource, MaterialTheme.typography.titleSmall, body = content,
    )
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
    if (!LocalIosStyle.current) {
        androidx.compose.material3.OutlinedButton(
            onClick = onClick, modifier = modifier, enabled = enabled, shape = shape, colors = colors ?: ButtonDefaults.outlinedButtonColors(),
            elevation = elevation, border = border ?: ButtonDefaults.outlinedButtonBorder(enabled), contentPadding = contentPadding,
            interactionSource = interactionSource, content = content,
        )
        return
    }
    val cs = MaterialTheme.colorScheme
    val fill = cs.onSurface.copy(alpha = if (LocalDarkTheme.current) 0.12f else 0.07f)
    IosButton(
        onClick, modifier, enabled, colors?.containerColor?.takeIf { it.alpha > 0f } ?: fill, colors?.contentColor ?: cs.primary,
        border, contentPadding, interactionSource, MaterialTheme.typography.titleSmall, body = content,
    )
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
    if (!LocalIosStyle.current) {
        androidx.compose.material3.TextButton(
            onClick = onClick, modifier = modifier, enabled = enabled, shape = shape, colors = colors ?: ButtonDefaults.textButtonColors(),
            elevation = elevation, border = border, contentPadding = contentPadding, interactionSource = interactionSource, content = content,
        )
        return
    }
    val cell = LocalAlertCell.current
    val fg = colors?.contentColor ?: MaterialTheme.colorScheme.primary
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
    if (!LocalIosStyle.current) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = onDismissRequest, confirmButton = confirmButton, modifier = modifier, dismissButton = dismissButton,
            icon = icon, title = title, text = text, properties = properties,
        )
        return
    }
    val cs = MaterialTheme.colorScheme
    val dark = LocalDarkTheme.current
    val shape = RoundedCornerShape(28.dp)
    val hair = cs.onSurface.copy(alpha = if (dark) 0.16f else 0.14f)
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        Column(
            modifier
                .width(300.dp)
                .shadow(30.dp, shape, ambientColor = Color.Black.copy(alpha = 0.35f), spotColor = Color.Black.copy(alpha = 0.35f))
                .clip(shape)
                .background(if (dark) Color(0xFF232325) else Color(0xFFF6F6F8))
                .border(
                    1.dp,
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) 0.18f else 0.8f), Color.White.copy(alpha = 0.03f))),
                    shape,
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
    if (!LocalIosStyle.current) {
        androidx.compose.material3.Card(
            modifier = modifier, shape = shape, colors = colors ?: CardDefaults.cardColors(),
            elevation = elevation ?: CardDefaults.cardElevation(), border = border, content = content,
        )
        return
    }
    IosCard(modifier, colors, elevation != null, border, null, true, content)
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
    if (!LocalIosStyle.current) {
        androidx.compose.material3.Card(
            onClick = onClick, modifier = modifier, enabled = enabled, shape = shape, colors = colors ?: CardDefaults.cardColors(),
            elevation = elevation ?: CardDefaults.cardElevation(), border = border, interactionSource = interactionSource, content = content,
        )
        return
    }
    IosCard(modifier, colors, elevation != null, border, onClick, enabled, content)
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
    if (!LocalIosStyle.current) {
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
    if (!LocalIosStyle.current) {
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
    val c = color.takeOrElse { cs.primary }
    val tc = trackColor.takeOrElse { cs.onSurface.copy(alpha = 0.15f) }
    Canvas(modifier.size(40.dp)) {
        val sw = strokeWidth.toPx()
        val d = size.minDimension - sw
        val tl = Offset((size.width - d) / 2, (size.height - d) / 2)
        drawArc(tc, 0f, 360f, false, tl, Size(d, d), style = Stroke(sw))
        drawArc(c, -90f, 360f * progress().coerceIn(0f, 1f), false, tl, Size(d, d), style = Stroke(sw, cap = StrokeCap.Round))
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
    if (!LocalIosStyle.current) {
        androidx.compose.material3.CircularProgressIndicator(
            modifier = modifier,
            color = color.takeOrElse { ProgressIndicatorDefaults.circularColor },
            strokeWidth = strokeWidth,
            trackColor = trackColor.takeOrElse { ProgressIndicatorDefaults.circularIndeterminateTrackColor },
            strokeCap = strokeCap,
        )
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
    if (!LocalIosStyle.current) {
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
    val cs = MaterialTheme.colorScheme
    val dark = LocalDarkTheme.current
    val fill = cs.onSurface.copy(alpha = if (dark) 0.1f else 0.06f)
    val style = MaterialTheme.typography.bodyLarge.copy(color = if (enabled) cs.onSurface else cs.onSurface.copy(alpha = 0.4f), textAlign = TextAlign.Start)
    val hint = style.copy(color = cs.onSurfaceVariant)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = style,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        visualTransformation = visualTransformation,
        interactionSource = interactionSource,
        cursorBrush = SolidColor(cs.primary),
        decorationBox = { inner ->
            Column {
                Row(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(fill)
                        .then(if (isError) Modifier.border(1.dp, cs.error, RoundedCornerShape(12.dp)) else Modifier)
                        .defaultMinSize(minHeight = 44.dp).padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
                ) {
                    CompositionLocalProvider(LocalContentColor provides cs.onSurfaceVariant, LocalTextStyle provides hint) {
                        leadingIcon?.let { it(); Spacer(Modifier.width(8.dp)) }
                        prefix?.let { it(); Spacer(Modifier.width(4.dp)) }
                    }
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty()) (placeholder ?: label)?.let { p ->
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
                        Box(Modifier.padding(start = 12.dp, top = 4.dp)) { it() }
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
    androidx.compose.material3.ListItem(
        headlineContent = headlineContent, modifier = modifier, overlineContent = overlineContent,
        supportingContent = supportingContent, leadingContent = leadingContent, trailingContent = trailingContent,
        colors = colors, tonalElevation = if (LocalIosStyle.current) 0.dp else tonalElevation, shadowElevation = shadowElevation,
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
    if (!LocalIosStyle.current) {
        androidx.compose.material3.Snackbar(
            modifier = modifier, action = action, dismissAction = dismissAction, actionOnNewLine = actionOnNewLine, shape = shape,
            containerColor = containerColor, contentColor = contentColor, actionContentColor = actionContentColor,
            dismissActionContentColor = dismissActionContentColor, content = content,
        )
        return
    }
    val cs = MaterialTheme.colorScheme
    Row(
        modifier.widthIn(max = 560.dp).iosGlass(RoundedCornerShape(26.dp)).heightIn(min = 52.dp).padding(start = 18.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).padding(vertical = 10.dp)) {
            CompositionLocalProvider(
                LocalContentColor provides cs.onSurface,
                LocalTextStyle provides MaterialTheme.typography.bodyMedium.copy(color = cs.onSurface, fontWeight = FontWeight.Medium),
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
    if (!LocalIosStyle.current) {
        androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismissRequest, modifier = modifier, sheetState = sheetState, content = content)
        return
    }
    val cs = MaterialTheme.colorScheme
    val dark = LocalDarkTheme.current
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = if (dark) Color(0xF01C1C1E) else Color(0xF5F2F2F7),
        contentColor = cs.onSurface,
        tonalElevation = 0.dp,
        scrimColor = Color.Black.copy(alpha = 0.35f),
        dragHandle = {
            Box(Modifier.padding(top = 6.dp, bottom = 12.dp).size(36.dp, 5.dp).clip(CircleShape).background(cs.onSurfaceVariant.copy(alpha = 0.5f)))
        },
        content = content,
    )
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
