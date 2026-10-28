package app.nocturne.player

import app.nocturne.FileStore
import app.nocturne.data.Library
import app.nocturne.data.SettingsStore
import app.nocturne.model.ResolvedStream
import app.nocturne.model.Source
import app.nocturne.model.Track
import app.nocturne.nowMillis
import app.nocturne.source.MusicProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class RepeatMode { OFF, ALL, ONE }

data class PlayerState(
    val queue: List<Track> = emptyList(),
    val index: Int = -1,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF,
    val error: String? = null,
    val sleepAtMs: Long? = null,
    val sleepAfterTrack: Boolean = false,
    val queueTitle: String? = null,
    /** Audio comes from an official instrumental (vocal remover on); metadata stays the original track's. */
    val instrumental: Boolean = false,
    /** Connected to another device: this one is only a remote, audio plays there. */
    val remoteName: String? = null,
) {
    val current: Track? get() = queue.getOrNull(index)
    val hasNext: Boolean get() = index < queue.lastIndex || repeat == RepeatMode.ALL
    val hasPrevious: Boolean get() = index > 0 || positionMs > 3000
    val progress: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/** Where playback goes while connected to another device. */
interface RemoteOutput {
    val name: String
    fun send(op: String, ms: Long = 0, vol: Float = -1f)
    fun load(queue: List<Track>, index: Int, ms: Long, play: Boolean, title: String?)
}

@Serializable
private data class SavedQueue(val queue: List<Track>, val index: Int, val positionMs: Long, val title: String? = null)

class PlayerController(
    private val engine: AudioEngine,
    private val providers: Map<Source, MusicProvider>,
    private val settings: SettingsStore,
    private val library: Library,
    private val scope: CoroutineScope,
) : EngineListener {
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var originalOrder: List<Track> = emptyList()
    private var loadJob: Job? = null
    private var radioJob: Job? = null
    private var retried = false
    private var loadingKey: String? = null
    private var loadedKey: String? = null
    private var pendingPlay: Boolean? = null
    private var pendingStartMs: Long? = null
    private var loadGen = 0
    private var synced = false
    private val json = Json { ignoreUnknownKeys = true }
    private val instrumentals = Instrumentals(providers, scope)
    private var lastVocal = settings.value.vocalRemover
    private var swapJob: Job? = null
    private var audioTrack: Track? = null // what the engine really plays (the instrumental or the track itself)

    init {
        engine.setListener(this)
        engine.setVolume(settings.value.volume)
        applyAudioSettings()
        restore()
        scope.launch {
            var applied = -1f
            while (isActive) {
                delay(80)
                val s = settings.value
                val cf = s.crossfadeSec * 1000L
                val target = if (muted) 0f else if (cf <= 0 || !_state.value.isPlaying) s.volume else {
                    val d = engine.durationMs
                    val p = engine.positionMs
                    val fadeOut = if (d > cf * 2) ((d - p).toFloat() / cf).coerceIn(0f, 1f) else 1f
                    val fadeIn = (p.toFloat() / (cf / 2f)).coerceIn(0f, 1f)
                    s.volume * minOf(fadeOut, fadeIn)
                }
                if (kotlin.math.abs(target - applied) > 0.01f) {
                    engine.setVolume(target)
                    applied = target
                }
            }
        }
        scope.launch {
            var tick = 0
            while (isActive) {
                delay(500)
                val s = _state.value
                if (s.current != null && loadingKey == null && remote == null) {
                    _state.update { it.copy(positionMs = engine.positionMs, durationMs = engine.durationMs.takeIf { d -> d > 0 } ?: it.current?.durationMs ?: 0) }
                }
                s.sleepAtMs?.let { if (nowMillis() >= it) { pause(); _state.update { st -> st.copy(sleepAtMs = null) } } }
                if (++tick % 20 == 0 && s.isPlaying) save()
            }
        }
    }

    fun play(tracks: List<Track>, startIndex: Int = 0, title: String? = null, shuffle: Boolean = false) {
        if (tracks.isEmpty()) return
        val filtered = filterExplicit(tracks)
        if (filtered.isEmpty()) return
        val start = filtered.indexOfFirst { it.key == tracks.getOrNull(startIndex)?.key }.coerceAtLeast(0)
        originalOrder = filtered
        synced = false
        val queue = if (shuffle) {
            val first = filtered[start]
            listOf(first) + (filtered - first).shuffled()
        } else filtered
        _state.update { it.copy(queue = queue, index = if (shuffle) 0 else start, shuffle = shuffle, queueTitle = title, error = null) }
        loadCurrent(0, true)
    }

    fun playTrack(track: Track) = play(listOf(track), 0, null)

    // Listen-together guest: plays [track] alone; startAt() is read once the stream is resolved so the start lands on the host's position.
    fun playSynced(track: Track, title: String, playing: Boolean, startAt: () -> Long) {
        originalOrder = listOf(track)
        synced = true
        _state.update { it.copy(queue = listOf(track), index = 0, shuffle = false, queueTitle = title, error = null) }
        loadCurrent(startAt(), playing, startAt)
    }

    // Device handoff: loads [tracks] at [index], starting at [startMs].
    fun playQueue(tracks: List<Track>, index: Int, startMs: Long, playWhenReady: Boolean, title: String? = null) {
        if (tracks.isEmpty()) return
        originalOrder = tracks
        synced = false
        _state.update { it.copy(queue = tracks, index = index.coerceIn(0, tracks.lastIndex), shuffle = false, queueTitle = title, error = null) }
        loadCurrent(startMs.coerceAtLeast(0), playWhenReady)
    }

    /** True while the current track's stream is still being resolved (engine still holds no / the previous media). */
    val isLoading: Boolean get() = loadingKey != null
    val isLoaded: Boolean get() = loadedKey != null && loadedKey == _state.value.current?.key

    fun startRadio(track: Track) {
        play(listOf(track), title = app.nocturne.i18n.t("Radio • %s", track.title))
        fetchRadio(track, replace = true)
    }

    /** Inserts right after the current track (moving copies already queued); starts playback on an empty queue. False if nothing changed. */
    fun playNext(tracks: List<Track>): Boolean = enqueue(tracks, next = true)

    /** Appends to the end of the queue (moving copies already queued); starts playback on an empty queue. */
    fun addToQueue(tracks: List<Track>): Boolean = enqueue(tracks, next = false)

    private fun enqueue(tracks: List<Track>, next: Boolean): Boolean {
        val add = filterExplicit(tracks).distinctBy { it.key }
        if (add.isEmpty()) return false
        val cur = _state.value.current
        if (cur == null) { play(add); return true }
        val fresh = add.filter { it.key != cur.key }
        if (fresh.isEmpty()) return false
        val keys = fresh.map { it.key }.toSet()
        _state.update { s ->
            val c = s.current ?: return@update s
            val kept = s.queue.filterIndexed { i, t -> i == s.index || t.key !in keys }
            val idx = kept.indexOfFirst { it === c }.takeIf { it >= 0 } ?: kept.indexOfFirst { it.key == c.key }.coerceAtLeast(0)
            val q = kept.toMutableList().apply { if (next) addAll(idx + 1, fresh) else addAll(fresh) }
            s.copy(queue = q, index = idx)
        }
        // keep the un-shuffled order in step so turning shuffle off doesn't lose the placement
        if (_state.value.shuffle) {
            val base = originalOrder.filter { it.key !in keys }.toMutableList()
            val at = base.indexOfFirst { it.key == cur.key }
            if (next && at >= 0) base.addAll(at + 1, fresh) else base.addAll(fresh)
            originalOrder = base
        }
        queueChanged()
        return true
    }

    /** Removes the queue entry at [i] (re-located by key if the queue moved meanwhile); skips ahead when it's the current track. */
    fun removeFromQueue(i: Int, track: Track) {
        val s = _state.value
        val at = if (s.queue.getOrNull(i)?.key == track.key) i else s.queue.indexOfFirst { it.key == track.key }
        if (at < 0) return
        if (at != s.index) return removeAt(at)
        when {
            s.queue.size == 1 -> stop()
            at < s.queue.lastIndex -> {
                _state.update { it.copy(queue = it.queue.toMutableList().apply { removeAt(at) }) }
                loadCurrent(0, s.isPlaying || loadingKey != null)
            }
            else -> {
                _state.update { it.copy(queue = it.queue.toMutableList().apply { removeAt(at) }, index = at - 1) }
                pause()
                loadCurrent(0, false)
            }
        }
    }

    fun removeAt(i: Int) {
        val s = _state.value
        if (i == s.index || i !in s.queue.indices) return
        _state.update { it.copy(queue = it.queue.toMutableList().apply { removeAt(i) }, index = if (i < it.index) it.index - 1 else it.index) }
        queueChanged()
    }

    fun move(from: Int, to: Int) {
        _state.update { s ->
            if (from !in s.queue.indices || to !in s.queue.indices) return@update s
            val q = s.queue.toMutableList()
            val t = q.removeAt(from)
            q.add(to, t)
            val cur = s.current
            s.copy(queue = q, index = q.indexOfFirst { it.key == cur?.key })
        }
        queueChanged()
    }

    fun clearQueue() {
        val cur = _state.value.current ?: return
        _state.update { it.copy(queue = listOf(cur), index = 0) }
        queueChanged()
    }

    fun skipTo(i: Int) {
        if (i !in _state.value.queue.indices) return
        _state.update { it.copy(index = i) }
        loadCurrent(0, true)
    }

    fun toggle() = if (_state.value.isPlaying) pause() else resume()

    var remote: RemoteOutput? = null
        set(v) {
            field = v
            if (v != null) { engine.stop(); loadedKey = null }
            _state.update { it.copy(remoteName = v?.name, isBuffering = false) }
        }

    /** Remote device's playback mirrored into the local state (no audio here). */
    fun mirror(track: Track?, positionMs: Long, durationMs: Long, playing: Boolean) {
        if (remote == null) return
        _state.update { s ->
            val base = when {
                track == null -> s
                s.current?.key == track.key -> s
                else -> s.queue.indexOfFirst { it.key == track.key }.let { i -> if (i >= 0) s.copy(index = i) else s.copy(queue = listOf(track), index = 0) }
            }
            base.copy(positionMs = positionMs, durationMs = durationMs.takeIf { it > 0 } ?: base.current?.durationMs ?: 0, isPlaying = playing, isBuffering = false)
        }
    }

    fun resume() {
        val s = _state.value
        val cur = s.current ?: return
        remote?.let { r ->
            if (loadedKey != cur.key) loadCurrent(s.positionMs, true) else r.send("play")
            _state.update { it.copy(isPlaying = true) }
            return
        }
        // While loading, the engine still holds the previous media: play() would restart the old track.
        if (loadingKey != null) { pendingPlay = true; return }
        if (loadedKey != cur.key || engine.durationMs <= 0 && !s.isBuffering) loadCurrent(s.positionMs, true) else engine.play()
    }

    fun pause() {
        remote?.let { it.send("pause"); _state.update { s -> s.copy(isPlaying = false) }; return }
        if (loadingKey != null) pendingPlay = false
        engine.pause()
    }

    fun next() {
        val s = _state.value
        when {
            s.index < s.queue.lastIndex -> { _state.update { it.copy(index = it.index + 1) }; loadCurrent(0, true) }
            s.repeat == RepeatMode.ALL && s.queue.isNotEmpty() -> { _state.update { it.copy(index = 0) }; loadCurrent(0, true) }
            else -> s.current?.let { if (settings.value.autoplayRadio && !synced) fetchRadio(it, replace = false, advance = true) }
        }
    }

    fun previous() {
        val s = _state.value
        if ((if (remote != null) s.positionMs else engine.positionMs) > 3000 || s.index <= 0) seekTo(0)
        else { _state.update { it.copy(index = it.index - 1) }; loadCurrent(0, true) }
    }

    fun seekTo(ms: Long) {
        remote?.let { it.send("seek", ms); _state.update { s -> s.copy(positionMs = ms) }; return }
        if (loadingKey != null) pendingStartMs = ms else engine.seekTo(ms)
        _state.update { it.copy(positionMs = ms) }
    }

    fun seekBy(deltaMs: Long) {
        val st = _state.value
        val pos = if (remote != null) st.positionMs else engine.positionMs
        val dur = if (remote != null) st.durationMs else engine.durationMs
        seekTo((pos + deltaMs).coerceIn(0, dur.coerceAtLeast(0)))
    }

    fun toggleShuffle() {
        _state.update { s ->
            val cur = s.current ?: return@update s.copy(shuffle = !s.shuffle)
            if (!s.shuffle) {
                originalOrder = s.queue
                val rest = s.queue.filterIndexed { i, _ -> i != s.index }.shuffled()
                s.copy(shuffle = true, queue = listOf(cur) + rest, index = 0)
            } else {
                val base = originalOrder.filter { o -> s.queue.any { it.key == o.key } } + s.queue.filter { q -> originalOrder.none { it.key == q.key } }
                s.copy(shuffle = false, queue = base, index = base.indexOfFirst { it.key == cur.key }.coerceAtLeast(0))
            }
        }
        queueChanged()
    }

    fun cycleRepeat() {
        _state.update { it.copy(repeat = RepeatMode.entries[(it.repeat.ordinal + 1) % RepeatMode.entries.size]) }
        queueChanged()
    }

    fun setVolume(v: Float) {
        remote?.let { it.send("vol", vol = v); return }
        engine.setVolume(v)
        settings.update { it.copy(volume = v) }
    }

    val audioFx: Set<AudioFx> get() = engine.fx

    /** Spectrum for the EQ icon, polled per frame; null → the UI animates a synthetic one. */
    fun levels(): FloatArray? = engine.levels()

    /** Live EQ while dragging, without persisting settings. */
    fun previewEqualizer(gains: List<Float>) { if (partyAudio == null) engine.setEqualizer(true, gains) }

    /** Listen-together guest: the host's version / EQ / effects replace ours until set back to null. */
    var partyAudio: app.nocturne.party.PartyAudio? = null
        set(v) {
            if (field == v) return
            field = v
            applyAudioSettings()
        }

    fun applyAudioSettings() {
        val s = settings.value
        val a = partyAudio
        engine.setSkipSilence(s.skipSilence)
        rampSpeed(a?.speed ?: s.playbackSpeed)
        if (a != null) engine.setEqualizer(a.eq, a.gains.takeIf { it.size == s.eqGains.size } ?: s.eqGains)
        else engine.setEqualizer(s.eqEnabled, s.eqGains)
        applyEffects()
        val want = vocalWanted()
        if (want != lastVocal) { lastVocal = want; swapVocal(want) }
    }

    // An instrumental is already vocal-free: the DSP remover stays off for it.
    private fun applyEffects() {
        val s = settings.value
        val a = partyAudio
        val vocal = vocalWanted() && !_state.value.instrumental
        if (a != null) engine.setEffects(a.normalize, a.spatial, a.eightD, vocal)
        else engine.setEffects(s.normalizeVolume, s.spatialAudio, s.eightD, vocal)
    }

    private fun vocalWanted() = partyAudio?.vocal ?: settings.value.vocalRemover

    // Vocal remover toggled mid-track: switch between the original and its instrumental at the same position.
    private fun swapVocal(on: Boolean) {
        swapJob?.cancel()
        val cur = _state.value.current ?: return
        if (loadingKey != null || loadedKey != cur.key) return // the pending load reads the setting itself
        if (on == _state.value.instrumental) return
        swapJob = scope.launch {
            val src = if (on) instrumentals.find(cur) ?: return@launch else cur
            val stream = runCatching { resolve(src) }.getOrNull() ?: return@launch
            if (_state.value.current?.key != cur.key || loadingKey != null || vocalWanted() != on) return@launch
            val pos = engine.positionMs
            engine.load(stream, cur, pos, _state.value.isPlaying)
            audioTrack = src
            setInstrumental(on)
        }
    }

    private fun setInstrumental(on: Boolean) {
        val was = _state.value.instrumental
        _state.update { it.copy(instrumental = on) }
        applyEffects()
        // shown as a subtitle in the player, no toast
    }

    private var speedJob: Job? = null
    private var appliedSpeed = Float.NaN

    // Version / speed changes glide (ease-in-out, ~1.3 s) while playing instead of jumping.
    private fun rampSpeed(target: Float) {
        val from = appliedSpeed
        if (from == target) return
        speedJob?.cancel()
        if (from.isNaN() || !_state.value.isPlaying) {
            appliedSpeed = target
            engine.setPlaybackSpeed(target)
            return
        }
        speedJob = scope.launch {
            val steps = 30
            for (i in 1..steps) {
                delay(1300L / steps)
                val t = i / steps.toFloat()
                appliedSpeed = if (i == steps) target else from + (target - from) * t * t * (3 - 2 * t)
                engine.setPlaybackSpeed(appliedSpeed)
            }
        }
    }

    fun setSleepTimer(minutes: Int?) {
        _state.update { it.copy(sleepAtMs = minutes?.let { m -> nowMillis() + m * 60_000L }, sleepAfterTrack = false) }
    }

    fun sleepAfterCurrentTrack() = _state.update { it.copy(sleepAtMs = null, sleepAfterTrack = true) }

    private fun curate(candidates: List<Track>): List<Track> {
        val queueKeys = _state.value.queue.map { it.key }.toSet()
        val recent = library.state.value.history.take(25).map { it.track.key }.toSet()
        val musical = filterExplicit(candidates)
            .filter { it.key !in queueKeys && !library.isBlocked(it) }
            .filter { it.durationMs == 0L || it.durationMs in 45_000L..15 * 60_000L }
        val pool = musical.filter { it.key !in recent }.takeIf { it.size >= 5 } ?: musical
        val favorites = library.favoriteArtists()
        val perArtist = mutableMapOf<String, Int>()
        val picked = mutableListOf<Track>()
        val deferred = mutableListOf<Track>()
        for (t in pool) {
            val artist = t.artists.firstOrNull()?.name?.lowercase().orEmpty()
            val count = perArtist[artist] ?: 0
            val lastSame = picked.lastOrNull()?.artists?.firstOrNull()?.name?.lowercase() == artist
            val cap = if (artist in favorites) 4 else 3
            if (artist.isNotEmpty() && (count >= cap || lastSame)) deferred += t
            else {
                picked += t
                perArtist[artist] = count + 1
            }
        }
        return picked + deferred.filter { (perArtist[it.artists.firstOrNull()?.name?.lowercase().orEmpty()] ?: 0) < 5 }
    }

    fun dropTrack(track: Track) {
        val s = _state.value
        val i = s.queue.indexOfFirst { it.key == track.key }
        if (i < 0) return
        if (i == s.index) {
            if (s.index < s.queue.lastIndex) {
                _state.update { it.copy(queue = it.queue.toMutableList().apply { removeAt(i) }) }
                loadCurrent(0, true)
            } else next()
        } else removeAt(i)
    }

    fun ensureAutoplay() {
        val s = _state.value
        val cur = s.current ?: return
        if (!synced && s.index >= s.queue.lastIndex - 1) fetchRadio(cur, replace = false)
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    // Reuses the error snackbar for short notices.
    fun showMessage(msg: String) = _state.update { it.copy(error = msg) }

    fun stop() {
        loadJob?.cancel()
        loadingKey = null
        loadedKey = null
        engine.stop()
        _state.update { PlayerState(shuffle = it.shuffle, repeat = it.repeat) }
        FileStore.delete(QUEUE_FILE)
    }

    private fun filterExplicit(tracks: List<Track>) =
        if (settings.value.hideExplicit) tracks.filterNot { it.explicit } else tracks

    private fun fetchRadio(seed: Track, replace: Boolean, advance: Boolean = false) {
        radioJob?.cancel()
        radioJob = scope.launch {
            val related = runCatching {
                withContext(Dispatchers.Default) { providers[seed.source]?.related(seed).orEmpty() }
            }.getOrDefault(emptyList())
            val fresh = curate(related)
            if (fresh.isEmpty()) return@launch
            _state.update { s -> s.copy(queue = s.queue + fresh, queueTitle = if (replace) s.queueTitle else s.queueTitle) }
            queueChanged()
            if (advance) next()
        }
    }

    private fun queueChanged() {
        val s = _state.value
        engine.onQueueChanged(s.index > 0, s.hasNext || settings.value.autoplayRadio)
        save()
    }

    private class CachedStream(val stream: ResolvedStream, val at: Long)

    private val streamCache = mutableMapOf<String, CachedStream>()
    private val inFlight = mutableMapOf<String, kotlinx.coroutines.Deferred<ResolvedStream>>()
    private var prefetchJob: Job? = null

    var localOverride: (Track) -> String? = { null }
    var muted = false

    suspend fun streamFor(track: Track): ResolvedStream = resolve(track, allowLocal = false)

    private suspend fun resolve(track: Track, fresh: Boolean = false, allowLocal: Boolean = true): ResolvedStream {
        if (engine.fake) return ResolvedStream("demo:" + track.key)
        if (allowLocal) localOverride(track)?.let { return ResolvedStream(it) }
        val key = track.key + ":" + settings.value.audioQuality
        if (!fresh) streamCache[key]?.takeIf { nowMillis() - it.at < STREAM_TTL }?.let { return it.stream }
        val pending = inFlight[key]?.takeIf { !fresh && it.isActive }
            ?: scope.async(Dispatchers.Default) {
                val provider = providers[track.source] ?: error(app.nocturne.i18n.t("%s is disabled", track.source.label))
                provider.stream(track, settings.value.audioQuality)
            }.also { inFlight[key] = it }
        return try {
            pending.await().also { streamCache[key] = CachedStream(it, nowMillis()) }
        } finally {
            if (inFlight[key] === pending && !pending.isActive) inFlight.remove(key)
        }
    }

    private fun prefetchNext() {
        prefetchJob?.cancel()
        prefetchJob = scope.launch {
            delay(1500)
            val s = _state.value
            listOfNotNull(s.queue.getOrNull(s.index + 1), s.queue.getOrNull(s.index + 2)).forEach { t ->
                runCatching { resolve(t) }
            }
            s.queue.getOrNull(s.index + 1)?.let { if (vocalWanted()) instrumentals.prefetch(it) }
        }
    }

    private fun loadCurrent(startMs: Long, playWhenReady: Boolean, startAt: (() -> Long)? = null) {
        val track = _state.value.current ?: return
        remote?.let { r ->
            val s = _state.value
            val from = (s.index - 50).coerceAtLeast(0)
            r.load(s.queue.subList(from, minOf(s.queue.size, from + 200)), s.index - from, startMs, playWhenReady, s.queueTitle)
            loadedKey = track.key
            _state.update { it.copy(positionMs = startMs, durationMs = track.durationMs, isPlaying = playWhenReady, isBuffering = false, error = null) }
            return
        }
        loadJob?.cancel()
        retried = false
        val gen = ++loadGen
        loadingKey = track.key
        loadedKey = null
        pendingPlay = null
        pendingStartMs = null
        engine.stop()
        swapJob?.cancel()
        _state.update { it.copy(positionMs = startMs, durationMs = track.durationMs, isBuffering = true, error = null, instrumental = false) }
        applyEffects()
        queueChanged()
        loadJob = scope.launch {
            try {
                // vocal remover: prefer an official instrumental (bounded wait; the lookup finishes and caches anyway)
                val inst = if (vocalWanted()) withTimeoutOrNull(4000) { instrumentals.find(track) } else null
                val instStream = inst?.let { runCatching { resolve(it) }.getOrNull() }
                val stream = instStream ?: resolve(track)
                if (_state.value.current?.key != track.key) { if (gen == loadGen) loadingKey = null; return@launch }
                val play = pendingPlay ?: playWhenReady
                val start = pendingStartMs ?: startAt?.invoke() ?: startMs
                engine.load(stream, track, start, play)
                audioTrack = if (instStream != null) inst else track
                setInstrumental(instStream != null)
                loadedKey = track.key
                loadingKey = null
                pendingPlay = null
                pendingStartMs = null
                _state.update { it.copy(positionMs = start) }
                if (play) library.recordPlay(track)
                prefetchNext()
                val s = _state.value
                if (settings.value.autoplayRadio && !synced && s.repeat == RepeatMode.OFF && s.index >= s.queue.lastIndex - 1) {
                    fetchRadio(track, replace = false)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (gen == loadGen) loadingKey = null
                _state.update { it.copy(isBuffering = false, error = app.nocturne.i18n.t("Can't play “%s”: %s", track.title, e.message ?: e::class.simpleName)) }
                delay(1500)
                if (_state.value.current?.key == track.key && _state.value.index < _state.value.queue.lastIndex) next()
            }
        }
    }

    override fun onEnded() {
        scope.launch {
            val s = _state.value
            if (s.sleepAfterTrack) {
                _state.update { it.copy(sleepAfterTrack = false, isPlaying = false) }
                if (s.index < s.queue.lastIndex) {
                    _state.update { it.copy(index = it.index + 1) }
                    loadCurrent(0, false)
                }
                return@launch
            }
            // listen-together guest: the host decides what plays next
            if (synced) { _state.update { it.copy(isPlaying = false) }; return@launch }
            if (s.repeat == RepeatMode.ONE) loadCurrent(0, true) else next()
        }
    }

    override fun onError(error: Throwable) {
        scope.launch {
            if (!retried) {
                retried = true
                val pos = engine.positionMs
                val track = _state.value.current ?: return@launch
                runCatching {
                    val src = audioTrack?.takeIf { _state.value.instrumental } ?: track
                    engine.load(resolve(src, fresh = true), track, pos, true)
                }.onSuccess { return@launch }
            }
            _state.update { it.copy(isBuffering = false, isPlaying = false, error = app.nocturne.i18n.t("Playback error: %s", error.message)) }
        }
    }

    override fun onPlayingChanged(playing: Boolean) {
        _state.update { it.copy(isPlaying = playing, isBuffering = if (playing) false else it.isBuffering) }
        if (!playing) save()
    }

    override fun onBufferingChanged(buffering: Boolean) = _state.update { it.copy(isBuffering = buffering) }

    private fun save() {
        if (!settings.value.persistQueue) return
        val s = _state.value
        if (s.queue.isEmpty()) return
        runCatching {
            FileStore.write(QUEUE_FILE, json.encodeToString(SavedQueue.serializer(), SavedQueue(s.queue.take(300), s.index, engine.positionMs, s.queueTitle)))
        }
    }

    private fun restore() {
        if (!settings.value.persistQueue) return
        val saved = FileStore.read(QUEUE_FILE)?.let { runCatching { json.decodeFromString(SavedQueue.serializer(), it) }.getOrNull() } ?: return
        if (saved.queue.isEmpty()) return
        _state.update { it.copy(queue = saved.queue, index = saved.index.coerceIn(0, saved.queue.lastIndex), positionMs = saved.positionMs, queueTitle = saved.title) }
        originalOrder = saved.queue
        if (settings.value.resumeOnStart) loadCurrent(saved.positionMs, true)
        else scope.launch {
            delay(2500)
            _state.value.current?.let { runCatching { resolve(it) } }
        }
    }

    companion object {
        private const val QUEUE_FILE = "queue.json"
        private const val STREAM_TTL = 3 * 60 * 60 * 1000L
    }
}
