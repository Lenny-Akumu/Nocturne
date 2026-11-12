package app.nocturne.party

import app.nocturne.AppGraph
import app.nocturne.model.Track
import app.nocturne.nowMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.random.Random

/** One shared playlist as it travels: the newest edit wins. */
@Serializable
private data class CoSnapshot(val v: Long, val n: String, val t: List<Track>)

/**
 * Playlists edited together: every copy with the same code listens on a retained relay topic and
 * republishes its whole list whenever someone changes it here.
 */
class CoPlaylists(private val graph: AppGraph, private val scope: CoroutineScope) {
    private val json = Json { ignoreUnknownKeys = true }
    private val outgoing = MutableSharedFlow<Pair<String, CoSnapshot>>(extraBufferCapacity = 32)
    // what each code looked like last time we sent or received it, to tell our own edits apart
    private val known = mutableMapOf<String, String>()
    // our latest version of each list, resent on every (re)connect so nothing made offline is lost
    private val mine = mutableMapOf<String, CoSnapshot>()

    private fun topic(code: String) = "nocturne/playlist/${code.lowercase()}"
    private fun fingerprint(name: String, tracks: List<Track>) = name + "|" + tracks.joinToString(",") { it.key }

    fun start() {
        val party = graph.party
        // reconnect with the new set of topics whenever a playlist is shared, joined or deleted
        scope.launch {
            graph.library.state.map { s -> s.playlists.mapNotNull { it.shareCode }.toSet() }.distinctUntilChanged().collectLatest { codes ->
                if (codes.isEmpty()) return@collectLatest
                party.brokers.forEach { r ->
                    launch {
                        var fails = 0
                        while (true) {
                            try {
                                party.wsHttp.mqtt(r.url, onMessage = { t, b, _ -> receive(t, b) }) {
                                    codes.forEach { subscribe(topic(it)) }
                                    fails = 0
                                    mine.filterKeys { it in codes }.forEach { (code, snap) ->
                                        publish(topic(code), json.encodeToString(CoSnapshot.serializer(), snap).encodeToByteArray(), retain = true)
                                    }
                                    outgoing.collect { (code, snap) ->
                                        if (code in codes) publish(topic(code), json.encodeToString(CoSnapshot.serializer(), snap).encodeToByteArray(), retain = true)
                                    }
                                }
                            } catch (e: Throwable) {
                                currentCoroutineContext().ensureActive()
                            }
                            delay((2000L shl fails++.coerceAtMost(5)).coerceAtMost(60_000) + Random.nextLong(1000))
                        }
                    }
                }
            }
        }
        // our own edits go out
        scope.launch {
            graph.library.state.map { s -> s.playlists.filter { it.shareCode != null } }.distinctUntilChanged().collect { list ->
                list.forEach { p ->
                    val code = p.shareCode ?: return@forEach
                    val fp = fingerprint(p.name, p.tracks)
                    if (known[code] == fp) return@forEach
                    if (p.version == 0L && p.tracks.isEmpty()) return@forEach // just joined: wait for the real list
                    known[code] = fp
                    val v = maxOf(nowMillis(), p.version + 1)
                    graph.library.setPlaylistShare(p.id, code, v)
                    val snap = CoSnapshot(v, p.name, p.tracks.take(MAX))
                    mine[code] = snap
                    outgoing.tryEmit(code to snap)
                }
            }
        }
    }

    private fun receive(topicName: String, payload: ByteArray) {
        if (payload.isEmpty()) return
        val code = graph.library.state.value.playlists.firstOrNull { topic(it.shareCode ?: return@firstOrNull false) == topicName }?.shareCode ?: return
        val snap = runCatching { json.decodeFromString(CoSnapshot.serializer(), payload.decodeToString()) }.getOrNull() ?: return
        val local = graph.library.state.value.playlists.firstOrNull { it.shareCode == code } ?: return
        if (snap.v <= local.version) return
        known[code] = fingerprint(snap.n, snap.t)
        mine.remove(code)
        graph.library.applySharedPlaylist(code, snap.n, snap.t, snap.v)
    }

    /** Starts co-editing [playlistId]; returns the code to give friends. */
    fun share(playlistId: String): String? {
        val p = graph.library.playlist(playlistId) ?: return null
        p.shareCode?.let { return it }
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val code = (1..8).map { alphabet.random() }.joinToString("")
        graph.library.setPlaylistShare(playlistId, code, nowMillis())
        return code
    }

    fun join(code: String) = graph.library.joinSharedPlaylist(code.filter { it.isLetterOrDigit() }.uppercase())

    fun stopSharing(playlistId: String) = graph.library.setPlaylistShare(playlistId, null, 0)

    private companion object { const val MAX = 500 }
}
