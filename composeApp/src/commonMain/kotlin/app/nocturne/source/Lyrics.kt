package app.nocturne.source

import app.nocturne.model.Track
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

data class LyricLine(val timeMs: Long, val text: String)

data class Lyrics(val synced: List<LyricLine>?, val plain: String?) {
    val isEmpty get() = synced.isNullOrEmpty() && plain.isNullOrBlank()
}

class LyricsProvider(private val http: HttpClient) {
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = mutableMapOf<String, Lyrics>()

    suspend fun lyrics(track: Track): Lyrics {
        cache[track.key]?.let { return it }
        val artist = track.artists.firstOrNull()?.name.orEmpty()
        val title = track.title.replace(Regex("\\s*[(\\[].*?(official|video|audio|lyrics|visualizer).*?[)\\]]", RegexOption.IGNORE_CASE), "")
        var result = runCatching {
            val q = buildString {
                append("track_name=${title.encodeURLParameter()}&artist_name=${artist.encodeURLParameter()}")
                track.album?.let { append("&album_name=${it.encodeURLParameter()}") }
                if (track.durationMs > 0) append("&duration=${track.durationMs / 1000}")
            }
            fetch("https://lrclib.net/api/get?$q")?.let { parse(it) }
        }.getOrNull()
        if (result == null || result.isEmpty) {
            result = runCatching {
                val arr = fetch("https://lrclib.net/api/search?q=${"$title $artist".encodeURLParameter()}")
                arr.arr?.firstOrNull { it.path("syncedLyrics").str != null }?.let { parse(it) }
                    ?: arr.arr?.firstOrNull()?.let { parse(it) }
            }.getOrNull()
        }
        val lyrics = result ?: Lyrics(null, null)
        cache[track.key] = lyrics
        return lyrics
    }

    private suspend fun fetch(url: String): JsonElement? {
        val r = http.get(url) { header("User-Agent", "Nocturne (https://github.com/lenny-akumu/nocturne)") }
        if (!r.status.isSuccess()) return null
        return json.parseToJsonElement(r.bodyAsText())
    }

    private fun parse(e: JsonElement): Lyrics =
        Lyrics(e.path("syncedLyrics").str?.let { parseLrc(it) }?.takeIf { it.isNotEmpty() }, e.path("plainLyrics").str)

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
    }
}
