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

internal const val CHROME_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36"

internal object FxLogin {
    private val cookies: CookieManager by lazy {
        CookieManager(null, CookiePolicy.ACCEPT_ALL).also { CookieHandler.setDefault(it) }
    }

    fun clear() {
        runCatching { cookies.cookieStore.removeAll() }
    }

    fun open(title: String, url: String, cookieUrl: String, cookieKey: String, onCookies: (String) -> Unit, onClose: () -> Unit) {
        val jar = cookies
        SwingUtilities.invokeLater {
            val frame = JFrame("Nocturne — $title")
            val panel = JFXPanel()
            frame.contentPane.add(panel)
            frame.setSize(520, 760)
            frame.setLocationRelativeTo(null)
            frame.defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
            var done = false
            frame.addWindowListener(object : WindowAdapter() {
                override fun windowClosed(e: WindowEvent?) {
                    if (!done) onClose()
                }
            })
            fun check() {
                val header = jar.cookieStore.get(URI(cookieUrl)).joinToString("; ") { "${it.name}=${it.value}" }
                if (!done && header.contains("$cookieKey=")) {
                    done = true
                    SwingUtilities.invokeLater { frame.dispose() }
                    onCookies(header)
                }
            }
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
        engine.userAgent = CHROME_UA
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
