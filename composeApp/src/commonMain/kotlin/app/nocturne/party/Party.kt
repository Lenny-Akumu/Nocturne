package app.nocturne.party

import app.nocturne.AppGraph
import app.nocturne.model.Track
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
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
data class PartyState(val track: Track? = null, val positionMs: Long = 0, val playing: Boolean = false, val sentAt: Long = 0)

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
    private val _code = MutableStateFlow<String?>(null)
    val code: StateFlow<String?> = _code.asStateFlow()
    private var publishJob: Job? = null

    private fun topic(code: String) = "https://ntfy.sh/nocturne-party-${code.lowercase()}"

    private fun snapshot(): PartyState {
        val p = graph.player.state.value
        return PartyState(p.current, p.positionMs, p.isPlaying, app.nocturne.nowMillis())
    }

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
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val code = (1..8).map { alphabet.random() }.joinToString("")
        _code.value = code
        publishJob = scope.launch {
            var last: PartyState? = null
            var lastSent = 0L
            while (isActive) {
                val now = snapshot()
                val prev = last
                val drift = if (prev != null && prev.playing) abs(now.positionMs - (prev.positionMs + (now.sentAt - prev.sentAt))) else 0L
                val changed = prev == null || prev.track?.key != now.track?.key || prev.playing != now.playing || drift > 2500 ||
                    (!now.playing && abs(now.positionMs - prev.positionMs) > 1500)
                if (changed || now.sentAt - lastSent > 25_000) {
                    runCatching {
                        graph.http.post(topic(code)) {
                            setBody(json.encodeToString(PartyState.serializer(), now))
                            timeout { requestTimeoutMillis = 8000 }
                        }
                        last = now
                        lastSent = now.sentAt
                        _status.value = null
                    }.onFailure { _status.value = "Online relay unavailable — friends on your Wi-Fi can still join" }
                }
                delay(1000)
            }
        }
    }

    fun stopHosting() {
        publishJob?.cancel()
        publishJob = null
        _code.value = null
        server?.stop()
        server = null
        _hosting.value = emptyList()
    }

    fun join(address: String) {
        leave()
        val input = address.trim()
        if (':' !in input) {
            joinOnline(input.uppercase().filter { it.isLetterOrDigit() })
            return
        }
        val addr = input.removePrefix("http://").trimEnd('/')
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

    private fun joinOnline(code: String) {
        _joined.value = code
        _status.value = null
        joinJob = scope.launch {
            var lastKey: String? = null
            var latest: PartyState? = null
            var since = "10m"
            while (isActive) {
                runCatching {
                    val lines = graph.http.get("${topic(code)}/json?poll=1&since=$since") {
                        timeout { requestTimeoutMillis = 10_000 }
                    }.bodyAsText().lines().filter { it.isNotBlank() }
                    for (line in lines) {
                        val obj = json.parseToJsonElement(line) as? kotlinx.serialization.json.JsonObject ?: continue
                        val id = (obj["id"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                        val msg = (obj["message"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: continue
                        runCatching { latest = json.decodeFromString(PartyState.serializer(), msg) }
                        if (id != null) since = id
                    }
                    val remote = latest ?: run { _status.value = "Waiting for the host…"; return@runCatching }
                    val elapsed = (app.nocturne.nowMillis() - remote.sentAt).coerceIn(0, 60_000)
                    val target = remote.positionMs + if (remote.playing) elapsed else 0
                    val track = remote.track
                    val local = graph.player.state.value
                    if (track != null && track.key != local.current?.key) {
                        if (track.key != lastKey) {
                            graph.player.play(listOf(track), 0, "Listening together")
                            lastKey = track.key
                            delay(1500)
                            graph.player.seekTo(target + 1500)
                        }
                    } else if (track != null) {
                        if (remote.playing != local.isPlaying) { if (remote.playing) graph.player.resume() else graph.player.pause() }
                        if (abs(target - local.positionMs) > 3000) graph.player.seekTo(target + 300)
                    }
                    _status.value = null
                }.onFailure { _status.value = "Connection problem — retrying…" }
                delay(2000)
            }
        }
    }

    fun leave() {
        joinJob?.cancel()
        joinJob = null
        _joined.value = null
    }
}
