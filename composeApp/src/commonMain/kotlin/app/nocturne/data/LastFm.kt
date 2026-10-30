package app.nocturne.data

import app.nocturne.AppGraph
import app.nocturne.model.Track
import app.nocturne.nowMillis
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.parameters
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Last.fm scrobbling with the user's own API account (last.fm/api/account/create). */
object LastFm {
    private const val API = "https://ws.audioscrobbler.com/2.0/"
    private const val KEY = "lastfm.session"
    private val json = Json { ignoreUnknownKeys = true }

    val connected: Boolean get() = !SecureStore.read(KEY).isNullOrBlank()

    private fun sign(p: Map<String, String>, secret: String): String =
        md5Hex(p.entries.sortedBy { it.key }.joinToString("") { it.key + it.value } + secret)

    private suspend fun call(params: Map<String, String>): String? {
        val s = AppGraph.instance.settings.value
        if (s.lastfmKey.isBlank() || s.lastfmSecret.isBlank()) return null
        val p = params + ("api_key" to s.lastfmKey)
        val signed = p + ("api_sig" to sign(p, s.lastfmSecret)) + ("format" to "json")
        return runCatching {
            AppGraph.instance.http.submitForm(API, parameters { signed.forEach { (k, v) -> append(k, v) } }).bodyAsText()
        }.getOrNull()
    }

    /** Returns an error message, or null when signed in. */
    suspend fun login(user: String, password: String): String? {
        val body = call(mapOf("method" to "auth.getMobileSession", "username" to user, "password" to password))
            ?: return "Enter the API key and secret first"
        val o = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return "Last.fm didn't answer"
        o["message"]?.jsonPrimitive?.contentOrNull?.let { return it }
        val sk = o["session"]?.jsonObject?.get("key")?.jsonPrimitive?.contentOrNull ?: return "Last.fm didn't answer"
        SecureStore.write(KEY, sk)
        AppGraph.instance.settings.update { it.copy(lastfmUser = user) }
        return null
    }

    fun logout() {
        SecureStore.write(KEY, "")
        AppGraph.instance.settings.update { it.copy(lastfmUser = "") }
    }

    private fun meta(t: Track) = buildMap {
        put("artist", t.artists.firstOrNull()?.name ?: t.artistNames)
        put("track", t.title)
        t.album?.let { put("album", it) }
        if (t.durationMs > 0) put("duration", (t.durationMs / 1000).toString())
    }

    private suspend fun nowPlaying(t: Track) {
        val sk = SecureStore.read(KEY)?.takeIf { it.isNotBlank() } ?: return
        call(meta(t) + mapOf("method" to "track.updateNowPlaying", "sk" to sk))
    }

    private suspend fun scrobble(t: Track, startedAt: Long) {
        val sk = SecureStore.read(KEY)?.takeIf { it.isNotBlank() } ?: return
        call(meta(t) + mapOf("method" to "track.scrobble", "sk" to sk, "timestamp" to (startedAt / 1000).toString()))
    }

    /** Last.fm rules: tracks over 30 s, scrobbled after half of them or 4 minutes of actual listening. */
    fun start(graph: AppGraph) {
        graph.scope.launch {
            var key: String? = null
            var started = 0L
            var listened = 0L
            var done = false
            var last = nowMillis()
            while (true) {
                delay(1000)
                val now = nowMillis()
                val st = graph.player.state.value
                val cur = st.current
                val on = graph.settings.value.lastfmScrobble && connected
                if (cur?.key != key) {
                    key = cur?.key; started = now; listened = 0; done = false
                    if (cur != null && on && !cur.isPodcast) launch { nowPlaying(cur) }
                }
                if (st.isPlaying) listened += now - last
                last = now
                if (cur == null || done || !on || cur.isPodcast) continue
                val dur = if (st.durationMs > 0) st.durationMs else cur.durationMs
                if (dur in 1..30_000) continue
                if (listened >= 240_000 || (dur > 0 && listened >= dur / 2)) {
                    done = true
                    val at = started
                    launch { scrobble(cur, at) }
                }
            }
        }
    }
}

// MD5 for request signatures (common code has no digest API).
fun md5Hex(text: String): String {
    val msg = text.encodeToByteArray()
    val s = intArrayOf(7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
        4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21)
    val k = IntArray(64) { (kotlin.math.abs(kotlin.math.sin((it + 1).toDouble())) * 4294967296.0).toLong().toInt() }
    val padLen = ((msg.size + 8) / 64 + 1) * 64
    val buf = ByteArray(padLen)
    msg.copyInto(buf)
    buf[msg.size] = 0x80.toByte()
    val bits = msg.size.toLong() * 8
    for (i in 0 until 8) buf[padLen - 8 + i] = (bits ushr (8 * i)).toByte()
    var a0 = 0x67452301; var b0 = 0xefcdab89.toInt(); var c0 = 0x98badcfe.toInt(); var d0 = 0x10325476
    val m = IntArray(16)
    for (chunk in 0 until padLen / 64) {
        for (i in 0 until 16) {
            val o = chunk * 64 + i * 4
            m[i] = (buf[o].toInt() and 0xff) or ((buf[o + 1].toInt() and 0xff) shl 8) or ((buf[o + 2].toInt() and 0xff) shl 16) or ((buf[o + 3].toInt() and 0xff) shl 24)
        }
        var a = a0; var b = b0; var c = c0; var d = d0
        for (i in 0 until 64) {
            val (f, g) = when (i / 16) {
                0 -> ((b and c) or (b.inv() and d)) to i
                1 -> ((d and b) or (d.inv() and c)) to (5 * i + 1) % 16
                2 -> (b xor c xor d) to (3 * i + 5) % 16
                else -> (c xor (b or d.inv())) to (7 * i) % 16
            }
            val t = d; d = c; c = b
            b += (a + f + k[i] + m[g]).rotateLeft(s[i])
            a = t
        }
        a0 += a; b0 += b; c0 += c; d0 += d
    }
    return intArrayOf(a0, b0, c0, d0).joinToString("") { v -> (0 until 4).joinToString("") { i -> ((v ushr (8 * i)) and 0xff).toString(16).padStart(2, '0') } }
}
