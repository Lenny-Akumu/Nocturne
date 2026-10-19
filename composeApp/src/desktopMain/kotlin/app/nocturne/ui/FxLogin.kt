package app.nocturne.ui

import javafx.application.Platform
import javafx.concurrent.Worker
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.web.WebEngine
import javafx.scene.web.WebView
import javafx.stage.Stage
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.URI
import javax.swing.JFrame
import javax.swing.SwingUtilities

internal const val FIREFOX_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"

internal object FxLogin {
    private val cookies: CookieManager by lazy {
        CookieManager(null, CookiePolicy.ACCEPT_ALL).also { CookieHandler.setDefault(it) }
    }

    fun clear() {
        runCatching { cookies.cookieStore.removeAll() }
    }

    fun open(title: String, url: String, cookieUrls: List<String>, cookieKey: String, onCookies: (String) -> Unit, onClose: () -> Unit) {
        val jar = cookies
        SwingUtilities.invokeLater {
            val frame = JFrame("Nocturne — $title")
            val panel = JFXPanel()
            frame.contentPane.add(panel)
            frame.setSize(520, 760)
            frame.setLocationRelativeTo(null)
            frame.defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
            var done = false
            val timer = java.util.Timer(true)
            fun check(): Boolean {
                if (done) return true
                val header = cookieUrls.map { u -> jar.cookieStore.get(URI(u)).joinToString("; ") { "${it.name}=${it.value}" } }
                    .firstOrNull { it.contains("$cookieKey=") } ?: return false
                done = true
                timer.cancel()
                SwingUtilities.invokeLater { frame.dispose() }
                onCookies(header)
                return true
            }
            timer.schedule(object : java.util.TimerTask() {
                override fun run() {
                    SwingUtilities.invokeLater { check() }
                }
            }, 1000, 1000)
            frame.addWindowListener(object : WindowAdapter() {
                override fun windowClosed(e: WindowEvent?) {
                    timer.cancel()
                    if (!check()) onClose()
                }
            })
            frame.isVisible = true
            Platform.setImplicitExit(false)
            Platform.runLater {
                val view = WebView()
                setup(view.engine) { check() }
                view.engine.load(url)
                panel.scene = Scene(view)
            }
        }
    }

    private fun setup(engine: WebEngine, onChange: () -> Unit) {
        engine.userAgent = FIREFOX_UA
        engine.isJavaScriptEnabled = true
        engine.locationProperty().addListener { _, _, _ -> onChange() }
        engine.loadWorker.stateProperty().addListener { _, _, s -> if (s == Worker.State.SUCCEEDED) onChange() }
        engine.setCreatePopupHandler {
            val popup = WebView()
            val stage = Stage()
            stage.title = "Nocturne"
            stage.scene = Scene(popup, 500.0, 680.0)
            setup(popup.engine, onChange)
            popup.engine.setOnVisibilityChanged { e -> if (!e.data) stage.close() }
            stage.show()
            popup.engine
        }
    }
}
