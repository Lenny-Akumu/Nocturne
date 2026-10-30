package app.nocturne.party

import app.nocturne.AppGraph
import app.nocturne.i18n.t
import app.nocturne.model.ArtistRef
import app.nocturne.model.Source
import app.nocturne.model.Track
import app.nocturne.nowMillis
import io.ktor.client.plugins.timeout
import io.ktor.client.plugins.websocket.WebSockets
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
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.abs
import kotlin.random.Random

/** Chat line or emoji reaction in a listen-together session. */
@Serializable
data class ChatMsg(
    val id: String,
    val from: String,
    val text: String,
    val at: Long,
    val reaction: Boolean = false,
    // presence heartbeat: [sid] is stable per app run, [host] marks who leads the session
    val presence: Boolean = false,
    val sid: String = "",
    val host: Boolean = false,
)

data class PartyMember(val name: String, val host: Boolean, val me: Boolean)

/** Host's version (speed), EQ and effects; guests play with these while in the session. */
@Serializable
data class PartyAudio(
    val speed: Float = 1f, val eq: Boolean = false, val gains: List<Float> = emptyList(),
    val normalize: Boolean = false, val spatial: Boolean = false, val eightD: Boolean = false, val vocal: Boolean = false,
)

/** Host playback snapshot. [seq] grows on every real change so guests can drop stale / out-of-order states. */
@Serializable
data class PartyState(
    val track: Track? = null, val positionMs: Long = 0, val playing: Boolean = false, val sentAt: Long = 0, val seq: Long = 0,
    val audio: PartyAudio? = null,
)

/** Compact online payload: just what a guest needs to load and sync the track (no account data). */
@Serializable
private data class Wire(
    val s: Source? = null, val i: String? = null, val t: String = "", val a: List<String> = emptyList(),
    val w: String? = null, val u: String? = null, val d: Long = 0,
    val p: Long = 0, val g: Boolean = false, val q: Long = 0, val at: Long = 0, val x: PartyAudio? = null,
) {
    fun toState() = PartyState(
        if (s != null && i != null) Track(i, s, t, a.map { ArtistRef(it) }, durationMs = d, artworkUrl = w, url = u) else null,
        p, g, at, q, x,
    )

    companion object {
        fun of(st: PartyState) = st.track.let { tr ->
            Wire(tr?.source, tr?.id, tr?.title.orEmpty(), tr?.artists?.map { it.name }.orEmpty(), tr?.artworkUrl, tr?.url, tr?.durationMs ?: 0, st.positionMs, st.playing, st.seq, st.sentAt, st.audio)
        }
    }
}

/** Online relays, tried in this fixed order by host and guest alike. ntfy.sh is only the last fallback. */
internal enum class Relay(val url: String) {
    EMQX("wss://broker.emqx.io:8084/mqtt"),
    HIVEMQ("wss://broker.hivemq.com:8884/mqtt"),
    MOSQUITTO("wss://test.mosquitto.org:8081"),
    NTFY("https://ntfy.sh/"),
}

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
        if (app.nocturne.sync.DeviceLinks.handle(url)) return true
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
    /** Per-relay state of the current session, one line each (copyable). */
    private val _details = MutableStateFlow("")
    val details: StateFlow<String> = _details.asStateFlow()
    private val _checks = MutableStateFlow<List<RelayCheck>?>(null)
    /** Last "Check connection" result. */
    val checks: StateFlow<List<RelayCheck>?> = _checks.asStateFlow()
    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()
    private val _code = MutableStateFlow<String?>(null)
    val code: StateFlow<String?> = _code.asStateFlow()
    private val _showInDiscord = MutableStateFlow(false)
    val showInDiscord: StateFlow<Boolean> = _showInDiscord.asStateFlow()
    /** True when a join link asks the UI to open the Listen-together sheet. */
    val sheet = MutableStateFlow(false)
    private var publishJob: Job? = null
    private var relayJob: Job? = null
    private var hostOut: MutableStateFlow<PartyState?>? = null
    private val wsHttp by lazy { graph.http.config { install(WebSockets) } }

    // Relay health for the current session; [relayGen] fences off late callbacks from a previous one.
    private var relayGen = 0
    private val relayOk = mutableSetOf<Relay>()
    private val relayErr = linkedMapOf<Relay, String>()

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

    private fun ntfyTopic(code: String) = "https://ntfy.sh/nocturne-party-${code.lowercase()}"
    private fun mqttTopic(code: String) = "nocturne/party/${code.lowercase()}"
    private val brokers get() = Relay.entries.filter { it != Relay.NTFY }

    // ---- chat & reactions: every broker at once (both sides may reach different ones), deduped by id ----
    private val _chat = MutableStateFlow<List<ChatMsg>>(emptyList())
    val chat: StateFlow<List<ChatMsg>> = _chat.asStateFlow()
    private var chatJob: Job? = null
    private var chatCode: String? = null
    private val chatOut = MutableStateFlow<ChatMsg?>(null)
    private fun chatTopic(code: String) = "nocturne/party/${code.lowercase()}/chat"
    private val myName get() = graph.settings.value.nickname.trim().ifBlank { graph.settings.value.deviceName.ifBlank { app.nocturne.deviceName() } }
    private val sid = Random.nextLong().toString(36)
    private val seen = mutableMapOf<String, Pair<ChatMsg, Long>>()
    private val _members = MutableStateFlow<List<PartyMember>>(emptyList())
    val members: StateFlow<List<PartyMember>> = _members.asStateFlow()

    private fun refreshMembers() {
        val now = nowMillis()
        seen.entries.removeAll { now - it.value.second > 50_000 }
        _members.value = seen.values.map { (m, _) -> PartyMember(m.from, m.host, m.sid == sid) }
            .sortedWith(compareByDescending<PartyMember> { it.host }.thenByDescending { it.me }.thenBy { it.name.lowercase() })
        if (_members.value.size > 1) app.nocturne.data.Achievements.unlock("together")
    }

    private fun startChat(code: String) {
        if (chatCode == code && chatJob?.isActive == true) return
        stopChat()
        chatCode = code
        chatJob = scope.launch {
            // announce ourselves every 20 s; members unseen for 50 s drop off
            launch {
                while (isActive) {
                    val hello = ChatMsg(sid + nowMillis().toString(36), myName, "", nowMillis(), presence = true, sid = sid, host = _code.value != null)
                    receiveChat(json.encodeToString(ChatMsg.serializer(), hello))
                    chatOut.value = hello
                    delay(20_000)
                    refreshMembers()
                }
            }
            brokers.forEach { r ->
                launch {
                    var fails = 0
                    while (isActive) {
                        try {
                            wsHttp.mqtt(r.url, onMessage = { _, b, _ -> receiveChat(b.decodeToString()) }) {
                                subscribe(chatTopic(code))
                                fails = 0
                                var sent: String? = null
                                chatOut.collect { m ->
                                    if (m != null && m.id != sent) {
                                        sent = m.id
                                        publish(chatTopic(code), json.encodeToString(ChatMsg.serializer(), m).encodeToByteArray(), retain = false)
                                    }
                                }
                            }
                        } catch (e: Throwable) {
                            currentCoroutineContext().ensureActive()
                        }
                        delay(backoff(fails++))
                    }
                }
            }
        }
    }

    private fun stopChat() {
        chatJob?.cancel()
        chatJob = null
        chatCode = null
        _chat.value = emptyList()
        seen.clear()
        _members.value = emptyList()
    }

    private fun receiveChat(text: String) {
        val m = runCatching { json.decodeFromString(ChatMsg.serializer(), text) }.getOrNull() ?: return
        if (m.presence) {
            seen[m.sid] = m to nowMillis()
            refreshMembers()
            return
        }
        if (_chat.value.any { it.id == m.id }) return
        _chat.value = (_chat.value + m).takeLast(80)
    }

    val chatAvailable: Boolean get() = chatCode != null

    /** Sends a message (or an emoji reaction) to everyone in the session. */
    fun say(text: String, reaction: Boolean = false) {
        val t = text.trim().take(300)
        if (t.isEmpty() || chatCode == null) return
        val m = ChatMsg(Random.nextLong().toString(36) + nowMillis().toString(36), myName, t, nowMillis(), reaction)
        receiveChat(json.encodeToString(ChatMsg.serializer(), m))
        chatOut.value = m
    }

    private fun resetRelays(): Int {
        relayOk.clear()
        relayErr.clear()
        _details.value = ""
        return ++relayGen
    }

    /** Round-trips test messages through every relay; results land in [checks]. */
    fun checkConnection() {
        if (_checking.value) return
        _checking.value = true
        scope.launch {
            try { _checks.value = runRelayChecks() } finally { _checking.value = false }
        }
    }

    suspend fun runRelayChecks(): List<RelayCheck> = checkRelays(graph.http, wsHttp)

    private fun relayUp(gen: Int, r: Relay) {
        if (gen != relayGen) return
        relayErr.remove(r)
        relayOk += r
        updateStatus()
    }

    private fun relayDown(gen: Int, r: Relay, e: Throwable) {
        if (gen != relayGen) return
        relayOk -= r
        relayErr[r] = describe(e).take(300)
        updateStatus()
    }

    // Errors show only once every relay has failed.
    private fun updateStatus() {
        _details.value = Relay.entries.joinToString("\n") { r ->
            "${r.name.lowercase()}: " + when (r) {
                in relayOk -> "connected"
                in relayErr -> relayErr[r]
                else -> "connecting…"
            } + " — ${r.url}"
        }
        val guest = _joined.value != null
        _status.value = when {
            relayOk.isNotEmpty() -> if (guest && latest == null) "Waiting for the host…" else null
            relayErr.size < Relay.entries.size -> _status.value
            else -> {
                val why = relayErr.entries.joinToString("; ") { "${it.key.name.lowercase()}: ${it.value.take(70)}" }
                if (guest) t("Connection problem (%s) — retrying…", why)
                else t("Online relay unavailable (%s) — friends on your Wi-Fi can still join", why)
            }
        }
    }

    private fun backoff(fails: Int) = (2000L shl fails.coerceAtMost(5)).coerceAtMost(60_000) + Random.nextLong(1000)

    private fun encodeWire(s: PartyState) = json.encodeToString(Wire.serializer(), Wire.of(s))

    // Accepts both the compact payload and a full PartyState (older hosts on ntfy).
    private fun onlineMessage(gen: Int, text: String, retained: Boolean = false) {
        if (gen != relayGen || text.isBlank()) return
        val obj = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return
        val s = runCatching {
            if ("at" in obj) json.decodeFromJsonElement(Wire.serializer(), obj).toState() else json.decodeFromJsonElement(PartyState.serializer(), obj)
        }.getOrNull() ?: return
        // Ignore a retained state left by a host that vanished long ago (live hosts refresh every 15s).
        if (retained && s.sentAt > 0 && nowMillis() - s.sentAt > 10 * 60_000) return
        offer(s)
        updateStatus()
    }

    private fun snapshot(): PartyState {
        val p = graph.player.state.value
        val a = graph.settings.value.let { PartyAudio(it.playbackSpeed, it.eqEnabled, it.eqGains, it.normalizeVolume, it.spatialAudio, it.eightD, it.vocalRemover) }
        return PartyState(p.current, p.positionMs, p.isPlaying, nowMillis(), seq, a)
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
        val gen = resetRelays()
        val out = MutableStateFlow<PartyState?>(lanState)
        hostOut = out
        startChat(code)
        relayJob = scope.launch {
            brokers.forEach { b -> launch { hostMqtt(gen, b, mqttTopic(code), out) } }
            launch { hostNtfy(gen, code, out) }
        }
        publishJob = scope.launch {
            var last: PartyState? = null
            var lastSent = 0L
            while (isActive) {
                var now = snapshot()
                val prev = last
                val speed = now.audio?.speed ?: 1f
                val drift = if (prev != null && prev.playing) abs(now.positionMs - (prev.positionMs + ((now.sentAt - prev.sentAt) * speed).toLong())) else 0L
                val changed = prev == null || prev.track?.key != now.track?.key || prev.playing != now.playing || drift > 2500 ||
                    prev.audio != now.audio ||
                    (!now.playing && abs(now.positionMs - prev.positionMs) > 1500)
                if (changed && prev != null) seq++
                now = now.copy(seq = seq)
                lanState = now
                if (changed || now.sentAt - lastSent > HEARTBEAT) {
                    last = now
                    lastSent = now.sentAt
                    out.value = now
                }
                delay(500)
            }
        }
    }

    // Retained publish so a joining guest gets the latest state at once; cleared when the session ends.
    private suspend fun hostMqtt(gen: Int, r: Relay, topic: String, out: StateFlow<PartyState?>) {
        var fails = 0
        while (out.value != null) {
            try {
                wsHttp.mqtt(r.url) {
                    relayUp(gen, r)
                    fails = 0
                    out.takeWhile { it != null }.collect { publish(topic, encodeWire(it!!).encodeToByteArray(), retain = true) }
                    publish(topic, ByteArray(0), retain = true)
                }
            } catch (e: Throwable) {
                currentCoroutineContext().ensureActive() // a dead socket may surface as CancellationException
                relayDown(gen, r, e)
            }
            if (out.value == null) break
            delay(backoff(fails++))
        }
    }

    // ntfy.sh has a small anonymous quota: post changes only, plus a 60s heartbeat while no MQTT broker is reachable.
    // Full PartyState JSON so guests on older app versions can still follow.
    private suspend fun hostNtfy(gen: Int, code: String, out: StateFlow<PartyState?>) {
        var lastSeq = -1L
        var lastPost = 0L
        out.takeWhile { it != null }.collect { st ->
            val s = st ?: return@collect
            val mqttUp = relayOk.any { it != Relay.NTFY }
            if (s.seq == lastSeq && (mqttUp || s.sentAt - lastPost < 60_000)) return@collect
            lastSeq = s.seq
            lastPost = s.sentAt
            try {
                val resp = graph.http.post(ntfyTopic(code)) {
                    setBody(json.encodeToString(PartyState.serializer(), s))
                    timeout { requestTimeoutMillis = 8000 }
                }
                if (!resp.status.isSuccess()) error("HTTP ${resp.status.value}")
                relayUp(gen, Relay.NTFY)
            } catch (e: Throwable) {
                currentCoroutineContext().ensureActive() // a dead socket may surface as CancellationException
                lastPost = s.sentAt - 45_000 // retry on a heartbeat ~15s later when it's the only relay
                relayDown(gen, Relay.NTFY, e)
            }
        }
    }

    fun stopHosting() {
        stopChat()
        publishJob?.cancel()
        publishJob = null
        // Null tells the broker links to clear the retained state and disconnect; cut them off if that stalls.
        hostOut?.value = null
        hostOut = null
        relayJob?.let { j -> scope.launch { delay(5000); j.cancel() } }
        relayJob = null
        if (_code.value != null) {
            resetRelays()
            if (_joined.value == null) _status.value = null
        }
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

    // Subscribes on every broker at once (duplicates are dropped by seq) with ntfy.sh as the last fallback.
    private fun joinOnline(code: String) {
        _joined.value = code
        startChat(code)
        _status.value = null
        val gen = resetRelays()
        joinJob = scope.launch {
            launch { syncLoop() }
            brokers.forEach { b -> launch { guestMqtt(gen, b, mqttTopic(code)) } }
            launch { guestNtfy(gen, code) }
        }
    }

    private suspend fun guestMqtt(gen: Int, r: Relay, topic: String) {
        var fails = 0
        while (true) {
            try {
                wsHttp.mqtt(r.url, onMessage = { _, b, retained -> onlineMessage(gen, b.decodeToString(), retained) }) {
                    subscribe(topic)
                    relayUp(gen, r)
                    fails = 0
                    awaitCancellation()
                }
            } catch (e: Throwable) {
                currentCoroutineContext().ensureActive() // a dead socket may surface as CancellationException
                relayDown(gen, r, e)
            }
            delay(backoff(fails++))
        }
    }

    // One long streaming request (ntfy.sh rate-limits polling); starts from the latest cached message.
    private suspend fun guestNtfy(gen: Int, code: String) {
        var since = "latest"
        var fails = 0
        while (true) {
            var opened = false
            try {
                graph.http.prepareGet("${ntfyTopic(code)}/json?since=$since") {
                    timeout { requestTimeoutMillis = 600_000L; socketTimeoutMillis = 90_000L }
                }.execute { resp ->
                    if (!resp.status.isSuccess()) {
                        if (since == "latest" && resp.status.value == 400) since = "10m" // older ntfy servers lack since=latest
                        error("HTTP ${resp.status.value}")
                    }
                    opened = true
                    fails = 0
                    relayUp(gen, Relay.NTFY)
                    val ch = resp.bodyAsChannel()
                    while (true) {
                        val line = ch.readUTF8Line() ?: break
                        if (line.isBlank()) continue
                        val obj = runCatching { json.parseToJsonElement(line) as? JsonObject }.getOrNull() ?: continue
                        fun str(k: String) = (obj[k] as? JsonPrimitive)?.content
                        if (str("event") != "message") continue
                        str("id")?.let { since = it }
                        onlineMessage(gen, str("message") ?: continue)
                    }
                }
            } catch (e: Throwable) {
                currentCoroutineContext().ensureActive() // a dead socket may surface as CancellationException
                relayDown(gen, Relay.NTFY, e)
            }
            delay(if (opened) 500 else backoff(fails++))
        }
    }

    private fun offer(s: PartyState) {
        val cur = latest?.state
        // Drop stale or out-of-order states (relay replays / duplicates, slow LAN responses).
        // a restarted host starts seq from 1 again: a clearly newer sentAt wins
        val restarted = cur != null && s.seq < cur.seq && s.sentAt > cur.sentAt + 5000
        if (cur != null && !restarted && (s.seq < cur.seq || (s.seq == cur.seq && s.sentAt <= cur.sentAt))) return
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
        val wall = since + if (s.sentAt > 0 && transit in -1_000..40_000) transit.coerceAtLeast(0) else 300L
        return s.positionMs + (wall * (s.audio?.speed ?: 1f)).toLong() // track time runs at the host's speed
    }

    private fun apply(r: Remote) {
        val remote = r.state
        val p = graph.player
        // Older hosts send no audio: keep our own settings then.
        p.partyAudio = remote.audio
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
        if (_code.value == null) stopChat()
        joinJob?.cancel()
        joinJob = null
        latest = null
        graph.player.partyAudio = null
        if (_joined.value != null && _code.value == null) resetRelays()
        _joined.value = null
        if (_code.value == null) _status.value = null
    }

    private companion object {
        const val START_LEAD = 700L
        const val SEEK_LEAD = 250L
        const val HEARTBEAT = 15_000L
    }
}
