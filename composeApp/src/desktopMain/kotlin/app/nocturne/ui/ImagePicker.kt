package app.nocturne.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

@Composable
actual fun rememberImagePicker(onPicked: (ByteArray) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    val cb = rememberUpdatedState(onPicked)
    return {
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                val dialog = FileDialog(null as Frame?, "Choose image", FileDialog.LOAD).apply {
                    setFilenameFilter { _, name -> name.substringAfterLast('.').lowercase() in setOf("png", "jpg", "jpeg", "bmp", "gif") }
                    file = "*.png;*.jpg;*.jpeg;*.bmp;*.gif"
                    isVisible = true
                }
                val name = dialog.file ?: return@withContext null
                runCatching { shrink(File(dialog.directory, name)) }.getOrNull()
            }
            bytes?.let { cb.value(it) }
        }
    }
}

private fun shrink(file: File, max: Int = 512): ByteArray {
    val src = ImageIO.read(file)!!
    val k = minOf(1.0, max.toDouble() / maxOf(src.width, src.height))
    val w = (src.width * k).toInt().coerceAtLeast(1)
    val h = (src.height * k).toInt().coerceAtLeast(1)
    val out = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
    out.createGraphics().apply {
        setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        drawImage(src, 0, 0, w, h, java.awt.Color.WHITE, null)
        dispose()
    }
    return ByteArrayOutputStream().also { ImageIO.write(out, "jpg", it) }.toByteArray()
}
