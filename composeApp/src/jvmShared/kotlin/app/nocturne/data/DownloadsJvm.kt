package app.nocturne.data

import app.nocturne.AppDirs
import app.nocturne.source.InnerTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

actual fun downloadsDir(): String = File(AppDirs.root, "downloads").apply { mkdirs() }.absolutePath

actual fun fileUri(path: String): String = File(path).toPath().toUri().toString()

actual fun deleteFile(path: String) {
    runCatching { File(path).delete() }
}

actual fun fileExists(path: String): Boolean = File(path).isFile

private const val CHUNK = 4L * 1024 * 1024

actual suspend fun downloadToFile(url: String, path: String, onProgress: (Float) -> Unit): Long = withContext(Dispatchers.IO) {
    val part = File("$path.part")
    part.parentFile?.mkdirs()
    var total = -1L
    var done = 0L
    RandomAccessFile(part, "rw").use { out ->
        out.setLength(0)
        while (total < 0 || done < total) {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                setRequestProperty("User-Agent", InnerTube.USER_AGENT)
                setRequestProperty("Range", "bytes=$done-${done + CHUNK - 1}")
                connectTimeout = 15_000
                readTimeout = 30_000
            }
            val code = conn.responseCode
            if (code !in 200..299) error("HTTP $code")
            if (total < 0) {
                total = conn.getHeaderField("Content-Range")?.substringAfter('/')?.toLongOrNull()
                    ?: conn.contentLengthLong.takeIf { code == 200 } ?: -1L
            }
            var read = 0L
            conn.inputStream.use { input ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    read += n
                    done += n
                    if (total > 0) onProgress(done.toFloat() / total)
                }
            }
            if (read == 0L || code == 200) break
        }
    }
    val target = File(path)
    target.delete()
    if (!part.renameTo(target)) error("Couldn't save the file")
    target.length()
}
