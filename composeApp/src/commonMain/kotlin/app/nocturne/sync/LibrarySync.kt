package app.nocturne.sync

import app.nocturne.FileStore
import app.nocturne.data.HistoryEntry
import app.nocturne.data.Library
import app.nocturne.data.LibraryData
import app.nocturne.data.LocalPlaylist
import app.nocturne.data.PlayEvent
import app.nocturne.data.SettingsStore
import app.nocturne.model.CollectionRef
import app.nocturne.model.Track
import app.nocturne.nowMillis
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.abs

/** Library payload shared between devices: a full snapshot or a delta of recent changes. */
@Serializable
data class LibSync(
    val liked: List<Track> = emptyList(),
    val playlists: List<LocalPlaylist> = emptyList(),
    val saved: List<CollectionRef> = emptyList(),
    val history: List<HistoryEntry> = emptyList(),
    val plays: List<PlayEvent> = emptyList(),
    /** Per-item timestamps: > 0 added/changed at, < 0 removed at. Keys: L:track, S:collection, P:playlist, T:playlist|track. */
    val stamps: Map<String, Long> = emptyMap(),
    /** History was cleared at this time. */
    val cleared: Long = 0,
)

@Serializable
private data class SyncStamps(val stamps: Map<String, Long> = emptyMap(), val cleared: Long = 0)

/**
 * Tracks local library edits as timestamps (so removals propagate) and merges other devices' data:
 * union of items, newest timestamp wins per item.
 */
internal class LibrarySync(private val library: Library, private val settings: SettingsStore) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val stamps: MutableMap<String, Long>
    private var cleared: Long
    private var prev: LibraryData = library.state.value

    // Pending local changes for the next delta.
    private val pendKeys = mutableSetOf<String>()
    private val pendHistory = mutableListOf<HistoryEntry>()
    private val pendPlays = mutableListOf<PlayEvent>()
    private var pendClear = false

    init {
        val saved = FileStore.read(FILE)?.let { runCatching { json.decodeFromString(SyncStamps.serializer(), it) }.getOrNull() } ?: SyncStamps()
        val cutoff = nowMillis() - KEEP_MS
        stamps = saved.stamps.filterValues { abs(it) > cutoff }.toMutableMap()
        cleared = saved.cleared
    }

    private fun save() = FileStore.write(FILE, json.encodeToString(SyncStamps.serializer(), SyncStamps(stamps, cleared)))

    private fun allowed(key: String): Boolean {
        val s = settings.value
        return when (key[0]) {
            'L' -> s.syncLikes
            else -> s.syncPlaylists
        }
    }

    private fun alive(key: String) = (stamps[key] ?: 1L) > 0

    val hasPending: Boolean get() = pendClear || pendKeys.isNotEmpty() || pendHistory.isNotEmpty() || pendPlays.isNotEmpty()

    /** Records what changed locally since the last call; true when something sync-relevant changed. */
    fun observe(new: LibraryData): Boolean {
        val old = prev
        if (old === new) return false
        prev = new
        val now = nowMillis()
        var changed = false
        fun stamp(k: String, v: Long) { stamps[k] = v; pendKeys += k; changed = true }
        fun diff(a: List<String>, b: List<String>, prefix: String) {
            if (a == b) return
            val sa = a.toSet(); val sb = b.toSet()
            (sb - sa).forEach { stamp(prefix + it, now) }
            (sa - sb).forEach { stamp(prefix + it, -now) }
        }
        diff(old.liked.map { it.key }, new.liked.map { it.key }, "L:")
        diff(old.saved.map { it.key }, new.saved.map { it.key }, "S:")
        if (old.playlists != new.playlists) {
            val before = old.playlists.associateBy { it.id }
            val after = new.playlists.associateBy { it.id }
            after.forEach { (id, p) ->
                val o = before[id]
                if (o == null || o.name != p.name) stamp("P:$id", now)
                diff(o?.tracks.orEmpty().map { it.key }, p.tracks.map { it.key }, "T:$id|")
            }
            (before.keys - after.keys).forEach { stamp("P:$it", -now) }
        }
        if (old.history.isNotEmpty() && new.history.isEmpty()) {
            cleared = now
            pendHistory.clear(); pendPlays.clear()
            pendClear = true
            changed = true
        } else {
            val lastH = old.history.firstOrNull()?.playedAt ?: 0
            val lastP = old.plays.firstOrNull()?.t ?: 0
            new.history.takeWhile { it.playedAt > lastH }.takeIf { it.isNotEmpty() }?.let { pendHistory.addAll(0, it); changed = true }
            new.plays.takeWhile { it.t > lastP }.takeIf { it.isNotEmpty() }?.let { pendPlays.addAll(0, it); changed = true }
        }
        if (changed) save()
        return changed
    }

    /** Everything we share (respecting the sync toggles). */
    fun snapshot(): LibSync {
        observe(library.state.value)
        val d = library.state.value
        val s = settings.value
        return LibSync(
            liked = if (s.syncLikes) d.liked else emptyList(),
            playlists = if (s.syncPlaylists) d.playlists else emptyList(),
            saved = if (s.syncPlaylists) d.saved else emptyList(),
            history = if (s.syncHistory) d.history.take(300) else emptyList(),
            plays = if (s.syncHistory) d.plays.take(3000) else emptyList(),
            stamps = stamps.filterKeys { allowed(it) },
            cleared = if (s.syncHistory) cleared else 0,
        )
    }

    /** Local changes since the last delta, or null. */
    fun takeDelta(): LibSync? {
        observe(library.state.value)
        if (!hasPending) return null
        val d = library.state.value
        val s = settings.value
        val keys = pendKeys.filter { allowed(it) }
        val likedKeys = keys.filter { it.startsWith("L:") }.map { it.drop(2) }.toSet()
        val savedKeys = keys.filter { it.startsWith("S:") }.map { it.drop(2) }.toSet()
        val plIds = keys.filter { it.startsWith("P:") || it.startsWith("T:") }.map { it.drop(2).substringBefore('|') }.toSet()
        val out = LibSync(
            liked = d.liked.filter { it.key in likedKeys },
            playlists = d.playlists.filter { it.id in plIds },
            saved = d.saved.filter { it.key in savedKeys },
            history = if (s.syncHistory) pendHistory.take(200) else emptyList(),
            plays = if (s.syncHistory) pendPlays.take(500) else emptyList(),
            stamps = keys.associateWith { stamps[it] ?: 1L },
            cleared = if (s.syncHistory) cleared else 0,
        )
        pendKeys.clear(); pendHistory.clear(); pendPlays.clear()
        pendClear = false
        return out
    }

    /** Merges another device's data; true when the local library changed. */
    fun merge(r: LibSync): Boolean {
        observe(library.state.value) // stamp pending local edits first
        val s = settings.value
        val before = stamps.toMap()
        var stampsChanged = false
        r.stamps.forEach { (k, v) ->
            if (allowed(k) && abs(v) > abs(stamps[k] ?: 0L)) { stamps[k] = v; stampsChanged = true }
        }
        if (s.syncHistory && r.cleared > cleared) { cleared = r.cleared; stampsChanged = true }
        val cut = cleared
        val changed = library.applySync { d ->
            val blocked = d.notInterested.map { it.key }.toSet()
            var out = d
            if (s.syncLikes) {
                val have = d.liked.map { it.key }.toSet()
                val add = r.liked.filter { it.key !in have && it.key !in blocked && alive("L:" + it.key) }.distinctBy { it.key }
                val liked = (add + d.liked).filter { alive("L:" + it.key) }
                if (liked != d.liked) out = out.copy(liked = liked)
            }
            if (s.syncPlaylists) {
                val haveSaved = d.saved.map { it.key }.toSet()
                val saved = (r.saved.filter { it.key !in haveSaved && alive("S:" + it.key) }.distinctBy { it.key } + d.saved).filter { alive("S:" + it.key) }
                if (saved != d.saved) out = out.copy(saved = saved)
                val remote = r.playlists.associateBy { it.id }
                val local = d.playlists.map { it.id }.toSet()
                fun tracks(id: String, base: List<Track>, extra: List<Track>): List<Track> {
                    val keys = base.map { it.key }.toMutableSet()
                    return (base + extra.filter { keys.add(it.key) }).filter { alive("T:$id|" + it.key) }
                }
                val playlists = d.playlists.mapNotNull { p ->
                    if (!alive("P:" + p.id)) return@mapNotNull null
                    val o = remote[p.id] ?: return@mapNotNull p.copy(tracks = tracks(p.id, p.tracks, emptyList()))
                    val k = "P:" + p.id
                    val name = if (abs(r.stamps[k] ?: 0L) > abs(before[k] ?: 0L)) o.name else p.name
                    p.copy(name = name, tracks = tracks(p.id, p.tracks, o.tracks))
                } + r.playlists.filter { it.id !in local && alive("P:" + it.id) }.map { it.copy(tracks = tracks(it.id, it.tracks, emptyList())) }
                if (playlists != d.playlists) out = out.copy(playlists = playlists)
            }
            if (s.syncHistory) {
                val history = (d.history + r.history).filter { it.playedAt > cut }.sortedByDescending { it.playedAt }.distinctBy { it.track.key }.take(2000)
                if (history != d.history) out = out.copy(history = history)
                val plays = (d.plays + r.plays).filter { it.t > cut }.distinctBy { it.k to it.t }.sortedByDescending { it.t }.take(10_000)
                if (plays != d.plays) {
                    val counts = plays.groupingBy { it.k }.eachCount().filter { (k, n) -> n > (d.playCounts[k] ?: 0) }
                    out = out.copy(plays = plays, playCounts = if (counts.isEmpty()) d.playCounts else d.playCounts + counts)
                }
            }
            out
        }
        prev = library.state.value // merged data isn't a local edit
        if (stampsChanged) save()
        return changed
    }

    fun reset() {
        stamps.clear(); cleared = 0
        pendKeys.clear(); pendHistory.clear(); pendPlays.clear()
        pendClear = false
        FileStore.delete(FILE)
    }

    private companion object {
        const val FILE = "sync_stamps.json"
        const val KEEP_MS = 120L * 24 * 60 * 60_000
    }
}
