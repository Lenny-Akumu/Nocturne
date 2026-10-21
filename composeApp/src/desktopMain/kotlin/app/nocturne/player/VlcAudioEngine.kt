package app.nocturne.player

import app.nocturne.model.ResolvedStream
import app.nocturne.model.Track
import app.nocturne.source.InnerTube
import java.io.File
import com.sun.jna.NativeLibrary
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter

class VlcAudioEngine : AudioEngine {
    private val factory: MediaPlayerFactory? = runCatching {
        val bundled = bundledDir()
        if (bundled != null) {
            NativeLibrary.addSearchPath("libvlc", bundled.absolutePath)
            NativeLibrary.addSearchPath("libvlccore", bundled.absolutePath)
            NativeLibrary.getInstance(File(bundled, "libvlccore.dll").absolutePath)
        } else {
            systemVlc = NativeDiscovery().discover()
        }
        MediaPlayerFactory("--no-video", "--quiet", "--intf=dummy", "--http-reconnect", "--network-caching=1500", "--no-audio-time-stretch")
    }.getOrNull()

    private val player: MediaPlayer? = runCatching { factory?.mediaPlayers()?.newMediaPlayer() }.getOrNull()
    private var listener: EngineListener? = null
    @Volatile private var volume = 100
    @Volatile private var time = 0L
    @Volatile private var length = 0L
    @Volatile private var playing = false

    val available: Boolean get() = player != null
    val mediaFactory: MediaPlayerFactory? get() = factory

    init {
        player?.events()?.addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun playing(mediaPlayer: MediaPlayer) {
                playing = true
                mediaPlayer.submit {
                    mediaPlayer.audio().setVolume(volume)
                    if (speed != 1f) mediaPlayer.controls().setRate(speed)
                }
                listener?.onPlayingChanged(true)
                listener?.onBufferingChanged(false)
            }

            override fun paused(mediaPlayer: MediaPlayer) {
                playing = false
                listener?.onPlayingChanged(false)
            }

            override fun stopped(mediaPlayer: MediaPlayer) {
                playing = false
                listener?.onPlayingChanged(false)
            }

            override fun finished(mediaPlayer: MediaPlayer) {
                playing = false
                listener?.onPlayingChanged(false)
                listener?.onEnded()
            }

            override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) {
                time = newTime.coerceAtLeast(0)
            }

            override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) {
                length = newLength.coerceAtLeast(0)
            }

            override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) {
                listener?.onBufferingChanged(newCache < 100f)
            }

            override fun error(mediaPlayer: MediaPlayer) {
                playing = false
                listener?.onError(IllegalStateException("VLC could not play this stream"))
            }
        })
    }

    override val positionMs: Long get() = time
    override val durationMs: Long get() = length
    override val isPlaying: Boolean get() = playing

    override fun setListener(listener: EngineListener) {
        this.listener = listener
    }

    private fun async(block: MediaPlayer.() -> Unit) {
        val p = player ?: return
        p.submit { runCatching { p.block() } }
    }

    override fun load(stream: ResolvedStream, track: Track, startPositionMs: Long, playWhenReady: Boolean) {
        if (player == null) {
            listener?.onError(IllegalStateException("The audio engine failed to load — reinstall Nocturne"))
            return
        }
        time = startPositionMs
        length = track.durationMs
        val options = buildList {
            add(":http-user-agent=${InnerTube.USER_AGENT}")
            if (startPositionMs > 0) add(":start-time=${startPositionMs / 1000.0}")
            add(":meta-title=${track.title}")
        }.toTypedArray()
        async { if (playWhenReady) media().play(stream.url, *options) else media().startPaused(stream.url, *options) }
    }

    override fun play() = async { controls().play() }

    override fun pause() = async { controls().setPause(true) }

    override fun stop() {
        playing = false
        async { controls().stop() }
    }

    override fun seekTo(positionMs: Long) {
        time = positionMs
        async { controls().setTime(positionMs) }
    }

    @Volatile private var speed = 1f

    override fun setPlaybackSpeed(speed: Float) {
        this.speed = speed.coerceIn(0.5f, 2f)
        async { controls().setRate(this@VlcAudioEngine.speed) }
    }

    override fun setEqualizer(enabled: Boolean, gains: List<Float>) {
        val f = factory ?: return
        async {
            if (!enabled) audio().setEqualizer(null)
            else {
                val bands = f.equalizer().bands()
                val eq = f.equalizer().newEqualizer()
                eq.preamp = 12f
                bands.forEachIndexed { i, hz -> eq.setAmp(i, Eq.gainAt(hz, gains).coerceIn(-20f, 20f)) }
                audio().setEqualizer(eq)
            }
        }
    }

    override fun setVolume(volume: Float) {
        this.volume = (volume * 100).toInt().coerceIn(0, 200)
        async { audio().setVolume(this@VlcAudioEngine.volume) }
    }

    companion object {
        @Volatile var systemVlc = false

        fun bundledDir(): File? = System.getProperty("compose.application.resources.dir")
            ?.let { File(it, "vlc") }
            ?.takeIf { File(it, "libvlc.dll").exists() }
    }

    override fun release() {
        player?.release()
        factory?.release()
    }
}
