package app.nocturne

import java.awt.image.BufferedImage

// Transparent app icon, pre-rendered per size so the OS never downsamples blurry.
internal object AppIcon {
    private val SIZES = listOf(16, 20, 24, 32, 40, 48, 64, 128, 256)

    val images: List<BufferedImage> by lazy {
        SIZES.mapNotNull { n ->
            runCatching {
                Thread.currentThread().contextClassLoader.getResourceAsStream("icons/nocturne-$n.png")!!.use { javax.imageio.ImageIO.read(it) }
            }.getOrNull()
        }
    }

    /** Closest pre-rendered image at least [px] wide (falls back to the largest). */
    fun forSize(px: Int): BufferedImage? = images.firstOrNull { it.width >= px } ?: images.lastOrNull()
}
