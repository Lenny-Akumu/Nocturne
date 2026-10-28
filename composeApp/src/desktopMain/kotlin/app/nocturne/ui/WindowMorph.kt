package app.nocturne.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import app.nocturne.CustomFrame
import app.nocturne.data.IconBackground
import app.nocturne.data.IconPalette
import java.awt.Frame
import java.awt.Rectangle
import javax.swing.SwingUtilities

/** Main window ⇄ floating player: the window shrinks into the pill when minimized and grows out of it on restore. */
object WindowMorph {
    /** On-screen pill of the floating player (AWT units), null while it isn't shown. */
    @Volatile var pill: Rectangle? = null
    @Volatile var dark = true

    internal class Spec(val from: Rectangle, val to: Rectangle, val grow: Boolean, val done: () -> Unit)
    internal var spec by mutableStateOf<Spec?>(null)
    private var normal: Rectangle? = null

    fun install(window: Frame) {
        CustomFrame.onMinimize = { pill != null && spec == null && SwingUtilities.invokeLater { minimize(window) }.let { true } }
        CustomFrame.onRestore = { pill != null && spec == null && SwingUtilities.invokeLater { restore(window) }.let { true } }
    }

    fun minimize(window: Frame, state: androidx.compose.ui.window.WindowState? = null) {
        val p = pill
        if (p == null || spec != null || !CustomFrame.installed) {
            if (state != null) state.isMinimized = true else window.extendedState = window.extendedState or Frame.ICONIFIED
            return
        }
        val from = window.bounds
        normal = from
        spec = Spec(from, p, grow = false) {}
        window.extendedState = window.extendedState or Frame.ICONIFIED
    }

    /** Grows out of the pill, then shows the window; false when there's nothing to grow from. */
    fun restore(window: Frame): Boolean {
        val p = pill ?: return false
        if (spec != null || !CustomFrame.installed) return false
        val to = normal ?: window.bounds
        spec = Spec(p, to, grow = true) {
            window.extendedState = window.extendedState and Frame.ICONIFIED.inv()
            window.isVisible = true
            window.toFront()
            window.requestFocus()
        }
        return true
    }
}

private fun lerp(a: Int, b: Int, t: Float) = a + (b - a) * t

/** Transparent, click-through overlay that plays the morph; parked off-screen while idle. */
@Composable
fun WindowMorphOverlay(palette: IconPalette) {
    val spec = WindowMorph.spec
    val area = spec?.let { it.from.union(it.to).apply { grow(24, 24) } }
    val state = rememberDialogState(position = WindowPosition((-40).dp, (-40).dp), size = DpSize(1.dp, 1.dp))
    DialogWindow(
        onCloseRequest = {},
        state = state,
        title = "",
        undecorated = true,
        transparent = true,
        resizable = false,
        focusable = false,
    ) {
        LaunchedEffect(Unit) { window.isAlwaysOnTop = true }
        LaunchedEffect(area) {
            if (area == null) window.setBounds(-40, -40, 1, 1) else window.setBounds(area.x, area.y, area.width, area.height)
        }
        val t = remember { Animatable(0f) }
        LaunchedEffect(spec) {
            if (spec == null) return@LaunchedEffect
            t.snapTo(0f)
            t.animateTo(1f, tween(if (spec.grow) 320 else 280, easing = FastOutSlowInEasing))
            spec.done()
            WindowMorph.spec = null
        }
        val colors = remember(palette) { palette.colors() }
        if (spec != null && area != null) Canvas(Modifier.fillMaxSize()) {
            val k = size.width / area.width
            val p = t.value
            val x = lerp(spec.from.x, spec.to.x, p) - area.x
            val y = lerp(spec.from.y, spec.to.y, p) - area.y
            val w = lerp(spec.from.width, spec.to.width, p)
            val h = lerp(spec.from.height, spec.to.height, p)
            val small = if (spec.grow) 1f - p else p
            val r = (14f + 16f * small) * density
            val bg = if (WindowMorph.dark) Color(0xFF111114) else Color(0xFFF4F4F7)
            val alpha = if (spec.grow) 0.6f + 0.4f * p else 1f - 0.35f * p
            drawRoundRect(bg.copy(alpha = alpha), Offset(x * k, y * k), Size(w * k, h * k), CornerRadius(r))
            drawRoundRect(Color.White.copy(alpha = 0.08f * alpha), Offset(x * k, y * k), Size(w * k, h * k), CornerRadius(r), style = Stroke(1f * density))
            val icon = minOf(w, h) * k * 0.28f
            withTransform({
                translate(x * k + (w * k - icon) / 2, y * k + (h * k - icon) / 2)
                scale(icon / size.minDimension, icon / size.minDimension, Offset.Zero)
            }) { drawNocturneIcon(IconBackground.NONE, colors) }
        }
    }
}
