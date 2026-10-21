@file:OptIn(ExperimentalForeignApi::class)

package app.nocturne.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
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
import androidx.compose.ui.viewinterop.UIKitView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.delay
import platform.Foundation.NSDate
import platform.Foundation.NSHTTPCookie
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKWebsiteDataStore

private const val SAFARI_UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.1 Mobile/15E148 Safari/604.1"
private const val DESKTOP_UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.1 Safari/605.1.15"

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

actual val supportsWebLogin: Boolean = true

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
    val hosts = remember(cookieUrls) { cookieUrls.mapNotNull { NSURL.URLWithString(it)?.host } }

    fun check(onResult: (Boolean) -> Unit = {}) {
        WKWebsiteDataStore.defaultDataStore().httpCookieStore.getAllCookies { list ->
            val cookies = list.orEmpty().filterIsInstance<NSHTTPCookie>()
            val header = hosts.map { host ->
                cookies.filter { c -> host.endsWith(c.domain.trimStart('.')) }.joinToString("; ") { "${it.name}=${it.value}" }
            }.firstOrNull { it.contains("$cookieKey=") }
            if (header != null && !done[0]) {
                done[0] = true
                onCookies(header)
            }
            onResult(header != null)
        }
    }

    LaunchedEffect(Unit) {
        while (!done[0]) {
            delay(1000)
            check()
        }
    }

    Dialog(onDismissRequest = { check { found -> if (!found) onDismiss() } }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { check { found -> if (!found) onDismiss() } }) { Icon(Icons.Rounded.Close, "Close") }
                    Text(title, style = MaterialTheme.typography.titleMedium)
                }
                UIKitView(
                    factory = {
                        WKWebView(platform.CoreGraphics.CGRectMake(0.0, 0.0, 0.0, 0.0), WKWebViewConfiguration()).apply {
                            customUserAgent = if (desktopMode) DESKTOP_UA else SAFARI_UA
                            NSURL.URLWithString(url)?.let { loadRequest(NSURLRequest.requestWithURL(it)) }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

actual fun clearWebLogins() {
    val store = WKWebsiteDataStore.defaultDataStore()
    store.removeDataOfTypes(WKWebsiteDataStore.allWebsiteDataTypes(), NSDate.dateWithTimeIntervalSince1970(0.0)) {}
}

@Composable
actual fun PairingCard() = Unit
