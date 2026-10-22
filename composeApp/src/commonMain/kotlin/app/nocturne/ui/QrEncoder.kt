package app.nocturne.ui

/** Small QR code encoder (byte mode, ECC level M, versions 1–15), after Nayuki's reference implementation. */
object QrEncoder {
    private val ECC_M = intArrayOf(-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24)
    private val BLOCKS_M = intArrayOf(-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10)

    /** modules[y][x], true = dark. */
    fun encode(text: String): Array<BooleanArray> {
        val data = text.encodeToByteArray()
        val ver = (1 until ECC_M.size).firstOrNull { v -> 4 + (if (v < 10) 8 else 16) + data.size * 8 <= dataCodewords(v) * 8 }
            ?: error("Text too long for a QR code")
        val bits = ArrayList<Boolean>()
        fun put(v: Int, n: Int) { for (i in n - 1 downTo 0) bits += (v ushr i) and 1 != 0 }
        put(4, 4)
        put(data.size, if (ver < 10) 8 else 16)
        data.forEach { put(it.toInt() and 0xff, 8) }
        val cap = dataCodewords(ver) * 8
        put(0, minOf(4, cap - bits.size))
        put(0, (8 - bits.size % 8) % 8)
        var pad = 0xEC
        while (bits.size < cap) { put(pad, 8); pad = pad xor 0xEC xor 0x11 }
        val bytes = ByteArray(bits.size / 8) { i -> var b = 0; for (j in 0 until 8) b = (b shl 1) or (if (bits[i * 8 + j]) 1 else 0); b.toByte() }
        return Builder(ver).build(interleave(ver, bytes))
    }

    private fun rawModules(v: Int): Int {
        var r = (16 * v + 128) * v + 64
        if (v >= 2) { val n = v / 7 + 2; r -= (25 * n - 10) * n - 55; if (v >= 7) r -= 36 }
        return r
    }

    private fun dataCodewords(v: Int) = rawModules(v) / 8 - ECC_M[v] * BLOCKS_M[v]

    private fun interleave(v: Int, data: ByteArray): ByteArray {
        val nb = BLOCKS_M[v]; val ecc = ECC_M[v]; val raw = rawModules(v) / 8
        val short = nb - raw % nb; val shortLen = raw / nb
        val div = rsDivisor(ecc)
        val blocks = ArrayList<ByteArray>()
        var k = 0
        for (i in 0 until nb) {
            val d = data.copyOfRange(k, k + shortLen - ecc + if (i < short) 0 else 1)
            k += d.size
            val e = rsRemainder(d, div)
            blocks += (if (i < short) d + byteArrayOf(0) else d) + e // short blocks get a placeholder to align columns
        }
        val out = ArrayList<Byte>(raw)
        for (i in blocks[0].indices) for (j in blocks.indices) {
            if (i != shortLen - ecc || j >= short) out += blocks[j][i]
        }
        return out.toByteArray()
    }

    private fun mul(x: Int, y: Int): Int {
        var z = 0
        for (i in 7 downTo 0) { z = (z shl 1) xor ((z ushr 7) * 0x11D); z = z xor (((y ushr i) and 1) * x) }
        return z
    }

    private fun rsDivisor(deg: Int): IntArray {
        val r = IntArray(deg); r[deg - 1] = 1
        var root = 1
        repeat(deg) {
            for (j in 0 until deg) { r[j] = mul(r[j], root); if (j + 1 < deg) r[j] = r[j] xor r[j + 1] }
            root = mul(root, 2)
        }
        return r
    }

    private fun rsRemainder(data: ByteArray, div: IntArray): ByteArray {
        val r = IntArray(div.size)
        for (b in data) {
            val f = (b.toInt() and 0xff) xor r[0]
            for (i in 0 until r.size - 1) r[i] = r[i + 1]
            r[r.size - 1] = 0
            for (i in r.indices) r[i] = r[i] xor mul(div[i], f)
        }
        return ByteArray(r.size) { r[it].toByte() }
    }

    private class Builder(val ver: Int) {
        val size = ver * 4 + 17
        val m = Array(size) { BooleanArray(size) }
        val fn = Array(size) { BooleanArray(size) }

        fun set(x: Int, y: Int, dark: Boolean) { m[y][x] = dark; fn[y][x] = true }

        fun build(codewords: ByteArray): Array<BooleanArray> {
            for (i in 0 until size) { set(6, i, i % 2 == 0); set(i, 6, i % 2 == 0) }
            finder(3, 3); finder(size - 4, 3); finder(3, size - 4)
            val al = alignment()
            for (i in al.indices) for (j in al.indices) {
                if (i == 0 && j == 0 || i == 0 && j == al.lastIndex || i == al.lastIndex && j == 0) continue
                for (dy in -2..2) for (dx in -2..2) set(al[i] + dx, al[j] + dy, maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy)) != 1)
            }
            format(0)
            if (ver >= 7) {
                var rem = ver
                repeat(12) { rem = (rem shl 1) xor ((rem ushr 11) * 0x1F25) }
                val bits = (ver shl 12) or rem
                for (i in 0 until 18) {
                    val b = (bits ushr i) and 1 != 0
                    val a = size - 11 + i % 3; val c = i / 3
                    set(a, c, b); set(c, a, b)
                }
            }
            var i = 0
            var right = size - 1
            while (right >= 1) {
                if (right == 6) right = 5
                for (vert in 0 until size) for (j in 0..1) {
                    val x = right - j
                    val up = ((right + 1) and 2) == 0
                    val y = if (up) size - 1 - vert else vert
                    if (!fn[y][x] && i < codewords.size * 8) {
                        m[y][x] = (codewords[i ushr 3].toInt() ushr (7 - (i and 7))) and 1 != 0
                        i++
                    }
                }
                right -= 2
            }
            var best = 0
            var bestScore = Int.MAX_VALUE
            for (mask in 0 until 8) {
                applyMask(mask); format(mask)
                val s = penalty()
                if (s < bestScore) { best = mask; bestScore = s }
                applyMask(mask) // undo (xor)
            }
            applyMask(best); format(best)
            return m
        }

        fun finder(cx: Int, cy: Int) {
            for (dy in -4..4) for (dx in -4..4) {
                val x = cx + dx; val y = cy + dy
                if (x in 0 until size && y in 0 until size) {
                    val d = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
                    set(x, y, d != 2 && d != 4)
                }
            }
        }

        fun alignment(): IntArray {
            if (ver == 1) return IntArray(0)
            val n = ver / 7 + 2
            val step = (ver * 8 + n * 3 + 5) / (n * 4 - 4) * 2
            val r = IntArray(n); r[0] = 6
            var pos = size - 7
            for (i in n - 1 downTo 1) { r[i] = pos; pos -= step }
            return r
        }

        fun format(mask: Int) {
            val data = (0 shl 3) or mask // ECC level M = 0b00
            var rem = data
            repeat(10) { rem = (rem shl 1) xor ((rem ushr 9) * 0x537) }
            val bits = ((data shl 10) or rem) xor 0x5412
            fun b(i: Int) = (bits ushr i) and 1 != 0
            for (i in 0..5) set(8, i, b(i))
            set(8, 7, b(6)); set(8, 8, b(7)); set(7, 8, b(8))
            for (i in 9 until 15) set(14 - i, 8, b(i))
            for (i in 0 until 8) set(size - 1 - i, 8, b(i))
            for (i in 8 until 15) set(8, size - 15 + i, b(i))
            set(8, size - 8, true)
        }

        fun applyMask(mask: Int) {
            for (y in 0 until size) for (x in 0 until size) {
                if (fn[y][x]) continue
                val inv = when (mask) {
                    0 -> (x + y) % 2 == 0
                    1 -> y % 2 == 0
                    2 -> x % 3 == 0
                    3 -> (x + y) % 3 == 0
                    4 -> (x / 3 + y / 2) % 2 == 0
                    5 -> x * y % 2 + x * y % 3 == 0
                    6 -> (x * y % 2 + x * y % 3) % 2 == 0
                    else -> ((x + y) % 2 + x * y % 3) % 2 == 0
                }
                if (inv) m[y][x] = !m[y][x]
            }
        }

        // Runs, 2x2 boxes and dark balance (finder-like pattern rule skipped).
        fun penalty(): Int {
            var p = 0
            for (pass in 0..1) for (a in 0 until size) {
                var run = 1
                for (b in 1 until size) {
                    val cur = if (pass == 0) m[a][b] else m[b][a]
                    val prev = if (pass == 0) m[a][b - 1] else m[b - 1][a]
                    if (cur == prev) { run++; if (run == 5) p += 3 else if (run > 5) p++ } else run = 1
                }
            }
            for (y in 0 until size - 1) for (x in 0 until size - 1) {
                val c = m[y][x]
                if (c == m[y][x + 1] && c == m[y + 1][x] && c == m[y + 1][x + 1]) p += 3
            }
            val dark = m.sumOf { r -> r.count { it } }
            val total = size * size
            p += ((kotlin.math.abs(dark * 20 - total * 10) + total - 1) / total - 1) * 10
            return p
        }
    }
}
