package app.nocturne.player

import app.nocturne.AppGraph
import app.nocturne.model.ResolvedStream
import app.nocturne.model.Track
import app.nocturne.source.InnerTube
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFoundation.*
import platform.CoreMedia.*
import platform.Foundation.NSData
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.MediaPlayer.*
import platform.UIKit.UIImage

internal fun avAsset(url: String): AVURLAsset? {
    val nsUrl = when {
        url.startsWith("/") -> NSURL.fileURLWithPath(url)
        else -> NSURL.URLWithString(url) ?: url.takeIf { it.startsWith("file://") }?.let { NSURL.fileURLWithPath(it.removePrefix("file://")) }
    } ?: return null
    val options: Map<Any?, Any?>? = if (url.startsWith("http")) mapOf<Any?, Any?>("AVURLAssetHTTPHeaderFieldsKey" to mapOf("User-Agent" to InnerTube.USER_AGENT)) else null
    return AVURLAsset.URLAssetWithURL(nsUrl, options)
}

@OptIn(ExperimentalForeignApi::class)
internal fun CValue<CMTime>.ms(): Long = CMTimeGetSeconds(this).let { if (it.isNaN() || it.isInfinite()) 0L else (it * 1000).toLong() }

@OptIn(ExperimentalForeignApi::class)
internal fun msTime(ms: Long): CValue<CMTime> = CMTimeMakeWithSeconds(ms / 1000.0, 1000)

@OptIn(ExperimentalForeignApi::class)
class IosAudioEngine : AudioEngine {
    private val player = AVPlayer()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var listener: EngineListener? = null
    private var observers = listOf<Any>()
    private val targets = mutableListOf<Pair<MPRemoteCommand, Any>>()
    private var track: Track? = null
    private var speed = 1f
    private var pendingSeekMs: Long? = null
    private var wantPlay = false
    private var lastPlaying = false
    private var lastBuffering = false
    private var failed = false
    private var lastDuration = 0L
    private var artwork: MPMediaItemArtwork? = null
    private var artworkUrl: String? = null
    private val controller get() = AppGraph.instance.player

    init {
        runCatching {
            val session = AVAudioSession.sharedInstance()
            session.setCategory(AVAudioSessionCategoryPlayback, null)
        }
        setupRemote()
        scope.launch { while (isActive) { runCatching { tick() }; delay(250) } }
    }

    private val ready get() = player.currentItem?.status == AVPlayerItemStatusReadyToPlay

    override val positionMs: Long get() = pendingSeekMs ?: player.currentTime().ms()
    override val durationMs: Long get() = player.currentItem?.takeIf { ready }?.duration?.ms() ?: 0L
    override val isPlaying: Boolean get() = player.timeControlStatus == AVPlayerTimeControlStatusPlaying

    override fun setListener(listener: EngineListener) {
        this.listener = listener
    }

    override fun load(stream: ResolvedStream, track: Track, startPositionMs: Long, playWhenReady: Boolean) {
        detach()
        failed = false
        this.track = track
        loadArtwork(track.artworkUrl)
        val asset = avAsset(stream.url)
        if (asset == null) {
            listener?.onError(IllegalArgumentException("Invalid stream URL"))
            return
        }
        val item = AVPlayerItem(asset = asset)
        player.replaceCurrentItemWithPlayerItem(item)
        val center = NSNotificationCenter.defaultCenter
        observers = listOf(
            center.addObserverForName(AVPlayerItemDidPlayToEndTimeNotification, item, NSOperationQueue.mainQueue) { _ ->
                wantPlay = false
                setPlaying(false)
                listener?.onEnded()
            },
            center.addObserverForName(AVPlayerItemFailedToPlayToEndTimeNotification, item, NSOperationQueue.mainQueue) { _ -> fail(item) },
        )
        pendingSeekMs = startPositionMs.takeIf { it > 0 }
        wantPlay = playWhenReady
        if (playWhenReady && pendingSeekMs == null) startPlayback()
        if (playWhenReady) setBuffering(true)
        updateNowPlaying()
    }

    private fun tick() {
        val item = player.currentItem ?: return
        if (item.status == AVPlayerItemStatusFailed) return fail(item)
        if (item.status == AVPlayerItemStatusReadyToPlay) pendingSeekMs?.let { ms ->
            pendingSeekMs = null
            seekNow(ms)
            if (wantPlay) startPlayback()
        }
        val status = player.timeControlStatus
        setPlaying(status == AVPlayerTimeControlStatusPlaying)
        setBuffering(status == AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate || (wantPlay && item.status == AVPlayerItemStatusUnknown))
        val d = durationMs
        if (d != lastDuration) { lastDuration = d; updateNowPlaying() }
    }

    private fun setPlaying(playing: Boolean) {
        if (playing == lastPlaying) return
        lastPlaying = playing
        listener?.onPlayingChanged(playing)
        updateNowPlaying()
    }

    private fun setBuffering(buffering: Boolean) {
        if (buffering == lastBuffering) return
        lastBuffering = buffering
        listener?.onBufferingChanged(buffering)
    }

    private fun fail(item: AVPlayerItem) {
        if (failed || item != player.currentItem) return
        failed = true
        wantPlay = false
        setPlaying(false)
        setBuffering(false)
        listener?.onError(IllegalStateException(item.error?.localizedDescription ?: "AVPlayer could not play this stream"))
    }

    private fun startPlayback() {
        player.play()
        if (speed != 1f) player.rate = speed
    }

    private fun seekNow(ms: Long) = player.seekToTime(msTime(ms), toleranceBefore = CMTimeMake(0, 1), toleranceAfter = CMTimeMake(0, 1))

    private fun detach() {
        val center = NSNotificationCenter.defaultCenter
        observers.forEach { center.removeObserver(it) }
        observers = emptyList()
    }

    override fun play() {
        wantPlay = true
        if (pendingSeekMs == null) startPlayback()
    }

    override fun pause() {
        wantPlay = false
        player.pause()
        updateNowPlaying()
    }

    override fun stop() {
        wantPlay = false
        pendingSeekMs = null
        player.pause()
        detach()
        player.replaceCurrentItemWithPlayerItem(null)
        setPlaying(false)
        setBuffering(false)
        MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = null
    }

    override fun seekTo(positionMs: Long) {
        if (pendingSeekMs != null || !ready) pendingSeekMs = positionMs else seekNow(positionMs)
        updateNowPlaying()
    }

    override fun setVolume(volume: Float) {
        player.volume = volume.coerceIn(0f, 1f)
    }

    override fun setPlaybackSpeed(speed: Float) {
        this.speed = speed.coerceIn(0.5f, 2f)
        if (player.rate != 0f) player.rate = this.speed
        updateNowPlaying()
    }

    private fun loadArtwork(url: String?) {
        if (url == artworkUrl) return
        artworkUrl = url
        artwork = null
        if (url == null) return
        scope.launch {
            val image = withContext(Dispatchers.Default) {
                runCatching { NSURL.URLWithString(url)?.let { NSData.create(contentsOfURL = it) }?.let { UIImage.imageWithData(it) } }.getOrNull()
            } ?: return@launch
            if (artworkUrl != url) return@launch
            artwork = MPMediaItemArtwork(boundsSize = image.size, requestHandler = { _ -> image })
            updateNowPlaying()
        }
    }

    private fun updateNowPlaying() {
        val t = track ?: return
        val info = mutableMapOf<Any?, Any?>(
            MPMediaItemPropertyTitle to t.title,
            MPMediaItemPropertyArtist to t.artistNames,
            MPMediaItemPropertyPlaybackDuration to (durationMs.takeIf { it > 0 } ?: t.durationMs) / 1000.0,
            MPNowPlayingInfoPropertyElapsedPlaybackTime to positionMs / 1000.0,
            MPNowPlayingInfoPropertyPlaybackRate to if (lastPlaying) speed.toDouble() else 0.0,
        )
        t.album?.let { info[MPMediaItemPropertyAlbumTitle] = it }
        artwork?.let { info[MPMediaItemPropertyArtwork] = it }
        MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = info
    }

    private fun setupRemote() {
        val c = MPRemoteCommandCenter.sharedCommandCenter()
        fun on(cmd: MPRemoteCommand, action: (MPRemoteCommandEvent?) -> Unit) {
            cmd.enabled = true
            targets += cmd to cmd.addTargetWithHandler { e ->
                runCatching { action(e) }
                MPRemoteCommandHandlerStatusSuccess
            }
        }
        on(c.playCommand) { controller.resume() }
        on(c.pauseCommand) { controller.pause() }
        on(c.togglePlayPauseCommand) { controller.toggle() }
        on(c.nextTrackCommand) { controller.next() }
        on(c.previousTrackCommand) { controller.previous() }
        on(c.changePlaybackPositionCommand) { e ->
            (e as? MPChangePlaybackPositionCommandEvent)?.let { controller.seekTo((it.positionTime * 1000).toLong()) }
        }
    }

    override fun release() {
        scope.cancel()
        detach()
        targets.forEach { (cmd, target) -> cmd.removeTarget(target) }
        targets.clear()
        player.pause()
        player.replaceCurrentItemWithPlayerItem(null)
        MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = null
    }
}
