package app.nocturne.ui.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import coil3.Image
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap

@Composable
actual fun platformDynamicColorScheme(dark: Boolean): ColorScheme? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val context = LocalContext.current
    return if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
}

actual fun platformAccentColor(): Color? = null

actual fun Image.toComposeBitmap(): ImageBitmap = toBitmap().asImageBitmap()

actual fun ImageRequest.Builder.softwareOnly(): ImageRequest.Builder = allowHardware(false)
