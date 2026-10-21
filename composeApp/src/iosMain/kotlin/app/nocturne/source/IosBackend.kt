package app.nocturne.source

import app.nocturne.model.ArtistRef
import app.nocturne.model.AudioQuality
import app.nocturne.model.CollectionPage
import app.nocturne.model.CollectionRef
import app.nocturne.model.CollectionType
import app.nocturne.model.MediaEntry
import app.nocturne.model.ResolvedStream
import app.nocturne.model.SearchFilter
import app.nocturne.model.Section
import app.nocturne.model.Source
import app.nocturne.model.Track
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.math.abs

actual fun createNewPipeBackend(): NewPipeBackend = IosBackend()

class IosBackend : NewPipeBackend {
    private val http = HttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private var locale = "en" to "US"
    private val innerTube = InnerTube(http, { locale })
    private var clientId: String? = null
    private val clientIdLock = Mutex()

    override fun init(language: String, country: String) {
        locale = language to country
    }

    override suspend fun search(source: Source, query: String, filter: SearchFilter): List<MediaEntry> =
        if (source == Source.SOUNDCLOUD) scSearch(query, filter) else ytSearch(source, query, filter)

    override suspend fun stream(source: Source, url: String, quality: AudioQuality): ResolvedStream =
        if (source == Source.SOUNDCLOUD) scStream(url) else ytStream(url, quality)

    override suspend fun track(source: Source, url: String): Track {
        if (source == Source.SOUNDCLOUD) return scTrack(resolve(url)) ?: throw SourceUnavailableException("Track not found")
        val d = player(videoId(url), requirePlayable = false).path("videoDetails")
        val id = d.path("videoId").str ?: videoId(url)
        val author = d.path("author").str?.removeSuffix(" - Topic")
        return Track(
            id = id,
            source = source,
            title = d.path("title").str ?: throw SourceUnavailableException("Video unavailable"),
            artists = listOfNotNull(author?.let { ArtistRef(it, d.path("channelId").str) }),
            durationMs = (d.path("lengthSeconds").str?.toLongOrNull() ?: 0L) * 1000,
            artworkUrl = d.path("thumbnail", "thumbnails").bestThumbnail(),
            url = YouTubeMusicProvider.watchUrl(id),
        )
    }

    override suspend fun playlist(source: Source, url: String, ref: CollectionRef?): CollectionPage =
        if (source == Source.SOUNDCLOUD) scPlaylist(url, ref) else ytPlaylist(source, url, ref)

    override suspend fun channel(source: Source, url: String, ref: CollectionRef?): CollectionPage {
        if (source == Source.SOUNDCLOUD) return scChannel(url, ref)
        val id = Regex("channel/(UC[A-Za-z0-9_-]+)").find(url)?.groupValues?.get(1) ?: url
        return innerTube.artist(id, ref ?: CollectionRef(id, source, CollectionType.ARTIST, "Artist"))
    }

    override suspend fun related(source: Source, url: String): List<Track> {
        if (source == Source.SOUNDCLOUD) {
            val id = resolve(url).path("id").num ?: return emptyList()
            return sc("tracks/$id/related?limit=30").path("collection").arr.orEmpty().mapNotNull { scTrack(it) }
        }
        return innerTube.radio(videoId(url)).map { it.copy(source = source) }
    }

    override suspend fun charts(source: Source): List<Track> = runCatching {
        if (source == Source.SOUNDCLOUD) {
            sc("charts?kind=top&genre=soundcloud%3Agenres%3Aall-music&limit=50").path("collection").arr.orEmpty()
                .mapNotNull { scTrack(it.path("track")) }
        } else {
            innerTube.request("browse", mapOf("browseId" to JsonPrimitive("FEmusic_charts")))
                .findAll("musicResponsiveListItemRenderer").mapNotNull { listTrack(source, it) }
        }
    }.getOrDefault(emptyList()).distinctBy { it.id }.take(40)

    override suspend fun videoStream(source: Source, url: String, maxHeight: Int): String? {
        if (source == Source.SOUNDCLOUD) return null
        val data = player(videoId(url)).path("streamingData")
        val candidates = data.path("adaptiveFormats").arr.orEmpty().filter { f ->
            val mime = f.path("mimeType").str.orEmpty()
            f.path("url").str != null && mime.startsWith("video/mp4") && "avc1" in mime
        }
        val height = { f: JsonElement -> f.path("height").num?.toInt() ?: 0 }
        val chosen = candidates.filter { height(it) in 1..maxHeight }.maxByOrNull(height) ?: candidates.minByOrNull(height)
        return chosen.path("url").str ?: data.path("hlsManifestUrl").str
    }

    override suspend fun soundcloudClientId(): String = clientIdLock.withLock {
        clientId ?: fetchClientId().also { clientId = it }
    }

    // YouTube

    private suspend fun ytSearch(source: Source, query: String, filter: SearchFilter): List<MediaEntry> {
        val params = when (filter) {
            SearchFilter.SONGS -> "EgWKAQIIAWoKEAkQBRAKEAMQBA=="
            SearchFilter.ALBUMS -> "EgWKAQIYAWoKEAkQAxAEEAkQBQ=="
            SearchFilter.PLAYLISTS -> "EgeKAQQoAEABagoQAxAEEAoQCRAF"
            SearchFilter.ARTISTS -> "EgWKAQIgAWoKEAkQAxAEEAoQBQ=="
        }
        val root = innerTube.request("search", mapOf("query" to JsonPrimitive(query), "params" to JsonPrimitive(params)))
        val items = root.findAll("musicResponsiveListItemRenderer")
        return when (filter) {
            SearchFilter.SONGS -> items.mapNotNull { listTrack(source, it) }.distinctBy { it.id }.map { MediaEntry.Song(it) }
            SearchFilter.ALBUMS -> items.mapNotNull { listRef(source, it, CollectionType.ALBUM) }.distinctBy { it.id }.map { MediaEntry.Collection(it) }
            SearchFilter.PLAYLISTS -> items.mapNotNull { listRef(source, it, CollectionType.PLAYLIST) }.distinctBy { it.id }.map { MediaEntry.Collection(it) }
            SearchFilter.ARTISTS -> items.mapNotNull { listRef(source, it, CollectionType.ARTIST) }.distinctBy { it.id }.map { MediaEntry.Collection(it) }
        }
    }

    private suspend fun ytPlaylist(source: Source, url: String, ref: CollectionRef?): CollectionPage {
        val id = Regex("list=([A-Za-z0-9_-]+)").find(url)?.groupValues?.get(1) ?: url.removePrefix("VL")
        val root = innerTube.request("browse", mapOf("browseId" to JsonPrimitive("VL$id")))
        val header = root.findAll("musicResponsiveHeaderRenderer").firstOrNull() ?: root.findAll("musicDetailHeaderRenderer").firstOrNull()
        val shelf = root.findAll("musicPlaylistShelfRenderer").firstOrNull() ?: root.findAll("musicShelfRenderer").firstOrNull() ?: root
        val tracks = shelf.findAll("musicResponsiveListItemRenderer").mapNotNull { listTrack(source, it) }.toMutableList()
        var token = continuation(shelf)
        var pages = 0
        while (tracks.size < 500 && pages++ < 10) {
            val t = token ?: break
            val next = runCatching { innerTube.request("browse", mapOf("continuation" to JsonPrimitive(t))) }.getOrNull() ?: break
            next.findAll("musicResponsiveListItemRenderer").mapNotNullTo(tracks) { listTrack(source, it) }
            token = continuation(next)
        }
        val resolved = CollectionRef(
            id = ref?.id ?: id,
            source = source,
            type = ref?.type ?: CollectionType.PLAYLIST,
            title = header.path("title").runsText() ?: ref?.title ?: "Playlist",
            subtitle = (header.path("straplineTextOne").runsText() ?: byline(header.path("subtitle"))) ?: ref?.subtitle,
            artworkUrl = ref?.artworkUrl ?: header?.findAll("thumbnails")?.firstOrNull().bestThumbnail(),
        )
        val description = header.path("description", "musicDescriptionShelfRenderer", "description").runsText()
            ?: header.path("description").runsText()
        val list = tracks.distinctBy { it.id }.map { t ->
            if (resolved.type == CollectionType.ALBUM) t.copy(artworkUrl = resolved.artworkUrl ?: t.artworkUrl) else t
        }
        return CollectionPage(resolved, list, description = description)
    }

    private fun continuation(root: JsonElement): String? =
        root.findAll("continuationCommand").firstOrNull().path("token").str
            ?: root.findAll("nextContinuationData").firstOrNull().path("continuation").str

    private suspend fun player(videoId: String, requirePlayable: Boolean = true): JsonElement {
        val (hl, gl) = locale
        val body = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", "IOS")
                    put("clientVersion", IOS_VERSION)
                    put("deviceMake", "Apple")
                    put("deviceModel", "iPhone16,2")
                    put("osName", "iPhone")
                    put("osVersion", "18.1.0.22B83")
                    put("userAgent", IOS_UA)
                    put("hl", hl)
                    put("gl", gl)
                }
            }
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
        }
        val raw = http.post("https://youtubei.googleapis.com/youtubei/v1/player?prettyPrint=false") {
            contentType(ContentType.Application.Json)
            header("User-Agent", IOS_UA)
            header("X-Youtube-Client-Name", "5")
            header("X-Youtube-Client-Version", IOS_VERSION)
            setBody(body.toString())
        }.bodyAsText()
        val root = json.parseToJsonElement(raw)
        val status = root.path("playabilityStatus", "status").str
        if (requirePlayable && status != "OK") {
            throw SourceUnavailableException(root.path("playabilityStatus", "reason").str ?: "Video unavailable")
        }
        return root
    }

    private suspend fun ytStream(url: String, quality: AudioQuality): ResolvedStream {
        val data = player(videoId(url)).path("streamingData")
        data.path("hlsManifestUrl").str?.let { return ResolvedStream(it, "application/x-mpegURL", isHls = true) }
        val audio = data.path("adaptiveFormats").arr.orEmpty()
            .filter { it.path("url").str != null && it.path("mimeType").str?.startsWith("audio/") == true }
        val original = audio.filter { it.path("audioTrack", "audioIsDefault").bool != false }.ifEmpty { audio }
        val chosen = pick(original.filter { it.path("mimeType").str.orEmpty().startsWith("audio/mp4") }, quality)
            ?: pick(original, quality)
            ?: throw SourceUnavailableException("No audio stream available")
        val mime = chosen.path("mimeType").str.orEmpty()
        return ResolvedStream(
            url = chosen.path("url").str!!,
            mimeType = mime.substringBefore(';').trim(),
            bitrate = kbps(chosen),
            codec = Regex("codecs=\"([^\"]+)\"").find(mime)?.groupValues?.get(1),
        )
    }

    private fun kbps(f: JsonElement): Int = ((f.path("averageBitrate").num ?: f.path("bitrate").num ?: 0L) / 1000).toInt()

    private fun pick(formats: List<JsonElement>, quality: AudioQuality): JsonElement? {
        if (formats.isEmpty()) return null
        val sorted = formats.sortedBy { kbps(it) }
        return when (quality) {
            AudioQuality.LOW -> sorted.first()
            AudioQuality.HIGH -> sorted.last()
            AudioQuality.NORMAL -> sorted.minBy { abs(kbps(it) - 128) }
        }
    }

    private fun videoId(url: String): String =
        Regex("(?:v=|youtu\\.be/|shorts/)([A-Za-z0-9_-]{11})").find(url)?.groupValues?.get(1)
            ?: url.takeIf { it.length == 11 }
            ?: throw SourceUnavailableException("Invalid YouTube url")

    private fun flex(r: JsonElement, i: Int): JsonElement? =
        r.path("flexColumns", i, "musicResponsiveListItemFlexColumnRenderer", "text")

    private fun listTrack(source: Source, r: JsonElement): Track? {
        val title = flex(r, 0).runsText() ?: return null
        val id = r.path("playlistItemData", "videoId").str
            ?: r.path("overlay", "musicItemThumbnailOverlayRenderer", "content", "musicPlayButtonRenderer",
                "playNavigationEndpoint", "watchEndpoint", "videoId").str
            ?: flex(r, 0).path("runs", 0, "navigationEndpoint", "watchEndpoint", "videoId").str
            ?: return null
        val runs = r.path("flexColumns").arr.orEmpty().drop(1)
            .flatMap { it.path("musicResponsiveListItemFlexColumnRenderer", "text", "runs").arr.orEmpty() }
        val artists = runs.filter { browseId(it)?.startsWith("UC") == true }
            .mapNotNull { run -> run.path("text").str?.let { ArtistRef(it.removeSuffix(" - Topic"), browseId(run)) } }
            .ifEmpty { listOfNotNull(byline(flex(r, 1))?.let { ArtistRef(it.removeSuffix(" - Topic")) }) }
        val album = runs.firstOrNull { browseId(it)?.startsWith("MPREb") == true }
        val clock = r.path("fixedColumns", 0, "musicResponsiveListItemFixedColumnRenderer", "text").runsText()
            ?: runs.mapNotNull { it.path("text").str?.trim() }.lastOrNull { CLOCK.matches(it) }
        return Track(
            id = id,
            source = source,
            title = title,
            artists = artists,
            album = album.path("text").str,
            albumId = browseId(album),
            durationMs = parseClock(clock),
            artworkUrl = r.path("thumbnail", "musicThumbnailRenderer", "thumbnail", "thumbnails").bestThumbnail(),
            explicit = r.path("badges").arr.orEmpty().any {
                it.path("musicInlineBadgeRenderer", "icon", "iconType").str == "MUSIC_EXPLICIT_BADGE"
            },
            url = "https://music.youtube.com/watch?v=$id",
        )
    }

    private fun listRef(source: Source, r: JsonElement, type: CollectionType): CollectionRef? {
        val title = flex(r, 0).runsText() ?: return null
        val browseId = r.path("navigationEndpoint", "browseEndpoint", "browseId").str ?: return null
        val sub = flex(r, 1)
        val (id, subtitle) = when (type) {
            CollectionType.ALBUM -> (r.path("overlay", "musicItemThumbnailOverlayRenderer", "content", "musicPlayButtonRenderer",
                "playNavigationEndpoint", "watchPlaylistEndpoint", "playlistId").str ?: browseId) to byline(sub)
            CollectionType.PLAYLIST -> browseId.removePrefix("VL") to byline(sub)
            CollectionType.ARTIST -> {
                if (!browseId.startsWith("UC")) return null
                browseId to (sub.runsText()?.split(" • ")?.firstOrNull { "subscriber" in it }?.trim() ?: "Artist")
            }
            CollectionType.LOCAL -> return null
        }
        val thumb = r.path("thumbnail", "musicThumbnailRenderer", "thumbnail", "thumbnails").bestThumbnail()
        return CollectionRef(id, source, type, title, subtitle, thumb)
    }

    private fun browseId(run: JsonElement?): String? = run.path("navigationEndpoint", "browseEndpoint", "browseId").str

    private fun byline(text: JsonElement?): String? {
        val artists = text.path("runs").arr.orEmpty().filter { browseId(it)?.startsWith("UC") == true }.mapNotNull { it.path("text").str }
        if (artists.isNotEmpty()) return artists.joinToString(", ")
        return text.runsText()?.split(" • ")?.map { it.trim() }?.firstOrNull { p ->
            p.isNotEmpty() && p.lowercase() !in TYPE_WORDS && p.toIntOrNull() == null && !CLOCK.matches(p) &&
                listOf("views", "plays", "songs", "tracks", "subscribers").none { it in p.lowercase() }
        }
    }

    // SoundCloud

    private suspend fun fetchClientId(): String {
        val page = http.get("https://soundcloud.com") { header("User-Agent", InnerTube.USER_AGENT) }.bodyAsText()
        val scripts = Regex("<script[^>]+src=\"(https://a-v2\\.sndcdn\\.com/assets/[^\"]+\\.js)\"")
            .findAll(page).map { it.groupValues[1] }.toList()
        for (src in scripts.asReversed()) {
            val js = runCatching { http.get(src) { header("Range", "bytes=0-50000") }.bodyAsText() }.getOrNull() ?: continue
            Regex("client_id:\"([a-zA-Z0-9]{32})\"").find(js)?.let { return it.groupValues[1] }
        }
        throw SourceUnavailableException("Could not get SoundCloud client id")
    }

    private suspend fun sc(path: String, retry: Boolean = true): JsonElement {
        val id = soundcloudClientId()
        val sep = if ('?' in path) '&' else '?'
        val response = http.get("$SC_API/$path${sep}client_id=$id")
        if (retry && (response.status.value == 401 || response.status.value == 403)) {
            clientIdLock.withLock { if (clientId == id) clientId = null }
            return sc(path, retry = false)
        }
        if (!response.status.isSuccess()) throw SourceUnavailableException("SoundCloud: ${response.status}")
        return json.parseToJsonElement(response.bodyAsText())
    }

    private suspend fun resolve(url: String): JsonElement = sc("resolve?url=${url.encodeURLParameter()}")

    private suspend fun scSearch(query: String, filter: SearchFilter): List<MediaEntry> {
        val kind = when (filter) {
            SearchFilter.SONGS -> "tracks"
            SearchFilter.ALBUMS, SearchFilter.PLAYLISTS -> "playlists"
            SearchFilter.ARTISTS -> "users"
        }
        val items = sc("search/$kind?q=${query.encodeURLParameter()}&limit=30").path("collection").arr.orEmpty()
        return items.mapNotNull { e ->
            when (kind) {
                "tracks" -> scTrack(e)?.let { MediaEntry.Song(it) }
                "playlists" -> scPlaylistRef(e)?.let { MediaEntry.Collection(it) }
                else -> scUserRef(e)?.let { MediaEntry.Collection(it) }
            }
        }
    }

    private suspend fun scStream(url: String): ResolvedStream {
        val t = resolve(url)
        if (t.path("policy").str == "BLOCK") throw SourceUnavailableException("This track is not available in your country")
        val all = t.path("media", "transcodings").arr.orEmpty().filter { it.path("url").str != null }
        val transcodings = all.filter { it.path("snipped").bool != true }.ifEmpty { all }
        fun protocol(e: JsonElement) = e.path("format", "protocol").str
        fun mime(e: JsonElement) = e.path("format", "mime_type").str.orEmpty()
        val chosen = transcodings.firstOrNull { protocol(it) == "progressive" && mime(it).startsWith("audio/mpeg") }
            ?: transcodings.firstOrNull { protocol(it) == "progressive" }
            ?: transcodings.firstOrNull { protocol(it) == "hls" && mime(it).startsWith("audio/mpeg") }
            ?: transcodings.firstOrNull { protocol(it) == "hls" && mime(it).startsWith("audio/mp4") }
            ?: transcodings.firstOrNull { protocol(it) == "hls" }
            ?: throw SourceUnavailableException("No audio stream available")
        val endpoint = chosen.path("url").str!!
        val sep = if ('?' in endpoint) '&' else '?'
        val auth = t.path("track_authorization").str?.let { "&track_authorization=$it" }.orEmpty()
        val body = http.get("$endpoint${sep}client_id=${soundcloudClientId()}$auth").bodyAsText()
        val stream = json.parseToJsonElement(body).path("url").str ?: throw SourceUnavailableException("No audio stream available")
        val m = mime(chosen)
        val hls = protocol(chosen) == "hls"
        return ResolvedStream(
            url = stream,
            mimeType = if (hls) "application/x-mpegURL" else m.substringBefore(';').trim(),
            isHls = hls,
            bitrate = when {
                m.startsWith("audio/mpeg") -> 128
                "opus" in m -> 64
                else -> 0
            },
            codec = when {
                m.startsWith("audio/mpeg") -> "mp3"
                "opus" in m -> "opus"
                m.startsWith("audio/mp4") -> "aac"
                else -> null
            },
        )
    }

    private suspend fun scPlaylist(url: String, ref: CollectionRef?): CollectionPage {
        val p = resolve(url)
        val stubs = p.path("tracks").arr.orEmpty().take(500)
        val full = mutableMapOf<Long, JsonElement>()
        stubs.mapNotNull { s -> s.path("id").num?.takeIf { s.path("title").str == null } }.chunked(50).forEach { ids ->
            runCatching { sc("tracks?ids=${ids.joinToString("%2C")}") }.getOrNull().arr.orEmpty().forEach { t ->
                t.path("id").num?.let { full[it] = t }
            }
        }
        val resolved = CollectionRef(
            id = ref?.id ?: p.path("permalink_url").str ?: url,
            source = Source.SOUNDCLOUD,
            type = ref?.type ?: CollectionType.PLAYLIST,
            title = p.path("title").str?.takeIf { it.isNotBlank() } ?: ref?.title ?: "Playlist",
            subtitle = p.path("user", "username").str?.takeIf { it.isNotBlank() } ?: ref?.subtitle,
            artworkUrl = ref?.artworkUrl ?: scArtwork(p),
        )
        val tracks = stubs.mapNotNull { s -> scTrack(s.path("id").num?.let { full[it] } ?: s) }.map { t ->
            if (resolved.type == CollectionType.ALBUM) t.copy(artworkUrl = resolved.artworkUrl ?: t.artworkUrl) else t
        }
        return CollectionPage(resolved, tracks, description = p.path("description").str?.takeIf { it.isNotBlank() })
    }

    private suspend fun scChannel(url: String, ref: CollectionRef?): CollectionPage {
        val u = resolve(url)
        val uid = u.path("id").num ?: throw SourceUnavailableException("Artist not found")
        val resolved = scUserRef(u)?.let { it.copy(id = ref?.id ?: it.id, artworkUrl = it.artworkUrl ?: ref?.artworkUrl) }
            ?: CollectionRef(ref?.id ?: url, Source.SOUNDCLOUD, CollectionType.ARTIST, ref?.title ?: "Artist", "Artist", ref?.artworkUrl)
        suspend fun list(path: String) =
            runCatching { sc("users/$uid/$path?limit=50").path("collection").arr.orEmpty() }.getOrDefault(emptyList())
        val tracks = list("tracks").mapNotNull { scTrack(it) }
        val sections = listOf("albums" to "Albums", "playlists_without_albums" to "Playlists").mapNotNull { (path, title) ->
            val entries = list(path).mapNotNull { e -> scPlaylistRef(e)?.let { MediaEntry.Collection(it) } }
            if (entries.isEmpty()) null else Section(title, entries, source = Source.SOUNDCLOUD)
        }
        return CollectionPage(resolved, tracks, description = u.path("description").str?.takeIf { it.isNotBlank() }, sections = sections)
    }

    private fun scTrack(e: JsonElement?): Track? {
        val url = e.path("permalink_url").str ?: return null
        val user = e.path("user")
        return Track(
            id = url.substringBefore('?'),
            source = Source.SOUNDCLOUD,
            title = e.path("title").str ?: return null,
            artists = listOfNotNull(user.path("username").str?.takeIf { it.isNotBlank() }?.let { ArtistRef(it, user.path("permalink_url").str) }),
            durationMs = e.path("duration").num ?: 0L,
            artworkUrl = (e.path("artwork_url").str ?: user.path("avatar_url").str)?.let(::upscaleArtwork),
            url = url,
        )
    }

    private fun scArtwork(p: JsonElement?): String? =
        (p.path("artwork_url").str ?: p.path("tracks", 0, "artwork_url").str)?.let(::upscaleArtwork)

    private fun scPlaylistRef(e: JsonElement?): CollectionRef? {
        val url = e.path("permalink_url").str ?: return null
        val title = e.path("title").str ?: return null
        return CollectionRef(url, Source.SOUNDCLOUD, CollectionType.PLAYLIST, title, e.path("user", "username").str, scArtwork(e))
    }

    private fun scUserRef(e: JsonElement?): CollectionRef? {
        val url = e.path("permalink_url").str ?: return null
        val name = e.path("username").str ?: return null
        val subs = e.path("followers_count").num?.takeIf { it > 0 }?.let { "${compact(it)} subscribers" } ?: "Artist"
        return CollectionRef(url, Source.SOUNDCLOUD, CollectionType.ARTIST, name, subs, e.path("avatar_url").str?.let(::upscaleArtwork))
    }

    private fun compact(n: Long): String = when {
        n >= 1_000_000 -> "${(n / 100_000) / 10.0}M"
        n >= 1_000 -> "${(n / 100) / 10.0}K"
        else -> n.toString()
    }

    private companion object {
        const val IOS_VERSION = "19.45.4"
        const val IOS_UA = "com.google.ios.youtube/19.45.4 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X;)"
        const val SC_API = "https://api-v2.soundcloud.com"
        val CLOCK = Regex("^\\d+(:\\d{2})+$")
        val TYPE_WORDS = setOf("song", "video", "single", "ep", "album", "playlist", "artist", "episode", "podcast")
    }
}
