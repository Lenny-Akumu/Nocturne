package app.nocturne.ui.kit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nocturne.data.DesignStyle
import app.nocturne.ui.theme.LocalDarkTheme

private class GroupRow(val inset: Dp)

// Marks a child of [GroupedColumn] as a grouped option row; [inset] = separator start.
fun Modifier.optionRow(inset: Dp = 16.dp): Modifier = layoutId(GroupRow(inset))

// Column that draws a container behind each run of consecutive [optionRow] children: iOS inset-grouped (Glass),
// Material 3 settings card, Fluent card (Windows). [segmented] gives every row its own tile (Material/Windows only).
@Composable
fun GroupedColumn(modifier: Modifier = Modifier, side: Dp = 16.dp, segmented: Boolean = false, content: @Composable () -> Unit) {
    val style = LocalDesignStyle.current
    val cs = MaterialTheme.colorScheme
    val dark = LocalDarkTheme.current
    val split = segmented && style != DesignStyle.GLASS
    val bg = when (style) {
        DesignStyle.GLASS -> cs.surfaceContainerLow
        DesignStyle.MATERIAL -> cs.surfaceContainerHigh
        // WinUI CardBackgroundFillColorDefault: works on the page and on sheets.
        DesignStyle.WINDOWS -> if (dark) Color.White.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.7f)
    }
    val stroke = if (style == DesignStyle.WINDOWS) cs.outlineVariant else null
    val sep = cs.outlineVariant
    val hair = if (style == DesignStyle.GLASS) 0.5.dp else 1.dp
    val outer = when (style) { DesignStyle.GLASS -> 22.dp; DesignStyle.MATERIAL -> 20.dp; DesignStyle.WINDOWS -> 8.dp }
    val inner = if (!split) 0.dp else if (style == DesignStyle.WINDOWS) 8.dp else 4.dp
    val gap = if (!split) 0.dp else if (style == DesignStyle.WINDOWS) 4.dp else 2.dp
    SubcomposeLayout(modifier) { constraints ->
        val w = constraints.maxWidth
        val sidePx = side.roundToPx()
        val cw = (w - 2 * sidePx).coerceAtLeast(0)
        val measurables = subcompose("content", content)
        val rows = measurables.map { it.layoutId as? GroupRow }
        val placeables = measurables.mapIndexed { i, m ->
            if (rows[i] != null) m.measure(Constraints.fixedWidth(cw)) else m.measure(Constraints(maxWidth = w))
        }
        // Empty non-row children (dialogs, effects) don't break a group.
        val runs = mutableListOf<List<Int>>()
        var cur = mutableListOf<Int>()
        for (i in rows.indices) {
            if (rows[i] != null) cur += i
            else if (placeables[i].height > 0 && cur.isNotEmpty()) { runs += cur; cur = mutableListOf() }
        }
        if (cur.isNotEmpty()) runs += cur
        val follows = BooleanArray(placeables.size)
        val shapes = arrayOfNulls<Shape>(placeables.size)
        runs.forEach { run ->
            run.forEachIndexed { k, i ->
                if (k > 0) follows[i] = true
                val top = if (k == 0) outer else inner
                val bottom = if (k == run.lastIndex) outer else inner
                shapes[i] = RoundedCornerShape(topStart = top, topEnd = top, bottomEnd = bottom, bottomStart = bottom)
            }
        }
        val gapPx = gap.roundToPx()
        val ys = IntArray(placeables.size)
        var total = 0
        placeables.forEachIndexed { i, p -> if (follows[i]) total += gapPx; ys[i] = total; total += p.height }
        val bgs = runs.mapIndexed { ri, run ->
            val top = ys[run.first()]
            val h = ys[run.last()] + placeables[run.last()].height - top
            val tiles = if (split) run.map { Triple(ys[it] - top, placeables[it].height, shapes[it]!!) }
            else listOf(Triple(0, h, RoundedCornerShape(outer)))
            val seps = if (split) emptyList() else run.zipWithNext { a, b -> (ys[b] - top).toFloat() to rows[a]!!.inset.toPx() }
            subcompose("bg$ri") {
                Box(
                    Modifier.drawBehind {
                        tiles.forEach { (y, th, shape) ->
                            translate(top = y.toFloat()) {
                                drawOutline(shape.createOutline(Size(size.width, th.toFloat()), layoutDirection, this), bg)
                                if (stroke != null) {
                                    val s = 1.dp.toPx()
                                    translate(s / 2, s / 2) {
                                        drawOutline(shape.createOutline(Size(size.width - s, th - s), layoutDirection, this), stroke, style = Stroke(s))
                                    }
                                }
                            }
                        }
                        val t = hair.toPx().coerceAtLeast(1f)
                        seps.forEach { (y, inset) -> drawRect(sep, Offset(inset, y - t / 2), Size(size.width - inset, t)) }
                    },
                )
            }.first().measure(Constraints.fixed(cw, h))
        }
        layout(w, total) {
            runs.forEachIndexed { ri, run -> bgs[ri].place(sidePx, ys[run.first()]) }
            placeables.forEachIndexed { i, p ->
                val s = shapes[i]
                if (s != null) p.placeWithLayer(sidePx, ys[i]) { shape = s; clip = true } else p.place(0, ys[i])
            }
        }
    }
}

// Section title above an option group.
@Composable
fun OptionHeader(title: String, modifier: Modifier = Modifier, icon: ImageVector? = null, side: Dp = 16.dp) {
    val style = LocalDesignStyle.current
    val cs = MaterialTheme.colorScheme
    val pad = when (style) {
        DesignStyle.GLASS -> PaddingValues(start = side + 16.dp, end = side + 16.dp, top = 24.dp, bottom = 7.dp)
        DesignStyle.MATERIAL -> PaddingValues(start = side + 16.dp, end = side + 16.dp, top = 20.dp, bottom = 8.dp)
        DesignStyle.WINDOWS -> PaddingValues(start = side + 4.dp, end = side + 4.dp, top = 20.dp, bottom = 8.dp)
    }
    Row(modifier.fillMaxWidth().padding(pad), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(18.dp), tint = if (style == DesignStyle.WINDOWS) cs.onSurface else cs.primary)
            Spacer(Modifier.width(8.dp))
        }
        when (style) {
            DesignStyle.GLASS -> Text(title.uppercase(), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            DesignStyle.MATERIAL -> Text(title, style = MaterialTheme.typography.labelLarge, color = cs.primary)
            // Fluent BodyStrong section title.
            DesignStyle.WINDOWS -> Text(title, style = MaterialTheme.typography.titleSmall, color = cs.onSurface)
        }
    }
}

// Titled container of option rows; children marked with [optionRow] (or [OptionRow]) are grouped.
@Composable
fun OptionGroup(
    title: String?,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    side: Dp = 16.dp,
    segmented: Boolean = false,
    content: @Composable () -> Unit,
) {
    Column(modifier) {
        if (title != null) OptionHeader(title, icon = icon, side = side)
        GroupedColumn(side = side, segmented = segmented, content = content)
    }
}

// Standard row of an [OptionGroup]; put clickable etc. in [modifier] so it covers the whole row.
@Composable
fun OptionRow(
    modifier: Modifier = Modifier,
    inset: Dp = 16.dp,
    padding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit,
) {
    Row(Modifier.optionRow(inset).fillMaxWidth().then(modifier).padding(padding), verticalAlignment = Alignment.CenterVertically, content = content)
}
