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
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import app.nocturne.AppGraph
import app.nocturne.model.ResolvedStream
import app.nocturne.model.Track
import app.nocturne.source.InnerTube
import java.io.File

@OptIn(UnstableApi::class)
class AndroidAudioEngine(context: Context, cacheMb: Int) : AudioEngine {
    private val cache = SimpleCache(
        File(context.cacheDir, "audio"),
        LeastRecentlyUsedCacheEvictor(cacheMb.coerceAtLeast(64) * 1024L * 1024L),
        StandaloneDatabaseProvider(context),
    )

    private val dataSourceFactory = CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(
            DefaultHttpDataSource.Factory()
                .setUserAgent(InnerTube.USER_AGENT)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(20_000)
        )
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    val exo: ExoPlayer = ExoPlayer.Builder(context)
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

    override fun release() {
        exo.release()
        cache.release()
    }
}
