package app.nocturne.ui.player

import android.view.Gravity
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import app.nocturne.source.InnerTube
import androidx.media3.ui.AspectRatioFrameLayout
import kotlin.math.abs

// TextureView (not PlayerView's SurfaceView): it follows Compose clipping, alpha and slide animations,
// so the video never punches through sheets or lingers while the player collapses.
@OptIn(UnstableApi::class)
@Composable
actual fun VideoSurface(url: String, positionMs: Long, playing: Boolean, speed: Float, modifier: Modifier, fit: Boolean) {
    val context = LocalContext.current
    val player = remember(url) {
        val http = DefaultHttpDataSource.Factory().setUserAgent(InnerTube.USER_AGENT).setAllowCrossProtocolRedirects(true)
        ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(http)).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
            volume = 0f
            setMediaItem(MediaItem.fromUri(url))
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.clearVideoSurface(); player.release() } }
    LaunchedEffect(player, playing) { player.playWhenReady = playing }
    LaunchedEffect(player, speed) { player.playbackParameters = PlaybackParameters(speed) }
    LaunchedEffect(player, positionMs / 1000) {
        if (abs(player.currentPosition - positionMs) > 700) player.seekTo(positionMs)
    }
    key(player) {
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                val texture = TextureView(ctx)
                val aspect = AspectRatioFrameLayout(ctx).apply {
                    addView(texture, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                }
                player.addListener(object : Player.Listener {
                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                        if (videoSize.height > 0) aspect.setAspectRatio(videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height)
                    }
                })
                player.setVideoTextureView(texture)
                FrameLayout(ctx).apply {
                    clipChildren = true
                    addView(aspect, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER))
                }
            },
            update = {
                (it.getChildAt(0) as? AspectRatioFrameLayout)?.resizeMode =
                    if (fit) AspectRatioFrameLayout.RESIZE_MODE_FIT else AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            },
        )
    }
}
