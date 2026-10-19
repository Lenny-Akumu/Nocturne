package app.nocturne.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

actual val supportsWebLogin: Boolean = true

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun WebLoginDialog(title: String, url: String, cookieUrl: String, cookieKey: String, onCookies: (String) -> Unit, onDismiss: () -> Unit) {
    val done = remember { booleanArrayOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Close") }
                    Text(title, style = MaterialTheme.typography.titleMedium)
                }
                AndroidView(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            val cookies = CookieManager.getInstance()
                            cookies.setAcceptCookie(true)
                            cookies.setAcceptThirdPartyCookies(this, true)
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, pageUrl: String?) {
                                    val c = cookies.getCookie(cookieUrl) ?: return
                                    if (!done[0] && c.contains("$cookieKey=")) {
                                        done[0] = true
                                        cookies.flush()
                                        onCookies(c)
                                    }
                                }
                            }
                            loadUrl(url)
                        }
                    },
                )
            }
        }
    }
}

actual fun clearWebLogins() {
    CookieManager.getInstance().removeAllCookies(null)
}
