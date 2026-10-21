package app.nocturne.data

import app.nocturne.i18n.t
import app.nocturne.model.CollectionRef
import app.nocturne.model.MediaEntry
import app.nocturne.model.SearchFilter
import app.nocturne.model.Source
import app.nocturne.model.Track
import app.nocturne.nowMillis
import app.nocturne.source.MusicProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RemoteLists(
    val lists: Map<Source, List<CollectionRef>> = emptyMap(),
    val loading: Set<Source> = emptySet(),
    val errors: Map<Source, String> = emptyMap(),
)

private class NoMatchException(message: String) : Exception(message)

// Editable playlists of connected accounts (cached) and add/remove/create across services.
class RemotePlaylists(
    private val scope: CoroutineScope,
    private val accounts: () -> List<MusicProvider>,
    private val provider: (Source) -> MusicProvider,
    private val notify: (String) -> Unit,
) {
    private val _state = MutableStateFlow(RemoteLists())
    val state: StateFlow<RemoteLists> = _state.asStateFlow()
    // Bumped per playlist key after a remote edit so open pages reload.
    private val _revisions = MutableStateFlow<Map<String, Int>>(emptyMap())
    val revisions: StateFlow<Map<String, Int>> = _revisions.asStateFlow()
    private var loadedAt = 0L
    private val matches = mutableMapOf<String, Track>()

    fun services(): List<Source> = accounts().map { it.source }

    fun invalidate() { loadedAt = 0L }

    fun refresh(force: Boolean = false) {
        val ps = accounts()
        val signed = ps.map { it.source }.toSet()
        _state.update { s -> s.copy(lists = s.lists.filterKeys { it in signed }, errors = s.errors.filterKeys { it in signed }) }
        if (!force && nowMillis() - loadedAt < 10 * 60_000 && signed.all { it in _state.value.lists }) return
        loadedAt = nowMillis()
        ps.forEach { p ->
            if (p.source in _state.value.loading) return@forEach
            _state.update { it.copy(loading = it.loading + p.source) }
            scope.launch {
                val r = runCatching { p.editablePlaylists() }
                _state.update { s ->
                    s.copy(
                        lists = r.getOrNull()?.let { s.lists + (p.source to it) } ?: s.lists,
                        errors = r.exceptionOrNull()?.let { s.errors + (p.source to (it.message ?: t("Failed to load"))) } ?: (s.errors - p.source),
                        loading = s.loading - p.source,
                    )
                }
            }
        }
    }

        private fun bump(ref: CollectionRef) = _revisions.update { it + (ref.key to (it[ref.key] ?: 0) + 1) }

    private fun norm(s: String) = s.lowercase()
        .replace(Regex("\\(.*?\\)|\\[.*?]|feat\\..*|ft\\..*"), "")
        .replace(Regex("[^\\p{L}\\p{N} ]"), "")
        .trim()

    // Same song on another service: title + first artist search, scored by title/artist/duration.
    private suspend fun match(target: Source, track: Track): Track? {
        if (track.source == target) return track
        val cacheKey = "${target.name}|${track.key}"
        matches[cacheKey]?.let { return it }
        val artist = track.artists.firstOrNull()?.name.orEmpty()
        val found = runCatching { provider(target).search("${track.title} $artist".trim(), SearchFilter.SONGS) }.getOrDefault(emptyList())
            .filterIsInstance<MediaEntry.Song>().map { it.track }.take(10)
        val nt = norm(track.title)
        val na = norm(artist)
        val best = found.map { c ->
            var score = 0.0
            val ct = norm(c.title)
            if (ct == nt) score += 4 else if (ct.isNotEmpty() && (ct.contains(nt) || nt.contains(ct))) score += 2
            if (na.isNotEmpty() && c.artists.any { norm(it.name).let { a -> a.isNotEmpty() && (na.contains(a) || a.contains(na)) } }) score += 3
            if (track.durationMs > 0 && c.durationMs > 0) {
                val diff = kotlin.math.abs(track.durationMs - c.durationMs) / 1000.0
                score += when {
                    diff < 3 -> 3.0
                    diff < 10 -> 1.5
                    diff > 60 -> -3.0
                    else -> 0.0
                }
            }
            c to score
        }.filter { it.second >= 4 }.maxByOrNull { it.second }?.first
        if (best != null) matches[cacheKey] = best
        return best
    }

    private suspend fun matchAll(target: Source, tracks: List<Track>): List<Track> {
        val out = tracks.chunked(4).flatMap { chunk ->
            coroutineScope { chunk.map { async { match(target, it) } }.awaitAll() }
        }.filterNotNull().distinctBy { it.key }
        if (out.isEmpty() && tracks.isNotEmpty()) throw NoMatchException(
            if (tracks.size == 1) t("Couldn't find “%s” on %s", tracks[0].title, target.label)
            else t("Couldn't find these songs on %s", target.label)
        )
        return out
    }

    fun add(ref: CollectionRef, tracks: List<Track>) = scope.launch {
        notify(t("Adding to %s…", ref.title))
        runCatching {
            val matched = matchAll(ref.source, tracks)
            provider(ref.source).addToPlaylist(ref.id, matched)
            bump(ref)
            matched.size
        }.onSuccess { n ->
            notify(if (n < tracks.size) t("Added %s of %s songs to %s", n, tracks.size, ref.title) else t("Added to %s", ref.title))
        }.onFailure { e ->
            notify(if (e is NoMatchException) e.message.orEmpty() else t("Couldn't add to %s: %s", ref.title, e.message.orEmpty()))
        }
    }

    fun remove(ref: CollectionRef, track: Track) = scope.launch {
        runCatching { provider(ref.source).removeFromPlaylist(ref.id, track); bump(ref) }
            .onSuccess { notify(t("Removed from %s", ref.title)) }
            .onFailure { notify(t("Couldn't remove: %s", it.message.orEmpty())) }
    }

    fun create(source: Source, name: String, tracks: List<Track>) = scope.launch {
        val title = name.trim().ifBlank { "My playlist" }
        runCatching {
            val matched = matchAll(source, tracks)
            val ref = provider(source).createPlaylist(title, matched)
            _state.update { s -> s.copy(lists = s.lists + (source to (listOf(ref) + s.lists[source].orEmpty()).distinctBy { it.id })) }
            ref to matched.size
        }.onSuccess { (_, n) ->
            notify(if (n < tracks.size) t("Added %s of %s songs to %s", n, tracks.size, title) else t("Created “%s” on %s", title, source.label))
        }.onFailure { e ->
            notify(if (e is NoMatchException) e.message.orEmpty() else t("Couldn't create playlist: %s", e.message.orEmpty()))
        }
    }
}
