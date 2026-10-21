package app.nocturne.party

import app.nocturne.AppGraph
import app.nocturne.model.Track
import app.nocturne.nowMillis
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.utils.io.readUTF8Line
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.abs

/** Host playback snapshot. [seq] grows on every real change so guests can drop stale / out-of-order states. */
@Serializable
data class PartyState(val track: Track? = null, val positionMs: Long = 0, val playing: Boolean = false, val sentAt: Long = 0, val seq: Long = 0)

interface PartyServer {
    val port: Int
    fun stop()
}

expect fun startPartyServer(state: () -> String): PartyServer

expect fun lanAddresses(): List<String>

/** Join links: nocturne://join?code=XXXX or the https join page. Platforms call [handle]; [Party] joins and opens the sheet. */
object PartyLinks {
    const val PAGE = "https://lenny-akumu.github.io/Nocturne/join.html"
    val pending = MutableStateFlow<String?>(null)

    fun joinUrl(code: String) = "$PAGE?code=$code"

    fun codeOf(url: String): String? {
        val u = url.trim()
        if (!u.startsWith("nocturne://join", true) && !u.startsWith(PAGE, true)) return null
        val raw = Regex("(?:[?&]code=|join/)([A-Za-z0-9-]+)", RegexOption.IGNORE_CASE).find(u)?.groupValues?.get(1) ?: return null
        return raw.filter { it.isLetterOrDigit() }.uppercase().takeIf { it.length in 8..16 }
    }

    fun handle(url: String): Boolean {
        val code = codeOf(url) ?: return false
        pending.value = code
        return true
    }
}

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
    private val _showInDiscord = MutableStateFlow(false)
    val showInDiscord: StateFlow<Boolean> = _showInDiscord.asStateFlow()
    /** True when a join link asks the UI to open the Listen-together sheet. */
    val sheet = MutableStateFlow(false)
    private var publishJob: Job? = null

    // Host side
    private var seq = 0L
    private var lanState = PartyState()

    // Guest side
    private class Remote(val state: PartyState, val receivedAt: Long)
    private var latest: Remote? = null
    private var loadAttemptAt = 0L
    private var lastSeekAt = 0L

    init {
        scope.launch {
            PartyLinks.pending.collect { c ->
                if (c == null) return@collect
                PartyLinks.pending.value = null
                if (_code.value != c && _joined.value != c) {
                    stopHosting()
                    join(c)
                }
                sheet.value = true
            }
        }
    }

    fun setShowInDiscord(on: Boolean) { _showInDiscord.value = on }

    /** Session code to advertise in Discord, or null. */
    val discordInvite: String? get() = _code.value?.takeIf { _showInDiscord.value }

    private fun topic(code: String) = "https://ntfy.sh/nocturne-party-${code.lowercase()}"

    private fun snapshot(): PartyState {
        val p = graph.player.state.value
        return PartyState(p.current, p.positionMs, p.isPlaying, nowMillis(), seq)
    }

    fun host() {
        if (_code.value != null) return
        leave()
        seq = 1
        lanState = snapshot()
        val s = runCatching { startPartyServer { json.encodeToString(PartyState.serializer(), lanState) } }.getOrNull()
        server = s
        _hosting.value = s?.let { srv -> lanAddresses().map { "$it:${srv.port}" } }.orEmpty().ifEmpty { listOf("") }
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val code = (1..8).map { alphabet.random() }.joinToString("")
        _code.value = code
        publishJob = scope.launch {
            var last: PartyState? = null
            var lastSent = 0L
            while (isActive) {
                var now = snapshot()
                val prev = last
                val drift = if (prev != null && prev.playing) abs(now.positionMs - (prev.positionMs + (now.sentAt - prev.sentAt))) else 0L
                val changed = prev == null || prev.track?.key != now.track?.key || prev.playing != now.playing || drift > 2500 ||
                    (!now.playing && abs(now.positionMs - prev.positionMs) > 1500)
                if (changed && prev != null) seq++
                now = now.copy(seq = seq)
                lanState = now
                if (changed || now.sentAt - lastSent > 60_000) {
                    // Track as sent even on failure so a dead relay isn't hammered every tick.
                    last = now
                    lastSent = now.sentAt
                    runCatching {
                        graph.http.post(topic(code)) {
                            setBody(json.encodeToString(PartyState.serializer(), now))
                            timeout { requestTimeoutMillis = 8000 }
                        }
                        _status.value = null
                    }.onFailure {
                        _status.value = "Online relay unavailable — friends on your Wi-Fi can still join"
                        lastSent = now.sentAt - 20_000 // retry in ~5s
                    }
                }
                delay(500)
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
        PartyLinks.codeOf(input)?.let { return joinOnline(it) }
        if (':' !in input) {
            joinOnline(input.uppercase().filter { it.isLetterOrDigit() })
            return
        }
        val addr = input.removePrefix("http://").substringBefore('/')
        _joined.value = addr
        _status.value = null
        joinJob = scope.launch {
            launch { syncLoop() }
            while (isActive) {
                runCatching {
                    val body = graph.http.get("http://$addr/state") { timeout { requestTimeoutMillis = 4000; connectTimeoutMillis = 3000 } }.bodyAsText()
                    offer(json.decodeFromString(PartyState.serializer(), body))
                    _status.value = null
                }.onFailure { _status.value = "Can't reach $addr" }
                delay(1000)
            }
        }
    }

    // Streams the ntfy topic (one long request instead of polling, which ntfy.sh rate-limits); starts from the latest retained state.
    private fun joinOnline(code: String) {
        _joined.value = code
        _status.value = null
        joinJob = scope.launch {
            launch { syncLoop() }
            var since = "latest"
            while (isActive) {
                var opened = false
                runCatching {
                    graph.http.prepareGet("${topic(code)}/json?since=$since") {
                        timeout { requestTimeoutMillis = 600_000L; socketTimeoutMillis = 90_000L }
                    }.execute { resp ->
                        if (!resp.status.isSuccess()) {
                            if (since == "latest") since = "10m" // older ntfy servers lack since=latest
                            error("HTTP ${resp.status.value}")
                        }
                        opened = true
                        if (latest == null) _status.value = "Waiting for the host…" else _status.value = null
                        val ch = resp.bodyAsChannel()
                        while (true) {
                            val line = ch.readUTF8Line() ?: break
                            if (line.isBlank()) continue
                            val obj = runCatching { json.parseToJsonElement(line) as? JsonObject }.getOrNull() ?: continue
                            fun str(k: String) = (obj[k] as? JsonPrimitive)?.content
                            if (str("event") != "message") continue
                            str("id")?.let { since = it }
                            val msg = str("message") ?: continue
                            runCatching { json.decodeFromString(PartyState.serializer(), msg) }.getOrNull()?.let {
                                offer(it)
                                _status.value = null
                            }
                        }
                    }
                }.onFailure { if (it is kotlinx.coroutines.CancellationException && !isActive) throw it }
                if (!opened) _status.value = "Connection problem — retrying…"
                delay(if (opened) 500 else 3000)
            }
        }
    }

    private fun offer(s: PartyState) {
        val cur = latest?.state
        // Drop stale or out-of-order states (ntfy replays, slow LAN responses).
        if (cur != null && (s.seq < cur.seq || (s.seq == cur.seq && s.sentAt <= cur.sentAt))) return
        val r = Remote(s, nowMillis())
        latest = r
        apply(r)
    }

    private suspend fun syncLoop() {
        while (true) {
            latest?.let { apply(it) }
            delay(1000)
        }
    }

    // Host position now: sentAt-based when clocks look sane, else time since we received it.
    private fun expected(r: Remote): Long {
        val s = r.state
        if (!s.playing) return s.positionMs
        val since = nowMillis() - r.receivedAt
        val transit = r.receivedAt - s.sentAt
        return s.positionMs + since + if (s.sentAt > 0 && transit in -1_000..40_000) transit.coerceAtLeast(0) else 300L
    }

    private fun apply(r: Remote) {
        val remote = r.state
        val p = graph.player
        val local = p.state.value
        val now = nowMillis()
        val track = remote.track ?: run { if (local.isPlaying) p.pause(); return }
        val stuck = local.current?.key == track.key && !p.isLoading && !p.isLoaded && now - loadAttemptAt > 8000
        if (local.current?.key != track.key || stuck) {
            // Always load the host's track by key; the player cancels any load still in flight for the previous one.
            loadAttemptAt = now
            lastSeekAt = now
            p.playSynced(track, app.nocturne.i18n.t("Listening together"), remote.playing) {
                latest?.takeIf { it.state.track?.key == track.key }?.let { expected(it) + START_LEAD } ?: 0L
            }
            return
        }
        if (p.isLoading) {
            // Queued until the stream is ready.
            if (remote.playing) p.resume() else p.pause()
            return
        }
        if (!p.isLoaded) return
        if (remote.playing && !local.isPlaying && !local.isBuffering) p.resume()
        else if (!remote.playing && local.isPlaying) p.pause()
        if (local.isBuffering) return
        val target = expected(r)
        if (abs(target - local.positionMs) > 2000 && now - lastSeekAt > 4000) {
            lastSeekAt = now
            p.seekTo((target + if (remote.playing) SEEK_LEAD else 0L).coerceAtLeast(0))
        }
    }

    fun leave() {
        joinJob?.cancel()
        joinJob = null
        latest = null
        _joined.value = null
        if (_code.value == null) _status.value = null
    }

    private companion object {
        const val START_LEAD = 700L
        const val SEEK_LEAD = 250L
    }
}
