package app.nocturne.ui.sound

import android.media.AudioAttributes
import android.media.MediaPlayer
import app.nocturne.AndroidContext
import java.io.File

actual fun playClip(name: String, wav: ByteArray, volume: Float) {
    val ctx = AndroidContext.app
    val f = File(ctx.cacheDir, "sfx-$name.wav")
    if (!f.exists() || f.length() != wav.size.toLong()) f.writeBytes(wav)
    MediaPlayer().apply {
        setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        setDataSource(f.absolutePath)
        setVolume(volume, volume)
        setOnCompletionListener { it.release() }
        setOnPreparedListener { it.start() }
        prepareAsync()
    }
}
