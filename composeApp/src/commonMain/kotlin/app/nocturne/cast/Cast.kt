package app.nocturne.cast

import app.nocturne.AppGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

data class CastDevice(val id: String, val name: String)

data class CastStatus(val positionMs: Long, val durationMs: Long, val playing: Boolean, val stopped: Boolean)

interface CastBackend {
    suspend fun discover(): List<CastDevice>
    suspend fun load(device: CastDevice, url: String, mime: String?, title: String, artist: String, art: String?)
    suspend fun play(device: CastDevice)
    suspend fun pause(device: CastDevice)
    suspend fun stop(device: CastDevice)
    suspend fun seek(device: CastDevice, ms: Long)
    suspend fun status(device: CastDevice): CastStatus?
}

expect fun createCastBackend(): CastBackend

expect fun acquireMulticastLock(): () -> Unit

class CastSession(private val graph: AppGraph, private val scope: CoroutineScope) {
    private val backend = createCastBackend()
    private val _devices = MutableStateFlow<List<CastDevice>>(emptyList())
    val devices: StateFlow<List<CastDevice>> = _devices.asStateFlow()
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()
    private val _device = MutableStateFlow<CastDevice?>(null)
    val device: StateFlow<CastDevice?> = _device.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private var job: Job? = null

    fun scan() {
        if (_scanning.value) return
        _scanning.value = true
        scope.launch {
            _devices.value = runCatching { backend.discover() }.getOrDefault(emptyList())
            _scanning.value = false
        }
    }

    fun connect(d: CastDevice) {
        disconnect()
        _device.value = d
        _error.value = null
        graph.player.muted = true
        job = scope.launch { follow(d) }
    }

    fun disconnect() {
        job?.cancel()
        job = null
        _device.value?.let { d -> scope.launch { runCatching { backend.stop(d) } } }
        _device.value = null
        graph.player.muted = false
    }

    private suspend fun follow(d: CastDevice) {
        var lastKey: String? = null
        var lastPlaying = false
        var lastPos = 0L
        var lastAt = 0L
        while (scope.isActive && _device.value == d) {
            val s = graph.player.state.value
            val cur = s.current
            runCatching {
                if (cur != null && cur.key != lastKey) {
                    val stream = graph.player.streamFor(cur)
                    backend.load(d, stream.url, stream.mimeType, cur.title, cur.artistNames, cur.artworkUrl)
                    if (s.isPlaying) backend.play(d)
                    if (s.positionMs > 3000) backend.seek(d, s.positionMs)
                    lastKey = cur.key
                    lastPlaying = s.isPlaying
                } else if (cur != null) {
                    if (s.isPlaying != lastPlaying) {
                        if (s.isPlaying) backend.play(d) else backend.pause(d)
                        lastPlaying = s.isPlaying
                    }
                    val expected = lastPos + if (s.isPlaying) 1000 * ((app.nocturne.nowMillis() - lastAt) / 1000) else 0
                    if (lastAt > 0 && abs(s.positionMs - expected) > 4000) backend.seek(d, s.positionMs)
                }
                _error.value = null
            }.onFailure { _error.value = it.message ?: "Cast failed" }
            lastPos = s.positionMs
            lastAt = app.nocturne.nowMillis()
            delay(1000)
        }
    }
}
