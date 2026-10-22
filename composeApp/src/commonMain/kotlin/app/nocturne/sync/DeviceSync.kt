package app.nocturne.sync

import app.nocturne.AppGraph
import app.nocturne.FileStore
import app.nocturne.PlatformKind
import app.nocturne.data.SecureStore
import app.nocturne.data.Sync
import app.nocturne.deviceName
import app.nocturne.i18n.t
import app.nocturne.model.Track
import app.nocturne.nowMillis
import app.nocturne.party.mqtt
import app.nocturne.platformKind
import app.nocturne.secureRandomBytes
import app.nocturne.sha256
import app.nocturne.ui.screens.invalidateHomeCache
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.math.abs
import kotlin.random.Random

@Serializable
data class DeviceInfo(val id: String, val name: String, val platform: PlatformKind, val lastSeen: Long = 0)

/** The user's own devices sharing one secret (from QR pairing). */
@Serializable
data class SyncGroup(val secret: String, val devices: List<DeviceInfo> = emptyList(), val removed: List<String> = emptyList())

/** Presence + playback, published retained on nocturne/sync/<group>/s/<device>. */
@Serializable
data class DeviceState(
    val id: String = "",
    val name: String = "",
    val platform: PlatformKind = PlatformKind.DESKTOP,
    val track: Track? = null,
    val pos: Long = 0,
    val dur: Long = 0,
    val playing: Boolean = false,
    val vol: Float = 1f,
    /** Accepts remote control. */
    val rc: Boolean = true,
    val online: Boolean = true,
    val at: Long = 0,
)

@Serializable
data class RemoteCmd(
    val op: String,
    val ms: Long = 0,
    val vol: Float = -1f,
    val track: Track? = null,
    val queue: List<Track> = emptyList(),
    val index: Int = 0,
    val play: Boolean = true,
    val title: String? = null,
)

/** Decrypted message envelope. [n] is a random id (dedupe across brokers, replay guard). Library snapshots are split into [c] chunks. */
@Serializable
private data class Env(
    val f: String, val k: String, val at: Long, val n: String, val b: String = "",
    val sid: Long = 0, val i: Int = 0, val c: Int = 1, val h: String = "",
)

class Peer(val info: DeviceInfo, val state: DeviceState?, val receivedAt: Long) {
    fun online(now: Long = nowMillis()) =
        state != null && state.online && now - receivedAt < OFFLINE_MS && now - state.at < OFFLINE_MS + SKEW_MS

    fun position(now: Long = nowMillis()): Long {
        val s = state ?: return 0
        return if (s.playing) s.pos + (now - receivedAt).coerceAtLeast(0) else s.pos
    }
}

private const val OFFLINE_MS = 45_000L
private const val SKEW_MS = 120_000L

/** nocturne://pair links from the OS; the app asks before joining. */
object DeviceLinks {
    val pending = MutableStateFlow<String?>(null)

    fun handle(url: String): Boolean {
        val u = url.trim()
        if (!u.startsWith("nocturne://pair", ignoreCase = true)) return false
        pending.value = u
        return true
    }
}

private class Pub(val topic: String, val data: ByteArray, val retain: Boolean)

private class Chunks(val sid: Long, count: Int, val hash: String) {
    val parts = arrayOfNulls<String>(count.coerceIn(1, 64))
}

/**
 * Sync between the user's own devices over public MQTT brokers (no server of our own).
 * Every payload is sealed with ChaCha20-Poly1305 under a key derived from the group secret, bound to its topic.
 * Topics under nocturne/sync/<groupId>/: s/<dev> state (retained), l/<dev>/<i> library snapshot (retained),
 * c/<dev> commands, b broadcasts (library deltas, hello, bye).
 */
class DeviceSync(private val graph: AppGraph, private val scope: CoroutineScope) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val wsHttp by lazy { graph.http.config { install(WebSockets) } }
    private val lib = LibrarySync(graph.library, graph.settings)

    /** False while the app is in the background (Android); links then close unless music is playing. */
    val foreground = MutableStateFlow(true)
    private val _group = MutableStateFlow(loadGroup())
    val group: StateFlow<SyncGroup?> = _group.asStateFlow()
    private val _peers = MutableStateFlow<List<Peer>>(emptyList())
    val peers: StateFlow<List<Peer>> = _peers.asStateFlow()
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()
    /** A device that just joined (closes pairing dialogs). */
    val paired = MutableSharedFlow<DeviceInfo>(extraBufferCapacity = 4)
    /** Pairing link from outside the app, waiting for the user to confirm. */
    val pendingLink = MutableStateFlow<String?>(null)

    var myId: String = loadId()
        private set
    val myName: String get() = graph.settings.value.deviceName.ifBlank { deviceName() }

    private var key = ByteArray(0)
    private var base = ""
    private val states = mutableMapOf<String, Pair<DeviceState, Long>>()
    private val seen = LinkedHashMap<String, Long>()
    private val out = MutableSharedFlow<Pub>(extraBufferCapacity = 256)
    private val up = mutableSetOf<Int>()
    private val ownSnap = mutableMapOf<Int, String>()
    private val chunks = mutableMapOf<String, Chunks>()
    private val mergedSnap = mutableMapOf<String, String>()
    private val transferAsked = mutableMapOf<String, Long>()
    private var snapCache: Pair<String, List<Pub>>? = null
    private var publishedSnap = ""
    private var lastState: DeviceState? = null
    private var lastStateAt = 0L
    private var helloUntil = 0L
    private var homeRefreshed = false
    private var leaving = false
    private var libLink: Int? = null
    private val deltaTick = MutableStateFlow(0)
    private val snapTick = MutableStateFlow(0)

    init {
        _group.value?.let { setKeys(it.secret) }
        refreshPeers()
        scope.launch {
            DeviceLinks.pending.collect { u ->
                if (u == null) return@collect
                DeviceLinks.pending.value = null
                pendingLink.value = u
            }
        }
        // Battery: stay connected while the app is open or music plays; drop a minute after both stop.
        scope.launch {
            val active = combine(foreground, graph.player.state.map { it.isPlaying }.distinctUntilChanged()) { fg, p -> fg || p }
                .distinctUntilChanged()
                .debounce { if (it) 0L else 60_000L }
            combine(_group.map { it?.secret }.distinctUntilChanged(), active) { s, a -> s.takeIf { a } }
                .distinctUntilChanged()
                .collectLatest { s ->
                    if (s == null) _connected.value = false
                    else runLinks(s)
                }
        }
        scope.launch {
            while (true) {
                delay(1000)
                if (_connected.value) tickState()
            }
        }
        scope.launch {
            graph.library.state.collect { if (lib.observe(it)) { deltaTick.value++; snapTick.value++ } }
        }
        scope.launch { deltaTick.drop(1).debounce(2500).collect { if (_connected.value) publishDelta() } }
        scope.launch { snapTick.drop(1).debounce(30_000).collect { if (_connected.value) publishSnapshot() } }
        scope.launch {
            graph.settings.state.map { listOf(it.syncLikes, it.syncPlaylists, it.syncHistory) }.distinctUntilChanged().drop(1).collect { snapTick.value++ }
        }
    }

    // ---- group ----

    private fun loadGroup(): SyncGroup? {
        val text = SecureStore.read(GROUP_FILE)?.takeIf { it.isNotBlank() } ?: FileStore.read(GROUP_FILE) ?: return null
        return runCatching { json.decodeFromString(SyncGroup.serializer(), text) }.getOrNull()
    }

    private fun saveGroup(g: SyncGroup?) {
        _group.value = g
        if (g == null) {
            SecureStore.write(GROUP_FILE, "")
            FileStore.delete(GROUP_FILE)
        } else {
            val text = json.encodeToString(SyncGroup.serializer(), g)
            if (SecureStore.write(GROUP_FILE, text)) FileStore.delete(GROUP_FILE) else FileStore.write(GROUP_FILE, text)
        }
        refreshPeers()
    }

    private fun loadId(): String = FileStore.read(ID_FILE)?.trim()?.takeIf { it.length >= 8 } ?: newId()

    private fun newId(): String = hex(secureRandomBytes(8)).also { FileStore.write(ID_FILE, it) }

    private fun setKeys(secret: String) {
        val raw = decodeSecret(secret) ?: return
        key = sha256("nocturne/sync/key".encodeToByteArray() + raw)
        base = "nocturne/sync/" + hex(sha256("nocturne/sync/id".encodeToByteArray() + raw)).take(24)
    }

    private fun selfInfo() = DeviceInfo(myId, myName, platformKind, nowMillis())

    /** Group secret for a pairing QR, creating the group on first use. */
    fun inviteSecret(): String {
        _group.value?.let { return it.secret }
        leaving = false
        return SyncGroup(b64(secureRandomBytes(32)), listOf(selfInfo())).also { saveGroup(it) }.secret
    }

    fun inviteLink(): String = "nocturne://pair?g=${inviteSecret()}&v=2"

    /** Joins the group in a pairing link; false when it has none. */
    fun joinLink(text: String): Boolean {
        val secret = Sync.groupOf(text) ?: return false
        if (_group.value?.secret == secret) return true
        states.clear(); chunks.clear(); mergedSnap.clear(); ownSnap.clear()
        publishedSnap = ""
        snapCache = null
        homeRefreshed = false
        leaving = false
        helloUntil = nowMillis() + 90_000
        saveGroup(SyncGroup(secret, listOf(selfInfo())))
        return true
    }

    /** Link opened from outside the app, confirmed by the user: join, and hand accounts to a PC waiting on the LAN. */
    fun confirmLink(url: String) {
        pendingLink.value = null
        val joined = joinLink(url)
        val target = Sync.parse(url)
        if (target != null) scope.launch {
            runCatching { Sync.send(graph, target) }
                .onSuccess { graph.toast(t("Sent! Your PC is signed in now.")) }
                .onFailure { if (!joined) graph.toast(it.message ?: "Failed") }
        }
        if (joined) graph.toast(t("Paired. Your devices will appear in a moment."))
    }

    /** Leaves the group: clears our retained data, tells the others, forgets the secret. */
    fun leave() {
        if (_group.value == null || leaving) return
        leaving = true
        if (_connected.value) {
            out.tryEmit(seal("b", env("bye", myId)))
            listOf("s/$myId", "x/$myId").forEach { out.tryEmit(Pub("$base/$it", ByteArray(0), true)) }
            (0 until 24).forEach { out.tryEmit(Pub("$base/l/$myId/$it", ByteArray(0), true)) }
        }
        scope.launch {
            delay(if (_connected.value) 1500 else 0)
            states.clear(); chunks.clear(); mergedSnap.clear()
            snapCache = null
            publishedSnap = ""
            saveGroup(null)
            myId = newId()
        }
    }

    /** Removes [id] from the group; it leaves when it gets the message (it still knows the secret until then). */
    fun unpair(id: String) {
        val g = _group.value ?: return
        out.tryEmit(seal("b", env("bye", id)))
        out.tryEmit(seal("x/$id", env("bye", id), retain = true)) // picked up when it next comes online
        states.remove(id)
        saveGroup(g.copy(devices = g.devices.filterNot { it.id == id }, removed = (g.removed + id).distinct().takeLast(50)))
    }

    // ---- remote control ----

    fun command(peer: String, cmd: RemoteCmd) {
        out.tryEmit(seal("c/$peer", env("cmd", json.encodeToString(RemoteCmd.serializer(), cmd))))
    }

    fun control(peer: String, op: String, ms: Long = 0, vol: Float = -1f) = command(peer, RemoteCmd(op, ms = ms, vol = vol))

    /** Take over: the peer sends us its queue and pauses. */
    fun playHere(peer: String) {
        transferAsked[peer] = nowMillis()
        command(peer, RemoteCmd("transfer"))
    }

    /** Hand our queue to the peer and pause here. */
    fun playOn(peer: String) {
        val c = loadCmd() ?: return
        command(peer, c)
        graph.player.pause()
    }

    private fun loadCmd(): RemoteCmd? {
        val p = graph.player.state.value
        if (p.current == null) return null
        val from = (p.index - 50).coerceAtLeast(0)
        val q = p.queue.subList(from, minOf(p.queue.size, from + 200))
        return RemoteCmd("load", ms = p.positionMs, queue = q, index = p.index - from, play = true, title = p.queueTitle)
    }

    fun nameOf(id: String) = _group.value?.devices?.firstOrNull { it.id == id }?.name ?: states[id]?.first?.name ?: t("Another device")

    // ---- links ----

    private suspend fun runLinks(secret: String) = coroutineScope {
        setKeys(secret)
        up.clear(); ownSnap.clear()
        libLink = null
        _connected.value = false
        BROKERS.forEachIndexed { i, url -> launch { link(i, url) } }
    }

    private fun setUp(i: Int, ok: Boolean) {
        if (ok) up += i else { up -= i; ownSnap.remove(i); if (libLink == i) libLink = null }
        _connected.value = up.isNotEmpty()
    }

    private suspend fun link(idx: Int, url: String) {
        var fails = 0
        while (true) {
            try {
                wsHttp.mqtt(url, onMessage = { t, b, retained -> onMessage(idx, t, b, retained) }) {
                    listOf("s/+", "b", "c/$myId", "x/$myId", "l/$myId/0").forEach { subscribe("$base/$it") }
                    // Other devices' library snapshots are big: fetch them over one broker only.
                    if (libLink == null) { libLink = idx; subscribe("$base/l/#") }
                    setUp(idx, true)
                    fails = 0
                    sealState(true).let { publish(it.topic, it.data, it.retain) }
                    if (nowMillis() < helloUntil) seal("b", env("hi", json.encodeToString(DeviceInfo.serializer(), selfInfo()))).let { publish(it.topic, it.data, false) }
                    try {
                        coroutineScope {
                            launch {
                                // Our retained snapshot arrives right after subscribing; re-upload only when it's missing or stale.
                                delay(6000)
                                val (h, pubs) = snapshotPubs()
                                if (ownSnap[idx] != h) pubs.forEach { publish(it.topic, it.data, it.retain) }
                                publishedSnap = h
                            }
                            out.collect { publish(it.topic, it.data, it.retain) }
                        }
                    } finally {
                        if (!leaving) withContext(NonCancellable) {
                            withTimeoutOrNull(1500) { runCatching { sealState(false).let { publish(it.topic, it.data, true) } } }
                        }
                    }
                }
            } catch (e: Throwable) {
                currentCoroutineContext().ensureActive() // a dead socket may surface as CancellationException
            }
            setUp(idx, false)
            val f = fails++
            delay((2000L shl f.coerceAtMost(5)).coerceAtMost(60_000) + Random.nextLong(1000))
        }
    }

    // ---- crypto / envelope ----

    private fun env(kind: String, body: String, sid: Long = 0, i: Int = 0, c: Int = 1, h: String = "") =
        Env(myId, kind, nowMillis(), hex(secureRandomBytes(8)), body, sid, i, c, h)

    private fun seal(sub: String, e: Env, retain: Boolean = false): Pub {
        val topic = "$base/$sub"
        val nonce = secureRandomBytes(ChaChaPoly.NONCE)
        val ct = ChaChaPoly.seal(key, nonce, json.encodeToString(Env.serializer(), e).encodeToByteArray(), topic.encodeToByteArray())
        return Pub(topic, byteArrayOf(1) + nonce + ct, retain)
    }

    private fun open(topic: String, data: ByteArray): Env? {
        if (data.size < 1 + ChaChaPoly.NONCE + ChaChaPoly.TAG || data[0] != 1.toByte() || key.isEmpty()) return null
        val plain = ChaChaPoly.open(key, data.copyOfRange(1, 1 + ChaChaPoly.NONCE), data.copyOfRange(1 + ChaChaPoly.NONCE, data.size), topic.encodeToByteArray())
            ?: return null
        return runCatching { json.decodeFromString(Env.serializer(), plain.decodeToString()) }.getOrNull()
    }

    private fun fresh(n: String, now: Long): Boolean {
        if (seen.containsKey(n)) return false
        seen[n] = now
        if (seen.size > 3000) {
            val it = seen.entries.iterator()
            while (it.hasNext()) { val e = it.next(); if (now - e.value > SEEN_MS || seen.size > 3000) it.remove() else break }
        }
        return true
    }

    // ---- incoming ----

    private fun onMessage(idx: Int, topic: String, data: ByteArray, retained: Boolean) {
        if (data.isEmpty() || !topic.startsWith("$base/")) return
        val e = open(topic, data) ?: return
        if (e.f == myId) {
            if (e.k == "lib") ownSnap[idx] = e.h
            return
        }
        val g = _group.value ?: return
        if (e.f in g.removed) return
        val now = nowMillis()
        if (!fresh(e.n, now)) return
        val recent = abs(now - e.at) < REPLAY_MS
        try {
            when (e.k) {
                "st" -> onState(e, now, retained)
                "lib" -> onChunk(e)
                "dl" -> applyLib(json.decodeFromString(LibSync.serializer(), e.b))
                "hi" -> onHello(json.decodeFromString(DeviceInfo.serializer(), e.b), recent)
                "bye" -> if (recent || topic == "$base/x/$myId") onBye(e.b, e.f)
                "cmd" -> if (recent && topic == "$base/c/$myId") onCmd(e, json.decodeFromString(RemoteCmd.serializer(), e.b), now)
                else -> {}
            }
        } catch (e: Exception) {
        }
    }

    private fun onState(e: Env, now: Long, retained: Boolean) {
        val s = json.decodeFromString(DeviceState.serializer(), e.b)
        if (s.id != e.f) return
        val old = states[e.f]?.first
        if (old != null && s.at < old.at) return
        // A retained state may be old: date it by its own clock (devices' clocks are close enough).
        states[e.f] = s to if (retained) now - (now - s.at).coerceIn(0, OFFLINE_MS) else now
        upsert(DeviceInfo(s.id, s.name, s.platform, s.at))
        refreshPeers()
    }

    private fun upsert(d: DeviceInfo) {
        val g = _group.value ?: return
        val old = g.devices.firstOrNull { it.id == d.id }
        if (old != null && old.name == d.name && old.platform == d.platform && d.lastSeen - old.lastSeen < 10 * 60_000) return
        val merged = d.copy(lastSeen = maxOf(d.lastSeen, old?.lastSeen ?: 0))
        saveGroup(g.copy(devices = g.devices.filterNot { it.id == d.id } + merged))
    }

    private fun onHello(d: DeviceInfo, recent: Boolean) {
        upsert(d)
        if (!recent) return
        paired.tryEmit(d)
        graph.toast(t("%s joined your devices", d.name))
    }

    private fun onBye(target: String, from: String) {
        if (target == myId) {
            graph.toast(t("This device was unpaired by %s", nameOf(from)))
            leave()
            return
        }
        val g = _group.value ?: return
        states.remove(target)
        saveGroup(g.copy(devices = g.devices.filterNot { it.id == target }, removed = (g.removed + target).distinct().takeLast(50)))
    }

    private fun onChunk(e: Env) {
        val cur = chunks[e.f]
        if (cur == null || e.sid > cur.sid) chunks[e.f] = Chunks(e.sid, e.c, e.h)
        else if (e.sid < cur.sid) return
        val buf = chunks.getValue(e.f)
        if (e.i !in buf.parts.indices) return
        buf.parts[e.i] = e.b
        if (buf.parts.any { it == null }) return
        chunks.remove(e.f)
        if (mergedSnap[e.f] == buf.hash) return
        mergedSnap[e.f] = buf.hash
        applyLib(json.decodeFromString(LibSync.serializer(), buf.parts.joinToString("")))
    }

    private fun applyLib(r: LibSync) {
        if (!lib.merge(r)) return
        snapTick.value++
        graph.invalidateRecommendations()
        if (!homeRefreshed) {
            homeRefreshed = true
            invalidateHomeCache()
        }
    }

    private fun onCmd(e: Env, c: RemoteCmd, now: Long) {
        val asked = transferAsked[e.f]?.let { now - it < 30_000 } == true
        if (!graph.settings.value.remoteControl && !(c.op == "load" && asked)) return
        val p = graph.player
        when (c.op) {
            "play" -> p.resume()
            "pause" -> p.pause()
            "toggle" -> p.toggle()
            "next" -> p.next()
            "prev" -> p.previous()
            "seek" -> p.seekTo(c.ms.coerceAtLeast(0))
            "vol" -> if (c.vol in 0f..1f) p.setVolume(c.vol)
            "track" -> c.track?.let { p.playTrack(it) }
            "load" -> {
                transferAsked.remove(e.f)
                val lag = if (c.play) (now - e.at).coerceIn(0, 10_000) + 500 else 0
                p.playQueue(c.queue, c.index, c.ms + lag, c.play, c.title)
                graph.toast(t("Playing from %s", nameOf(e.f)))
            }
            "transfer" -> {
                val l = loadCmd() ?: return
                command(e.f, l)
                p.pause()
            }
        }
    }

    // ---- outgoing ----

    private fun stateNow(online: Boolean): DeviceState {
        val p = graph.player.state.value
        val s = graph.settings.value
        return DeviceState(myId, myName, platformKind, p.current, p.positionMs, p.durationMs, p.isPlaying && online, s.volume, s.remoteControl, online, nowMillis())
    }

    private fun sealState(online: Boolean) = seal("s/$myId", env("st", json.encodeToString(DeviceState.serializer(), stateNow(online))), retain = true)

    private fun tickState() {
        val st = stateNow(true)
        val prev = lastState
        val now = st.at
        val changed = prev == null || prev.track?.key != st.track?.key || prev.playing != st.playing || abs(prev.vol - st.vol) > 0.01f ||
            prev.name != st.name || prev.rc != st.rc || abs(st.pos - (prev.pos + if (prev.playing) now - prev.at else 0)) > 3000
        if (!changed && now - lastStateAt < HEARTBEAT_MS) return
        lastState = st
        lastStateAt = now
        out.tryEmit(seal("s/$myId", env("st", json.encodeToString(DeviceState.serializer(), st)), retain = true))
        if (prev?.track?.key != st.track?.key || prev?.playing != st.playing) refreshPeers()
    }

    private fun publishDelta() {
        val d = lib.takeDelta() ?: return
        val text = json.encodeToString(LibSync.serializer(), d)
        if (text.length > 150_000) return // the snapshot carries it
        out.tryEmit(seal("b", env("dl", text)))
    }

    private fun publishSnapshot() {
        val (h, pubs) = snapshotPubs()
        if (h == publishedSnap) return
        publishedSnap = h
        pubs.forEach { out.tryEmit(it) }
    }

    private fun snapshotPubs(): Pair<String, List<Pub>> {
        val text = json.encodeToString(LibSync.serializer(), lib.snapshot())
        val h = hex(sha256(text.encodeToByteArray())).take(16)
        snapCache?.takeIf { it.first == h }?.let { return it }
        val parts = split(text, CHUNK)
        val sid = nowMillis()
        val pubs = parts.mapIndexed { i, part -> seal("l/$myId/$i", env("lib", part, sid, i, parts.size, h), retain = true) } +
            (parts.size until parts.size + 4).map { Pub("$base/l/$myId/$it", ByteArray(0), true) } // clear leftovers of a bigger snapshot
        return (h to pubs).also { snapCache = it }
    }

    private fun refreshPeers() {
        val g = _group.value
        _peers.value = g?.devices.orEmpty().filter { it.id != myId && it.id !in g!!.removed }.map { d ->
            val s = states[d.id]
            Peer(d, s?.first, s?.second ?: 0)
        }
    }

    private companion object {
        const val GROUP_FILE = "syncgroup.json"
        const val ID_FILE = "device_id"
        const val HEARTBEAT_MS = 15_000L
        const val REPLAY_MS = 5 * 60_000L
        const val SEEN_MS = 15 * 60_000L
        const val CHUNK = 60_000
        val BROKERS = listOf("wss://broker.emqx.io:8084/mqtt", "wss://broker.hivemq.com:8884/mqtt", "wss://test.mosquitto.org:8081")

        fun hex(b: ByteArray) = b.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
        fun b64(b: ByteArray) = Base64.UrlSafe.encode(b).trimEnd('=')
        fun decodeSecret(s: String) = runCatching { Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(s) }.getOrNull()?.takeIf { it.size == 32 }

        // Splits without cutting a surrogate pair.
        fun split(s: String, size: Int): List<String> {
            val out = ArrayList<String>()
            var i = 0
            while (i < s.length) {
                var end = minOf(s.length, i + size)
                if (end < s.length && s[end - 1].isHighSurrogate()) end--
                out += s.substring(i, end)
                i = end
            }
            return out.ifEmpty { listOf("") }
        }
    }
}
