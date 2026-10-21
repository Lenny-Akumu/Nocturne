package app.nocturne.source

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import app.nocturne.AndroidContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object LocalAudioPermission {
    var launcher: ((String, (Boolean) -> Unit) -> Unit)? = null
}

private val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

actual fun hasLocalAudioAccess(): Boolean =
    AndroidContext.app.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

actual fun requestLocalAudioAccess(onResult: (Boolean) -> Unit) {
    LocalAudioPermission.launcher?.invoke(permission, onResult) ?: onResult(false)
}

actual suspend fun scanLocalAudio(folders: List<String>): List<LocalAudio> = withContext(Dispatchers.IO) {
    val resolver = AndroidContext.app.contentResolver
    val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val cols = arrayOf(
        MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.ALBUM_ID,
    )
    val out = mutableListOf<LocalAudio>()
    resolver.query(uri, cols, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, null)?.use { c ->
        while (c.moveToNext()) {
            val id = c.getLong(0)
            val duration = c.getLong(4)
            if (duration in 1..29_999) continue
            out += LocalAudio(
                uri = ContentUris.withAppendedId(uri, id).toString(),
                title = c.getString(1) ?: "Unknown",
                artist = c.getString(2) ?: "",
                album = c.getString(3),
                durationMs = duration,
                art = "content://media/external/audio/albumart/${c.getLong(5)}",
            )
        }
    }
    out
}
