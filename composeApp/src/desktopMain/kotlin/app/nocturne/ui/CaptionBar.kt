package app.nocturne.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.FilterNone
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import app.nocturne.CustomFrame
import app.nocturne.ui.kit.Icon
import app.nocturne.ui.theme.LocalDarkTheme

/** Minimize / maximize / close in a frosted capsule; the rest of the strip drags the window. */
@Composable
fun CaptionBar(window: java.awt.Window, state: WindowState) {
    val density = LocalDensity.current
    CustomFrame.captionPx = with(density) { WindowChrome.height.roundToPx() }
    Box(Modifier.fillMaxWidth().height(WindowChrome.height)) {
        val shape = RoundedCornerShape(8.dp)
        val dark = LocalDarkTheme.current
        val cs = MaterialTheme.colorScheme
        val glass = cs.onSurface.copy(alpha = if (dark) 0.07f else 0.05f)
        Row(
            Modifier.align(Alignment.CenterEnd).padding(end = 6.dp)
                .onGloballyPositioned { c ->
                    val b = c.boundsInWindow()
                    CustomFrame.buttons = java.awt.Rectangle(b.left.toInt(), b.top.toInt(), b.width.toInt(), b.height.toInt())
                }
                .clip(shape)
                .background(glass, shape),
        ) {
            val maximized = state.placement == WindowPlacement.Maximized
            CaptionButton(Icons.Rounded.Remove, cs.onSurface.copy(alpha = 0.1f)) { (window as? java.awt.Frame)?.let { WindowMorph.minimize(it, state) } ?: run { state.isMinimized = true } }
            CaptionButton(if (maximized) Icons.Rounded.FilterNone else Icons.Rounded.CropSquare, cs.onSurface.copy(alpha = 0.1f), small = true) {
                state.placement = if (maximized) WindowPlacement.Floating else WindowPlacement.Maximized
            }
            CaptionButton(Icons.Rounded.Close, Color(0xFFE5484D), hoverTint = Color.White) {
                window.dispatchEvent(java.awt.event.WindowEvent(window, java.awt.event.WindowEvent.WINDOW_CLOSING))
            }
        }
    }
}

@Composable
private fun CaptionButton(icon: ImageVector, hoverBg: Color, small: Boolean = false, hoverTint: Color? = null, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    val bg by animateColorAsState(if (hovered) hoverBg else Color.Transparent, label = "cap")
    val fg = if (hovered && hoverTint != null) hoverTint else MaterialTheme.colorScheme.onSurface
    Box(
        Modifier.size(40.dp, 26.dp).background(bg).hoverable(src).clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(if (small) 12.dp else 15.dp), tint = fg.copy(alpha = if (hovered) 1f else 0.8f))
    }
}
