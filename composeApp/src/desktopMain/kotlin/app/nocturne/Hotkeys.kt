package app.nocturne

import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import app.nocturne.data.Settings

enum class HotkeyAction(val label: String, val default: String) {
    PLAY_PAUSE("Play / pause", "ctrl:32"),
    NEXT("Next track", "ctrl:39"),
    PREVIOUS("Previous track", "ctrl:37"),
    SEEK_FORWARD("Seek forward", "ctrl+shift:39"),
    SEEK_BACK("Seek back", "ctrl+shift:37"),
    VOLUME_UP("Volume up", "ctrl:38"),
    VOLUME_DOWN("Volume down", "ctrl:40"),
    LIKE("Like / unlike", "ctrl:76"),
    NOT_INTERESTED("Not interested", "ctrl:68"),
    SHUFFLE("Shuffle", "ctrl:83"),
    REPEAT("Repeat mode", "ctrl:82"),
    TOGGLE_PLAYER("Open / close player", "ctrl:80"),
    BACK("Go back", ":27"),
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
