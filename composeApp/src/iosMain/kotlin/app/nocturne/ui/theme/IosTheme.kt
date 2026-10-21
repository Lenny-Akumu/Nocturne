package app.nocturne.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.text.font.FontFamily
import coil3.Image
import coil3.request.ImageRequest
import coil3.toBitmap

@Composable
actual fun platformDynamicColorScheme(dark: Boolean): ColorScheme? = null

actual fun platformAccentColor(): Color? = null

actual fun minecraftFont(): FontFamily? = null

actual fun Image.toComposeBitmap(): ImageBitmap = toBitmap().asComposeImageBitmap()

actual fun ImageRequest.Builder.softwareOnly(): ImageRequest.Builder = this
