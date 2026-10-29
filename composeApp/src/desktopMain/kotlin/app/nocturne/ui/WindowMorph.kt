package app.nocturne.ui

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.Alignment
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
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
    /** Where the pill will sit before it has been laid out once. */
    @Volatile var pillGuess: Rectangle? = null
    private val target get() = pill ?: pillGuess
    @Volatile var dark = true

    internal class Spec(val from: Rectangle, val to: Rectangle, val grow: Boolean, val done: () -> Unit)
    internal var spec by mutableStateOf<Spec?>(null)
    private var normal: Rectangle? = null

    fun install(window: Frame) {
        CustomFrame.onMinimize = { target != null && spec == null && SwingUtilities.invokeLater { minimize(window) }.let { true } }
        CustomFrame.onRestore = { target != null && spec == null && SwingUtilities.invokeLater { restore(window) }.let { true } }
    }

    fun minimize(window: Frame, state: androidx.compose.ui.window.WindowState? = null) {
        val p = target
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
        val p = target ?: return false
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

// fast out, long soft landing
private val Swoosh = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0.9f, 0.1f, 1f)

/** Transparent, click-through overlay that plays the morph; parked off-screen while idle. */
@Composable
fun WindowMorphOverlay(palette: IconPalette) {
    val spec = WindowMorph.spec
    val area = spec?.let { it.from.union(it.to).apply { grow(40, 40) } }
    val state = rememberDialogState(position = WindowPosition((-40).dp, (-40).dp), size = DpSize(1.dp, 1.dp))
    val art = app.nocturne.AppGraph.instance.player.state.collectAsState().value.current?.artworkUrl
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
            t.animateTo(1f, tween(if (spec.grow) 300 else 250, easing = Swoosh))
            spec.done()
            WindowMorph.spec = null
        }
        if (spec == null || area == null) return@DialogWindow
        val p = t.value
        val x = lerp(spec.from.x, spec.to.x, p) - area.x
        val y = lerp(spec.from.y, spec.to.y, p) - area.y
        val w = lerp(spec.from.width, spec.to.width, p)
        val h = lerp(spec.from.height, spec.to.height, p)
        // 0 = window, 1 = pill
        val pillness = if (spec.grow) 1f - p else p
        val shape = RoundedCornerShape((14f + (h / 2f - 14f).coerceAtLeast(0f).coerceAtMost(40f) * pillness).dp)
        val dark = WindowMorph.dark
        Box(
            Modifier
                .offset(x.dp, y.dp)
                .size(w.dp, h.dp)
                .graphicsLayer {
                    // a touch of squash in flight
                    val k = 4f * p * (1f - p)
                    scaleX = 1f + 0.03f * k
                    scaleY = 1f - 0.03f * k
                    alpha = if (spec.grow) 0.75f + 0.25f * p else 1f - 0.25f * p
                }
                .shadow(28.dp, shape, ambientColor = Color.Black.copy(alpha = 0.4f), spotColor = Color.Black.copy(alpha = 0.5f))
                .clip(shape)
                .background(if (dark) Color(0xFF111114) else Color(0xFFF4F4F7)),
        ) {
            if (art != null) {
                coil3.compose.AsyncImage(
                    art, null,
                    Modifier.fillMaxSize().blur(36.dp).graphicsLayer { alpha = 0.55f },
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
            }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.05f), Color.Black.copy(alpha = 0.35f)))))
            val side = (minOf(w, h) * (0.42f + 0.38f * pillness)).dp
            Box(
                Modifier.align(if (pillness > 0.5f) Alignment.CenterStart else Alignment.Center)
                    .padding(start = (10f * pillness).dp)
                    .size(side)
                    .shadow(12.dp, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape((12f + 8f * pillness).dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (art != null) coil3.compose.AsyncImage(art, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                else NocturneIcon(IconBackground.SQUARE, palette, Modifier.fillMaxSize())
            }
        }
    }
}
