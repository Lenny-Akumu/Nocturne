package app.nocturne.data

import app.nocturne.FileStore
import app.nocturne.model.CollectionRef
import app.nocturne.model.CollectionType
import app.nocturne.model.Source
import app.nocturne.model.Track
import app.nocturne.nowMillis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class LocalPlaylist(
    val id: String,
    val name: String,
    val tracks: List<Track> = emptyList(),
    val createdAt: Long = 0,
) {
    val ref: CollectionRef
        get() = CollectionRef(id, Source.YOUTUBE_MUSIC, CollectionType.LOCAL, name, "${tracks.size} songs", tracks.firstOrNull()?.artworkUrl)
}

@Serializable
data class HistoryEntry(val track: Track, val playedAt: Long)

@Serializable
data class LibraryData(
    val liked: List<Track> = emptyList(),
    val saved: List<CollectionRef> = emptyList(),
    val playlists: List<LocalPlaylist> = emptyList(),
    val history: List<HistoryEntry> = emptyList(),
    val searchHistory: List<String> = emptyList(),
    val playCounts: Map<String, Int> = emptyMap(),
)

class Library(private val settings: SettingsStore) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val _state = MutableStateFlow(load())
    val state: StateFlow<LibraryData> = _state.asStateFlow()

    private fun load(): LibraryData =
        FileStore.read(FILE)?.let { runCatching { json.decodeFromString<LibraryData>(it) }.getOrNull() } ?: LibraryData()

    private fun mutate(block: (LibraryData) -> LibraryData) {
        _state.value = block(_state.value)
        FileStore.write(FILE, json.encodeToString(LibraryData.serializer(), _state.value))
    }

    fun isLiked(track: Track) = _state.value.liked.any { it.key == track.key }

    fun toggleLike(track: Track) = mutate { d ->
        if (d.liked.any { it.key == track.key }) d.copy(liked = d.liked.filterNot { it.key == track.key })
        else d.copy(liked = listOf(track) + d.liked)
    }

    fun isSaved(ref: CollectionRef) = _state.value.saved.any { it.key == ref.key }

    fun toggleSaved(ref: CollectionRef) = mutate { d ->
        if (d.saved.any { it.key == ref.key }) d.copy(saved = d.saved.filterNot { it.key == ref.key })
        else d.copy(saved = listOf(ref) + d.saved)
    }

    fun createPlaylist(name: String, initial: List<Track> = emptyList()): LocalPlaylist {
        val p = LocalPlaylist("local-${nowMillis()}", name.ifBlank { "My playlist" }, initial, nowMillis())
        mutate { it.copy(playlists = listOf(p) + it.playlists) }
        return p
    }

    fun renamePlaylist(id: String, name: String) = mutate { d ->
        d.copy(playlists = d.playlists.map { if (it.id == id) it.copy(name = name) else it })
    }

    fun deletePlaylist(id: String) = mutate { d -> d.copy(playlists = d.playlists.filterNot { it.id == id }) }

    fun addToPlaylist(id: String, tracks: List<Track>) = mutate { d ->
        d.copy(playlists = d.playlists.map { p ->
            if (p.id != id) p else p.copy(tracks = p.tracks + tracks.filter { t -> p.tracks.none { it.key == t.key } })
        })
    }

    fun removeFromPlaylist(id: String, track: Track) = mutate { d ->
        d.copy(playlists = d.playlists.map { p -> if (p.id != id) p else p.copy(tracks = p.tracks.filterNot { it.key == track.key }) })
    }

    fun playlist(id: String) = _state.value.playlists.firstOrNull { it.id == id }

    fun recordPlay(track: Track) {
        if (settings.value.pauseHistory) return
        mutate { d ->
            val history = (listOf(HistoryEntry(track, nowMillis())) + d.history.filterNot { it.track.key == track.key }).take(500)
            d.copy(history = history, playCounts = d.playCounts + (track.key to (d.playCounts[track.key] ?: 0) + 1))
        }
    }

    fun clearHistory() = mutate { it.copy(history = emptyList(), playCounts = emptyMap()) }

    fun addSearch(query: String) {
        if (settings.value.pauseSearchHistory || query.isBlank()) return
        mutate { d -> d.copy(searchHistory = (listOf(query) + d.searchHistory.filterNot { it == query }).take(30)) }
    }

    fun removeSearch(query: String) = mutate { d -> d.copy(searchHistory = d.searchHistory - query) }

    fun clearSearchHistory() = mutate { it.copy(searchHistory = emptyList()) }

    fun mostPlayed(limit: Int = 20): List<Track> {
        val d = _state.value
        val byKey = d.history.associateBy { it.track.key }
        return d.playCounts.entries.sortedByDescending { it.value }.mapNotNull { byKey[it.key]?.track }.take(limit)
    }

    companion object {
        private const val FILE = "library.json"
    }
}
