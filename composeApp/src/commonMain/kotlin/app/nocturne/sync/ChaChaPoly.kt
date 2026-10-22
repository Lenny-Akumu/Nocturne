package app.nocturne.sync

/** ChaCha20-Poly1305 AEAD (RFC 8439) in plain Kotlin, so every platform shares one cipher. */
object ChaChaPoly {
    const val KEY = 32
    const val NONCE = 12
    const val TAG = 16

    /** ciphertext || tag */
    fun seal(key: ByteArray, nonce: ByteArray, plain: ByteArray, aad: ByteArray = ByteArray(0)): ByteArray {
        require(key.size == KEY && nonce.size == NONCE)
        val out = ByteArray(plain.size + TAG)
        xor(key, nonce, 1, plain, plain.size, out)
        mac(key, nonce, aad, out, plain.size).copyInto(out, plain.size)
        return out
    }

    /** Plaintext, or null when the tag doesn't match. */
    fun open(key: ByteArray, nonce: ByteArray, data: ByteArray, aad: ByteArray = ByteArray(0)): ByteArray? {
        if (key.size != KEY || nonce.size != NONCE || data.size < TAG) return null
        val n = data.size - TAG
        val tag = mac(key, nonce, aad, data, n)
        var diff = 0
        for (i in 0 until TAG) diff = diff or (tag[i].toInt() xor data[n + i].toInt())
        if (diff != 0) return null
        val out = ByteArray(n)
        xor(key, nonce, 1, data, n, out)
        return out
    }

    private fun mac(key: ByteArray, nonce: ByteArray, aad: ByteArray, ct: ByteArray, ctLen: Int): ByteArray {
        val block = ByteArray(64)
        chachaBlock(key, nonce, 0, block)
        val p = Poly1305(block)
        p.update(aad, 0, aad.size); p.pad()
        p.update(ct, 0, ctLen); p.pad()
        val lens = ByteArray(16)
        putLe64(lens, 0, aad.size.toLong()); putLe64(lens, 8, ctLen.toLong())
        p.update(lens, 0, 16)
        return p.finish()
    }

    private fun xor(key: ByteArray, nonce: ByteArray, counter: Int, src: ByteArray, len: Int, dst: ByteArray) {
        val ks = ByteArray(64)
        var off = 0
        var c = counter
        while (off < len) {
            chachaBlock(key, nonce, c++, ks)
            val n = minOf(64, len - off)
            for (i in 0 until n) dst[off + i] = (src[off + i].toInt() xor ks[i].toInt()).toByte()
            off += n
        }
    }

    private fun le32(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xff) or ((b[o + 1].toInt() and 0xff) shl 8) or ((b[o + 2].toInt() and 0xff) shl 16) or ((b[o + 3].toInt() and 0xff) shl 24)

    private fun putLe64(b: ByteArray, o: Int, v: Long) { for (i in 0 until 8) b[o + i] = (v ushr (8 * i)).toByte() }

    internal fun chachaBlock(key: ByteArray, nonce: ByteArray, counter: Int, out: ByteArray) {
        val s = IntArray(16)
        s[0] = 0x61707865; s[1] = 0x3320646e; s[2] = 0x79622d32; s[3] = 0x6b206574
        for (i in 0 until 8) s[4 + i] = le32(key, i * 4)
        s[12] = counter
        for (i in 0 until 3) s[13 + i] = le32(nonce, i * 4)
        val x = s.copyOf()
        fun qr(a: Int, b: Int, c: Int, d: Int) {
            x[a] += x[b]; x[d] = (x[d] xor x[a]).rotateLeft(16)
            x[c] += x[d]; x[b] = (x[b] xor x[c]).rotateLeft(12)
            x[a] += x[b]; x[d] = (x[d] xor x[a]).rotateLeft(8)
            x[c] += x[d]; x[b] = (x[b] xor x[c]).rotateLeft(7)
        }
        repeat(10) {
            qr(0, 4, 8, 12); qr(1, 5, 9, 13); qr(2, 6, 10, 14); qr(3, 7, 11, 15)
            qr(0, 5, 10, 15); qr(1, 6, 11, 12); qr(2, 7, 8, 13); qr(3, 4, 9, 14)
        }
        for (i in 0 until 16) {
            val v = x[i] + s[i]
            out[i * 4] = v.toByte(); out[i * 4 + 1] = (v ushr 8).toByte(); out[i * 4 + 2] = (v ushr 16).toByte(); out[i * 4 + 3] = (v ushr 24).toByte()
        }
    }

    /** poly1305-donna, 26-bit limbs. */
    private class Poly1305(key: ByteArray) {
        private val r0: Long; private val r1: Long; private val r2: Long; private val r3: Long; private val r4: Long
        private val pad = LongArray(4) { u32(key, 16 + it * 4) }
        private var h0 = 0L; private var h1 = 0L; private var h2 = 0L; private var h3 = 0L; private var h4 = 0L
        private val buf = ByteArray(16)
        private var used = 0

        init {
            r0 = u32(key, 0) and 0x3ffffff
            r1 = (u32(key, 3) ushr 2) and 0x3ffff03
            r2 = (u32(key, 6) ushr 4) and 0x3ffc0ff
            r3 = (u32(key, 9) ushr 6) and 0x3f03fff
            r4 = (u32(key, 12) ushr 8) and 0x00fffff
        }

        fun update(m: ByteArray, off: Int, len: Int) {
            for (i in off until off + len) {
                buf[used++] = m[i]
                if (used == 16) { block(buf, 1L shl 24); used = 0 }
            }
        }

        /** Zero-pads to a 16-byte boundary (AEAD framing). */
        fun pad() {
            if (used == 0) return
            buf.fill(0, used)
            block(buf, 1L shl 24)
            used = 0
        }

        fun finish(): ByteArray {
            if (used > 0) {
                buf[used] = 1
                buf.fill(0, used + 1)
                block(buf, 0)
                used = 0
            }
            val m = 0x3ffffffL
            var c: Long
            c = h1 ushr 26; h1 = h1 and m; h2 += c
            c = h2 ushr 26; h2 = h2 and m; h3 += c
            c = h3 ushr 26; h3 = h3 and m; h4 += c
            c = h4 ushr 26; h4 = h4 and m; h0 += c * 5
            c = h0 ushr 26; h0 = h0 and m; h1 += c
            var g0 = h0 + 5; c = g0 ushr 26; g0 = g0 and m
            var g1 = h1 + c; c = g1 ushr 26; g1 = g1 and m
            var g2 = h2 + c; c = g2 ushr 26; g2 = g2 and m
            var g3 = h3 + c; c = g3 ushr 26; g3 = g3 and m
            var g4 = h4 + c - (1L shl 26)
            val sel = (g4 ushr 63) - 1 // all ones when h >= p
            g0 = g0 and sel; g1 = g1 and sel; g2 = g2 and sel; g3 = g3 and sel; g4 = g4 and sel
            val keep = sel.inv()
            h0 = (h0 and keep) or g0; h1 = (h1 and keep) or g1; h2 = (h2 and keep) or g2; h3 = (h3 and keep) or g3; h4 = (h4 and keep) or g4
            val w0 = (h0 or (h1 shl 26)) and 0xffffffffL
            val w1 = ((h1 ushr 6) or (h2 shl 20)) and 0xffffffffL
            val w2 = ((h2 ushr 12) or (h3 shl 14)) and 0xffffffffL
            val w3 = ((h3 ushr 18) or (h4 shl 8)) and 0xffffffffL
            val out = ByteArray(16)
            var f = 0L
            listOf(w0, w1, w2, w3).forEachIndexed { i, w ->
                f = w + pad[i] + (f ushr 32)
                for (j in 0 until 4) out[i * 4 + j] = (f ushr (8 * j)).toByte()
            }
            return out
        }

        private fun block(b: ByteArray, hibit: Long) {
            val m = 0x3ffffffL
            h0 += u32(b, 0) and m
            h1 += (u32(b, 3) ushr 2) and m
            h2 += (u32(b, 6) ushr 4) and m
            h3 += (u32(b, 9) ushr 6) and m
            h4 += (u32(b, 12) ushr 8) or hibit
            val s1 = r1 * 5; val s2 = r2 * 5; val s3 = r3 * 5; val s4 = r4 * 5
            val d0 = h0 * r0 + h1 * s4 + h2 * s3 + h3 * s2 + h4 * s1
            var d1 = h0 * r1 + h1 * r0 + h2 * s4 + h3 * s3 + h4 * s2
            var d2 = h0 * r2 + h1 * r1 + h2 * r0 + h3 * s4 + h4 * s3
            var d3 = h0 * r3 + h1 * r2 + h2 * r1 + h3 * r0 + h4 * s4
            var d4 = h0 * r4 + h1 * r3 + h2 * r2 + h3 * r1 + h4 * r0
            var c = d0 ushr 26; h0 = d0 and m
            d1 += c; c = d1 ushr 26; h1 = d1 and m
            d2 += c; c = d2 ushr 26; h2 = d2 and m
            d3 += c; c = d3 ushr 26; h3 = d3 and m
            d4 += c; c = d4 ushr 26; h4 = d4 and m
            h0 += c * 5; c = h0 ushr 26; h0 = h0 and m; h1 += c
        }

        private fun u32(b: ByteArray, o: Int): Long = le32(b, o).toLong() and 0xffffffffL
    }
}
