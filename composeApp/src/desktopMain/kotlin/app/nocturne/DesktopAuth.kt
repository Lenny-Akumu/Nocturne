package app.nocturne

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI

private const val PORT = 8898

actual val spotifyRedirectUri: String = "http://127.0.0.1:$PORT/callback"

actual suspend fun authorizeInBrowser(url: String): String = withContext(Dispatchers.IO) {
    ServerSocket(PORT, 1, InetAddress.getLoopbackAddress()).use { server ->
        server.soTimeout = 5 * 60_000
        Desktop.getDesktop().browse(URI(url))
        var result: String? = null
        while (result == null) {
            server.accept().use { socket ->
                val path = socket.getInputStream().bufferedReader().readLine()?.split(" ")?.getOrNull(1).orEmpty()
                val ok = path.startsWith("/callback")
                val body = if (ok) "<html><body style=\"font-family:sans-serif;background:#000;color:#eee;text-align:center;padding-top:80px\">" +
                    "<h2>Nocturne</h2><p>You can close this tab and return to the app.</p></body></html>" else "Not found"
                val bytes = body.toByteArray()
                socket.getOutputStream().apply {
                    write("HTTP/1.1 ${if (ok) "200 OK" else "404 Not Found"}\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    write(bytes)
                    flush()
                }
                if (ok) result = "http://127.0.0.1:$PORT$path"
            }
        }
        result!!
    }
}
