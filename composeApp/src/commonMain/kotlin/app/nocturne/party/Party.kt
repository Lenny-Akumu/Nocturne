package app.nocturne.party

import app.nocturne.AppGraph
import app.nocturne.model.Track
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.abs

@Serializable
data class PartyState(val track: Track? = null, val positionMs: Long = 0, val playing: Boolean = false)

interface PartyServer {
    val port: Int
    fun stop()
}

expect fun startPartyServer(state: () -> String): PartyServer

expect fun lanAddresses(): List<String>

class Party(private val graph: AppGraph, private val scope: CoroutineScope) {
    private val json = Json { ignoreUnknownKeys = true }
    private var server: PartyServer? = null
    private var joinJob: Job? = null
    private val _hosting = MutableStateFlow<List<String>>(emptyList())
    val hosting: StateFlow<List<String>> = _hosting.asStateFlow()
    private val _joined = MutableStateFlow<String?>(null)
    val joined: StateFlow<String?> = _joined.asStateFlow()
    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    fun host() {
        if (server != null) return
        val s = runCatching {
            startPartyServer {
                val p = graph.player.state.value
                json.encodeToString(PartyState.serializer(), PartyState(p.current, p.positionMs, p.isPlaying))
            }
        }.getOrElse { _status.value = it.message; return }
        server = s
        _hosting.value = lanAddresses().map { "$it:${s.port}" }
    }

    fun stopHosting() {
        server?.stop()
        server = null
        _hosting.value = emptyList()
    }

    fun join(address: String) {
        leave()
        val addr = address.trim().removePrefix("http://").trimEnd('/')
        _joined.value = addr
        _status.value = null
        joinJob = scope.launch {
            var lastKey: String? = null
            while (isActive) {
                runCatching {
                    val body = graph.http.get("http://$addr/state") { timeout { requestTimeoutMillis = 4000; connectTimeoutMillis = 3000 } }.bodyAsText()
                    val remote = json.decodeFromString(PartyState.serializer(), body)
                    val track = remote.track
                    val local = graph.player.state.value
                    if (track != null && track.key != local.current?.key) {
                        if (track.key != lastKey) {
                            graph.player.play(listOf(track), 0, "Listening together")
                            lastKey = track.key
                            delay(1500)
                            graph.player.seekTo(remote.positionMs + 1500)
                        }
                    } else if (track != null) {
                        if (remote.playing != local.isPlaying) { if (remote.playing) graph.player.resume() else graph.player.pause() }
                        if (abs(remote.positionMs - local.positionMs) > 3000) graph.player.seekTo(remote.positionMs + 300)
                    }
                    _status.value = null
                }.onFailure { _status.value = "Can't reach $addr" }
                delay(1500)
            }
        }
    }

    fun leave() {
        joinJob?.cancel()
        joinJob = null
        _joined.value = null
    }
}
