package app.nocturne.source

import app.nocturne.AppDirs
import app.nocturne.AppGraph
import io.github.givimad.whisperjni.WhisperContext
import io.github.givimad.whisperjni.WhisperFullParams
import io.github.givimad.whisperjni.WhisperJNI
import io.github.givimad.whisperjni.WhisperSamplingStrategy
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

actual fun speechEngine(): SpeechEngine? = WhisperEngine

/** whisper.cpp (multilingual base model, quantized) through whisper-jni. */
private object WhisperEngine : SpeechEngine {
    private const val URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin"
    private val file get() = File(AppDirs.root, "speech/ggml-base-q5_1.bin")
    private var whisper: WhisperJNI? = null
    private var ctx: WhisperContext? = null

    override val modelReady: Boolean get() = file.length() > 30_000_000
    override val modelSizeMb = 57

    override suspend fun download(progress: (Float) -> Unit): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            file.parentFile.mkdirs()
            val tmp = File(file.parentFile, file.name + ".part")
            AppGraph.instance.http.prepareGet(URL).execute { resp ->
                val total = resp.contentLength() ?: 60_000_000L
                val ch = resp.bodyAsChannel()
                val buf = ByteArray(64 * 1024)
                var done = 0L
                tmp.outputStream().use { out ->
                    while (!ch.isClosedForRead) {
                        val n = ch.readAvailable(buf, 0, buf.size)
                        if (n <= 0) continue
                        out.write(buf, 0, n)
                        done += n
                        progress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            if (tmp.length() < 30_000_000) error("incomplete")
            tmp.renameTo(file)
        }.getOrDefault(false)
    }

    @Synchronized
    private fun context(): Pair<WhisperJNI, WhisperContext>? {
        ctx?.let { c -> return whisper!! to c }
        if (!modelReady) return null
        return runCatching {
            WhisperJNI.loadLibrary()
            WhisperJNI.setLibraryLogger(null)
            val w = WhisperJNI()
            val c = w.init(file.toPath()) ?: return null
            whisper = w; ctx = c
            w to c
        }.getOrNull()
    }

    @Synchronized
    override fun transcribe(pcm: FloatArray): List<Pair<Long, String>> {
        val (w, c) = context() ?: return emptyList()
        val p = WhisperFullParams(WhisperSamplingStrategy.GREEDY).apply {
            language = "auto"
            translate = false
            noContext = true
            nThreads = (Runtime.getRuntime().availableProcessors() - 1).coerceIn(1, 4)
            printProgress = false
            printTimestamps = false
            suppressBlank = true
        }
        if (w.full(c, p, pcm, pcm.size) != 0) return emptyList()
        return (0 until w.fullNSegments(c)).map { i -> w.fullGetSegmentTimestamp0(c, i) * 10 to w.fullGetSegmentText(c, i) }
    }
}
