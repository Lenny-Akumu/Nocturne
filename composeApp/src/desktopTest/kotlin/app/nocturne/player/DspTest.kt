package app.nocturne.player

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

class DspTest {
    private val rate = 48000

    private fun sine(freq: Double, ampL: Double, ampR: Double, seconds: Double = 2.0): FloatArray {
        val n = (rate * seconds).toInt()
        return FloatArray(n * 2) { i ->
            val v = sin(2 * PI * freq * (i / 2) / rate)
            (if (i % 2 == 0) v * ampL else v * ampR).toFloat()
        }
    }

    private fun run(dsp: Dsp, buf: FloatArray): FloatArray {
        val out = buf.copyOf()
        var off = 0
        val chunk = 1024
        val tmp = FloatArray(chunk * 2)
        while (off < out.size) {
            val len = minOf(chunk * 2, out.size - off)
            out.copyInto(tmp, 0, off, off + len)
            dsp.process(tmp, len / 2)
            tmp.copyInto(out, off, 0, len)
            off += len
        }
        return out
    }

    /** RMS (dB) of the last half second, both channels or one. */
    private fun rmsDb(buf: FloatArray, channel: Int = -1): Double {
        val from = buf.size - rate // last 0.5 s of interleaved stereo
        var sum = 0.0
        var n = 0
        for (i in from until buf.size) {
            if (channel >= 0 && i % 2 != channel) continue
            sum += buf[i] * buf[i]; n++
        }
        return 20 * log10(sqrt(sum / n) + 1e-12)
    }

    @Test
    fun vocalRemoverCancelsCenter() {
        val dsp = Dsp(rate).apply { vocalRemover = true }
        val center = sine(1000.0, 0.5, 0.5)
        val drop = rmsDb(center) - rmsDb(run(dsp, center))
        assertTrue(drop > 30, "center sine only dropped $drop dB")

        val dsp2 = Dsp(rate).apply { vocalRemover = true }
        val left = sine(1000.0, 0.5, 0.0)
        val kept = rmsDb(left, 0) - rmsDb(run(dsp2, left), 0)
        assertTrue(abs(kept) < 6, "left-only sine changed by $kept dB")
    }

    @Test
    fun normalizeRaisesQuietSignalWithoutClipping() {
        val dsp = Dsp(rate).apply { normalize = true }
        val quiet = sine(440.0, 0.0316, 0.0316, 4.0) // -30 dBFS peak
        val out = run(dsp, quiet)
        val gain = rmsDb(out) - rmsDb(quiet)
        assertTrue(gain > 6, "normalize gained only $gain dB")
        assertTrue(out.all { abs(it) <= 1f })

        val loud = Dsp(rate).apply { normalize = true; setEq(true, List(Eq.bands.size) { 12f }, autoPreamp = false) }
        val hot = run(loud, sine(1000.0, 1.0, 1.0))
        assertTrue(hot.all { abs(it) <= 1f }, "limiter let samples above 0 dBFS through")
    }

    @Test
    fun eqBoostRaisesBand() {
        val i = Eq.bands.indexOf(1000f)
        val dsp = Dsp(rate).apply { setEq(true, List(Eq.bands.size) { if (it == i) 6f else 0f }, autoPreamp = false) }
        val input = sine(1000.0, 0.1, 0.1)
        val gain = rmsDb(run(dsp, input)) - rmsDb(input)
        assertTrue(abs(gain - 6) < 1, "1 kHz +6 dB band gave $gain dB")

        val off = Dsp(rate).apply { setEq(false, List(Eq.bands.size) { 6f }) }
        val flat = rmsDb(run(off, input)) - rmsDb(input)
        assertTrue(abs(flat) < 0.1, "disabled EQ changed level by $flat dB")
    }

    @Test
    fun spatialWidensStereo() {
        val input = sine(500.0, 0.4, 0.2)
        fun side(b: FloatArray): Double {
            var s = 0.0
            for (k in b.size - rate until b.size step 2) { val d = (b[k] - b[k + 1]) / 2.0; s += d * d }
            return s
        }
        val dsp = Dsp(rate).apply { spatial = true }
        val ratio = side(run(dsp, input)) / side(input)
        assertTrue(ratio > 1.5, "side energy ratio $ratio")
    }

    @Test
    fun eightDMovesSound() {
        val dsp = Dsp(rate).apply { eightD = true }
        val out = run(dsp, sine(500.0, 0.3, 0.3, 6.0))
        val l = rmsDb(out, 0)
        val r = rmsDb(out, 1)
        assertTrue(abs(l - r) > 3, "8D left/right differ by only ${abs(l - r)} dB")
        assertTrue(out.all { abs(it) <= 1f })
    }

    @Test
    fun volumeScales() {
        val dsp = Dsp(rate).apply { volume = 0.5f }
        val input = sine(440.0, 0.2, 0.2)
        val gain = rmsDb(run(dsp, input)) - rmsDb(input)
        assertTrue(abs(gain + 6.02) < 0.2, "volume 0.5 gave $gain dB")
    }
}
