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
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.debounce
import app.nocturne.player.VlcAudioEngine
import app.nocturne.ui.App
import app.nocturne.ui.theme.isDark
import java.io.File
import kotlin.system.exitProcess
import kotlin.math.roundToInt

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
        log("vlc available=${engine.available} bundled=${VlcAudioEngine.bundledDir() != null} dsp=${engine.dspActive}")
        if (isWindows && !engine.available) error("bundled VLC failed to load")
        Class.forName("javafx.scene.web.WebView")
        log("javafx ok")
        javax.swing.SwingUtilities.invokeAndWait { AppGraph.init(engine) }
        log("graph ok")
        // Listen-together relays through the packaged runtime (TLS, engine); informational only.
        runCatching {
            kotlinx.coroutines.runBlocking { kotlinx.coroutines.withTimeoutOrNull(40_000) { AppGraph.instance.party.runRelayChecks() } }
                ?.forEach { log("relay $it") } ?: log("relay check timed out")
        }.onFailure { log("relay check failed: $it") }
    }.onFailure { log("FAILED\n" + it.stackTraceToString()) }.isSuccess
    if (ok) log("SMOKE OK")
    Runtime.getRuntime().halt(if (ok) 0 else 1)
}

fun main(args: Array<String>) {
    // Must precede any skiko init: vsync-paced GPU frames (Direct3D on Windows keeps transparent windows hardware-composited).
    if (System.getProperty("skiko.vsync.enabled") == null) System.setProperty("skiko.vsync.enabled", "true")
    AppDirs.root = dataDir().apply { mkdirs() }
    args.firstOrNull { it.startsWith("--smoke-test=") }?.let { smokeTest(File(it.substringAfter('='))) }
    // nocturne://join?code=… (registered by the installer): hand it to a running instance, else handle it ourselves.
    args.firstOrNull { it.startsWith("nocturne:", ignoreCase = true) }?.let { url ->
        if (LinkForwarder.forward(url)) exitProcess(0)
        app.nocturne.party.PartyLinks.handle(url)
    }
    LinkForwarder.listen()
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
        androidx.compose.runtime.LaunchedEffect(Unit) { app.nocturne.ui.runPromoShot(graph, System.getenv("NOCTURNE_SHOT"), System.getenv("NOCTURNE_STYLE")) }
        val settings by graph.settings.state.collectAsState()
        val windowState = remember {
            val s0 = graph.settings.value
            val onScreen = s0.windowX != Int.MIN_VALUE && java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.any {
                it.defaultConfiguration.bounds.contains(s0.windowX + 60, s0.windowY + 20)
            }
            WindowState(
                placement = if (s0.windowMaximized) WindowPlacement.Maximized else WindowPlacement.Floating,
                position = if (onScreen) WindowPosition(s0.windowX.dp, s0.windowY.dp) else WindowPosition.PlatformDefault,
                size = DpSize(s0.windowW.coerceAtLeast(420).dp, s0.windowH.coerceAtLeast(600).dp),
            )
        }
        LaunchedEffect(windowState) {
            snapshotFlow { Triple(windowState.placement, windowState.position, windowState.size) }
                .debounce(600)
                .collect { (placement, pos, size) ->
                    graph.settings.update {
                        if (placement == WindowPlacement.Floating && pos is WindowPosition.Absolute) it.copy(
                            windowMaximized = false,
                            windowX = pos.x.value.toInt(), windowY = pos.y.value.toInt(),
                            windowW = size.width.value.toInt(), windowH = size.height.value.toInt(),
                        ) else it.copy(windowMaximized = placement == WindowPlacement.Maximized)
                    }
                }
        }
        val playing by graph.player.state.collectAsState()
        if (settings.floatingPlayer && (playing.current != null || app.nocturne.ui.FloatingEdit.editing)) app.nocturne.ui.FloatingPlayerWindow()

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
                if (e.isAltPressed && (e.key == Key.DirectionLeft || e.key == Key.DirectionRight)) {
                    if (e.key == Key.DirectionLeft) graph.navigator.back() else graph.navigator.forward()
                    return@Window true
                }
                if (e.isCtrlPressed) {
                    val step = when (e.key) {
                        Key.Equals, Key.Plus, Key.NumPadAdd -> 0.1f
                        Key.Minus, Key.NumPadSubtract -> -0.1f
                        Key.Zero, Key.NumPad0 -> 0f
                        else -> null
                    }
                    if (step != null) {
                        graph.settings.update { s ->
                            s.copy(uiScale = if (step == 0f) 1f else ((s.uiScale + step) * 10).roundToInt().div(10f).coerceIn(0.6f, 1.8f))
                        }
                        return@Window true
                    }
                }
                Hotkeys.find(e, settings)?.let { performHotkey(it) } ?: false
            },
            // plain player keys only if nothing focused (e.g. a text field) used them
            onKeyEvent = { e -> !Hotkeys.capturing && app.nocturne.ui.handleAppKey(e) },
        ) {
            window.minimumSize = java.awt.Dimension(420, 600)
            // mouse side buttons (X1 = back, X2 = forward), like a browser
            androidx.compose.runtime.DisposableEffect(Unit) {
                val listener = java.awt.event.AWTEventListener { ev ->
                    val me = ev as? java.awt.event.MouseEvent ?: return@AWTEventListener
                    if (me.id != java.awt.event.MouseEvent.MOUSE_PRESSED) return@AWTEventListener
                    when (me.button) {
                        4 -> javax.swing.SwingUtilities.invokeLater { graph.navigator.back() }
                        5 -> javax.swing.SwingUtilities.invokeLater { graph.navigator.forward() }
                    }
                }
                java.awt.Toolkit.getDefaultToolkit().addAWTEventListener(listener, java.awt.AWTEvent.MOUSE_EVENT_MASK)
                onDispose { java.awt.Toolkit.getDefaultToolkit().removeAWTEventListener(listener) }
            }
            LaunchedEffect(settings.iconBackground, settings.iconPalette) {
                val images = AppIcon.update(settings.iconBackground, settings.iconPalette)
                images.takeIf { it.isNotEmpty() }?.let { window.iconImages = it }
                // Start menu / desktop shortcuts follow too (Windows)
                val isDefault = settings.iconBackground == app.nocturne.data.IconBackground.SQUARE && settings.iconPalette == app.nocturne.data.IconPalette.STANDARD
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    ShellIcon.sync("${settings.iconBackground}-${settings.iconPalette}", isDefault, images)
                }
            }
            MainWindow.window = window
            LaunchedEffect(settings.globalHotkeys, settings.hotkeys) { GlobalHotkeys.update(settings) }
            LaunchedEffect(Unit) { DiscordRpc.start() }
            val dark = isDark(settings)
            var backdrop by remember { mutableStateOf(false) }
            LaunchedEffect(settings.windowBackdrop, dark) {
                backdrop = runCatching { WindowEffects.apply(window, settings.windowBackdrop, dark) }.getOrDefault(false)
            }
            App(backdrop = backdrop)
        }
    }
}
