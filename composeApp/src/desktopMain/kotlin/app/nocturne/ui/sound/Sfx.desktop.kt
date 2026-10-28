package app.nocturne.ui.sound

import java.io.ByteArrayInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.FloatControl
import javax.sound.sampled.LineEvent
import kotlin.math.log10

actual fun playClip(name: String, wav: ByteArray, volume: Float) {
    Thread {
        runCatching {
            val clip = AudioSystem.getClip()
            clip.open(AudioSystem.getAudioInputStream(ByteArrayInputStream(wav)))
            (clip.getControl(FloatControl.Type.MASTER_GAIN) as? FloatControl)?.let { g ->
                g.value = (20 * log10(volume.coerceIn(0.01f, 1f))).coerceIn(g.minimum, g.maximum)
            }
            clip.addLineListener { if (it.type == LineEvent.Type.STOP) clip.close() }
            clip.start()
        }
    }.apply { isDaemon = true }.start()
}
