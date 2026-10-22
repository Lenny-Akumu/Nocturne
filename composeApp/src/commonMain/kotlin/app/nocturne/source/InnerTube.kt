package app.nocturne.source

import app.nocturne.model.ArtistRef
import app.nocturne.model.CollectionPage
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class InnerTube(
    private val http: HttpClient,
    private val locale: () -> Pair<String, String>,
    private val cookie: () -> String = { "" },
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
            val c = cookie()
            if (c.isNotBlank()) {
                header("Cookie", c)
                authorization(c)?.let { header("Authorization", it) }
                header("X-Goog-AuthUser", "0")
                header("X-Origin", ORIGIN)
            }
            setBody(merged.toString())
        }.bodyAsText()
    }

    private fun authorization(cookie: String): String? {
        val sapisid = Regex("(?:^|;\\s*)(?:__Secure-3PAPISID|SAPISID)=([^;]+)").find(cookie)?.groupValues?.get(1) ?: return null
        val ts = app.nocturne.nowMillis() / 1000
        return "SAPISIDHASH ${ts}_${app.nocturne.sha1Hex("$ts $sapisid $ORIGIN")}"
    }

    private suspend fun browse(browseId: String): JsonElement {
        val raw = call("browse") { mapOf("browseId" to primitive(browseId)) }
        return json.parseToJsonElement(raw)
    }

    suspend fun request(endpoint: String, body: Map<String, JsonElement>): JsonElement =
        json.parseToJsonElement(call(endpoint) { body })

    // youtube.com WEB client `next`: the only one that carries the comments section.
    suspend fun webNext(body: Map<String, JsonElement>): JsonElement {
        val (hl, gl) = locale()
        val ctx = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", "WEB")
                    put("clientVersion", WEB_VERSION)
                    put("hl", hl)
                    put("gl", gl)
                }
            }
        }
        val raw = http.post("https://www.youtube.com/youtubei/v1/next?prettyPrint=false") {
            contentType(ContentType.Application.Json)
            header("Origin", "https://www.youtube.com")
            header("Referer", "https://www.youtube.com/")
            header("User-Agent", USER_AGENT)
            header("X-YouTube-Client-Name", "1")
            header("X-YouTube-Client-Version", WEB_VERSION)
            setBody(JsonObject(ctx + body).toString())
        }.bodyAsText()
        return json.parseToJsonElement(raw)
    }

    private fun entries(root: JsonElement): List<MediaEntry> =
        (root.findAll("musicTwoRowItemRenderer").mapNotNull { parseTwoRow(it) } +
            root.findAll("musicResponsiveListItemRenderer").mapNotNull { parseListItem(it) ?: parseListCollection(it) })

    suspend fun rate(videoId: String, rating: Rating) {
        val endpoint = when (rating) {
            Rating.LIKE -> "like/like"
            Rating.DISLIKE -> "like/dislike"
            Rating.NONE -> "like/removelike"
        }
        call(endpoint) { mapOf("target" to JsonObject(mapOf("videoId" to primitive(videoId)))) }
    }

    suspend fun accountName(): String? = accountInfo().first

    // name + profile photo from the account menu
    suspend fun accountInfo(): Pair<String?, String?> {
        val root = json.parseToJsonElement(call("account/account_menu"))
        val name = root.findAll("accountName").firstOrNull().runsText()
        val photo = root.findAll("accountPhoto").firstOrNull().path("thumbnails").arr?.lastOrNull().path("url").str
        return name to photo
    }

    suspend fun libraryCollections(browseId: String): List<CollectionRef> =
        entries(browse(browseId)).filterIsInstance<MediaEntry.Collection>().map { it.ref }.distinctBy { it.id }

    // Owned playlists: library items whose menu offers edit/delete; falls back to the owner's name.
    suspend fun editablePlaylists(owner: String?): List<CollectionRef> {
        val root = browse("FEmusic_liked_playlists")
        val items = (root.findAll("musicTwoRowItemRenderer").map { it to parseTwoRow(it) } +
            root.findAll("musicResponsiveListItemRenderer").map { it to parseListCollection(it) })
            .mapNotNull { (r, e) -> (e as? MediaEntry.Collection)?.ref?.takeIf { it.type == CollectionType.PLAYLIST }?.let { r to it } }
            .filter { (_, ref) -> ref.id.removePrefix("VL").let { it != "LM" && it != "SE" && !it.startsWith("RD") } }
        val own = items.filter { (r, _) -> r.path("menu")?.findAll("iconType")?.any { it.str == "EDIT" || it.str == "DELETE" } == true }
            .ifEmpty { items.filter { (_, ref) -> !owner.isNullOrBlank() && ref.subtitle?.contains(owner) == true } }
        return own.map { it.second }.distinctBy { it.id }
    }

    private fun ensureOk(root: JsonElement) {
        root.path("error", "message").str?.let { throw SourceUnavailableException("YouTube Music: $it") }
        val status = root.path("status").str
        if (status != null && status != "STATUS_SUCCEEDED") throw SourceUnavailableException("YouTube Music: $status")
    }

    private suspend fun editPlaylist(playlistId: String, actions: List<JsonObject>) = ensureOk(
        request("browse/edit_playlist", mapOf("playlistId" to primitive(playlistId.removePrefix("VL")), "actions" to JsonArray(actions)))
    )

    suspend fun addToPlaylist(playlistId: String, videoIds: List<String>) = editPlaylist(
        playlistId,
        videoIds.map { JsonObject(mapOf("action" to primitive("ACTION_ADD_VIDEO"), "addedVideoId" to primitive(it))) },
    )

    suspend fun removeFromPlaylist(playlistId: String, videoId: String) {
        val root = browse("VL" + playlistId.removePrefix("VL"))
        val setId = root.findAll("playlistItemData").firstOrNull { it.path("videoId").str == videoId }.path("playlistSetVideoId").str
            ?: throw SourceUnavailableException("Track not found in this playlist")
        editPlaylist(playlistId, listOf(JsonObject(mapOf(
            "action" to primitive("ACTION_REMOVE_VIDEO"), "setVideoId" to primitive(setId), "removedVideoId" to primitive(videoId),
        ))))
    }

    suspend fun createPlaylist(title: String, videoIds: List<String>): String {
        val root = request("playlist/create", mapOf(
            "title" to primitive(title),
            "privacyStatus" to primitive("PRIVATE"),
            "videoIds" to JsonArray(videoIds.map { primitive(it) }),
        ))
        ensureOk(root)
        return root.path("playlistId").str ?: throw SourceUnavailableException("Couldn't create playlist")
    }

    suspend fun playlistTracks(playlistId: String): Pair<String?, List<Track>> {
        val root = browse(if (playlistId.startsWith("VL")) playlistId else "VL$playlistId")
        val title = root.findAll("musicResponsiveHeaderRenderer").firstOrNull().path("title").runsText()
            ?: root.findAll("musicDetailHeaderRenderer").firstOrNull().path("title").runsText()
            ?: root.findAll("musicEditablePlaylistDetailHeaderRenderer").firstOrNull()?.findAll("title")?.firstOrNull().runsText()
        val tracks = root.findAll("musicResponsiveListItemRenderer").mapNotNull { parseListItem(it) }
            .filterIsInstance<MediaEntry.Song>().map { it.track }.distinctBy { it.id }
        return title to tracks
    }

    suspend fun home(): List<Section> = carousels(browse("FEmusic_home"))

    private fun carousels(root: JsonElement): List<Section> =
        root.findAll("musicCarouselShelfRenderer").mapNotNull { shelf ->
            val title = shelf.path("header", "musicCarouselShelfBasicHeaderRenderer", "title").runsText()
                ?: return@mapNotNull null
            val contents = shelf.path("contents").arr ?: return@mapNotNull null
            var listItems = 0
            val items = contents.mapNotNull { c ->
                c.path("musicTwoRowItemRenderer")?.let { parseTwoRow(it) }
                    ?: c.path("musicResponsiveListItemRenderer")?.let { listItems++; parseListItem(it) ?: parseListCollection(it) }
            }
            if (items.isEmpty()) null
            else Section(
                title = title,
                items = items,
                layout = if (listItems > items.size / 2) SectionLayout.QUICK_PICKS else SectionLayout.CAROUSEL,
                source = Source.YOUTUBE_MUSIC,
            )
        }

    suspend fun artist(browseId: String, fallback: CollectionRef): CollectionPage {
        val root = browse(browseId)
        val header = root.path("header").let { h ->
            h.path("musicImmersiveHeaderRenderer") ?: h.path("musicVisualHeaderRenderer") ?: h.path("musicHeaderRenderer")
        } ?: root.findAll("musicImmersiveHeaderRenderer").firstOrNull() ?: root.findAll("musicVisualHeaderRenderer").firstOrNull()
        val name = header.path("title").runsText()?.removeSuffix(" - Topic") ?: fallback.title.removeSuffix(" - Topic")
        val art = header.path("thumbnail", "musicThumbnailRenderer", "thumbnail", "thumbnails").bestThumbnail()
            ?: header.path("foregroundThumbnail", "musicThumbnailRenderer", "thumbnail", "thumbnails").bestThumbnail()
            ?: fallback.artworkUrl
        val description = header.path("description").runsText()
        val shelf = root.findAll("musicShelfRenderer").firstOrNull()
        var songs = shelf?.path("contents").arr.orEmpty().mapNotNull { it.path("musicResponsiveListItemRenderer")?.let { r -> parseListItem(r) } }
            .filterIsInstance<MediaEntry.Song>().map { it.track }
        val allSongs = shelf.path("title", "runs", 0, "navigationEndpoint", "browseEndpoint", "browseId").str
            ?: shelf.path("bottomEndpoint", "browseEndpoint", "browseId").str
        if (allSongs != null && allSongs.startsWith("VL")) {
            runCatching { playlistTracks(allSongs).second }.getOrNull()?.takeIf { it.size > songs.size }?.let { songs = it }
        }
        val ref = CollectionRef(fallback.id, Source.YOUTUBE_MUSIC, CollectionType.ARTIST, name, fallback.subtitle?.takeIf { it != "Artist" } ?: "Artist", art)
        return CollectionPage(ref, songs.distinctBy { it.id }, description = description, sections = carousels(root))
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

    suspend fun musicVideoId(videoId: String): String? {
        val raw = call("next") { mapOf("videoId" to primitive(videoId), "isAudioOnly" to kotlinx.serialization.json.JsonPrimitive(false)) }
        val root = json.parseToJsonElement(raw)
        fun type(r: JsonElement?) = r.path("navigationEndpoint", "watchEndpoint", "watchEndpointMusicSupportedConfigs", "watchEndpointMusicConfig", "musicVideoType").str
        for (w in root.findAll("playlistPanelVideoWrapperRenderer")) {
            if (w.path("primaryRenderer", "playlistPanelVideoRenderer", "videoId").str != videoId) continue
            w.path("counterpart").arr?.forEach { c ->
                val r = c.path("counterpartRenderer", "playlistPanelVideoRenderer")
                val id = r.path("videoId").str
                if (id != null && type(r) != ATV) return id
            }
        }
        val self = root.findAll("playlistPanelVideoRenderer").firstOrNull { it.path("videoId").str == videoId }
        return if (type(self) == ATV) null else videoId
    }

    suspend fun lyrics(videoId: String): String? {
        val raw = call("next") { mapOf("videoId" to primitive(videoId), "isAudioOnly" to kotlinx.serialization.json.JsonPrimitive(true)) }
        val browseId = Regex("\"(MPLYt[A-Za-z0-9_-]+)\"").find(raw)?.groupValues?.get(1) ?: return null
        val root = browse(browseId)
        return root.findAll("musicDescriptionShelfRenderer").firstOrNull().path("description").runsText()
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

    private fun parseListCollection(r: JsonElement): MediaEntry? {
        val cols = r.path("flexColumns").arr ?: return null
        val title = cols.getOrNull(0).path("musicResponsiveListItemFlexColumnRenderer", "text").runsText() ?: return null
        val subtitle = cols.getOrNull(1).path("musicResponsiveListItemFlexColumnRenderer", "text").runsText()
        val nav = r.path("navigationEndpoint")
        val browseId = nav.path("browseEndpoint", "browseId").str ?: return null
        val pt = pageType(nav).orEmpty()
        val type = when {
            "ALBUM" in pt || browseId.startsWith("MPREb") -> CollectionType.ALBUM
            "ARTIST" in pt || browseId.startsWith("UC") -> CollectionType.ARTIST
            "PLAYLIST" in pt || browseId.startsWith("VL") -> CollectionType.PLAYLIST
            else -> return null
        }
        val thumb = r.path("thumbnail", "musicThumbnailRenderer", "thumbnail", "thumbnails").bestThumbnail()
        return MediaEntry.Collection(CollectionRef(browseId, Source.YOUTUBE_MUSIC, type, title, subtitle, thumb))
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
        private const val ORIGIN = "https://music.youtube.com"
        private const val CLIENT_NAME = "WEB_REMIX"
        private const val CLIENT_VERSION = "1.20250929.03.00"
        private const val WEB_VERSION = "2.20261015.01.00"
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36"
    }
}

private const val ATV = "MUSIC_VIDEO_TYPE_ATV"
