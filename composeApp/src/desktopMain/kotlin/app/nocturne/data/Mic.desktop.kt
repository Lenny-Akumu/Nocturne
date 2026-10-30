package app.nocturne.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem

actual suspend fun recordMicWav(seconds: Int): ByteArray? = withContext(Dispatchers.IO) {
    runCatching {
        val rate = 44100
        val fmt = AudioFormat(rate.toFloat(), 16, 1, true, false)
        val line = AudioSystem.getTargetDataLine(fmt)
        line.open(fmt)
        line.start()
        val total = rate * 2 * seconds
        val pcm = ByteArray(total)
        var got = 0
        while (got < total) {
            val n = line.read(pcm, got, minOf(4096, total - got))
            if (n <= 0) break
            got += n
        }
        line.stop(); line.close()
        pcm16ToWav(pcm.copyOf(got), rate)
    }.getOrNull()
}

actual fun hasMicAccess(): Boolean = true

actual fun requestMicAccess(onResult: (Boolean) -> Unit) = onResult(true)
