package app.nocturne.source

import app.nocturne.AppGraph
import app.nocturne.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Offline speech-to-text for audio that has no text of its own (radio, podcasts). */
interface SpeechEngine {
    val modelReady: Boolean
    val modelSizeMb: Int
    suspend fun download(progress: (Float) -> Unit): Boolean
    /** [pcm] is 16 kHz mono; returns (offset ms, text) segments. */
    fun transcribe(pcm: FloatArray): List<Pair<Long, String>>
}

expect fun speechEngine(): SpeechEngine?

/** Turns what's playing into text as it plays, a few seconds behind. */
object LiveTranscript {
    private const val RATE = 16_000
    private const val CHUNK = RATE * 6

    private val _lines = MutableStateFlow<List<LyricLine>>(emptyList())
    val lines: StateFlow<List<LyricLine>> = _lines.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val engine by lazy { speechEngine() }
    val supported: Boolean get() = engine != null
    val modelReady: Boolean get() = engine?.modelReady == true
    val modelSizeMb: Int get() = engine?.modelSizeMb ?: 0

    private var job: Job? = null
    private var trackKey: String? = null

    suspend fun download(progress: (Float) -> Unit) = engine?.download(progress) ?: false

    fun start(track: Track) {
        val e = engine ?: return
        if (!e.modelReady) return
        if (trackKey != track.key) { _lines.value = emptyList(); trackKey = track.key }
        if (job?.isActive == true) return
        val graph = AppGraph.instance
        val queue = Channel<FloatArray>(64, BufferOverflow.DROP_OLDEST)
        graph.engine.setPcmTap { pcm, n -> queue.trySend(pcm.copyOf(n)) }
        job = graph.scope.launch {
            val buf = FloatArray(CHUNK)
            var filled = 0
            var at = 0L
            for (piece in queue) {
                if (filled == 0) at = graph.player.state.value.positionMs
                val take = minOf(piece.size, CHUNK - filled)
                piece.copyInto(buf, filled, 0, take)
                filled += take
                if (filled < CHUNK) continue
                filled = 0
                val chunk = buf.copyOf()
                _busy.value = true
                val segs = withContext(Dispatchers.Default) { runCatching { e.transcribe(chunk) }.getOrDefault(emptyList()) }
                _busy.value = false
                // skip what piled up while we were busy, so the text stays close to the sound
                while (queue.tryReceive().isSuccess) Unit
                val add = segs.map { (off, text) -> LyricLine(at + off, text.trim()) }
                    .filter { it.text.isNotEmpty() && !it.text.startsWith("[") && !it.text.startsWith("(") }
                if (add.isNotEmpty()) _lines.value = (_lines.value + add).takeLast(400)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _busy.value = false
        runCatching { AppGraph.instance.engine.setPcmTap(null) }
    }
}
