package app.nocturne.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import coil3.Image
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

expect fun Image.toComposeBitmap(): ImageBitmap

expect fun ImageRequest.Builder.softwareOnly(): ImageRequest.Builder

private val seedCache = mutableStateMapOf<String, Color>()

@Composable
fun rememberArtworkSeed(url: String?): Color? {
    val context = LocalPlatformContext.current
    var color by remember(url) { mutableStateOf(url?.let { seedCache[it] }) }
    LaunchedEffect(url) {
        if (url == null || color != null) return@LaunchedEffect
        val request = ImageRequest.Builder(context).data(url).size(96, 96).softwareOnly().build()
        val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult ?: return@LaunchedEffect
        val seed = withContext(Dispatchers.Default) {
            runCatching { dominantColor(result.image.toComposeBitmap()) }.getOrNull()
        } ?: return@LaunchedEffect
        seedCache[url] = seed
        color = seed
    }
    return color
}

fun dominantColor(bitmap: ImageBitmap): Color? {
    val pixels = bitmap.toPixelMap()
    val buckets = FloatArray(36)
    val sums = Array(36) { FloatArray(3) }
    var fallbackR = 0f; var fallbackG = 0f; var fallbackB = 0f; var n = 0
    val step = max(1, min(pixels.width, pixels.height) / 48)
    for (x in 0 until pixels.width step step) for (y in 0 until pixels.height step step) {
        val c = pixels[x, y]
        if (c.alpha < 0.5f) continue
        val mx = max(c.red, max(c.green, c.blue))
        val mn = min(c.red, min(c.green, c.blue))
        val sat = if (mx == 0f) 0f else (mx - mn) / mx
        fallbackR += c.red; fallbackG += c.green; fallbackB += c.blue; n++
        if (sat < 0.2f || mx < 0.15f || mx > 0.98f && sat < 0.3f) continue
        val d = mx - mn
        var h = when (mx) {
            c.red -> ((c.green - c.blue) / d) % 6
            c.green -> (c.blue - c.red) / d + 2
            else -> (c.red - c.green) / d + 4
        } * 60
        if (h < 0) h += 360
        val idx = (h / 10).toInt().coerceIn(0, 35)
        val w = sat * mx
        buckets[idx] += w
        sums[idx][0] += c.red * w; sums[idx][1] += c.green * w; sums[idx][2] += c.blue * w
    }
    val best = buckets.indices.maxByOrNull { buckets[it] } ?: return null
    if (buckets[best] < 0.5f || n > 0 && buckets[best] / n < 0.04f) {
        return if (n == 0) null else Color(fallbackR / n, fallbackG / n, fallbackB / n)
    }
    val w = buckets[best]
    return Color(sums[best][0] / w, sums[best][1] / w, sums[best][2] / w)
}
