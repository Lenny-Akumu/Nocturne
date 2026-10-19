package app.nocturne.player

import app.nocturne.FileStore
import app.nocturne.data.Library
import app.nocturne.data.SettingsStore
import app.nocturne.model.Source
import app.nocturne.model.Track
import app.nocturne.nowMillis
import app.nocturne.source.MusicProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
) {
    val current: Track? get() = queue.getOrNull(index)
    val hasNext: Boolean get() = index < queue.lastIndex || repeat == RepeatMode.ALL
    val hasPrevious: Boolean get() = index > 0 || positionMs > 3000
    val progress: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
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
    private val json = Json { ignoreUnknownKeys = true }

    init {
        engine.setListener(this)
        engine.setVolume(settings.value.volume)
        engine.setSkipSilence(settings.value.skipSilence)
        restore()
        scope.launch {
            var tick = 0
            while (isActive) {
                delay(500)
                val s = _state.value
                if (s.current != null) {
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
        val queue = if (shuffle) {
            val first = filtered[start]
            listOf(first) + (filtered - first).shuffled()
        } else filtered
        _state.update { it.copy(queue = queue, index = if (shuffle) 0 else start, shuffle = shuffle, queueTitle = title, error = null) }
        loadCurrent(0, true)
    }

    fun playTrack(track: Track) = play(listOf(track), 0, null)

    fun startRadio(track: Track) {
        play(listOf(track), title = "Radio • ${track.title}")
        fetchRadio(track, replace = true)
    }

    fun playNext(tracks: List<Track>) {
        if (_state.value.current == null) return play(tracks)
        _state.update { s ->
            val q = s.queue.toMutableList()
            q.addAll(s.index + 1, tracks.filter { t -> q.none { it.key == t.key } })
            s.copy(queue = q)
        }
        queueChanged()
    }

    fun addToQueue(tracks: List<Track>) {
        if (_state.value.current == null) return play(tracks)
        _state.update { s -> s.copy(queue = s.queue + tracks.filter { t -> s.queue.none { it.key == t.key } }) }
        queueChanged()
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

    fun resume() {
        val s = _state.value
        if (s.current == null) return
        if (engine.durationMs <= 0 && !s.isBuffering) loadCurrent(s.positionMs, true) else engine.play()
    }

    fun pause() = engine.pause()

    fun next() {
        val s = _state.value
        when {
            s.index < s.queue.lastIndex -> { _state.update { it.copy(index = it.index + 1) }; loadCurrent(0, true) }
            s.repeat == RepeatMode.ALL && s.queue.isNotEmpty() -> { _state.update { it.copy(index = 0) }; loadCurrent(0, true) }
            else -> s.current?.let { if (settings.value.autoplayRadio) fetchRadio(it, replace = false, advance = true) }
        }
    }

    fun previous() {
        val s = _state.value
        if (engine.positionMs > 3000 || s.index <= 0) seekTo(0)
        else { _state.update { it.copy(index = it.index - 1) }; loadCurrent(0, true) }
    }

    fun seekTo(ms: Long) {
        engine.seekTo(ms)
        _state.update { it.copy(positionMs = ms) }
    }

    fun seekBy(deltaMs: Long) = seekTo((engine.positionMs + deltaMs).coerceIn(0, engine.durationMs.coerceAtLeast(0)))

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
        engine.setVolume(v)
        settings.update { it.copy(volume = v) }
    }

    fun applyAudioSettings() {
        engine.setSkipSilence(settings.value.skipSilence)
    }

    fun setSleepTimer(minutes: Int?) {
        _state.update { it.copy(sleepAtMs = minutes?.let { m -> nowMillis() + m * 60_000L }, sleepAfterTrack = false) }
    }

    fun sleepAfterCurrentTrack() = _state.update { it.copy(sleepAtMs = null, sleepAfterTrack = true) }

    fun dismissError() = _state.update { it.copy(error = null) }

    fun stop() {
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
            val fresh = filterExplicit(related).filter { r -> _state.value.queue.none { it.key == r.key } }
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

    private fun loadCurrent(startMs: Long, playWhenReady: Boolean) {
        val track = _state.value.current ?: return
        loadJob?.cancel()
        retried = false
        engine.stop()
        _state.update { it.copy(positionMs = startMs, durationMs = track.durationMs, isBuffering = true, error = null) }
        queueChanged()
        loadJob = scope.launch {
            try {
                val provider = providers[track.source] ?: error("${track.source.label} is disabled")
                val stream = withContext(Dispatchers.Default) { provider.stream(track, settings.value.audioQuality) }
                if (_state.value.current?.key != track.key) return@launch
                engine.load(stream, track, startMs, playWhenReady)
                if (playWhenReady) library.recordPlay(track)
                val s = _state.value
                if (settings.value.autoplayRadio && s.repeat == RepeatMode.OFF && s.index >= s.queue.lastIndex - 1) {
                    fetchRadio(track, replace = false)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.update { it.copy(isBuffering = false, error = "Can't play “${track.title}”: ${e.message ?: e::class.simpleName}") }
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
                    val stream = withContext(Dispatchers.Default) { providers.getValue(track.source).stream(track, settings.value.audioQuality) }
                    engine.load(stream, track, pos, true)
                }.onSuccess { return@launch }
            }
            _state.update { it.copy(isBuffering = false, isPlaying = false, error = "Playback error: ${error.message}") }
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
    }

    companion object {
        private const val QUEUE_FILE = "queue.json"
    }
}
