package app.nocturne

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import app.nocturne.data.IconBackground
import app.nocturne.data.IconPalette
import app.nocturne.ui.colors
import app.nocturne.ui.drawNocturneIcon
import java.awt.image.BufferedImage

// App icon, one image per size so the OS never downsamples blurry. Default design is pre-rendered; custom ones drawn at runtime.
internal object AppIcon {
    private val SIZES = listOf(16, 20, 24, 32, 40, 48, 64, 128, 256)

    private val bundled: List<BufferedImage> by lazy {
        SIZES.mapNotNull { n ->
            runCatching {
                Thread.currentThread().contextClassLoader.getResourceAsStream("icons/nocturne-$n.png")!!.use { javax.imageio.ImageIO.read(it) }
            }.getOrNull()
        }
    }

    @Volatile
    var images: List<BufferedImage> = emptyList()
        get() = field.ifEmpty { bundled }
        private set

    /** Renders the chosen icon (the default uses the bundled PNGs) and returns it. */
    fun update(background: IconBackground, palette: IconPalette, spooky: Boolean = false): List<BufferedImage> {
        images = if (!spooky && background == IconBackground.SQUARE && palette == IconPalette.STANDARD) bundled
        else runCatching { SIZES.map { render(it, background, palette, spooky) } }.getOrDefault(bundled)
        return images
    }

    private fun render(n: Int, background: IconBackground, palette: IconPalette, spooky: Boolean): BufferedImage {
        val bmp = ImageBitmap(n, n)
        val colors = palette.colors()
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bmp), Size(n.toFloat(), n.toFloat())) {
            drawRect(Color.Transparent, blendMode = BlendMode.Clear)
            drawNocturneIcon(background, colors, spooky)
        }
        return bmp.toAwtImage()
    }

    /** Closest image at least [px] wide (falls back to the largest). */
    fun forSize(px: Int): BufferedImage? = images.firstOrNull { it.width >= px } ?: images.lastOrNull()
}
