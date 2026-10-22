package app.nocturne

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.awt.Desktop
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI

// Must match the redirect URI registered in the Spotify app exactly.
const val SPOTIFY_LOOPBACK_PORT = 43821

actual val spotifyRedirectUri: String = "http://127.0.0.1:$SPOTIFY_LOOPBACK_PORT/callback"

private var authServer: HttpServer? = null

private const val DONE_PAGE = "<!doctype html><html><head><meta charset=\"utf-8\"><title>Nocturne</title></head>" +
    "<body style=\"font-family:system-ui,sans-serif;background:#0b0b0f;color:#eee;text-align:center;padding-top:96px\">" +
    "<h2>Nocturne</h2><p>You're signed in. You can close this tab and return to the app.</p></body></html>"

// Loopback redirect: a tiny local HTTP server catches /callback, then stops.
actual suspend fun authorizeInBrowser(url: String): String {
    authServer?.stop(0) // an abandoned attempt may still hold the port
    authServer = null
    val result = CompletableDeferred<String>()
    val server = withContext(Dispatchers.IO) {
        try {
            HttpServer.create(InetSocketAddress(InetAddress.getByName("127.0.0.1"), SPOTIFY_LOOPBACK_PORT), 0)
        } catch (e: java.io.IOException) {
            throw IllegalStateException("Port $SPOTIFY_LOOPBACK_PORT is busy. Close other Nocturne windows and try again.")
        }
    }
    server.createContext("/callback") { ex ->
        val bytes = DONE_PAGE.toByteArray()
        ex.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
        ex.sendResponseHeaders(200, bytes.size.toLong())
        ex.responseBody.use { it.write(bytes) }
        result.complete("http://127.0.0.1:$SPOTIFY_LOOPBACK_PORT${ex.requestURI}")
    }
    server.start()
    authServer = server
    try {
        withContext(Dispatchers.IO) { openBrowser(url) }
        return withTimeout(5 * 60_000L) { result.await() }
    } finally {
        server.stop(0)
        if (authServer === server) authServer = null
    }
}

private fun openBrowser(url: String) {
    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
        runCatching { Desktop.getDesktop().browse(URI(url)); return }
    }
    val os = System.getProperty("os.name").lowercase()
    val cmd = when {
        "win" in os -> listOf("rundll32", "url.dll,FileProtocolHandler", url)
        "mac" in os -> listOf("open", url)
        else -> listOf("xdg-open", url)
    }
    ProcessBuilder(cmd).start()
}
