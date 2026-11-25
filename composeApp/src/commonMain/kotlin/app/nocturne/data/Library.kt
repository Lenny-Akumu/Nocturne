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
    /** Shared for co-editing: everyone with this code edits the same list. */
    val shareCode: String? = null,
    /** Time of the edit this copy reflects (newest wins between devices). */
    val version: Long = 0,
    /** Cover made in the app (a recipe, see GeneratedCover). */
    val cover: String? = null,
) {
    val ref: CollectionRef
        get() = CollectionRef(id, Source.YOUTUBE_MUSIC, CollectionType.LOCAL, name, app.nocturne.i18n.t("%s songs", tracks.size), cover ?: tracks.firstOrNull()?.artworkUrl)
}

@Serializable
data class HistoryEntry(val track: Track, val playedAt: Long)

@Serializable
data class PlayEvent(val k: String, val t: Long, val a: String = "", val d: Long = 0)

@Serializable
data class LibraryData(
    val liked: List<Track> = emptyList(),
    val saved: List<CollectionRef> = emptyList(),
    val playlists: List<LocalPlaylist> = emptyList(),
    val history: List<HistoryEntry> = emptyList(),
    val searchHistory: List<String> = emptyList(),
    val searchPlays: List<Track> = emptyList(),
    val playCounts: Map<String, Int> = emptyMap(),
    val notInterested: List<Track> = emptyList(),
    val dislikedArtists: Map<String, Int> = emptyMap(),
    val plays: List<PlayEvent> = emptyList(),
)

class Library(private val settings: SettingsStore) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val _state = MutableStateFlow(load())
    val state: StateFlow<LibraryData> = _state.asStateFlow()

    private fun load(): LibraryData =
        FileStore.read(FILE)?.let { runCatching { json.decodeFromString<LibraryData>(it) }.getOrNull() } ?: LibraryData()

    private val saver = Persist(FILE, 800) { json.encodeToString(LibraryData.serializer(), _state.value) }

    private fun mutate(block: (LibraryData) -> LibraryData) {
        val before = _state.value
        _state.value = block(before)
        if (_state.value !== before) saver.schedule()
    }

    fun isLiked(track: Track) = _state.value.liked.any { it.key == track.key }

    fun toggleLike(track: Track) = mutate { d ->
        if (d.liked.any { it.key == track.key }) d.copy(liked = d.liked.filterNot { it.key == track.key })
        else d.copy(liked = listOf(track) + d.liked)
    }

    fun markNotInterested(track: Track) = mutate { d ->
        if (d.notInterested.any { it.key == track.key }) return@mutate d
        val artists = track.artists.map { it.name.lowercase().trim() }.filter { it.isNotEmpty() }
        d.copy(
            notInterested = (listOf(track) + d.notInterested).take(1000),
            dislikedArtists = d.dislikedArtists + artists.associateWith { (d.dislikedArtists[it] ?: 0) + 1 },
            liked = d.liked.filterNot { it.key == track.key },
        )
    }

    fun clearNotInterested() = mutate { it.copy(notInterested = emptyList(), dislikedArtists = emptyMap()) }

    fun isBlocked(track: Track): Boolean {
        val d = _state.value
        if (d.notInterested.any { it.key == track.key || (it.title.equals(track.title, true) && it.artistNames.equals(track.artistNames, true)) }) return true
        return track.artists.any { (d.dislikedArtists[it.name.lowercase().trim()] ?: 0) >= 2 }
    }

    fun favoriteArtists(): Set<String> {
        val d = _state.value
        val fromLikes = d.liked.flatMap { t -> t.artists.map { it.name.lowercase() } }
        val fromPlays = d.history.take(200).flatMap { h -> h.track.artists.map { it.name.lowercase() } }
        return (fromLikes + fromPlays).groupingBy { it }.eachCount().filterValues { it >= 2 }.keys
    }

    /**
     * Puts a service's likes in that service's order (newest first). Older versions appended new remote likes
     * at the very end, which left a gap: recent songs sat below the oldest ones. Songs liked only here keep
     * their place next to the song that followed them.
     */
    fun syncLiked(remote: List<Track>) = mutate { d ->
        val blocked = d.notInterested.map { it.key }.toSet()
        val r = remote.distinctBy { it.key }.filter { it.key !in blocked }
        if (r.isEmpty()) return@mutate d
        val inRemote = r.mapTo(HashSet()) { it.key }
        val before = HashMap<String, MutableList<Track>>()
        val pending = ArrayList<Track>()
        val top = ArrayList<Track>()
        var seenRemote = false
        for (t in d.liked) {
            if (t.key in inRemote) {
                // the ones above every synced song are the newest of all: they stay on top
                if (!seenRemote) { top += pending; pending.clear(); seenRemote = true }
                else if (pending.isNotEmpty()) { before.getOrPut(t.key) { ArrayList() } += pending; pending.clear() }
            } else pending += t
        }
        val have = d.liked.associateBy { it.key }
        val out = ArrayList<Track>(d.liked.size + r.size)
        out += top
        for (t in r) { before[t.key]?.let(out::addAll); out += have[t.key] ?: t }
        out += pending
        val merged = out.distinctBy { it.key }
        if (merged == d.liked) d else d.copy(liked = merged)
    }

    fun mergeLiked(remote: List<Track>) = mutate { d ->
        val have = d.liked.map { it.key }.toSet()
        val blocked = d.notInterested.map { it.key }.toSet()
        val fresh = remote.filter { it.key !in have && it.key !in blocked }.distinctBy { it.key }
        // likes found on a service are newer than our last sync: they go on top, like ones made here
        if (fresh.isEmpty()) d else d.copy(liked = fresh + d.liked)
    }

    fun merge(other: LibraryData) = mutate { d ->
        d.copy(
            liked = (d.liked + other.liked).distinctBy { it.key },
            saved = (d.saved + other.saved).distinctBy { it.key },
            playlists = d.playlists.map { p -> other.playlists.firstOrNull { it.id == p.id }?.let { o -> p.copy(tracks = (p.tracks + o.tracks).distinctBy { it.key }) } ?: p } +
                other.playlists.filter { o -> d.playlists.none { it.id == o.id } },
            history = (d.history + other.history).sortedByDescending { it.playedAt }.distinctBy { it.track.key }.take(2000),
            notInterested = (d.notInterested + other.notInterested).distinctBy { it.key },
            plays = (d.plays + other.plays).distinctBy { it.k to it.t }.sortedByDescending { it.t }.take(10_000),
        )
    }

    /** Device sync: replaces the data in one step; true when anything changed. */
    fun applySync(block: (LibraryData) -> LibraryData): Boolean {
        val before = _state.value
        val after = block(before)
        if (after == before) return false
        mutate { after }
        return true
    }

    fun isSaved(ref: CollectionRef) = _state.value.saved.any { it.key == ref.key }

    fun toggleSaved(ref: CollectionRef) = mutate { d ->
        if (d.saved.any { it.key == ref.key }) d.copy(saved = d.saved.filterNot { it.key == ref.key })
        else d.copy(saved = listOf(ref) + d.saved)
    }

    fun createPlaylist(name: String, initial: List<Track> = emptyList()): LocalPlaylist {
        val p = LocalPlaylist("local-${nowMillis()}", name.ifBlank { app.nocturne.i18n.t("My playlist") }, initial, nowMillis())
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

    fun setPlaylistShare(id: String, code: String?, version: Long) = mutate { d ->
        d.copy(playlists = d.playlists.map { if (it.id == id) it.copy(shareCode = code, version = version) else it })
    }

    fun setPlaylistCover(id: String, cover: String?) = mutate { d ->
        d.copy(playlists = d.playlists.map { if (it.id == id) it.copy(cover = cover) else it })
    }

    /** A newer copy of a shared playlist arrived from someone else. */
    fun applySharedPlaylist(code: String, name: String, tracks: List<Track>, version: Long, cover: String? = null) = mutate { d ->
        d.copy(playlists = d.playlists.map { if (it.shareCode == code && version > it.version) it.copy(name = name, tracks = tracks, version = version, cover = cover) else it })
    }

    fun joinSharedPlaylist(code: String): LocalPlaylist {
        _state.value.playlists.firstOrNull { it.shareCode == code }?.let { return it }
        val p = LocalPlaylist("local-${nowMillis()}", app.nocturne.i18n.t("Shared playlist"), emptyList(), nowMillis(), shareCode = code, version = 0)
        mutate { it.copy(playlists = listOf(p) + it.playlists) }
        return p
    }

    fun recordPlay(track: Track) {
        if (settings.value.pauseHistory) return
        mutate { d ->
            val now = nowMillis()
            val history = (listOf(HistoryEntry(track, now)) + d.history.filterNot { it.track.key == track.key }).take(2000)
            d.copy(
                history = history,
                playCounts = d.playCounts + (track.key to (d.playCounts[track.key] ?: 0) + 1),
                plays = (listOf(PlayEvent(track.key, now, track.artistNames, track.durationMs)) + d.plays).take(10_000),
            )
        }
    }

    fun clearHistory() = mutate { it.copy(history = emptyList(), playCounts = emptyMap(), plays = emptyList()) }

    fun trackFor(key: String): Track? = _state.value.history.firstOrNull { it.track.key == key }?.track

    fun addSearch(query: String) {
        if (settings.value.pauseSearchHistory || query.isBlank()) return
        mutate { d -> d.copy(searchHistory = (listOf(query) + d.searchHistory.filterNot { it == query }).take(30)) }
    }

    fun addSearchPlay(track: Track) {
        if (settings.value.pauseSearchHistory) return
        mutate { d -> d.copy(searchPlays = (listOf(track) + d.searchPlays.filterNot { it.key == track.key }).take(20)) }
    }

    fun removeSearchPlay(track: Track) = mutate { d -> d.copy(searchPlays = d.searchPlays.filterNot { it.key == track.key }) }

    fun removeSearch(query: String) = mutate { d -> d.copy(searchHistory = d.searchHistory - query) }

    fun clearSearchHistory() = mutate { it.copy(searchHistory = emptyList(), searchPlays = emptyList()) }

    fun mostPlayed(limit: Int = 20): List<Track> {
        val d = _state.value
        val byKey = d.history.associateBy { it.track.key }
        return d.playCounts.entries.sortedByDescending { it.value }.mapNotNull { byKey[it.key]?.track }.take(limit)
    }

    companion object {
        private const val FILE = "library.json"
    }
}
