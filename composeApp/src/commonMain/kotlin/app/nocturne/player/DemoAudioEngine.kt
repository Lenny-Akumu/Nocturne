package app.nocturne.player

import app.nocturne.model.ResolvedStream
import app.nocturne.model.Track
import app.nocturne.nowMillis

/** Silent stand-in for promo recordings: the UI plays, seeks and pauses as usual, nothing is fetched or heard. */
class DemoAudioEngine : AudioEngine {
    override val fake: Boolean get() = true
    override val fx: Set<AudioFx> = AudioFx.entries.toSet()
    private var listener: EngineListener? = null
    private var base = 0L
    private var since = 0L
    private var playing = false
    private var dur = 0L

    override val positionMs: Long get() = (if (playing) base + (nowMillis() - since) else base).coerceAtMost(dur.coerceAtLeast(0))
    override val durationMs: Long get() = dur
    override val isPlaying: Boolean get() = playing

    override fun setListener(listener: EngineListener) { this.listener = listener }

    override fun load(stream: ResolvedStream, track: Track, startPositionMs: Long, playWhenReady: Boolean) {
        dur = track.durationMs.takeIf { it > 0 } ?: 200_000
        base = startPositionMs
        listener?.onBufferingChanged(false)
        if (playWhenReady) play() else pause()
    }

    override fun play() { since = nowMillis(); playing = true; listener?.onPlayingChanged(true) }
    override fun pause() { base = positionMs; playing = false; listener?.onPlayingChanged(false) }
    override fun stop() { playing = false; base = 0 }
    override fun seekTo(positionMs: Long) { base = positionMs; since = nowMillis() }
    override fun setVolume(volume: Float) {}
    override fun release() {}
}
