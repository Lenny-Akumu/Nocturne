package app.nocturne.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Runs the shared [Dsp] (normalize, spatial, 8D, vocal remover) on 16-bit stereo PCM. */
@UnstableApi
class FxAudioProcessor : BaseAudioProcessor() {
    @Volatile var normalize = false
    @Volatile var spatial = false
    @Volatile var eightD = false
    @Volatile var vocalRemover = false
    @Volatile private var dsp: Dsp? = null
    private var floats = FloatArray(0)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount != 2) return AudioProcessor.AudioFormat.NOT_SET
        if (dsp?.rate != inputAudioFormat.sampleRate) dsp = Dsp(inputAudioFormat.sampleRate).apply { analyzer.latencyMs = 250 } // ≈ AudioTrack buffer
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining() and 3.inv()
        if (size == 0) return
        val d = dsp ?: return
        d.normalize = normalize; d.spatial = spatial; d.eightD = eightD; d.vocalRemover = vocalRemover
        val n = size / 2
        if (floats.size < n) floats = FloatArray(n)
        val src = inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until n) floats[i] = src.short / 32768f
        d.process(floats, n / 2)
        val out = replaceOutputBuffer(size)
        for (i in 0 until n) out.putShort((floats[i] * 32767f).toInt().toShort())
        out.flip()
    }

    fun levels(): FloatArray? = dsp?.analyzer?.levels()

    override fun onReset() {
        dsp = null
    }
}
