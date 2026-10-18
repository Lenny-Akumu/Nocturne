package app.nocturne.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import app.nocturne.isWindows
import coil3.Image
import coil3.request.ImageRequest
import coil3.toBitmap
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg

@Composable
actual fun platformDynamicColorScheme(dark: Boolean): ColorScheme? = null

private val windowsAccent: Color? by lazy {
    if (!isWindows) return@lazy null
    runCatching {
        val abgr = Advapi32Util.registryGetIntValue(WinReg.HKEY_CURRENT_USER, "Software\\Microsoft\\Windows\\DWM", "AccentColor")
        Color(red = abgr and 0xFF, green = (abgr shr 8) and 0xFF, blue = (abgr shr 16) and 0xFF)
    }.getOrNull()
}

actual fun platformAccentColor(): Color? = windowsAccent

actual fun Image.toComposeBitmap(): ImageBitmap = toBitmap().asComposeImageBitmap()

actual fun ImageRequest.Builder.softwareOnly(): ImageRequest.Builder = this
