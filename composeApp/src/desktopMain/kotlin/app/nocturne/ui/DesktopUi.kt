package app.nocturne.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import javax.swing.SwingUtilities

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

actual val supportsWebLogin: Boolean = runCatching { Class.forName("javafx.scene.web.WebView") }.isSuccess

@Composable
actual fun WebLoginDialog(title: String, url: String, cookieUrl: String, cookieKey: String, onCookies: (String) -> Unit, onDismiss: () -> Unit) {
    val latestCookies by rememberUpdatedState(onCookies)
    val latestDismiss by rememberUpdatedState(onDismiss)
    DisposableEffect(url) {
        FxLogin.open(title, url, cookieUrl, cookieKey, { c -> SwingUtilities.invokeLater { latestCookies(c) } }, { SwingUtilities.invokeLater { latestDismiss() } })
        onDispose { }
    }
}

actual fun clearWebLogins() = FxLogin.clear()
