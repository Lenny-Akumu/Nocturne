package app.nocturne.source

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull

data class LyricHit(val title: String, val artist: String, val snippet: String, val artworkUrl: String?)

/** "What's that song that goes…": finds songs by a line of their lyrics (Genius, then LRCLIB). */
class LyricSearch(private val http: HttpClient) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun find(line: String): List<LyricHit> {
        val q = line.trim()
        if (q.length < 3) return emptyList()
        val genius = runCatching { genius(q) }.getOrDefault(emptyList())
        if (genius.isNotEmpty()) return genius
        return runCatching { lrclib(q) }.getOrDefault(emptyList())
    }

    private suspend fun genius(q: String): List<LyricHit> {
        val r = http.get("https://genius.com/api/search/lyric") {
            parameter("q", q)
            parameter("per_page", 15)
            header("User-Agent", "Mozilla/5.0")
        }
        if (!r.status.isSuccess()) return emptyList()
        val root = json.parseToJsonElement(r.bodyAsText())
        val sections = (root.obj("response")?.get("sections") as? JsonArray).orEmpty()
        return sections.flatMap { (it.obj()?.get("hits") as? JsonArray).orEmpty() }.mapNotNull { hit ->
            val res = hit.obj("result") ?: return@mapNotNull null
            val title = res.str("title") ?: return@mapNotNull null
            val artist = res.str("artist_names") ?: res.obj("primary_artist")?.str("name") ?: ""
            val snippet = (hit.obj()?.get("highlights") as? JsonArray)?.firstOrNull()?.obj()?.str("value").orEmpty()
            LyricHit(title, artist, snippet.lines().joinToString(" / ") { it.trim() }.take(160), res.str("song_art_image_thumbnail_url"))
        }.distinctBy { (it.title + "|" + it.artist).lowercase() }
    }

    private suspend fun lrclib(q: String): List<LyricHit> {
        val r = http.get("https://lrclib.net/api/search") { parameter("q", q) }
        if (!r.status.isSuccess()) return emptyList()
        val needle = norm(q)
        return (json.parseToJsonElement(r.bodyAsText()) as? JsonArray).orEmpty().mapNotNull { e ->
            val o = e.obj() ?: return@mapNotNull null
            val text = o.str("plainLyrics") ?: return@mapNotNull null
            val line = text.lines().firstOrNull { norm(it).contains(needle) } ?: return@mapNotNull null
            LyricHit(o.str("trackName") ?: return@mapNotNull null, o.str("artistName").orEmpty(), line.trim(), null)
        }.distinctBy { (it.title + "|" + it.artist).lowercase() }.take(15)
    }

    private fun norm(s: String) = s.lowercase().replace(Regex("[^\\p{L}\\p{N} ]"), "").replace(Regex("\\s+"), " ").trim()

    private fun JsonElement.obj(key: String? = null): JsonObject? {
        val o = this as? JsonObject ?: return null
        return if (key == null) o else o[key] as? JsonObject
    }

    private fun JsonObject.str(key: String) = (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
}
