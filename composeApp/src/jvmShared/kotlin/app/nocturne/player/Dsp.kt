package app.nocturne.player

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Stereo float DSP chain (interleaved L/R, -1..1): EQ → vocal remover → spatial → 8D → normalize → limiter → volume. */
class Dsp(val rate: Int, val bands: List<Float> = Eq.bands) {
    @Volatile var eqEnabled = false
    @Volatile var eqGains = FloatArray(bands.size)
    @Volatile var preampDb = 0f
    @Volatile var normalize = false
    @Volatile var spatial = false
    @Volatile var eightD = false
    @Volatile var vocalRemover = false
    @Volatile var volume = 1f

    fun setEq(enabled: Boolean, gains: List<Float>, autoPreamp: Boolean = true) {
        eqGains = FloatArray(bands.size) { gains.getOrElse(it) { 0f } }
        preampDb = if (autoPreamp) -0.4f * max(0f, eqGains.maxOrNull() ?: 0f) else 0f
        eqEnabled = enabled
    }

    private fun coef(ms: Double) = exp(-1.0 / (ms * 0.001 * rate)).toFloat()
    private val fade = 1f / (0.03f * rate) // effect on/off ramp: 30 ms
    private val smooth = coef(20.0)

    // EQ: one peaking biquad per band, coefficients recomputed per block from smoothed gains
    private val curDb = FloatArray(bands.size)
    private var curPre = 0f
    private val eqF = Array(bands.size) { Biquad() }
    private val eqOn = BooleanArray(bands.size)
    private var eqActive = false
    private var preLin = 1f
    private var eqBlock = 0

    // vocal remover: mid low-passed below ~150 Hz is kept (4th-order), the rest of the mid is cancelled
    private val bassA = Biquad().apply { lowPass(150.0, rate) }
    private val bassB = Biquad().apply { lowPass(150.0, rate) }
    private var vocalMix = 0f

    private var spatialMix = 0f
    private val haas = Delay(rate / 20 + 2)
    private val haasDelay = 0.012f * rate

    private var eightMix = 0f
    private var lfo = 0.0
    private val lfoStep = 2 * PI * 0.12 / rate
    private val dl = Delay(64)
    private val dr = Delay(64)

    private var normMix = 0f
    private var level = 0f
    private var agc = 1f
    private val levelUp = coef(10.0)
    private val levelDown = coef(300.0)
    private val agcDown = coef(10.0)
    private val agcUp = coef(300.0)

    private var lim = 1f
    private val limRelease = coef(80.0)
    private var vol = 1f

    /** Band levels for the EQ bars, taken after the effects (pre-volume). */
    val analyzer = Analyzer(rate)

    fun reset() {
        analyzer.reset()
        eqF.forEach { it.clear() }
        bassA.clear(); bassB.clear(); haas.clear(); dl.clear(); dr.clear()
        lim = 1f
    }

    fun process(buf: FloatArray, frames: Int) {
        val eqTarget = eqGains
        val eqOnNow = eqEnabled
        val wantVocal = if (vocalRemover) 1f else 0f
        val wantSpatial = if (spatial) 1f else 0f
        val want8d = if (eightD) 1f else 0f
        val wantNorm = if (normalize) 1f else 0f
        val wantVol = volume.coerceIn(0f, 2f)
        analyzer.begin(System.nanoTime())
        var i = 0
        for (n in 0 until frames) {
            if (eqBlock-- <= 0) {
                eqBlock = 32
                updateEq(eqTarget, eqOnNow)
            }
            var l = buf[i]
            var r = buf[i + 1]

            if (eqActive) {
                l *= preLin; r *= preLin
                for (b in eqF.indices) if (eqOn[b]) {
                    l = eqF[b].left(l); r = eqF[b].right(r)
                }
            }

            vocalMix = ramp(vocalMix, wantVocal)
            if (vocalMix > 0f) {
                val m = (l + r) * 0.5f
                val s = (l - r) * 0.5f * 1.41f
                val bass = bassB.left(bassA.left(m))
                l += (bass + s - l) * vocalMix
                r += (bass - s - r) * vocalMix
            } else if (wantVocal == 0f) { bassA.clear(); bassB.clear() }

            spatialMix = ramp(spatialMix, wantSpatial)
            val m0 = (l + r) * 0.5f
            haas.push(m0)
            if (spatialMix > 0f) {
                val s = (l - r) * 0.5f * (1f + 0.6f * spatialMix) + haas.tap(haasDelay) * 0.25f * spatialMix
                val m = m0 * (1f - 0.1f * spatialMix)
                l = m + s; r = m - s
            }

            eightMix = ramp(eightMix, want8d)
            if (eightMix > 0f) {
                lfo += lfoStep
                if (lfo > 2 * PI) lfo -= 2 * PI
                val p = (sin(lfo) * 0.85).toFloat()
                val a = (p + 1f) * (PI / 4).toFloat()
                val m = (l + r) * 0.5f
                val s = (l - r) * 0.5f
                dl.push(m); dr.push(m)
                // far ear: quieter and up to ~0.6 ms later
                val ol = dl.tap(max(0f, p) * 0.0006f * rate) * cos(a) * 1.41f + s * 0.4f
                val orr = dr.tap(max(0f, -p) * 0.0006f * rate) * sin(a) * 1.41f - s * 0.4f
                l += (ol - l) * eightMix
                r += (orr - r) * eightMix
            }

            normMix = ramp(normMix, wantNorm)
            val ms = (l * l + r * r) * 0.5f
            level = if (ms > level) ms + (level - ms) * levelUp else ms + (level - ms) * levelDown
            if (normMix > 0f) {
                val rms = sqrt(level)
                val target = if (rms < 0.003f) agc else (TARGET_RMS / rms).coerceIn(0.25f, 4f) // ~-50 dBFS gate, max +12 dB
                agc = if (target < agc) target + (agc - target) * agcDown else target + (agc - target) * agcUp
                val g = 1f + (agc - 1f) * normMix
                l *= g; r *= g
            } else agc = 1f

            // peak limiter: instant attack, 80 ms release, -0.3 dBFS ceiling
            val peak = max(abs(l), abs(r))
            lim = 1f + (lim - 1f) * limRelease
            if (peak * lim > CEIL) lim = CEIL / peak
            analyzer.feed((l + r) * 0.5f * lim)
            vol = wantVol + (vol - wantVol) * smooth
            val g = lim * vol
            buf[i] = (l * g).coerceIn(-1f, 1f)
            buf[i + 1] = (r * g).coerceIn(-1f, 1f)
            i += 2
        }
    }

    private fun ramp(v: Float, to: Float) = if (v < to) minOf(to, v + fade) else if (v > to) maxOf(to, v - fade) else v

    private fun updateEq(target: FloatArray, on: Boolean) {
        val k = 0.25f // per 32-sample block ≈ 3 ms time constant
        val pre = if (on) preampDb else 0f
        curPre += (pre - curPre) * k
        if (abs(curPre - pre) < 0.01f) curPre = pre
        for (b in bands.indices) {
            val t = if (on) target.getOrElse(b) { 0f } else 0f
            val before = curDb[b]
            curDb[b] += (t - curDb[b]) * k
            if (abs(curDb[b] - t) < 0.01f) curDb[b] = t
            val active = curDb[b] != 0f
            if (!active) { if (eqOn[b]) eqF[b].clear(); eqOn[b] = false; continue }
            if (!eqOn[b] || before != curDb[b]) eqF[b].peaking(bands[b].toDouble(), curDb[b].toDouble(), 1.0, rate)
            eqOn[b] = true
        }
        preLin = db(curPre)
        eqActive = curPre != 0f || eqOn.any { it }
    }

    companion object {
        const val TARGET_RMS = 0.16f // ≈ -16 dBFS RMS, roughly -14 LUFS for music
        const val CEIL = 0.966f
        fun db(v: Float) = 10.0.pow(v / 20.0).toFloat()
    }
}

/** RBJ biquad with independent L/R state. */
internal class Biquad {
    private var b0 = 1.0; private var b1 = 0.0; private var b2 = 0.0; private var a1 = 0.0; private var a2 = 0.0
    private var l1 = 0.0; private var l2 = 0.0; private var r1 = 0.0; private var r2 = 0.0

    fun peaking(f: Double, gainDb: Double, q: Double, rate: Int) {
        val a = 10.0.pow(gainDb / 40)
        val w = 2 * PI * f.coerceAtMost(rate * 0.45) / rate
        val alpha = sin(w) / (2 * q)
        val a0 = 1 + alpha / a
        b0 = (1 + alpha * a) / a0; b1 = -2 * cos(w) / a0; b2 = (1 - alpha * a) / a0
        a1 = -2 * cos(w) / a0; a2 = (1 - alpha / a) / a0
    }

    // constant 0 dB peak gain
    fun bandPass(f: Double, rate: Int, q: Double) {
        val w = 2 * PI * f.coerceAtMost(rate * 0.45) / rate
        val alpha = sin(w) / (2 * q)
        val a0 = 1 + alpha
        b0 = alpha / a0; b1 = 0.0; b2 = -alpha / a0
        a1 = -2 * cos(w) / a0; a2 = (1 - alpha) / a0
    }

    fun lowPass(f: Double, rate: Int, q: Double = 0.7071) {
        val w = 2 * PI * f / rate
        val alpha = sin(w) / (2 * q)
        val a0 = 1 + alpha
        b0 = (1 - cos(w)) / 2 / a0; b1 = (1 - cos(w)) / a0; b2 = b0
        a1 = -2 * cos(w) / a0; a2 = (1 - alpha) / a0
    }

    // transposed direct form II
    fun left(x: Float): Float {
        val y = b0 * x + l1
        l1 = b1 * x - a1 * y + l2
        l2 = b2 * x - a2 * y
        return y.toFloat()
    }

    fun right(x: Float): Float {
        val y = b0 * x + r1
        r1 = b1 * x - a1 * y + r2
        r2 = b2 * x - a2 * y
        return y.toFloat()
    }

    fun clear() { l1 = 0.0; l2 = 0.0; r1 = 0.0; r2 = 0.0 }
}

/** Ring buffer with fractional (linear) taps. */
internal class Delay(size: Int) {
    private val buf = FloatArray(size)
    private var pos = 0

    fun push(x: Float) {
        pos = (pos + 1) % buf.size
        buf[pos] = x
    }

    fun tap(samples: Float): Float {
        val d = samples.coerceIn(0f, (buf.size - 2).toFloat())
        val i = d.toInt()
        val f = d - i
        val a = buf[(pos - i + buf.size) % buf.size]
        val b = buf[(pos - i - 1 + buf.size) % buf.size]
        return a + (b - a) * f
    }

    fun clear() = buf.fill(0f)
}

/**
 * 5-band level meter (60 Hz…10 kHz) for the animated EQ icon. Fed per sample on the audio thread; each ~33 ms
 * snapshot is stamped with when it will be heard ([latencyMs] = output buffer), so [levels] lines up with the speaker.
 */
class Analyzer(private val rate: Int) {
    @Volatile var latencyMs = 0
    private val f = Array(FREQS.size) { Biquad().apply { bandPass(FREQS[it], rate, 1.4) } }
    private val f2 = Array(FREQS.size) { Biquad().apply { bandPass(FREQS[it], rate, 1.4) } } // cascaded: 4th order
    private val acc = FloatArray(FREQS.size)
    private val peak = FloatArray(FREQS.size) { -30f }
    private val hop = max(1, rate / 30)
    private var count = 0
    private var base = 0L
    private var frame = 0L
    private val ring = arrayOfNulls<FloatArray>(RING)
    private val due = LongArray(RING)
    @Volatile private var head = -1

    fun begin(now: Long) { base = now; frame = 0 }

    fun reset() { f.forEach { it.clear() }; f2.forEach { it.clear() }; acc.fill(0f); count = 0 }

    fun feed(x: Float) {
        for (b in f.indices) { val y = f2[b].left(f[b].left(x)); acc[b] += y * y }
        frame++
        if (++count < hop) return
        val out = FloatArray(FREQS.size)
        var top = -100f
        for (b in f.indices) {
            val db = 10f * log10(acc[b] / count + 1e-10f)
            // per-band normalization: adaptive peak that falls ~2.4 dB/s, floored so silence stays low
            peak[b] = max(db, max(peak[b] - 0.08f, -42f))
            top = max(top, peak[b])
            out[b] = db
            acc[b] = 0f
        }
        // quiet bands get up to 15 dB of lift, not more, so filter leakage doesn't read as full scale
        for (b in f.indices) out[b] = ((out[b] - max(peak[b], top - 15f) + RANGE) / RANGE).coerceIn(0f, 1f)
        count = 0
        val i = (head + 1) and (RING - 1)
        ring[i] = out
        due[i] = base + frame * 1_000_000_000L / rate + latencyMs * 1_000_000L
        head = i
    }

    /** Latest snapshot due by now (0..1 per band); zeros once stale, null before any audio. */
    fun levels(now: Long = System.nanoTime()): FloatArray? {
        val h = head
        if (h < 0) return null
        for (k in 0 until RING) {
            val i = (h - k) and (RING - 1)
            val d = due[i]
            val v = ring[i] ?: break
            if (d <= now) return if (now - d > 250_000_000L) SILENT else v
        }
        return SILENT
    }

    companion object {
        val FREQS = doubleArrayOf(60.0, 250.0, 1000.0, 4000.0, 10000.0)
        private const val RANGE = 26f
        private const val RING = 32
        private val SILENT = FloatArray(5)
    }
}
