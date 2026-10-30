package app.nocturne.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.delay
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.Image

@Composable
actual fun AnimatedImage(bytes: ByteArray, modifier: Modifier) {
    val codec = remember(bytes) { runCatching { Codec.makeFromData(Data.makeFromBytes(bytes)) }.getOrNull() } ?: return
    val bitmap = remember(codec) {
        Bitmap().apply {
            allocPixels(codec.imageInfo)
            runCatching { codec.readPixels(this, 0) }
        }
    }
    // the Skia image behind an ImageBitmap is a snapshot, so every frame gets a fresh one
    var image by remember(bitmap) { mutableStateOf(Image.makeFromBitmap(bitmap).toComposeImageBitmap()) }
    LaunchedEffect(codec) {
        val n = codec.frameCount
        if (n <= 1) return@LaunchedEffect
        val info = codec.framesInfo
        var frame = 0
        while (true) {
            delay(info[frame].duration.coerceAtLeast(20).toLong())
            val next = (frame + 1) % n
            runCatching { codec.readPixels(bitmap, next, if (next == 0) -1 else frame) }
            bitmap.notifyPixelsChanged()
            frame = next
            image = Image.makeFromBitmap(bitmap).toComposeImageBitmap()
        }
    }
    Canvas(modifier) {
        val iw = image.width.toFloat()
        val ih = image.height.toFloat()
        if (iw <= 0 || ih <= 0) return@Canvas
        // crop to fill
        val k = maxOf(size.width / iw, size.height / ih)
        val sw = size.width / k
        val sh = size.height / k
        drawImage(
            image,
            srcOffset = IntOffset(((iw - sw) / 2).toInt(), ((ih - sh) / 2).toInt()),
            srcSize = IntSize(sw.toInt(), sh.toInt()),
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
        )
    }
}
