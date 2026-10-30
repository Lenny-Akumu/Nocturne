package app.nocturne.data

/** Wraps raw little-endian 16-bit PCM in a WAV header. */
fun pcm16ToWav(pcm: ByteArray, rate: Int, channels: Int = 1): ByteArray {
    val out = ByteArray(44 + pcm.size)
    fun put32(at: Int, v: Int) { for (i in 0 until 4) out[at + i] = (v shr (8 * i)).toByte() }
    fun put16(at: Int, v: Int) { out[at] = v.toByte(); out[at + 1] = (v shr 8).toByte() }
    "RIFF".encodeToByteArray().copyInto(out, 0)
    put32(4, 36 + pcm.size)
    "WAVEfmt ".encodeToByteArray().copyInto(out, 8)
    put32(16, 16); put16(20, 1); put16(22, channels); put32(24, rate)
    put32(28, rate * channels * 2); put16(32, channels * 2); put16(34, 16)
    "data".encodeToByteArray().copyInto(out, 36)
    put32(40, pcm.size)
    pcm.copyInto(out, 44)
    return out
}
