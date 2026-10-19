package app.nocturne.player

import app.nocturne.model.ResolvedStream
import app.nocturne.model.Track
import app.nocturne.source.InnerTube
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter

class VlcAudioEngine : AudioEngine {
    private val factory: MediaPlayerFactory? = runCatching {
        NativeDiscovery().discover()
        MediaPlayerFactory("--no-video", "--quiet", "--intf=dummy", "--http-reconnect", "--network-caching=1500")
    }.getOrNull()

    private val player: MediaPlayer? = factory?.mediaPlayers()?.newMediaPlayer()
    private var listener: EngineListener? = null
    private var volume = 100

    val available: Boolean get() = player != null

    init {
        player?.events()?.addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun playing(mediaPlayer: MediaPlayer) {
                mediaPlayer.submit { mediaPlayer.audio().setVolume(volume) }
                listener?.onPlayingChanged(true)
                listener?.onBufferingChanged(false)
            }

            override fun paused(mediaPlayer: MediaPlayer) {
                listener?.onPlayingChanged(false)
            }

            override fun stopped(mediaPlayer: MediaPlayer) {
                listener?.onPlayingChanged(false)
            }

            override fun finished(mediaPlayer: MediaPlayer) {
                listener?.onPlayingChanged(false)
                listener?.onEnded()
            }

            override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) {
                listener?.onBufferingChanged(newCache < 100f)
            }

            override fun error(mediaPlayer: MediaPlayer) {
                listener?.onError(IllegalStateException("VLC could not play this stream"))
            }
        })
    }

    override val positionMs: Long get() = player?.status()?.time()?.coerceAtLeast(0) ?: 0
    override val durationMs: Long get() = player?.status()?.length()?.coerceAtLeast(0) ?: 0
    override val isPlaying: Boolean get() = player?.status()?.isPlaying ?: false

    override fun setListener(listener: EngineListener) {
        this.listener = listener
    }

    override fun load(stream: ResolvedStream, track: Track, startPositionMs: Long, playWhenReady: Boolean) {
        val p = player ?: run {
            listener?.onError(IllegalStateException("VLC media player (64-bit) is required for playback on desktop — install it from videolan.org"))
            return
        }
        val options = buildList {
            add(":http-user-agent=${InnerTube.USER_AGENT}")
            if (startPositionMs > 0) add(":start-time=${startPositionMs / 1000.0}")
            add(":meta-title=${track.title}")
        }.toTypedArray()
        if (playWhenReady) p.media().play(stream.url, *options) else p.media().startPaused(stream.url, *options)
    }

    override fun play() {
        player?.controls()?.play()
    }

    override fun pause() {
        player?.controls()?.setPause(true)
    }

    override fun stop() {
        player?.controls()?.stop()
    }

    override fun seekTo(positionMs: Long) {
        player?.controls()?.setTime(positionMs)
    }

    override fun setVolume(volume: Float) {
        this.volume = (volume * 100).toInt().coerceIn(0, 200)
        player?.audio()?.setVolume(this.volume)
    }

    override fun release() {
        player?.release()
        factory?.release()
    }
}
