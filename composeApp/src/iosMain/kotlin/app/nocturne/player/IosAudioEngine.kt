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
    val options: Map<Any?, Any?>? = if (url.startsWith("http")) mapOf<Any?, Any?>("AVURLAssetHTTPHeaderFieldsKey" to mapOf("User-Agent" to (app.nocturne.source.IosBackend.uaFor(url) ?: InnerTube.USER_AGENT))) else null
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

    private var source: ResolvedStream? = null
    private var cached = false
    private var currentUrl: String? = null

    override fun load(stream: ResolvedStream, track: Track, startPositionMs: Long, playWhenReady: Boolean) {
        source = stream
        cached = false
        val local = cacheFile(stream, track)
        if (local != null && app.nocturne.data.fileExists(local)) {
            cached = true
            return open(local, track, startPositionMs, playWhenReady)
        }
        open(stream.url, track, startPositionMs, playWhenReady)
    }

    // Local copy used when AVPlayer can't stream a progressive googlevideo / SoundCloud file; null for HLS and unsupported formats.
    private fun cacheFile(stream: ResolvedStream, track: Track): String? {
        val url = stream.url
        if (stream.isHls || !url.startsWith("http") || ".m3u8" in url || "/manifest/" in url) return null
        val mime = stream.mimeType.orEmpty()
        val ext = when {
            "mpeg" in mime || "mp3" in mime -> "mp3"
            "mp4" in mime || "m4a" in mime || "aac" in mime -> "m4a"
            else -> return null
        }
        val key = when {
            "googlevideo.com" in url -> {
                val id = Regex("[?&]id=([^&]+)").find(url)?.groupValues?.get(1) ?: return null
                val itag = Regex("[?&]itag=(\\d+)").find(url)?.groupValues?.get(1) ?: "0"
                "$id-$itag"
            }
            "sndcdn.com" in url || "soundcloud" in url -> "sc-" + app.nocturne.sha1Hex(track.id)
            else -> return null
        }
        val dir = platform.Foundation.NSTemporaryDirectory() + "nocturne-audio"
        platform.Foundation.NSFileManager.defaultManager.createDirectoryAtPath(dir, true, null, null)
        return "$dir/${key.replace(Regex("[^A-Za-z0-9_.-]"), "_")}.$ext"
    }

    private fun fallback(reason: String?): Boolean {
        val stream = source ?: return false
        val t = track ?: return false
        if (cached) return false
        val path = cacheFile(stream, t) ?: return false
        cached = true
        val resume = player.currentTime().ms()
        val play = wantPlay || pendingSeekMs != null || lastPlaying
        setBuffering(true)
        scope.launch {
            val ok = runCatching { withContext(Dispatchers.Default) { app.nocturne.data.downloadToFile(stream.url, path) {} } }
            if (source !== stream) return@launch
            ok.onSuccess { open(path, t, resume, play) }
                .onFailure { e ->
                    app.nocturne.data.deleteFile("$path.part")
                    wantPlay = false
                    setPlaying(false)
                    setBuffering(false)
                    val msg = listOfNotNull(reason, e.message?.let { "download failed: $it" }).joinToString("; ")
                    listener?.onError(IllegalStateException(msg.ifEmpty { "Couldn't load the track" }))
                }
        }
        return true
    }

    private fun open(url: String, track: Track, startPositionMs: Long, playWhenReady: Boolean) {
        detach()
        failed = false
        currentUrl = url
        this.track = track
        loadArtwork(track.artworkUrl)
        val asset = avAsset(url)
        if (asset == null) {
            listener?.onError(IllegalArgumentException("Invalid stream URL"))
            return
        }
        val item = AVPlayerItem(asset = asset)
        // pitch follows speed (like ExoPlayer / VLC here) and rate changes glide without re-buffering
        item.audioTimePitchAlgorithm = AVAudioTimePitchAlgorithmVarispeed
        if (!url.startsWith("http") || !(".m3u8" in url || "/manifest/" in url || source?.isHls == true)) IosFx.attach(item, asset)
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
        val reason = item.error?.localizedDescription
        // A broken cached copy is dropped so the next attempt streams again.
        currentUrl?.takeIf { it.startsWith("/") && "/nocturne-audio/" in it }?.let { app.nocturne.data.deleteFile(it) }
        if (fallback(reason)) return
        wantPlay = false
        setPlaying(false)
        setBuffering(false)
        listener?.onError(IllegalStateException(reason ?: "AVPlayer could not play this stream"))
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

    override val fx: Set<AudioFx> = AudioFx.entries.toSet()

    override fun setEqualizer(enabled: Boolean, gains: List<Float>) = IosFx.setEq(enabled, gains)

    override fun setEffects(normalize: Boolean, spatial: Boolean, eightD: Boolean, vocalRemover: Boolean) =
        IosFx.setEffects(normalize, spatial, eightD, vocalRemover)

    override fun levels(): FloatArray? = IosFx.levels()

    override fun setVolume(volume: Float) {
        player.volume = volume.coerceIn(0f, 1f)
    }

    override fun setPlaybackSpeed(speed: Float) {
        val s = speed.coerceIn(0.5f, 2f)
        if (s == this.speed) return
        this.speed = s
        if (player.rate != 0f) player.rate = s
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
