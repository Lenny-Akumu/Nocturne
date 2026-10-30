package app.nocturne.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import app.nocturne.AndroidContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual fun hasMicAccess(): Boolean =
    AndroidContext.app.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

actual fun requestMicAccess(onResult: (Boolean) -> Unit) {
    app.nocturne.source.LocalAudioPermission.launcher?.invoke(Manifest.permission.RECORD_AUDIO, onResult) ?: onResult(false)
}

@SuppressLint("MissingPermission")
actual suspend fun recordMicWav(seconds: Int): ByteArray? = withContext(Dispatchers.IO) {
    if (!hasMicAccess()) return@withContext null
    runCatching {
        val rate = 44100
        val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, 8192))
        val total = rate * 2 * seconds
        val pcm = ByteArray(total)
        var got = 0
        rec.startRecording()
        while (got < total) {
            val n = rec.read(pcm, got, minOf(4096, total - got))
            if (n <= 0) break
            got += n
        }
        rec.stop(); rec.release()
        pcm16ToWav(pcm.copyOf(got), rate)
    }.getOrNull()
}
