package app.nocturne.data

import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import app.nocturne.AndroidContext
import app.nocturne.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// Android has no MP3 encoder built in: the song is saved in the format it streams in (M4A or Opus), which plays everywhere.
actual val exportLabel: String = "Save to device"

actual suspend fun exportAudio(track: Track, file: String, ext: String): String? = withContext(Dispatchers.IO) {
    val ctx = AndroidContext.app
    val name = listOfNotNull(track.artistNames.takeIf { it.isNotBlank() }, track.title).joinToString(" - ")
        .replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(150).ifBlank { "track" } + "." + ext
    val mime = when (ext) { "mp3" -> "audio/mpeg"; "webm" -> "audio/webm"; "ogg" -> "audio/ogg"; else -> "audio/mp4" }
    if (Build.VERSION.SDK_INT >= 29) {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, name)
            put(MediaStore.Audio.Media.MIME_TYPE, mime)
            put(MediaStore.Audio.Media.TITLE, track.title)
            put(MediaStore.Audio.Media.ARTIST, track.artistNames)
            track.album?.let { put(MediaStore.Audio.Media.ALBUM, it) }
            put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/Nocturne")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val resolver = ctx.contentResolver
        val uri = resolver.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values) ?: error("Couldn't create the file")
        resolver.openOutputStream(uri)!!.use { out -> File(file).inputStream().use { it.copyTo(out) } }
        values.clear()
        values.put(MediaStore.Audio.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        "Music/Nocturne/$name"
    } else {
        // older Android: the app's own Music folder needs no storage permission
        val dir = File(ctx.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "Nocturne").apply { mkdirs() }
        val out = File(dir, name)
        File(file).copyTo(out, overwrite = true)
        out.absolutePath
    }
}
