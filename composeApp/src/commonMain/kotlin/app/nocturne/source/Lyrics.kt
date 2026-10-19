package app.nocturne.source

import app.nocturne.model.Source
import app.nocturne.model.Track
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.math.abs

data class LyricLine(val timeMs: Long, val text: String)

data class Lyrics(val synced: List<LyricLine>?, val plain: String?, val source: String? = null) {
    val isEmpty get() = synced.isNullOrEmpty() && plain.isNullOrBlank()
}

class LyricsProvider(private val http: HttpClient, private val innerTube: InnerTube? = null) {
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = mutableMapOf<String, Lyrics>()

    suspend fun lyrics(track: Track): Lyrics {
        cache[track.key]?.let { return it }
        val (title, artist) = cleanup(track)
        val durationSec = track.durationMs / 1000
        var plainFallback: Lyrics? = null

        fun accept(l: Lyrics?): Lyrics? {
            if (l == null || l.isEmpty) return null
            if (!l.synced.isNullOrEmpty()) return l
            if (plainFallback == null) plainFallback = l
            return null
        }

        val synced = accept(runCatching { lrclibGet(title, artist, track.album, durationSec) }.getOrNull())
            ?: accept(runCatching { lrclibSearch(title, artist, durationSec) }.getOrNull())
            ?: accept(runCatching { netease(title, artist, track.durationMs) }.getOrNull())
        val result = synced ?: plainFallback
            ?: runCatching { youtube(track) }.getOrNull()?.takeIf { !it.isEmpty }
            ?: runCatching { genius(title, artist) }.getOrNull()?.takeIf { !it.isEmpty }
            ?: Lyrics(null, null)
        cache[track.key] = result
        return result
    }

    private fun cleanup(track: Track): Pair<String, String> {
        var artist = track.artists.firstOrNull()?.name.orEmpty().removeSuffix(" - Topic").trim()
        var title = track.title
        if (title.contains(" - ") && (artist.isBlank() || title.startsWith(artist, ignoreCase = true))) {
            val parts = title.split(" - ", limit = 2)
            if (artist.isBlank()) artist = parts[0].trim()
            title = parts[1]
        }
        title = title
            .replace(Regex("\\s*[(\\[][^)\\]]*(official|video|audio|lyric|visuali[sz]er|remaster|live|explicit|clean|hd|4k|mv|prod\\.?)[^)\\]]*[)\\]]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*[(\\[](feat\\.?|ft\\.?|with)\\s[^)\\]]*[)\\]]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+(feat\\.?|ft\\.?)\\s.*$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*\\|.*$"), "")
            .replace("\"", "")
            .trim()
        return title.ifBlank { track.title } to artist
    }

    private suspend fun fetch(url: String, referer: String? = null): String? {
        val r = http.get(url) {
            header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36 Nocturne")
            referer?.let { header("Referer", it) }
        }
        if (!r.status.isSuccess()) return null
        return r.bodyAsText()
    }

    private suspend fun fetchJson(url: String, referer: String? = null): JsonElement? = fetch(url, referer)?.let { json.parseToJsonElement(it) }

    private fun parseLrclib(e: JsonElement): Lyrics =
        Lyrics(e.path("syncedLyrics").str?.let { parseLrc(it) }?.takeIf { it.isNotEmpty() }, e.path("plainLyrics").str, "LRCLIB")

    private suspend fun lrclibGet(title: String, artist: String, album: String?, durationSec: Long): Lyrics? {
        val q = buildString {
            append("track_name=${title.encodeURLParameter()}&artist_name=${artist.encodeURLParameter()}")
            album?.let { append("&album_name=${it.encodeURLParameter()}") }
            if (durationSec > 0) append("&duration=$durationSec")
        }
        return fetchJson("https://lrclib.net/api/get?$q")?.let { parseLrclib(it) }
    }

    private suspend fun lrclibSearch(title: String, artist: String, durationSec: Long): Lyrics? {
        val queries = listOf("track_name=${title.encodeURLParameter()}&artist_name=${artist.encodeURLParameter()}", "q=${"$title $artist".encodeURLParameter()}")
        for (q in queries) {
            val items = fetchJson("https://lrclib.net/api/search?$q")?.arr.orEmpty()
            val fitting = items.filter { durationSec <= 0 || abs((it.path("duration").num ?: durationSec) - durationSec) <= 6 }
            val best = fitting.firstOrNull { it.path("syncedLyrics").str != null } ?: fitting.firstOrNull()
            if (best != null) return parseLrclib(best)
        }
        return null
    }

    private suspend fun netease(title: String, artist: String, durationMs: Long): Lyrics? {
        val referer = "https://music.163.com/"
        val search = fetchJson("https://music.163.com/api/search/get/web?s=${"$title $artist".encodeURLParameter()}&type=1&limit=10", referer)
        val songs = search.path("result", "songs").arr.orEmpty()
        val nt = norm(title)
        val na = norm(artist)
        val song = songs.filter { s ->
            val d = s.path("duration").num ?: 0
            durationMs <= 0 || d <= 0 || abs(d - durationMs) <= 6000
        }.maxByOrNull { s ->
            var score = 0
            val st = norm(s.path("name").str.orEmpty())
            if (st == nt) score += 3 else if (st.contains(nt) || nt.contains(st)) score += 1
            if (s.path("artists").arr.orEmpty().any { a -> norm(a.path("name").str.orEmpty()).let { it.isNotEmpty() && (na.contains(it) || it.contains(na)) } }) score += 3
            score
        } ?: return null
        val id = song.path("id").num ?: return null
        val lrc = fetchJson("https://music.163.com/api/song/lyric?id=$id&lv=1&kv=1&tv=-1", referer).path("lrc", "lyric").str ?: return null
        if (lrc.contains("纯音乐")) return null
        val lines = parseLrc(lrc).filterNot { Regex("^(作词|作曲|编曲|制作人|混音|母带|录音|和声|吉他|贝斯|鼓|监制|出品|发行|OP|SP)\\s*[:：]").containsMatchIn(it.text) }
        return if (lines.size < 3) null else Lyrics(lines, null, "NetEase")
    }

    private suspend fun youtube(track: Track): Lyrics? {
        if (track.source != Source.YOUTUBE_MUSIC) return null
        val text = innerTube?.lyrics(track.id) ?: return null
        return Lyrics(null, text, "YouTube Music")
    }

    private suspend fun genius(title: String, artist: String): Lyrics? {
        val search = fetchJson("https://genius.com/api/search/multi?per_page=5&q=${"$title $artist".encodeURLParameter()}")
        val hits = search.path("response", "sections").arr.orEmpty()
            .filter { it.path("type").str == "song" }
            .flatMap { it.path("hits").arr.orEmpty() }
        val na = norm(artist)
        val hit = hits.firstOrNull { h -> norm(h.path("result", "primary_artist", "name").str.orEmpty()).let { na.isNotEmpty() && (it.contains(na) || na.contains(it)) } }
            ?: hits.firstOrNull() ?: return null
        val url = hit.path("result", "url").str ?: return null
        val html = fetch(url) ?: return null
        val text = extractGenius(html)?.takeIf { it.isNotBlank() } ?: return null
        return Lyrics(null, text, "Genius")
    }

    private fun norm(s: String) = s.lowercase().replace(Regex("[^\\p{L}\\p{N}]"), "")

    companion object {
        private val tag = Regex("\\[(\\d+):(\\d+)(?:[.:](\\d+))?]")

        fun parseLrc(text: String): List<LyricLine> = text.lines().flatMap { line ->
            val stamps = tag.findAll(line).toList()
            if (stamps.isEmpty()) return@flatMap emptyList()
            val content = line.substring(stamps.last().range.last + 1).trim()
            stamps.map { m ->
                val (mm, ss, frac) = m.destructured
                val ms = when (frac.length) {
                    0 -> 0
                    1 -> frac.toInt() * 100
                    2 -> frac.toInt() * 10
                    else -> frac.take(3).toInt()
                }
                LyricLine(mm.toLong() * 60_000 + ss.toLong() * 1000 + ms, content)
            }
        }.sortedBy { it.timeMs }

        private fun matchingDivEnd(html: String, start: Int): Int {
            var depth = 0
            var i = start
            while (i < html.length) {
                if (html.startsWith("<div", i)) depth++
                else if (html.startsWith("</div>", i)) {
                    depth--
                    if (depth == 0) return i + 6
                }
                i++
            }
            return html.length
        }

        private fun removeExcluded(fragment: String): String {
            var s = fragment
            while (true) {
                val attr = s.indexOf("data-exclude-from-selection=\"true\"")
                if (attr < 0) return s
                val start = s.lastIndexOf("<div", attr)
                if (start < 0) return s
                s = s.removeRange(start, matchingDivEnd(s, start))
            }
        }

        fun extractGenius(html: String): String? {
            val parts = mutableListOf<String>()
            var from = 0
            while (true) {
                val attr = html.indexOf("data-lyrics-container=\"true\"", from)
                if (attr < 0) break
                val start = html.lastIndexOf("<div", attr)
                val end = matchingDivEnd(html, start)
                parts += removeExcluded(html.substring(start, end))
                from = end
            }
            if (parts.isEmpty()) return null
            return parts.joinToString("\n") { p ->
                p.replace(Regex("<br\\s*/?>"), "\n")
                    .replace(Regex("<[^>]+>"), "")
                    .replace("&amp;", "&").replace("&quot;", "\"").replace("&#x27;", "'").replace("&#39;", "'")
                    .replace("&lt;", "<").replace("&gt;", ">")
            }.lines().joinToString("\n") { it.trim() }.replace(Regex("\n{3,}"), "\n\n").trim()
        }
    }
}
