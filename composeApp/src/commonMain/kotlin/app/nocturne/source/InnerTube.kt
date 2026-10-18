package app.nocturne.source

import app.nocturne.model.ArtistRef
import app.nocturne.model.CollectionRef
import app.nocturne.model.CollectionType
import app.nocturne.model.MediaEntry
import app.nocturne.model.Section
import app.nocturne.model.SectionLayout
import app.nocturne.model.Source
import app.nocturne.model.Track
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class InnerTube(
    private val http: HttpClient,
    private val locale: () -> Pair<String, String>,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun call(endpoint: String, extra: JsonObject.() -> Map<String, JsonElement> = { emptyMap() }): String {
        val (hl, gl) = locale()
        val body = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", CLIENT_NAME)
                    put("clientVersion", CLIENT_VERSION)
                    put("hl", hl)
                    put("gl", gl)
                }
            }
        }
        val merged = JsonObject(body + body.extra())
        return http.post("$BASE/$endpoint?prettyPrint=false") {
            contentType(ContentType.Application.Json)
            header("Origin", "https://music.youtube.com")
            header("Referer", "https://music.youtube.com/")
            header("User-Agent", USER_AGENT)
            header("X-YouTube-Client-Name", "67")
            header("X-YouTube-Client-Version", CLIENT_VERSION)
            setBody(merged.toString())
        }.bodyAsText()
    }

    suspend fun home(): List<Section> {
        val raw = call("browse") { mapOf("browseId" to primitive("FEmusic_home")) }
        val root = json.parseToJsonElement(raw)
        return root.findAll("musicCarouselShelfRenderer").mapNotNull { shelf ->
            val title = shelf.path("header", "musicCarouselShelfBasicHeaderRenderer", "title").runsText()
                ?: return@mapNotNull null
            val contents = shelf.path("contents").arr ?: return@mapNotNull null
            var listItems = 0
            val items = contents.mapNotNull { c ->
                c.path("musicTwoRowItemRenderer")?.let { parseTwoRow(it) }
                    ?: c.path("musicResponsiveListItemRenderer")?.let { listItems++; parseListItem(it) }
            }
            if (items.isEmpty()) null
            else Section(
                title = title,
                items = items,
                layout = if (listItems > items.size / 2) SectionLayout.QUICK_PICKS else SectionLayout.CAROUSEL,
                source = Source.YOUTUBE_MUSIC,
            )
        }
    }

    suspend fun radio(videoId: String): List<Track> {
        val raw = call("next") {
            mapOf(
                "videoId" to primitive(videoId),
                "playlistId" to primitive("RDAMVM$videoId"),
                "isAudioOnly" to kotlinx.serialization.json.JsonPrimitive(true),
            )
        }
        return json.parseToJsonElement(raw).findAll("playlistPanelVideoRenderer")
            .mapNotNull { parsePanelVideo(it) }
            .filter { it.id != videoId }
            .distinctBy { it.id }
    }

    suspend fun suggestions(query: String): List<String> {
        val raw = call("music/get_search_suggestions") { mapOf("input" to primitive(query)) }
        return json.parseToJsonElement(raw).findAll("searchSuggestionRenderer")
            .mapNotNull { it.path("suggestion").runsText() }
    }

    suspend fun albumPlaylistId(browseId: String): String? {
        val raw = call("browse") { mapOf("browseId" to primitive(browseId)) }
        return Regex("(OLAK5uy_[A-Za-z0-9_-]+)").find(raw)?.groupValues?.get(1)
    }

    private fun primitive(v: String) = kotlinx.serialization.json.JsonPrimitive(v)

    private fun pageType(nav: JsonElement?): String? =
        nav.path("browseEndpoint", "browseEndpointContextSupportedConfigs", "browseEndpointContextMusicConfig", "pageType").str

    private fun artistsFromRuns(runs: List<JsonElement>): List<ArtistRef> =
        runs.filter { pageType(it.path("navigationEndpoint"))?.contains("ARTIST") == true }
            .mapNotNull { r ->
                val name = r.path("text").str ?: return@mapNotNull null
                ArtistRef(name, r.path("navigationEndpoint", "browseEndpoint", "browseId").str)
            }

    private fun fallbackArtists(text: String?): List<ArtistRef> {
        if (text.isNullOrBlank()) return emptyList()
        val skip = setOf("song", "video", "single", "ep", "album", "playlist", "artist")
        return text.split(" • ")
            .map { it.trim() }
            .firstOrNull { p -> p.lowercase() !in skip && !p.contains("views") && !p.contains("plays") && p.toIntOrNull() == null }
            ?.let { listOf(ArtistRef(it)) } ?: emptyList()
    }

    private fun parseTwoRow(r: JsonElement): MediaEntry? {
        val title = r.path("title").runsText() ?: return null
        val subtitleRuns = r.path("subtitle", "runs").arr.orEmpty()
        val subtitle = r.path("subtitle").runsText()
        val thumb = r.path("thumbnailRenderer", "musicThumbnailRenderer", "thumbnail", "thumbnails").bestThumbnail()
        val nav = r.path("navigationEndpoint")
        nav.path("watchEndpoint", "videoId").str?.let { videoId ->
            val artists = artistsFromRuns(subtitleRuns).ifEmpty { fallbackArtists(subtitle) }
            return MediaEntry.Song(
                Track(id = videoId, source = Source.YOUTUBE_MUSIC, title = title, artists = artists, artworkUrl = thumb)
            )
        }
        val browseId = nav.path("browseEndpoint", "browseId").str ?: return null
        val type = when {
            pageType(nav)?.contains("ALBUM") == true -> CollectionType.ALBUM
            pageType(nav)?.contains("ARTIST") == true || pageType(nav)?.contains("USER_CHANNEL") == true -> CollectionType.ARTIST
            pageType(nav)?.contains("PLAYLIST") == true -> CollectionType.PLAYLIST
            browseId.startsWith("MPREb") -> CollectionType.ALBUM
            browseId.startsWith("VL") -> CollectionType.PLAYLIST
            browseId.startsWith("UC") -> CollectionType.ARTIST
            else -> return null
        }
        return MediaEntry.Collection(CollectionRef(browseId, Source.YOUTUBE_MUSIC, type, title, subtitle, thumb))
    }

    private fun parseListItem(r: JsonElement): MediaEntry? {
        val cols = r.path("flexColumns").arr ?: return null
        val title = cols.getOrNull(0).path("musicResponsiveListItemFlexColumnRenderer", "text").runsText() ?: return null
        val runs = cols.getOrNull(1).path("musicResponsiveListItemFlexColumnRenderer", "text", "runs").arr.orEmpty()
        val thumb = r.path("thumbnail", "musicThumbnailRenderer", "thumbnail", "thumbnails").bestThumbnail()
        val videoId = r.path("playlistItemData", "videoId").str
            ?: r.path("overlay", "musicItemThumbnailOverlayRenderer", "content", "musicPlayButtonRenderer",
                "playNavigationEndpoint", "watchEndpoint", "videoId").str
            ?: return null
        val album = runs.firstOrNull { pageType(it.path("navigationEndpoint"))?.contains("ALBUM") == true }
        val explicit = r.path("badges").arr.orEmpty().any {
            it.path("musicInlineBadgeRenderer", "icon", "iconType").str == "MUSIC_EXPLICIT_BADGE"
        }
        val artists = artistsFromRuns(runs).ifEmpty {
            fallbackArtists(cols.getOrNull(1).path("musicResponsiveListItemFlexColumnRenderer", "text").runsText())
        }
        return MediaEntry.Song(
            Track(
                id = videoId,
                source = Source.YOUTUBE_MUSIC,
                title = title,
                artists = artists,
                album = album.path("text").str,
                albumId = album.path("navigationEndpoint", "browseEndpoint", "browseId").str,
                artworkUrl = thumb,
                explicit = explicit,
            )
        )
    }

    private fun parsePanelVideo(r: JsonElement): Track? {
        val videoId = r.path("videoId").str ?: return null
        val title = r.path("title").runsText() ?: return null
        val runs = r.path("longBylineText", "runs").arr.orEmpty()
        val album = runs.firstOrNull { pageType(it.path("navigationEndpoint"))?.contains("ALBUM") == true }
        return Track(
            id = videoId,
            source = Source.YOUTUBE_MUSIC,
            title = title,
            artists = artistsFromRuns(runs).ifEmpty { fallbackArtists(r.path("shortBylineText").runsText()) },
            album = album.path("text").str,
            albumId = album.path("navigationEndpoint", "browseEndpoint", "browseId").str,
            durationMs = parseClock(r.path("lengthText").runsText()),
            artworkUrl = r.path("thumbnail", "thumbnails").bestThumbnail(),
        )
    }

    companion object {
        private const val BASE = "https://music.youtube.com/youtubei/v1"
        private const val CLIENT_NAME = "WEB_REMIX"
        private const val CLIENT_VERSION = "1.20250929.03.00"
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36"
    }
}
