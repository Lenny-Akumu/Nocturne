package app.nocturne.player

import app.nocturne.model.ResolvedStream
import app.nocturne.model.Track

interface EngineListener {
    fun onEnded()
    fun onError(error: Throwable)
    fun onPlayingChanged(playing: Boolean)
    fun onBufferingChanged(buffering: Boolean)
}

enum class AudioFx { NORMALIZE, SPATIAL, EIGHT_D, VOCAL_REMOVER, EQUALIZER }

interface AudioEngine {
    /** Effects this engine really applies; the UI hides the rest. */
    val fx: Set<AudioFx> get() = emptySet()
    /** Promo recordings: no real stream is resolved. */
    val fake: Boolean get() = false
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
    fun setEffects(normalize: Boolean, spatial: Boolean, eightD: Boolean, vocalRemover: Boolean) {}
    fun onQueueChanged(hasPrevious: Boolean, hasNext: Boolean) {}
    /** Live 5-band levels (0..1, low→high) of what is audible now; null when the engine has no PCM access. */
    fun levels(): FloatArray? = null
    fun release()
}
