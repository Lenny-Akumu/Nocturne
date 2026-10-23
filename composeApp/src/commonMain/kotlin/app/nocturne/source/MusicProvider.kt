package app.nocturne.source

import app.nocturne.model.AccountLibrary
import app.nocturne.model.AudioQuality
import app.nocturne.model.CollectionPage
import app.nocturne.model.CollectionRef
import app.nocturne.model.MediaEntry
import app.nocturne.model.ResolvedStream
import app.nocturne.model.SearchFilter
import app.nocturne.model.Section
import app.nocturne.model.Source
import app.nocturne.model.Track

interface MusicProvider {
    val source: Source

    suspend fun search(query: String, filter: SearchFilter): List<MediaEntry>
    suspend fun home(): List<Section> = emptyList()
    suspend fun collection(ref: CollectionRef): CollectionPage
    suspend fun stream(track: Track, quality: AudioQuality): ResolvedStream
    suspend fun related(track: Track): List<Track> = emptyList()
    suspend fun suggestions(query: String): List<String> = emptyList()

    val isSignedIn: Boolean get() = false
    suspend fun rate(track: Track, rating: Rating) {}
    suspend fun likedTracks(): List<Track> = emptyList()
    suspend fun videoStream(track: Track): String? = null
    suspend fun library(): AccountLibrary? = null

    // Account playlists the user can edit; tracks passed in are already from this source.
    suspend fun editablePlaylists(): List<CollectionRef> = emptyList()
    suspend fun addToPlaylist(playlistId: String, tracks: List<Track>): Unit = throw SourceUnavailableException("Not supported")
    suspend fun removeFromPlaylist(playlistId: String, track: Track): Unit = throw SourceUnavailableException("Not supported")
    suspend fun createPlaylist(name: String, tracks: List<Track>): CollectionRef = throw SourceUnavailableException("Not supported")

    fun canOpen(url: String): Boolean = false
    suspend fun open(url: String): MediaEntry? = null
}

enum class Rating { LIKE, DISLIKE, NONE }

class SourceUnavailableException(message: String) : Exception(message)

interface NewPipeBackend {
    fun init(language: String, country: String)
    suspend fun search(source: Source, query: String, filter: SearchFilter): List<MediaEntry>
    suspend fun stream(source: Source, url: String, quality: AudioQuality): ResolvedStream
    suspend fun track(source: Source, url: String): Track
    suspend fun playlist(source: Source, url: String, ref: CollectionRef?): CollectionPage
    suspend fun channel(source: Source, url: String, ref: CollectionRef?): CollectionPage
    suspend fun related(source: Source, url: String): List<Track>
    suspend fun charts(source: Source): List<Track>
    suspend fun soundcloudClientId(): String
    suspend fun videoStream(source: Source, url: String, maxHeight: Int): String?
    /** YouTube comments through NewPipe where available; null = use our own parser. */
    suspend fun comments(videoId: String, token: String?): CommentsPage? = null
}

expect fun createNewPipeBackend(): NewPipeBackend
