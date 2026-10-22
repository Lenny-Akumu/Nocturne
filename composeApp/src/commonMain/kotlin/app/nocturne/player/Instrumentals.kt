package app.nocturne.player

import app.nocturne.FileStore
import app.nocturne.model.MediaEntry
import app.nocturne.model.SearchFilter
import app.nocturne.model.Source
import app.nocturne.model.Track
import app.nocturne.nowMillis
import app.nocturne.source.MusicProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.math.abs

/**
 * Finds an official instrumental of a track (vocal remover's first choice) through search; results, misses included,
 * are cached per track key in memory and in a small file. Call from the main thread.
 */
class Instrumentals(private val providers: Map<Source, MusicProvider>, private val scope: CoroutineScope) {
    @Serializable
    private class Hit(val track: Track? = null, val at: Long = 0)

    private val json = Json { ignoreUnknownKeys = true }
    private val ser = MapSerializer(String.serializer(), Hit.serializer())
    private val cache: MutableMap<String, Hit> by lazy {
        FileStore.read(FILE)?.let { runCatching { json.decodeFromString(ser, it) }.getOrNull() }.orEmpty().toMutableMap()
    }
    private val inFlight = mutableMapOf<String, Deferred<Track?>>()

    /** The instrumental for [track], or null; a lookup keeps running (and is cached) even if the caller stops waiting. */
    suspend fun find(track: Track): Track? = lookup(track)?.await()

    fun prefetch(track: Track) { lookup(track) }

    private fun lookup(track: Track): Deferred<Track?>? {
        if (MARKER.containsMatchIn(track.title.lowercase())) return null // already an instrumental
        val now = nowMillis()
        cache[track.key]?.let { h ->
            if (now - h.at < (if (h.track != null) HIT_TTL else MISS_TTL)) return scope.async { h.track }
        }
        inFlight[track.key]?.takeIf { it.isActive }?.let { return it }
        return scope.async {
            val found = runCatching { search(track) }.getOrElse { return@async null } // errors aren't cached
            cache[track.key] = Hit(found, nowMillis())
            persist()
            found
        }.also { d -> inFlight[track.key] = d; d.invokeOnCompletion { if (inFlight[track.key] === d) inFlight.remove(track.key) } }
    }

    private suspend fun search(track: Track): Track? {
        val title = clean(track.title)
        val artist = track.artists.firstOrNull()?.name.orEmpty()
        if (title.isBlank()) return null
        val yt = providers[Source.YOUTUBE_MUSIC]
        val own = providers[track.source]?.takeIf { track.source != Source.YOUTUBE_MUSIC && track.source != Source.LOCAL }
        val plan = buildList {
            if (yt != null) QUERIES.forEach { add(yt to "$title $artist $it") }
            if (own != null) add(own to "$title $artist instrumental")
        }
        for ((p, q) in plan) {
            val songs = withContext(Dispatchers.Default) { p.search(q.trim(), SearchFilter.SONGS) }
                .mapNotNull { (it as? MediaEntry.Song)?.track }
            pick(track, title, songs)?.let { return it }
        }
        return null
    }

    // Title holds the original title plus an instrumental marker, same artist, length within ±8% (or ±10 s).
    private fun pick(orig: Track, title: String, songs: List<Track>): Track? {
        val names = orig.artists.map { it.name.lowercase().trim() }.filter { it.isNotEmpty() }
        val exact = setOf("$title (instrumental)", "$title - instrumental", "$title (inst.)", "$title (inst)")
        return songs.filter { c ->
            val t = c.title.lowercase()
            val artists = c.artistNames.lowercase()
            c.key != orig.key && title in squash(t) && MARKER.containsMatchIn(t) &&
                (names.isEmpty() || names.any { it in artists || (artists.isNotEmpty() && artists in it) }) &&
                (orig.durationMs <= 0 || c.durationMs > 0 && abs(c.durationMs - orig.durationMs) <= maxOf(orig.durationMs * 8 / 100, 10_000L))
        }.minByOrNull { c ->
            (if (squash(c.title.lowercase()) in exact) 0L else 1_000_000L) + abs(c.durationMs - orig.durationMs) / 100
        }
    }

    private fun persist() {
        if (cache.size > MAX) cache.entries.sortedBy { it.value.at }.take(cache.size - MAX).map { it.key }.forEach(cache::remove)
        runCatching { FileStore.write(FILE, json.encodeToString(ser, cache)) }
    }

    companion object {
        private const val FILE = "instrumentals.json"
        private const val MAX = 400
        private const val HIT_TTL = 60L * 24 * 60 * 60 * 1000
        private const val MISS_TTL = 7L * 24 * 60 * 60 * 1000
        private val QUERIES = listOf("instrumental", "inst", "karaoke", "minus", "backing track")
        private val MARKER = Regex("instrumental|\\binst\\b|karaoke|\\bminus\\b|backing track|off[ -]?vocal|без вокалу|мінус")
        private val BRACKETS = Regex("\\s*[(\\[][^)\\]]*[)\\]]")
        private val FEAT = Regex("\\s+(feat\\.?|ft\\.?|featuring)\\s.*$")

        private fun squash(s: String) = s.replace(Regex("\\s+"), " ").trim()

        /** Lowercase title without brackets and "feat." tails. */
        fun clean(title: String) = squash(title.lowercase().replace(BRACKETS, "").replace(FEAT, ""))
    }
}
