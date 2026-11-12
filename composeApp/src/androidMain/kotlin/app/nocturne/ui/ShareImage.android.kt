package app.nocturne.ui

import android.content.Intent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.content.FileProvider
import app.nocturne.AndroidContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

actual fun ImageBitmap.encodePng(): ByteArray {
    var bmp = asAndroidBitmap()
    if (android.os.Build.VERSION.SDK_INT >= 26 && bmp.config == android.graphics.Bitmap.Config.HARDWARE) bmp = bmp.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
    return ByteArrayOutputStream().also { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
}

actual suspend fun shareImage(png: ByteArray, name: String): String? = withContext(Dispatchers.IO) {
    val ctx = AndroidContext.app
    val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    val file = File(dir, name.replace(Regex("[\\\\/:*?\"<>|]"), "_") + ".png").apply { writeBytes(png) }
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    withContext(Dispatchers.Main) {
        ctx.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }
    null
}
