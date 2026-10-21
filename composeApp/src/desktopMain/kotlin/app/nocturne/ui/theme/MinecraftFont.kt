package app.nocturne.ui.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font

private val family: FontFamily? by lazy {
    val loader = Thread.currentThread().contextClassLoader
    listOf("fonts/monocraft.ttf", "fonts/monocraft.otf").firstNotNullOfOrNull { path ->
        loader.getResourceAsStream(path)?.use { FontFamily(Font("monocraft", it.readBytes())) }
    }
}

actual fun minecraftFont(): FontFamily? = family

actual fun platformFontFamily(font: String): FontFamily? = null
