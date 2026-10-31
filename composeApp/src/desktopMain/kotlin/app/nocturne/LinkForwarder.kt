package app.nocturne

import app.nocturne.party.PartyLinks
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/** nocturne:// links on desktop: a second launch hands its link to the running instance over a localhost socket. */
object LinkForwarder {
    private val portFile get() = File(AppDirs.root, "instance.port")

    /** True if a running Nocturne accepted [url]. */
    fun forward(url: String): Boolean = runCatching {
        val port = portFile.readText().trim().toInt()
        Socket().use { s ->
            s.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), port), 1500)
            s.soTimeout = 2000
            s.getOutputStream().apply { write((url.take(1024).filter { it != '\n' && it != '\r' } + "\n").toByteArray()); flush() }
            s.getInputStream().read() == 'k'.code
        }
    }.getOrDefault(false)

    fun listen() {
        // macOS delivers URL-scheme opens as an AWT event instead of an argument.
        runCatching {
            val d = java.awt.Desktop.getDesktop()
            if (d.isSupported(java.awt.Desktop.Action.APP_OPEN_URI)) d.setOpenURIHandler { e -> PartyLinks.handle(e.uri.toString()); MainWindow.show() }
        }
        val server = runCatching { ServerSocket(0, 8, InetAddress.getLoopbackAddress()) }.getOrNull() ?: return
        runCatching { portFile.writeText(server.localPort.toString()) }
        thread(isDaemon = true, name = "link-listener") {
            while (true) {
                val c = runCatching { server.accept() }.getOrNull() ?: break
                runCatching {
                    c.use {
                        it.soTimeout = 2000
                        val line = it.getInputStream().bufferedReader().readLine().orEmpty()
                        it.getOutputStream().apply { write('k'.code); flush() }
                        val op = line.substringAfter("nocturne://control/", "")
                        if (op.isNotEmpty()) javax.swing.SwingUtilities.invokeLater { runCatching { PanelWidget.command(op.trim()) } }
                        else {
                            PartyLinks.handle(line)
                            MainWindow.show()
                        }
                    }
                }
            }
        }
    }
}
