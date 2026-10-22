package app.nocturne.ui

import android.annotation.SuppressLint
import android.os.Message
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import app.nocturne.ui.kit.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

actual val supportsWebLogin: Boolean = true

private const val DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36"

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun WebLoginDialog(
    title: String,
    url: String,
    cookieUrls: List<String>,
    cookieKey: String,
    desktopMode: Boolean,
    onCookies: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val done = remember { booleanArrayOf(false) }
    val cookies = remember { CookieManager.getInstance() }

    fun check(): Boolean {
        if (done[0]) return true
        cookies.flush()
        val found = cookieUrls.mapNotNull { cookies.getCookie(it) }.firstOrNull { it.contains("$cookieKey=") } ?: return false
        done[0] = true
        onCookies(found)
        return true
    }

    fun close() {
        if (!check()) onDismiss()
    }

    LaunchedEffect(Unit) {
        while (!done[0]) {
            delay(1000)
            check()
        }
    }

    Dialog(onDismissRequest = ::close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = ::close) { Icon(Icons.Rounded.Close, "Close") }
                    Text(title, style = MaterialTheme.typography.titleMedium)
                }
                AndroidView(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    factory = { ctx ->
                        val container = FrameLayout(ctx)
                        cookies.setAcceptCookie(true)
                        fun configure(web: WebView) {
                            web.settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                javaScriptCanOpenWindowsAutomatically = true
                                setSupportMultipleWindows(true)
                                userAgentString = if (desktopMode) DESKTOP_UA
                                else userAgentString.replace("; wv", "").replace(Regex("Version/[\\d.]+ "), "")
                                if (desktopMode) {
                                    useWideViewPort = true
                                    loadWithOverviewMode = true
                                }
                            }
                            cookies.setAcceptThirdPartyCookies(web, true)
                            web.webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, pageUrl: String?) {
                                    if (check()) return
                                    view?.evaluateJavascript("document.cookie") { raw ->
                                        val jar = raw?.trim('"').orEmpty()
                                        if (!done[0] && jar.contains("$cookieKey=")) {
                                            done[0] = true
                                            onCookies(jar)
                                        }
                                    }
                                }

                                override fun doUpdateVisitedHistory(view: WebView?, pageUrl: String?, isReload: Boolean) {
                                    check()
                                }
                            }
                            web.webChromeClient = object : WebChromeClient() {
                                override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                                    val popup = WebView(view.context)
                                    configure(popup)
                                    container.addView(popup, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
                                    (resultMsg.obj as WebView.WebViewTransport).webView = popup
                                    resultMsg.sendToTarget()
                                    return true
                                }

                                override fun onCloseWindow(window: WebView) {
                                    container.removeView(window)
                                    window.destroy()
                                    check()
                                }
                            }
                        }
                        val main = WebView(ctx)
                        configure(main)
                        container.addView(main, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
                        main.loadUrl(url)
                        container
                    },
                )
            }
        }
    }
}

actual fun clearWebLogins() {
    CookieManager.getInstance().removeAllCookies(null)
}
