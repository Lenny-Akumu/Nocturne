package app.nocturne.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@UnstableApi
class FxAudioProcessor : BaseAudioProcessor() {
    @Volatile var eightD = false
    @Volatile var vocalRemover = false
    private var frames = 0L
    private var rate = 44100

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount != 2) return AudioProcessor.AudioFormat.NOT_SET
        rate = inputAudioFormat.sampleRate
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val out = replaceOutputBuffer(size)
        val pan = eightD
        val karaoke = vocalRemover
        val src = inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        while (src.remaining() >= 4) {
            var l = src.short.toFloat()
            var r = src.short.toFloat()
            if (karaoke) {
                val side = (l - r) * 0.9f
                l = side
                r = side
            }
            if (pan) {
                val t = frames.toDouble() / rate
                val angle = ((sin(2 * PI * t / 9.0) + 1) * PI / 4).toFloat()
                val m = (l + r) * 0.5f
                l = m * cos(angle) * 1.35f
                r = m * sin(angle) * 1.35f
            }
            out.putShort(l.coerceIn(-32768f, 32767f).toInt().toShort())
            out.putShort(r.coerceIn(-32768f, 32767f).toInt().toShort())
            frames++
        }
        out.flip()
    }

    override fun onReset() {
        frames = 0
    }
}
