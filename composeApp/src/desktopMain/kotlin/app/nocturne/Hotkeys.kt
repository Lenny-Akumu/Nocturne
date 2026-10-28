package app.nocturne

import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import app.nocturne.data.Settings

enum class HotkeyAction(val label: String, val default: String, val global: Boolean = true) {
    PLAY_PAUSE("Play / pause", "ctrl+alt:32"),
    NEXT("Next track", "ctrl+alt:39"),
    PREVIOUS("Previous track", "ctrl+alt:37"),
    SEEK_FORWARD("Seek forward", "ctrl+alt+shift:39"),
    SEEK_BACK("Seek back", "ctrl+alt+shift:37"),
    VOLUME_UP("Volume up", "ctrl+alt:38"),
    VOLUME_DOWN("Volume down", "ctrl+alt:40"),
    LIKE("Like / unlike", "ctrl+alt:76"),
    NOT_INTERESTED("Not interested", "ctrl+alt:68"),
    SHUFFLE("Shuffle", "ctrl+alt:83"),
    REPEAT("Repeat mode", "ctrl+alt:82"),
    TOGGLE_PLAYER("Open / close player", "ctrl+alt:80"),
    BACK("Go back", ":27", global = false),
}

object Hotkeys {
    @Volatile var capturing = false

    private val modifierCodes = setOf(16, 17, 18, 157, 524)

    fun binding(action: HotkeyAction, settings: Settings): String = settings.hotkeys[action.name] ?: action.default

    fun isModifierOnly(event: KeyEvent) = event.key.nativeKeyCode in modifierCodes

    fun of(event: KeyEvent): String {
        val mods = buildList {
            if (event.isCtrlPressed) add("ctrl")
            if (event.isShiftPressed) add("shift")
            if (event.isAltPressed) add("alt")
            if (event.isMetaPressed) add("meta")
        }.joinToString("+")
        return "$mods:${event.key.nativeKeyCode}"
    }

    fun label(binding: String): String {
        if (binding.isBlank()) return "Not set"
        val mods = binding.substringBefore(':').split('+').filter { it.isNotBlank() }
            .map { it.replaceFirstChar(Char::uppercase) }
        val code = binding.substringAfter(':').toIntOrNull() ?: return "Not set"
        return (mods + java.awt.event.KeyEvent.getKeyText(code)).joinToString(" + ")
    }

    fun find(event: KeyEvent, settings: Settings): HotkeyAction? {
        val b = of(event)
        return HotkeyAction.entries.firstOrNull { binding(it, settings) == b }
    }
}

fun performHotkey(action: HotkeyAction): Boolean {
    val graph = AppGraph.instance
    val p = graph.player
    val settings = graph.settings.value
    val step = settings.seekStepSeconds * 1000L
    val current = p.state.value.current
    when (action) {
        HotkeyAction.PLAY_PAUSE -> p.toggle()
        HotkeyAction.NEXT -> p.next()
        HotkeyAction.PREVIOUS -> p.previous()
        HotkeyAction.SEEK_FORWARD -> p.seekBy(step)
        HotkeyAction.SEEK_BACK -> p.seekBy(-step)
        HotkeyAction.VOLUME_UP -> p.setVolume((settings.volume + 0.1f).coerceAtMost(1f))
        HotkeyAction.VOLUME_DOWN -> p.setVolume((settings.volume - 0.1f).coerceAtLeast(0f))
        HotkeyAction.LIKE -> current?.let { graph.toggleLike(it) }
        HotkeyAction.NOT_INTERESTED -> current?.let { graph.notInterested(it) }
        HotkeyAction.SHUFFLE -> p.toggleShuffle()
        HotkeyAction.REPEAT -> p.cycleRepeat()
        HotkeyAction.TOGGLE_PLAYER -> {
            MainWindow.show()
            graph.navigator.playerExpanded = !graph.navigator.playerExpanded && current != null
        }
        HotkeyAction.BACK -> return graph.navigator.back()
    }
    return true
}

object MainWindow {
    @Volatile var window: java.awt.Frame? = null

    fun show() {
        val w = window ?: return
        javax.swing.SwingUtilities.invokeLater {
            if (w.extendedState and java.awt.Frame.ICONIFIED != 0 && app.nocturne.ui.WindowMorph.restore(w)) return@invokeLater
            if (w.extendedState and java.awt.Frame.ICONIFIED != 0) w.extendedState = w.extendedState and java.awt.Frame.ICONIFIED.inv()
            w.isVisible = true
            w.toFront()
            w.requestFocus()
        }
    }
}

object GlobalHotkeys {
    private const val WM_HOTKEY = 0x0312
    private const val MEDIA_BASE = 1000
    @Volatile private var wanted: Map<Int, Pair<Int, Int>> = emptyMap()
    @Volatile private var dirty = false
    private var thread: Thread? = null

    fun update(settings: Settings) {
        if (!isWindows) return
        val map = mutableMapOf<Int, Pair<Int, Int>>()
        if (settings.globalHotkeys) {
            HotkeyAction.entries.filter { it.global }.forEach { a ->
                val b = Hotkeys.binding(a, settings)
                val code = b.substringAfter(':').toIntOrNull() ?: return@forEach
                val mods = b.substringBefore(':').split('+').filter { it.isNotBlank() }
                if (mods.isEmpty()) return@forEach
                var flags = 0x4000
                if ("alt" in mods) flags = flags or 0x1
                if ("ctrl" in mods) flags = flags or 0x2
                if ("shift" in mods) flags = flags or 0x4
                if ("meta" in mods) flags = flags or 0x8
                map[a.ordinal + 1] = flags to code
            }
            map[MEDIA_BASE] = 0x4000 to 0xB3
            map[MEDIA_BASE + 1] = 0x4000 to 0xB0
            map[MEDIA_BASE + 2] = 0x4000 to 0xB1
        }
        wanted = map
        dirty = true
        if (thread == null) start()
    }

    private fun start() {
        thread = Thread({
            val user32 = com.sun.jna.platform.win32.User32.INSTANCE
            val registered = mutableSetOf<Int>()
            val msg = com.sun.jna.platform.win32.WinUser.MSG()
            while (true) {
                if (dirty) {
                    dirty = false
                    registered.forEach { user32.UnregisterHotKey(null, it) }
                    registered.clear()
                    wanted.forEach { (id, v) -> if (user32.RegisterHotKey(null, id, v.first, v.second)) registered += id }
                }
                while (user32.PeekMessage(msg, null, 0, 0, 1)) {
                    if (msg.message == WM_HOTKEY) {
                        val id = msg.wParam.toInt()
                        if (!Hotkeys.capturing) javax.swing.SwingUtilities.invokeLater {
                            val p = AppGraph.instance.player
                            when (id) {
                                MEDIA_BASE -> p.toggle()
                                MEDIA_BASE + 1 -> p.next()
                                MEDIA_BASE + 2 -> p.previous()
                                else -> HotkeyAction.entries.getOrNull(id - 1)?.let { performHotkey(it) }
                            }
                        }
                    }
                }
                Thread.sleep(25)
            }
        }, "nocturne-hotkeys").apply { isDaemon = true; start() }
    }
}
