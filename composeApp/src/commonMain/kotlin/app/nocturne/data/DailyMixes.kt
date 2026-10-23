package app.nocturne.data

import app.nocturne.AppGraph
import app.nocturne.FileStore
import app.nocturne.model.Track
import app.nocturne.nowMillis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class DailyMix(val title: String, val artists: List<String>, val tracks: List<Track>)

@Serializable
private data class DailyCache(val day: Long, val mixes: List<DailyMix>)

// Spotify-style Daily Mixes: one per favourite artist cluster, your tracks mixed with radio picks, rebuilt once a day.
class DailyMixes(private val graph: AppGraph) {
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()
    private var day = -1L
    private val _state = MutableStateFlow(load())
    val state: StateFlow<List<DailyMix>> = _state.asStateFlow()

    private fun today() = nowMillis() / 86_400_000L

    private fun load(): List<DailyMix> {
        val c = FileStore.read(FILE)?.let { runCatching { json.decodeFromString(DailyCache.serializer(), it) }.getOrNull() } ?: return emptyList()
        day = c.day
        return c.mixes
    }

    suspend fun refresh(force: Boolean = false) = lock.withLock {
        if (!force && day == today() && _state.value.isNotEmpty()) return@withLock
        val mixes = runCatching { build() }.getOrNull() ?: return@withLock
        if (mixes.isEmpty()) return@withLock
        day = today()
        _state.value = mixes
        FileStore.write(FILE, json.encodeToString(DailyCache.serializer(), DailyCache(day, mixes)))
    }

    private suspend fun build(): List<DailyMix> {
        val lib = graph.library.state.value
        val since = nowMillis() - 60L * 86_400_000L
        val known = (lib.history.map { it.track } + lib.liked).distinctBy { it.key }
        fun lead(t: Track) = t.artists.firstOrNull()?.name ?: t.artistNames
        // artist weight: recent plays + likes count double
        val weight = HashMap<String, Int>()
        val byKey = known.associateBy { it.key }
        lib.plays.filter { it.t >= since }.forEach { p -> byKey[p.k]?.let { weight[lead(it)] = (weight[lead(it)] ?: 0) + 1 } }
        lib.liked.forEach { weight[lead(it)] = (weight[lead(it)] ?: 0) + 2 }
        val top = weight.entries.sortedByDescending { it.value }.map { it.key }.filter { it.isNotBlank() }.take(MIXES)
        val used = HashSet<String>()
        return top.mapIndexedNotNull { i, artist ->
            val own = known.filter { lead(it) == artist }.shuffled().take(10)
            val seed = own.firstOrNull() ?: return@mapIndexedNotNull null
            val radio = runCatching { graph.provider(seed.source).related(seed) }.getOrDefault(emptyList())
                .filter { it.key !in used && !graph.library.isBlocked(it) }
                .take(30)
            val tracks = interleave(own.filter { it.key !in used }, radio).distinctBy { it.key }.take(40)
            if (tracks.size < 8) return@mapIndexedNotNull null
            tracks.forEach { used += it.key }
            DailyMix("Daily Mix ${i + 1}", tracks.map { lead(it) }.distinct().take(3), tracks)
        }.mapIndexed { i, m -> m.copy(title = "Daily Mix ${i + 1}") }
    }

    // roughly one familiar track for every two new ones
    private fun interleave(own: List<Track>, fresh: List<Track>): List<Track> {
        val out = mutableListOf<Track>()
        var a = 0
        var b = 0
        while (a < own.size || b < fresh.size) {
            if (a < own.size) out += own[a++]
            repeat(2) { if (b < fresh.size) out += fresh[b++] }
        }
        return out
    }

    private companion object {
        const val FILE = "daily_mixes.json"
        const val MIXES = 6
    }
}
