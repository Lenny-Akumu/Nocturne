package app.nocturne.data

import app.nocturne.AppGraph
import app.nocturne.model.ArtistRef
import app.nocturne.model.Source
import app.nocturne.model.Track
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class PodcastSub(val feedUrl: String, val title: String, val author: String = "", val artworkUrl: String? = null)

data class Episode(val guid: String, val title: String, val url: String, val published: String, val durationMs: Long, val art: String?, val summary: String)

/** Episodes play as plain audio URLs through the local source. */
val Track.isPodcast: Boolean get() = source == Source.LOCAL && (id.startsWith("http://") || id.startsWith("https://"))

object Podcasts {
    private val json = Json { ignoreUnknownKeys = true }
    private val feeds = mutableMapOf<String, List<Episode>>()

    suspend fun search(term: String): List<PodcastSub> = runCatching {
        val body = AppGraph.instance.http.get("https://itunes.apple.com/search") {
            parameter("media", "podcast"); parameter("term", term); parameter("limit", 30)
        }.bodyAsText()
        json.parseToJsonElement(body).jsonObject["results"]!!.jsonArray.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            fun s(k: String) = o[k]?.jsonPrimitive?.contentOrNull
            val feed = s("feedUrl") ?: return@mapNotNull null
            PodcastSub(feed, s("collectionName") ?: s("trackName") ?: feed, s("artistName").orEmpty(), s("artworkUrl600") ?: s("artworkUrl100"))
        }
    }.getOrDefault(emptyList())

    suspend fun episodes(sub: PodcastSub, refresh: Boolean = false): List<Episode> {
        if (!refresh) feeds[sub.feedUrl]?.let { return it }
        val xml = runCatching { AppGraph.instance.http.get(sub.feedUrl).bodyAsText() }.getOrNull() ?: return feeds[sub.feedUrl].orEmpty()
        val channelArt = Regex("<itunes:image[^>]*href=\"([^\"]+)\"").find(xml.substringBefore("<item"))?.groupValues?.get(1) ?: sub.artworkUrl
        val list = Regex("<item[\\s>].*?</item>", RegexOption.DOT_MATCHES_ALL).findAll(xml).mapNotNull { m ->
            val it = m.value
            val url = Regex("<enclosure[^>]*url=\"([^\"]+)\"").find(it)?.groupValues?.get(1)?.let(::unescape) ?: return@mapNotNull null
            Episode(
                guid = tag(it, "guid") ?: url,
                title = tag(it, "title") ?: "Episode",
                url = url,
                published = tag(it, "pubDate")?.split(' ')?.drop(1)?.take(3)?.joinToString(" ").orEmpty(),
                durationMs = parseDuration(tag(it, "itunes:duration")),
                art = Regex("<itunes:image[^>]*href=\"([^\"]+)\"").find(it)?.groupValues?.get(1) ?: channelArt,
                summary = (tag(it, "itunes:summary") ?: tag(it, "description")).orEmpty().replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim(),
            )
        }.toList()
        feeds[sub.feedUrl] = list
        return list
    }

    private fun tag(x: String, name: String): String? {
        val raw = Regex("<$name(?:\\s[^>]*)?>(.*?)</$name>", RegexOption.DOT_MATCHES_ALL).find(x)?.groupValues?.get(1) ?: return null
        val v = raw.trim().removePrefix("<![CDATA[").removeSuffix("]]>").trim()
        return unescape(v).takeIf { it.isNotEmpty() }
    }

    private fun unescape(s: String) = s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")

    private fun parseDuration(s: String?): Long {
        if (s.isNullOrBlank()) return 0
        val parts = s.trim().split(':').mapNotNull { it.trim().toLongOrNull() }
        if (parts.isEmpty()) return 0
        return parts.fold(0L) { acc, p -> acc * 60 + p } * 1000
    }

    fun toTrack(sub: PodcastSub, e: Episode) = Track(
        id = e.url, source = Source.LOCAL, title = e.title, artists = listOf(ArtistRef(sub.title)),
        album = sub.title, durationMs = e.durationMs, artworkUrl = e.art ?: sub.artworkUrl,
    )

    /** Plays the episode and jumps back to where it was left. */
    fun play(sub: PodcastSub, list: List<Episode>, index: Int) {
        val graph = AppGraph.instance
        val tracks = list.map { toTrack(sub, it) }
        graph.player.play(tracks, index, sub.title)
        val target = tracks[index]
        val pos = graph.settings.value.episodePositions[target.id] ?: return
        graph.scope.launch {
            withTimeoutOrNull(15_000) { graph.player.state.first { it.current?.key == target.key && it.durationMs > 0 } } ?: return@launch
            if (pos < graph.player.state.value.durationMs - 15_000) graph.player.seekTo(pos)
        }
    }

    fun progress(e: Episode): Float {
        val pos = AppGraph.instance.settings.value.episodePositions[e.url] ?: return 0f
        return if (e.durationMs > 0) (pos.toFloat() / e.durationMs).coerceIn(0f, 1f) else 0f
    }

    /** Remembers positions while an episode plays, and switches to the podcast speed. */
    fun start(graph: AppGraph) {
        graph.scope.launch {
            graph.player.state.distinctUntilChangedBy { it.current?.key }.collect { graph.player.applyAudioSettings() }
        }
        graph.scope.launch {
            while (true) {
                delay(10_000)
                val st = graph.player.state.value
                val cur = st.current ?: continue
                if (!cur.isPodcast || !st.isPlaying || st.positionMs <= 0) continue
                graph.settings.update { it.copy(episodePositions = (it.episodePositions + (cur.id to st.positionMs)).entries.toList().takeLast(500).associate { e -> e.key to e.value }) }
            }
        }
    }
}
