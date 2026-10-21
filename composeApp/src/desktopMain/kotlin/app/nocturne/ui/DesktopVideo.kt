package app.nocturne.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import app.nocturne.player.VlcAudioEngine
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ImageInfo
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallbackAdapter
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.nio.ByteBuffer
import javax.swing.SwingUtilities
import kotlin.math.abs
import kotlin.math.max
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlin.math.min

private object VideoFactory {
    val factory: MediaPlayerFactory? by lazy {
        if (VlcAudioEngine.bundledDir() == null && !VlcAudioEngine.systemVlc) null
        else runCatching { MediaPlayerFactory("--quiet", "--intf=dummy", "--no-audio", "--http-reconnect") }.getOrNull()
    }
}

private class VideoFrames(url: String) {
    @Volatile var image: org.jetbrains.skia.Image? = null
    private var bytes = ByteArray(0)
    var onFrame: () -> Unit = {}
    val player: EmbeddedMediaPlayer? = VideoFactory.factory?.mediaPlayers()?.newEmbeddedMediaPlayer()

    init {
        val f = VideoFactory.factory
        if (f != null && player != null) {
            val format = object : BufferFormatCallbackAdapter() {
                override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
                    return RV32BufferFormat(sourceWidth, sourceHeight)
                }
            }
            val render = object : RenderCallback {
                override fun lock(mediaPlayer: MediaPlayer) = Unit
                override fun unlock(mediaPlayer: MediaPlayer) = Unit
                override fun display(mediaPlayer: MediaPlayer, nativeBuffers: Array<ByteBuffer>, bufferFormat: BufferFormat, displayWidth: Int, displayHeight: Int) {
                    val buf = nativeBuffers.firstOrNull() ?: return
                    val w = bufferFormat.width
                    val h = bufferFormat.height
                    val pitch = bufferFormat.pitches.firstOrNull() ?: (w * 4)
                    val need = pitch * h
                    if (w <= 0 || h <= 0 || buf.capacity() < need) return
                    if (bytes.size != need) bytes = ByteArray(need)
                    buf.rewind()
                    buf.get(bytes, 0, need)
                    image = runCatching {
                        org.jetbrains.skia.Image.makeRaster(ImageInfo.makeN32(w, h, ColorAlphaType.OPAQUE), bytes, pitch)
                    }.getOrNull() ?: return
                    SwingUtilities.invokeLater { onFrame() }
                }
            }
            player.videoSurface().set(f.videoSurfaces().newVideoSurface(format, render, true))
            player.submit { player.media().play(url, ":no-audio", ":input-repeat=0", ":network-caching=400", ":http-user-agent=${app.nocturne.source.InnerTube.USER_AGENT}") }
        }
    }

    fun release() {
        val p = player ?: return
        p.submit { runCatching { p.controls().stop(); p.release() } }
    }
}

@Composable
actual fun VideoSurface(url: String, positionMs: Long, playing: Boolean, speed: Float, modifier: Modifier, fit: Boolean) {
    val frames = remember(url) { VideoFrames(url) }
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(frames) {
        frames.onFrame = { tick++ }
        onDispose { frames.release() }
    }
    LaunchedEffect(frames, playing) {
        val p = frames.player ?: return@LaunchedEffect
        p.submit { p.controls().setPause(!playing) }
    }
    LaunchedEffect(frames, speed) {
        val p = frames.player ?: return@LaunchedEffect
        p.submit { p.controls().setRate(speed) }
    }
    LaunchedEffect(frames, positionMs / 1000) {
        val p = frames.player ?: return@LaunchedEffect
        p.submit {
            val t = p.status().time()
            if (t >= 0 && abs(t - positionMs) > 700) p.controls().setTime(positionMs)
        }
    }
    Canvas(modifier) {
        if (tick < 0) return@Canvas
        val image = runCatching { frames.image?.toComposeImageBitmap() }.getOrNull() ?: return@Canvas
        val scale = if (fit) min(size.width / image.width, size.height / image.height) else max(size.width / image.width, size.height / image.height)
        val w = (image.width * scale).toInt()
        val h = (image.height * scale).toInt()
        drawImage(
            image,
            dstOffset = IntOffset(((size.width - w) / 2).toInt(), ((size.height - h) / 2).toInt()),
            dstSize = IntSize(w, h),
        )
    }
}
