package app.nocturne

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.nocturne.player.VlcAudioEngine
import app.nocturne.ui.App
import app.nocturne.ui.theme.isDark
import java.io.File

private fun dataDir(): File {
    val appData = System.getenv("APPDATA")
    return if (isWindows && appData != null) File(appData, "Nocturne") else File(System.getProperty("user.home"), ".nocturne")
}

fun main() {
    AppDirs.root = dataDir().apply { mkdirs() }
    val icon = runCatching {
        Thread.currentThread().contextClassLoader.getResourceAsStream("nocturne.png")!!.use {
            BitmapPainter(org.jetbrains.skia.Image.makeFromEncoded(it.readBytes()).toComposeImageBitmap())
        }
    }.getOrNull()

    application {
        val graph = remember { AppGraph.init(VlcAudioEngine()) }
        val settings by graph.settings.state.collectAsState()
        val windowState = rememberWindowState(size = DpSize(1280.dp, 820.dp))

        Window(
            onCloseRequest = {
                graph.player.pause()
                graph.engine.release()
                exitApplication()
            },
            title = "Nocturne",
            icon = icon,
            state = windowState,
            onPreviewKeyEvent = { e ->
                if (e.type != KeyEventType.KeyDown) return@Window false
                val p = graph.player
                val step = settings.seekStepSeconds * 1000L
                when {
                    e.isCtrlPressed && e.key == Key.Spacebar -> { p.toggle(); true }
                    e.isCtrlPressed && e.isShiftPressed && e.key == Key.DirectionRight -> { p.seekBy(step); true }
                    e.isCtrlPressed && e.isShiftPressed && e.key == Key.DirectionLeft -> { p.seekBy(-step); true }
                    e.isCtrlPressed && e.key == Key.DirectionRight -> { p.next(); true }
                    e.isCtrlPressed && e.key == Key.DirectionLeft -> { p.previous(); true }
                    e.key == Key.MediaPlayPause -> { p.toggle(); true }
                    e.key == Key.MediaNext -> { p.next(); true }
                    e.key == Key.MediaPrevious -> { p.previous(); true }
                    e.key == Key.Escape -> graph.navigator.back()
                    else -> false
                }
            },
        ) {
            window.minimumSize = java.awt.Dimension(420, 600)
            val dark = isDark(settings)
            var backdrop by remember { mutableStateOf(false) }
            LaunchedEffect(settings.windowBackdrop, dark) {
                backdrop = runCatching { WindowEffects.apply(window, settings.windowBackdrop, dark) }.getOrDefault(false)
            }
            App(backdrop = backdrop)
        }
    }
}
