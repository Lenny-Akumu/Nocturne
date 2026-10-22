package app.nocturne.player

import app.nocturne.model.ResolvedStream
import app.nocturne.model.Track
import app.nocturne.source.InnerTube
import java.io.File
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.base.callback.AudioCallbackAdapter

class VlcAudioEngine : AudioEngine {
    private val nativeOk: Boolean = runCatching {
        val bundled = bundledDir()
        if (bundled != null) {
            NativeLibrary.addSearchPath("libvlc", bundled.absolutePath)
            NativeLibrary.addSearchPath("libvlccore", bundled.absolutePath)
            NativeLibrary.getInstance(File(bundled, "libvlccore.dll").absolutePath)
        } else {
            systemVlc = NativeDiscovery().discover()
        }
        true
    }.getOrDefault(false)

    // libVLC decodes (and applies rate) and hands us PCM; effects run in our own Dsp, output goes to a Java Sound line.
    @Volatile private var factory: MediaPlayerFactory? = null
    @Volatile private var player: MediaPlayer? = null
    private val dsp = Dsp(RATE)
    private val sink = Sink()
    @Volatile var dspActive = false
        private set
    private var listener: EngineListener? = null
    @Volatile private var volume = 100
    @Volatile private var time = 0L
    @Volatile private var length = 0L
    @Volatile private var playing = false
    @Volatile private var ended = false

    val available: Boolean get() = player != null
    val mediaFactory: MediaPlayerFactory? get() = factory

    override val fx: Set<AudioFx> get() = if (dspActive) AudioFx.entries.toSet() else emptySet()

    private val events = object : MediaPlayerEventAdapter() {
        override fun playing(mediaPlayer: MediaPlayer) {
            if (mediaPlayer !== player) return
            playing = true
            mediaPlayer.submit {
                if (!dspActive) mediaPlayer.audio().setVolume(volume)
                if (speed != 1f) mediaPlayer.controls().setRate(speed)
            }
            listener?.onPlayingChanged(true)
            listener?.onBufferingChanged(false)
        }

        override fun paused(mediaPlayer: MediaPlayer) {
            if (mediaPlayer !== player) return
            playing = false
            listener?.onPlayingChanged(false)
        }

        override fun stopped(mediaPlayer: MediaPlayer) {
            if (mediaPlayer !== player) return
            playing = false
            listener?.onPlayingChanged(false)
        }

        override fun finished(mediaPlayer: MediaPlayer) {
            if (mediaPlayer !== player) return
            playing = false
            ended = true
            listener?.onPlayingChanged(false)
            listener?.onEnded()
        }

        override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) {
            if (mediaPlayer === player) time = newTime.coerceAtLeast(0)
        }

        override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) {
            if (mediaPlayer === player) length = newLength.coerceAtLeast(0)
        }

        override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) {
            if (mediaPlayer === player) listener?.onBufferingChanged(newCache < 100f)
        }

        override fun error(mediaPlayer: MediaPlayer) {
            if (mediaPlayer !== player) return
            playing = false
            listener?.onError(IllegalStateException("VLC could not play this stream"))
        }
    }

    init {
        if (nativeOk) {
            val f = runCatching {
                MediaPlayerFactory("--no-video", "--quiet", "--intf=dummy", "--http-reconnect", "--network-caching=1500", "--no-audio-time-stretch")
            }.getOrNull()
            val p = runCatching { f?.mediaPlayers()?.newMediaPlayer() }.getOrNull()
            if (p == null) runCatching { f?.release() }
            else {
                p.events().addMediaPlayerEventListener(events)
                // only reroute audio when a Java Sound line is really available, else keep VLC's own output
                if (sink.open()) dspActive = runCatching { p.audio().callback("S16N", RATE, 2, sink, true); true }.getOrDefault(false)
                factory = f
                player = p
            }
        }
    }

    /** Receives decoded S16 stereo from libVLC, runs [dsp] and writes to a ~120 ms Java Sound buffer. */
    private inner class Sink : AudioCallbackAdapter() {
        private val format = AudioFormat(RATE.toFloat(), 16, 2, true, false)
        @Volatile private var line: SourceDataLine? = null
        private var shorts = ShortArray(0)
        private var floats = FloatArray(0)
        private var bytes = ByteArray(0)

        fun open(): Boolean = line != null || runCatching {
            val l = AudioSystem.getSourceDataLine(format)
            l.open(format, RATE / 8 * 4)
            line = l
        }.isSuccess

        override fun play(mediaPlayer: MediaPlayer, samples: Pointer, sampleCount: Int, pts: Long) {
            val n = sampleCount * 2
            if (n <= 0) return
            if (shorts.size < n) { shorts = ShortArray(n); floats = FloatArray(n); bytes = ByteArray(n * 2) }
            samples.read(0, shorts, 0, n)
            for (i in 0 until n) floats[i] = shorts[i] / 32768f
            dsp.process(floats, sampleCount)
            for (i in 0 until n) {
                val v = (floats[i] * 32767f).toInt()
                bytes[2 * i] = v.toByte()
                bytes[2 * i + 1] = (v shr 8).toByte()
            }
            if (!open()) return
            val l = line ?: return
            runCatching {
                if (!l.isRunning) l.start()
                l.write(bytes, 0, n * 2)
            }.onFailure { // device lost: reopen on the next buffer
                runCatching { l.close() }
                line = null
            }
        }

        override fun pause(mediaPlayer: MediaPlayer, pts: Long) { runCatching { line?.stop() } }
        override fun resume(mediaPlayer: MediaPlayer, pts: Long) { runCatching { line?.start() } }
        override fun flush(mediaPlayer: MediaPlayer, pts: Long) = flush()
        override fun drain(mediaPlayer: MediaPlayer) { runCatching { line?.drain() } }
        override fun setVolume(volume: Float, mute: Boolean) = Unit // gain is applied by dsp

        fun flush() {
            runCatching { line?.flush() }
            dsp.reset()
        }

        fun close() { runCatching { line?.close() }; line = null }
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
        sink.flush()
        val options = buildList {
            add(":http-user-agent=${InnerTube.USER_AGENT}")
            if (startPositionMs > 0) add(":start-time=${startPositionMs / 1000.0}")
            add(":meta-title=${track.title}")
        }.toTypedArray()
        ended = false
        async { if (playWhenReady) media().play(stream.url, *options) else media().startPaused(stream.url, *options) }
    }

    override fun play() = async { controls().play() }

    override fun pause() = async { controls().setPause(true) }

    override fun stop() {
        playing = false
        async { controls().stop(); sink.flush() }
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

    override fun setEqualizer(enabled: Boolean, gains: List<Float>) = dsp.setEq(enabled, gains)

    override fun setEffects(normalize: Boolean, spatial: Boolean, eightD: Boolean, vocalRemover: Boolean) {
        dsp.normalize = normalize
        dsp.spatial = spatial
        dsp.eightD = eightD
        dsp.vocalRemover = vocalRemover
    }

    override fun setVolume(volume: Float) {
        this.volume = (volume * 100).toInt().coerceIn(0, 200)
        dsp.volume = volume.coerceIn(0f, 2f)
        if (!dspActive) async { audio().setVolume(this@VlcAudioEngine.volume) }
    }

    companion object {
        @Volatile var systemVlc = false
        const val RATE = 48000

        fun bundledDir(): File? = System.getProperty("compose.application.resources.dir")
            ?.let { File(it, "vlc") }
            ?.takeIf { File(it, "libvlc.dll").exists() }
    }

    override fun release() {
        player?.release()
        factory?.release()
        sink.close()
    }
}
