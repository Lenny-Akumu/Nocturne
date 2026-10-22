package app.nocturne.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import app.nocturne.AppGraph
import app.nocturne.model.ResolvedStream
import app.nocturne.model.Track
import app.nocturne.source.InnerTube
import java.io.File

@OptIn(UnstableApi::class)
class AndroidAudioEngine(context: Context, cacheMb: Int) : AudioEngine {
    override fun clearCache(): Long {
        val before = cache.cacheSpace
        cache.keys.toList().forEach { runCatching { cache.removeResource(it) } }
        return (before - cache.cacheSpace).coerceAtLeast(0)
    }

    private val cache = SimpleCache(
        File(context.cacheDir, "audio"),
        LeastRecentlyUsedCacheEvictor(cacheMb.coerceAtLeast(64) * 1024L * 1024L),
        StandaloneDatabaseProvider(context),
    )

    private val dataSourceFactory = CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(
            androidx.media3.datasource.DefaultDataSource.Factory(
                context,
                DefaultHttpDataSource.Factory()
                    .setUserAgent(InnerTube.USER_AGENT)
                    .setAllowCrossProtocolRedirects(true)
                    .setConnectTimeoutMs(15_000)
                    .setReadTimeoutMs(20_000),
            )
        )
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    private val fxProcessor = FxAudioProcessor()

    override val fx = AudioFx.entries.toSet()

    val exo: ExoPlayer = ExoPlayer.Builder(context)
        .setRenderersFactory(object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
                DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessorChain(DefaultAudioSink.DefaultAudioProcessorChain(fxProcessor))
                    .build()
        })
        .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
            true,
        )
        .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(15_000, 60_000, 800, 2_000).build())
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .build()

    private var listener: EngineListener? = null
    private val controller get() = AppGraph.instance.player

    val sessionPlayer: Player = object : ForwardingPlayer(exo) {
        override fun getAvailableCommands(): Player.Commands = super.getAvailableCommands().buildUpon()
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .add(Player.COMMAND_PLAY_PAUSE)
            .build()

        override fun isCommandAvailable(command: Int): Boolean = availableCommands.contains(command)
        override fun hasNextMediaItem() = true
        override fun hasPreviousMediaItem() = true
        override fun setMediaItems(mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long) {
            if (!AutoBrowse.play(mediaItems, startIndex)) super.setMediaItems(mediaItems, startIndex, startPositionMs)
        }
        override fun setMediaItems(mediaItems: MutableList<MediaItem>, resetPosition: Boolean) {
            if (!AutoBrowse.play(mediaItems, 0)) super.setMediaItems(mediaItems, resetPosition)
        }
        override fun setMediaItem(mediaItem: MediaItem) {
            if (!AutoBrowse.play(listOf(mediaItem), 0)) super.setMediaItem(mediaItem)
        }
        override fun seekToNext() = controller.next()
        override fun seekToNextMediaItem() = controller.next()
        override fun seekToPrevious() = controller.previous()
        override fun seekToPreviousMediaItem() = controller.previous()

        override fun play() {
            if (exo.mediaItemCount == 0 || exo.playbackState == Player.STATE_IDLE) controller.resume() else super.play()
        }
    }

    init {
        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                listener?.onPlayingChanged(isPlaying)
            }

            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> listener?.onBufferingChanged(true)
                    Player.STATE_READY -> listener?.onBufferingChanged(false)
                    Player.STATE_ENDED -> listener?.onEnded()
                    else -> Unit
                }
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                equalizer?.release()
                equalizer = null
                applyEqualizer()
            }

            override fun onPlayerError(error: PlaybackException) {
                listener?.onError(error)
            }
        })
    }

    override val positionMs: Long get() = exo.currentPosition
    override val durationMs: Long get() = exo.duration.takeIf { it != C.TIME_UNSET } ?: 0
    override val isPlaying: Boolean get() = exo.isPlaying

    override fun setListener(listener: EngineListener) {
        this.listener = listener
    }

    override fun load(stream: ResolvedStream, track: Track, startPositionMs: Long, playWhenReady: Boolean) {
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artistNames)
            .setAlbumTitle(track.album)
            .setArtworkUri(track.artworkUrl?.let(Uri::parse))
            .setIsPlayable(true)
            .build()
        val item = MediaItem.Builder()
            .setMediaId(track.key)
            .setUri(stream.url)
            .setMediaMetadata(metadata)
            .apply {
                if (stream.isHls) setMimeType(MimeTypes.APPLICATION_M3U8)
                else setCustomCacheKey(track.key)
            }
            .build()
        exo.setMediaItem(item, startPositionMs)
        exo.playWhenReady = playWhenReady
        exo.prepare()
    }

    override fun play() = exo.play()
    override fun pause() = exo.pause()
    override fun stop() = exo.stop()
    override fun seekTo(positionMs: Long) = exo.seekTo(positionMs)
    override fun setVolume(volume: Float) {
        exo.volume = volume
    }

    override fun setSkipSilence(enabled: Boolean) {
        exo.skipSilenceEnabled = enabled
    }

    private var equalizer: android.media.audiofx.Equalizer? = null
    private var eqEnabled = false
    private var eqGains: List<Float> = emptyList()

    override fun setPlaybackSpeed(speed: Float) {
        val s = speed.coerceIn(0.5f, 2f)
        exo.playbackParameters = androidx.media3.common.PlaybackParameters(s, s)
    }

    override fun setEqualizer(enabled: Boolean, gains: List<Float>) {
        eqEnabled = enabled
        eqGains = gains
        applyEqualizer()
    }

    private fun applyEqualizer() {
        runCatching {
            if (!eqEnabled) {
                equalizer?.enabled = false
                return
            }
            val session = exo.audioSessionId
            if (session == C.AUDIO_SESSION_ID_UNSET) return
            val eq = equalizer ?: android.media.audiofx.Equalizer(0, session).also { equalizer = it }
            val (min, max) = eq.bandLevelRange.let { it[0] to it[1] }
            for (band in 0 until eq.numberOfBands) {
                val freqHz = eq.getCenterFreq(band.toShort()) / 1000f
                val mb = (Eq.gainAt(freqHz, eqGains) * 100).toInt().coerceIn(min.toInt(), max.toInt())
                eq.setBandLevel(band.toShort(), mb.toShort())
            }
            eq.enabled = true
        }
    }

    // effects run in our own DSP (FxAudioProcessor) so they behave the same on every device
    override fun setEffects(normalize: Boolean, spatial: Boolean, eightD: Boolean, vocalRemover: Boolean) {
        fxProcessor.normalize = normalize
        fxProcessor.spatial = spatial
        fxProcessor.eightD = eightD
        fxProcessor.vocalRemover = vocalRemover
    }

    override fun release() {
        exo.release()
        cache.release()
    }
}
