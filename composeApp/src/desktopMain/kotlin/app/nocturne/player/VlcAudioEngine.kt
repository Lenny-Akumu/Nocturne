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

    // libVLC 3 builds the audio filter chain from instance args only (per-media ":audio-filter" is ignored),
    // so toggling an effect swaps in a fresh instance and resumes at the same position.
    @Volatile private var factory: MediaPlayerFactory? = null
    @Volatile private var player: MediaPlayer? = null
    private var filters = emptyList<String>()
    private var listener: EngineListener? = null
    @Volatile private var volume = 100
    @Volatile private var time = 0L
    @Volatile private var length = 0L
    @Volatile private var playing = false
    @Volatile private var ended = false

    val available: Boolean get() = player != null
    val mediaFactory: MediaPlayerFactory? get() = factory

    override val fx = setOf(AudioFx.NORMALIZE, AudioFx.SPATIAL, AudioFx.VOCAL_REMOVER, AudioFx.EQUALIZER)

    private val events = object : MediaPlayerEventAdapter() {
        override fun playing(mediaPlayer: MediaPlayer) {
            if (mediaPlayer !== player) return
            playing = true
            mediaPlayer.submit {
                mediaPlayer.audio().setVolume(volume)
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
        if (nativeOk) open(emptyList())
    }

    private fun open(chain: List<String>) {
        val args = buildList {
            addAll(listOf("--no-video", "--quiet", "--intf=dummy", "--http-reconnect", "--network-caching=1500", "--no-audio-time-stretch"))
            if (chain.isNotEmpty()) add("--audio-filter=${chain.joinToString(":")}")
            if ("normvol" in chain) addAll(listOf("--norm-buff-size=20", "--norm-max-level=1.5"))
        }
        val f = runCatching { MediaPlayerFactory(*args.toTypedArray()) }.getOrNull()
        val p = runCatching { f?.mediaPlayers()?.newMediaPlayer() }.getOrNull()
        if (p == null) runCatching { f?.release() }
        p?.events()?.addMediaPlayerEventListener(events)
        factory = f.takeIf { p != null }
        player = p
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
        ended = false
        lastLoad = stream to track
        async { if (playWhenReady) media().play(stream.url, *options) else media().startPaused(stream.url, *options) }
    }

    override fun play() = async { controls().play() }

    override fun pause() = async { controls().setPause(true) }

    override fun stop() {
        playing = false
        lastLoad = null
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

    private var eq: Pair<Boolean, List<Float>> = false to emptyList()

    override fun setEqualizer(enabled: Boolean, gains: List<Float>) {
        eq = enabled to gains
        val f = factory ?: return
        async {
            if (!enabled) audio().setEqualizer(null)
            else {
                val bands = f.equalizer().bands()
                val e = f.equalizer().newEqualizer()
                e.setPreamp(12f)
                bands.forEachIndexed { i, hz -> e.setAmp(i, Eq.gainAt(hz, gains).coerceIn(-20f, 20f)) }
                audio().setEqualizer(e)
            }
        }
    }

    private var lastLoad: Pair<ResolvedStream, Track>? = null

    override fun setEffects(normalize: Boolean, spatial: Boolean, eightD: Boolean, vocalRemover: Boolean) {
        val chain = listOfNotNull("normvol".takeIf { normalize }, "stereo_widen".takeIf { spatial }, "karaoke".takeIf { vocalRemover })
        if (chain == filters || !nativeOk) return
        filters = chain
        val oldPlayer = player
        val oldFactory = factory
        val wasPlaying = playing
        val at = time
        open(chain)
        if (player == null) { // new chain failed: keep the old instance
            factory = oldFactory
            player = oldPlayer
            return
        }
        Thread {
            runCatching { oldPlayer?.controls()?.stop() }
            runCatching { oldPlayer?.release() }
            runCatching { oldFactory?.release() }
        }.apply { isDaemon = true }.start()
        setEqualizer(eq.first, eq.second)
        val last = lastLoad
        if (last != null && !ended) load(last.first, last.second, at, wasPlaying)
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
