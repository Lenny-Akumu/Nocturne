package app.nocturne.source

import app.nocturne.data.SettingsStore
import app.nocturne.model.ArtistRef
import app.nocturne.model.AudioQuality
import app.nocturne.model.CollectionPage
import app.nocturne.model.CollectionRef
import app.nocturne.model.MediaEntry
import app.nocturne.model.ResolvedStream
import app.nocturne.model.SearchFilter
import app.nocturne.model.Source
import app.nocturne.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LocalAudio(val uri: String, val title: String, val artist: String, val album: String?, val durationMs: Long, val art: String?)

expect suspend fun scanLocalAudio(folders: List<String>): List<LocalAudio>

expect fun hasLocalAudioAccess(): Boolean

expect fun requestLocalAudioAccess(onResult: (Boolean) -> Unit)

class LocalProvider(private val settings: SettingsStore) : MusicProvider {
    override val source = Source.LOCAL

    private val _tracks = MutableStateFlow<List<Track>?>(null)
    val tracks: StateFlow<List<Track>?> = _tracks.asStateFlow()

    suspend fun rescan() {
        if (!hasLocalAudioAccess()) return
        _tracks.value = scanLocalAudio(settings.value.localFolders).map { a ->
            Track(
                id = a.uri,
                source = source,
                title = a.title,
                artists = a.artist.split(", ", " & ", "; ").filter { it.isNotBlank() && it != "<unknown>" }.map { ArtistRef(it) },
                album = a.album,
                durationMs = a.durationMs,
                artworkUrl = a.art,
            )
        }.sortedBy { it.title.lowercase() }
    }

    override suspend fun search(query: String, filter: SearchFilter): List<MediaEntry> {
        val q = query.lowercase()
        return tracks.value.orEmpty().filter { q in it.title.lowercase() || q in it.artistNames.lowercase() }.map { MediaEntry.Song(it) }
    }

    override suspend fun collection(ref: CollectionRef): CollectionPage = throw SourceUnavailableException("Not available for local files")

    override suspend fun stream(track: Track, quality: AudioQuality) = ResolvedStream(track.id)

    override suspend fun related(track: Track): List<Track> =
        tracks.value.orEmpty().filter { it.key != track.key }.shuffled().take(25)
}
