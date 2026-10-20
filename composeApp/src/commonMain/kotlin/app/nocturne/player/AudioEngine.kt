package app.nocturne.player

import app.nocturne.model.ResolvedStream
import app.nocturne.model.Track

interface EngineListener {
    fun onEnded()
    fun onError(error: Throwable)
    fun onPlayingChanged(playing: Boolean)
    fun onBufferingChanged(buffering: Boolean)
}

interface AudioEngine {
    fun clearCache(): Long = 0
    val positionMs: Long
    val durationMs: Long
    val isPlaying: Boolean

    fun setListener(listener: EngineListener)
    fun load(stream: ResolvedStream, track: Track, startPositionMs: Long, playWhenReady: Boolean)
    fun play()
    fun pause()
    fun stop()
    fun seekTo(positionMs: Long)
    fun setVolume(volume: Float)
    fun setSkipSilence(enabled: Boolean) {}
    fun setPlaybackSpeed(speed: Float) {}
    fun setEqualizer(enabled: Boolean, gains: List<Float>) {}
    fun onQueueChanged(hasPrevious: Boolean, hasNext: Boolean) {}
    fun release()
}
