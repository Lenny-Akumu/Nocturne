package app.nocturne.data

import kotlinx.coroutines.withContext
import app.nocturne.FileStore
import app.nocturne.model.ResolvedStream
import app.nocturne.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class DownloadEntry(val track: Track, val path: String, val bytes: Long = 0)

expect fun downloadsDir(): String
expect fun fileUri(path: String): String
expect fun deleteFile(path: String)
expect fun fileExists(path: String): Boolean
expect fun moveFile(from: String, to: String): Boolean
expect suspend fun downloadToFile(url: String, path: String, onProgress: (Float) -> Unit): Long

class Downloads(private val scope: CoroutineScope, private val resolve: suspend (Track) -> ResolvedStream) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _items = MutableStateFlow(load())
    val items: StateFlow<Map<String, DownloadEntry>> = _items.asStateFlow()
    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val progress: StateFlow<Map<String, Float>> = _progress.asStateFlow()
    private val _errors = MutableStateFlow<Map<String, String>>(emptyMap())
    val errors: StateFlow<Map<String, String>> = _errors.asStateFlow()
    private val gate = Semaphore(2)

    private fun load(): Map<String, DownloadEntry> =
        FileStore.read(FILE)?.let { runCatching { json.decodeFromString(ListSerializer(DownloadEntry.serializer()), it) }.getOrNull() }
            .orEmpty().filter { fileExists(it.path) }.associateBy { it.track.key }

    private fun save() = FileStore.write(FILE, json.encodeToString(ListSerializer(DownloadEntry.serializer()), _items.value.values.toList()))

    fun uriFor(track: Track): String? = _items.value[track.key]?.let { fileUri(it.path) }

    fun isDownloaded(track: Track) = _items.value.containsKey(track.key)

    fun enqueue(tracks: List<Track>) {
        tracks.filter { !isDownloaded(it) && it.key !in _progress.value }.forEach { track ->
            _progress.update { it + (track.key to 0f) }
            _errors.update { it - track.key }
            scope.launch(Dispatchers.Default) {
                gate.withPermit {
                    runCatching {
                        val stream = resolve(track)
                        if (stream.isHls) error("Streaming-only track")
                        val ext = when {
                            stream.mimeType?.contains("webm") == true -> "webm"
                            stream.mimeType?.contains("mp4") == true || stream.mimeType?.contains("m4a") == true -> "m4a"
                            stream.mimeType?.contains("mpeg") == true -> "mp3"
                            stream.mimeType?.contains("ogg") == true -> "ogg"
                            else -> "audio"
                        }
                        val safe = track.key.replace(Regex("[^A-Za-z0-9_.-]"), "_").take(120)
                        val path = "${downloadsDir()}/$safe.$ext"
                        val bytes = downloadToFile(stream.url, path) { p -> _progress.update { it + (track.key to p) } }
                        _items.update { it + (track.key to DownloadEntry(track, path, bytes)) }
                        save()
                    }.onFailure { e -> _errors.update { it + (track.key to (e.message ?: "Download failed")) } }
                    _progress.update { it - track.key }
                }
            }
        }
    }

    /** Moves every downloaded file into [dir]; returns how many moved. */
    suspend fun moveAll(dir: String, onProgress: (Int, Int) -> Unit = { _, _ -> }): Int = withContext(Dispatchers.Default) {
        val all = _items.value.values.toList()
        var moved = 0
        all.forEachIndexed { i, e ->
            val target = dir.trimEnd('/', '\\') + "/" + e.path.substringAfterLast('/').substringAfterLast('\\')
            if (target != e.path && moveFile(e.path, target)) {
                moved++
                _items.update { it + (e.track.key to e.copy(path = target)) }
            }
            onProgress(i + 1, all.size)
        }
        save()
        moved
    }

    fun remove(track: Track) {
        _items.value[track.key]?.let { deleteFile(it.path) }
        _items.update { it - track.key }
        save()
    }

    fun totalBytes(): Long = _items.value.values.sumOf { it.bytes }

    companion object {
        private const val FILE = "downloads.json"
    }
}
