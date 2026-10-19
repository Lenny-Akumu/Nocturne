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
    suspend fun library(): AccountLibrary? = null

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
}

expect fun createNewPipeBackend(): NewPipeBackend
