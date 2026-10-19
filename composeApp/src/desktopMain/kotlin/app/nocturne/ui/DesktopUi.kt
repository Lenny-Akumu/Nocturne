package app.nocturne.ui

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

actual val supportsWebLogin: Boolean = false

@Composable
actual fun WebLoginDialog(title: String, url: String, cookieUrl: String, cookieKey: String, onCookies: (String) -> Unit, onDismiss: () -> Unit) = Unit

actual fun clearWebLogins() = Unit
