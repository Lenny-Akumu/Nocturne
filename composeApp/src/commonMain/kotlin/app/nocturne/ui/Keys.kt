package app.nocturne.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import app.nocturne.AppGraph

private var lastVolume = 0.6f

/**
 * Standard player keys on every platform (hardware keyboards on phones too). Text fields consume their own
 * keys first, so typing a space in search never pauses.
 * Space/K play-pause · ←/→ seek · Shift/Ctrl+←/→ prev/next · ↑/↓ volume · M mute · L like · S shuffle
 * · R repeat · F / Ctrl+F search · Esc back · media keys.
 */
fun handleAppKey(e: KeyEvent): Boolean {
    if (e.type != KeyEventType.KeyDown) return false
    if (!AppGraph.ready) return false
    val g = AppGraph.instance
    val p = g.player
    val s = g.settings.value
    val step = s.seekStepSeconds.coerceAtLeast(1) * 1000L
    val mod = e.isCtrlPressed || e.isMetaPressed
    if (e.isAltPressed) return false
    when (e.key) {
        Key.Spacebar, Key.K, Key.MediaPlayPause -> if (!mod) p.toggle() else return false
        Key.MediaPlay -> p.resume()
        Key.MediaPause -> p.pause()
        Key.MediaNext -> p.next()
        Key.MediaPrevious -> p.previous()
        Key.DirectionRight -> if (e.isShiftPressed || mod) p.next() else p.seekBy(step)
        Key.DirectionLeft -> if (e.isShiftPressed || mod) p.previous() else p.seekBy(-step)
        Key.J -> p.seekBy(-step)
        Key.DirectionUp -> p.setVolume((s.volume + 0.05f).coerceAtMost(1f))
        Key.DirectionDown -> p.setVolume((s.volume - 0.05f).coerceAtLeast(0f))
        Key.M -> if (s.volume > 0.01f) { lastVolume = s.volume; p.setVolume(0f) } else p.setVolume(lastVolume.coerceAtLeast(0.1f))
        Key.L -> p.state.value.current?.let { g.toggleLike(it) } ?: return false
        Key.S -> if (!mod) p.toggleShuffle() else return false
        Key.R -> if (!mod) p.cycleRepeat() else return false
        Key.F, Key.Slash -> g.navigator.selectTab(Screen.Search)
        Key.Escape -> return g.navigator.back()
        else -> return false
    }
    return true
}
