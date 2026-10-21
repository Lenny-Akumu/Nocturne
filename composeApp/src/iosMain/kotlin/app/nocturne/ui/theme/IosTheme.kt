package app.nocturne.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.text.platform.SystemFont
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.create
import platform.posix.memcpy
import coil3.Image
import coil3.request.ImageRequest
import coil3.toBitmap

@Composable
actual fun platformDynamicColorScheme(dark: Boolean): ColorScheme? = null

actual fun platformAccentColor(): Color? = null

// Bundled by CI into the app (iosApp/iosApp/monocraft.ttf).
private val minecraft: FontFamily? by lazy {
    listOf("ttf", "otf").firstNotNullOfOrNull { ext ->
        NSBundle.mainBundle.pathForResource("monocraft", ext)?.let { NSData.create(contentsOfFile = it) }?.toByteArray()
    }?.takeIf { it.isNotEmpty() }?.let { FontFamily(Font("monocraft", it)) }
}

actual fun minecraftFont(): FontFamily? = minecraft

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray = ByteArray(length.toInt()).also { out ->
    if (out.isNotEmpty()) out.usePinned { memcpy(it.addressOf(0), bytes, length) }
}

// Skia's generic aliases (.AppleSystemUIFontSerif etc.) fall back to SF on iOS, so name real fonts.
@OptIn(ExperimentalTextApi::class)
private fun named(name: String) = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map { SystemFont(name, it) },
)

private val serif by lazy { named("Georgia") }
private val mono by lazy { named("Menlo") }
private val cursive by lazy { named("Snell Roundhand") }

actual fun platformFontFamily(font: String): FontFamily? = when (font) {
    "serif" -> serif
    "mono" -> mono
    "cursive" -> cursive
    else -> null
}

actual fun Image.toComposeBitmap(): ImageBitmap = toBitmap().asComposeImageBitmap()

actual fun ImageRequest.Builder.softwareOnly(): ImageRequest.Builder = this
