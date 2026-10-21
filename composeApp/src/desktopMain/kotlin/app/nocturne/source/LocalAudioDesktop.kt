package app.nocturne.source

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val audioExt = setOf("mp3", "flac", "m4a", "aac", "ogg", "opus", "wav", "wma", "aiff")
private val coverNames = listOf("cover.jpg", "folder.jpg", "cover.png", "front.jpg", "album.jpg")
private val trackNo = Regex("^\\d+\\.?\\s*-?\\s*")

actual fun hasLocalAudioAccess(): Boolean = true

actual fun requestLocalAudioAccess(onResult: (Boolean) -> Unit) = onResult(true)

actual suspend fun scanLocalAudio(folders: List<String>): List<LocalAudio> = withContext(Dispatchers.IO) {
    val roots = folders.ifEmpty { listOf(File(System.getProperty("user.home"), "Music").path) }
    roots.map(::File).filter { it.isDirectory }.flatMap { root ->
        root.walkTopDown().maxDepth(8).filter { it.isFile && it.extension.lowercase() in audioExt }.take(20_000).map { f ->
            val name = f.nameWithoutExtension.replace('_', ' ')
            val parts = name.split(" - ", limit = 2)
            val artist = if (parts.size == 2) parts[0].replace(trackNo, "").trim() else ""
            val title = (if (parts.size == 2) parts[1] else name).replace(trackNo, "").trim()
            val cover = coverNames.map { File(f.parentFile, it) }.firstOrNull { it.exists() }
            LocalAudio(f.toPath().toUri().toString(), title.ifBlank { name }, artist, f.parentFile?.name, 0, cover?.toPath()?.toUri()?.toString())
        }.toList()
    }.distinctBy { it.uri }
}
