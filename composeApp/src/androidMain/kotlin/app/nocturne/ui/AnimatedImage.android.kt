package app.nocturne.ui

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import java.nio.ByteBuffer

@Composable
actual fun AnimatedImage(bytes: ByteArray, modifier: Modifier) {
    val drawable: Drawable = remember(bytes) {
        runCatching {
            if (Build.VERSION.SDK_INT >= 28) ImageDecoder.decodeDrawable(ImageDecoder.createSource(ByteBuffer.wrap(bytes)))
            else android.graphics.drawable.BitmapDrawable(null, android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        }.getOrNull()
    } ?: return
    var tick by remember { mutableIntStateOf(0) }
    val animated = Build.VERSION.SDK_INT >= 28 && drawable is AnimatedImageDrawable
    DisposableEffect(drawable) {
        if (animated) (drawable as AnimatedImageDrawable).start()
        onDispose { if (animated) (drawable as AnimatedImageDrawable).stop() }
    }
    LaunchedEffect(drawable) { if (animated) while (true) withFrameNanos { tick++ } }
    Canvas(modifier) {
        tick
        val iw = drawable.intrinsicWidth.toFloat().takeIf { it > 0 } ?: return@Canvas
        val ih = drawable.intrinsicHeight.toFloat().takeIf { it > 0 } ?: return@Canvas
        val k = maxOf(size.width / iw, size.height / ih)
        val w = iw * k
        val h = ih * k
        val l = ((size.width - w) / 2).toInt()
        val t = ((size.height - h) / 2).toInt()
        drawable.setBounds(l, t, l + w.toInt(), t + h.toInt())
        drawIntoCanvas { drawable.draw(it.nativeCanvas) }
    }
}
