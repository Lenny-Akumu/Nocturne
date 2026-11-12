package app.nocturne.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toAwtImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

actual fun ImageBitmap.encodePng(): ByteArray =
    ByteArrayOutputStream().also { javax.imageio.ImageIO.write(toAwtImage(), "png", it) }.toByteArray()

actual suspend fun shareImage(png: ByteArray, name: String): String? = withContext(Dispatchers.IO) {
    var picked: File? = null
    javax.swing.SwingUtilities.invokeAndWait {
        val home = System.getProperty("user.home")
        val dir = File(home, "Pictures").takeIf { it.isDirectory } ?: File(home)
        val dialog = java.awt.FileDialog(null as java.awt.Frame?, "Save image", java.awt.FileDialog.SAVE)
        dialog.directory = dir.absolutePath
        dialog.file = "$name.png"
        dialog.isVisible = true
        val d = dialog.directory
        val f = dialog.file
        if (d != null && f != null) picked = File(d, if (f.endsWith(".png", true)) f else "$f.png")
    }
    picked?.also { it.writeBytes(png) }?.absolutePath
}
