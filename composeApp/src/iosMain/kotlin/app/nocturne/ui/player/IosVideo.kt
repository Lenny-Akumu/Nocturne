package app.nocturne.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import app.nocturne.player.avAsset
import app.nocturne.player.ms
import app.nocturne.player.msTime
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.*
import platform.CoreGraphics.CGRectMake
import platform.QuartzCore.CATransaction
import platform.UIKit.UIColor
import platform.UIKit.UIView
import kotlin.math.abs

@OptIn(ExperimentalForeignApi::class)
private class PlayerLayerView(val playerLayer: AVPlayerLayer) : UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    init {
        backgroundColor = UIColor.clearColor
        clipsToBounds = true
        userInteractionEnabled = false
        layer.addSublayer(playerLayer)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        playerLayer.frame = bounds
        CATransaction.commit()
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun VideoSurface(url: String, positionMs: Long, playing: Boolean, speed: Float, modifier: Modifier, fit: Boolean) {
    val player = remember(url) {
        AVPlayer().apply {
            volume = 0f
            avAsset(url)?.let { replaceCurrentItemWithPlayerItem(AVPlayerItem(asset = it)) }
        }
    }
    DisposableEffect(player) {
        onDispose {
            player.pause()
            player.replaceCurrentItemWithPlayerItem(null)
        }
    }
    LaunchedEffect(player, playing, speed) {
        if (playing) {
            player.play()
            player.rate = speed
        } else player.pause()
    }
    LaunchedEffect(player, positionMs / 1000) {
        if (abs(player.currentTime().ms() - positionMs) > 700) player.seekToTime(msTime(positionMs))
    }
    UIKitView(
        factory = { PlayerLayerView(AVPlayerLayer.playerLayerWithPlayer(player)) },
        modifier = modifier,
        update = {
            it.playerLayer.player = player
            it.playerLayer.videoGravity = if (fit) AVLayerVideoGravityResizeAspect else AVLayerVideoGravityResizeAspectFill
        },
        properties = UIKitInteropProperties(interactionMode = null),
    )
}
