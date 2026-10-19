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
import kotlin.system.exitProcess

private fun dataDir(): File {
    val appData = System.getenv("APPDATA")
    return if (isWindows && appData != null) File(appData, "Nocturne") else File(System.getProperty("user.home"), ".nocturne")
}

private fun reportCrash(t: Throwable) {
    val log = File(AppDirs.root, "crash.log")
    runCatching { log.appendText("${java.time.LocalDateTime.now()} Nocturne ${BuildInfo.VERSION}\n${t.stackTraceToString()}\n\n") }
    runCatching {
        javax.swing.JOptionPane.showMessageDialog(
            null,
            "Nocturne hit an error and has to close:\n$t\n\nDetails were saved to:\n${log.absolutePath}",
            "Nocturne",
            javax.swing.JOptionPane.ERROR_MESSAGE,
        )
    }
}

private fun smokeTest(output: File) {
    fun log(line: String) = output.appendText(line + "\n")
    output.writeText("Nocturne ${BuildInfo.VERSION}\n")
    Thread {
        Thread.sleep(90_000)
        log("TIMEOUT")
        Runtime.getRuntime().halt(2)
    }.apply { isDaemon = true }.start()
    val ok = runCatching {
        org.jetbrains.skia.Surface.makeRasterN32Premul(16, 16).close()
        log("skia ok")
        val engine = VlcAudioEngine()
        log("vlc available=${engine.available} bundled=${VlcAudioEngine.bundledDir() != null}")
        if (isWindows && !engine.available) error("bundled VLC failed to load")
        Class.forName("javafx.scene.web.WebView")
        log("javafx ok")
        javax.swing.SwingUtilities.invokeAndWait { AppGraph.init(engine) }
        log("graph ok")
    }.onFailure { log("FAILED\n" + it.stackTraceToString()) }.isSuccess
    if (ok) log("SMOKE OK")
    Runtime.getRuntime().halt(if (ok) 0 else 1)
}

fun main(args: Array<String>) {
    AppDirs.root = dataDir().apply { mkdirs() }
    args.firstOrNull { it.startsWith("--smoke-test=") }?.let { smokeTest(File(it.substringAfter('='))) }
    Thread.setDefaultUncaughtExceptionHandler { _, e ->
        reportCrash(e)
        exitProcess(1)
    }
    try {
        launchApp()
    } catch (t: Throwable) {
        reportCrash(t)
        exitProcess(1)
    }
}

private fun launchApp() {
    val icon = runCatching {
        Thread.currentThread().contextClassLoader.getResourceAsStream("nocturne.png")!!.use {
            BitmapPainter(org.jetbrains.skia.Image.makeFromEncoded(it.readBytes()).toComposeImageBitmap())
        }
    }.getOrNull()

    application {
        val graph = remember { AppGraph.init(VlcAudioEngine()) }
        val settings by graph.settings.state.collectAsState()
        val windowState = rememberWindowState(size = DpSize(1280.dp, 820.dp))
        val playing by graph.player.state.collectAsState()
        if (settings.floatingPlayer && playing.current != null) app.nocturne.ui.FloatingPlayerWindow()

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
                when (e.key) {
                    Key.MediaPlayPause -> { p.toggle(); return@Window true }
                    Key.MediaNext -> { p.next(); return@Window true }
                    Key.MediaPrevious -> { p.previous(); return@Window true }
                    else -> Unit
                }
                if (Hotkeys.capturing) return@Window false
                Hotkeys.find(e, settings)?.let { performHotkey(it) } ?: false
            },
        ) {
            window.minimumSize = java.awt.Dimension(420, 600)
            MainWindow.window = window
            LaunchedEffect(settings.globalHotkeys, settings.hotkeys) { GlobalHotkeys.update(settings) }
            val dark = isDark(settings)
            var backdrop by remember { mutableStateOf(false) }
            LaunchedEffect(settings.windowBackdrop, dark) {
                backdrop = runCatching { WindowEffects.apply(window, settings.windowBackdrop, dark) }.getOrDefault(false)
            }
            App(backdrop = backdrop)
        }
    }
}
