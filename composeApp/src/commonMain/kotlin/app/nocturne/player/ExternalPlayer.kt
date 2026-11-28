package app.nocturne.player

import app.nocturne.model.Track

/** Plays some tracks in another app (Spotify via Connect) while Nocturne keeps the queue. */
interface ExternalPlayer {
    val name: String
    fun handles(track: Track): Boolean
    /** Starts [tracks] there (the current one first, then what follows it); throws when it can't, and it then plays here. */
    suspend fun start(tracks: List<Track>, ms: Long, play: Boolean)
    suspend fun command(op: String, ms: Long = 0, vol: Float = -1f)
    suspend fun poll(): ExternalState?
}

data class ExternalState(val trackId: String?, val positionMs: Long, val durationMs: Long, val playing: Boolean)
