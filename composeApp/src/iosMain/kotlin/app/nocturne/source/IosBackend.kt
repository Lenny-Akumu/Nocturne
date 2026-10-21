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
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.decodeURLQueryComponent
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.random.Random

actual fun createNewPipeBackend(): NewPipeBackend = IosBackend()

class IosBackend : NewPipeBackend {
    private val http = HttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private var locale = "en" to "US"
    private val innerTube = InnerTube(http, { locale })
    private var clientId: String? = null
    private val clientIdLock = Mutex()
    private val playerJs = YtPlayerJs(http)

    override fun init(language: String, country: String) {
        locale = language to country
        if (country.isNotBlank()) uaCountry = country
    }

    override suspend fun search(source: Source, query: String, filter: SearchFilter): List<MediaEntry> =
        if (source == Source.SOUNDCLOUD) scSearch(query, filter) else ytSearch(source, query, filter)

    override suspend fun stream(source: Source, url: String, quality: AudioQuality): ResolvedStream =
        if (source == Source.SOUNDCLOUD) scStream(url) else ytStream(url, quality)

    override suspend fun track(source: Source, url: String): Track {
        if (source == Source.SOUNDCLOUD) return scTrack(resolve(url)) ?: throw SourceUnavailableException("Track not found")
        val d = metadata(videoId(url))
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
        val (candidates, hls) = ytFormats(videoId(url)) { it.startsWith("video/mp4") && "avc1" in it }
        val height = { f: Fmt -> f.json.path("height").num?.toInt() ?: 0 }
        val chosen = candidates.filter { height(it) in 1..maxHeight }.maxByOrNull(height) ?: candidates.minByOrNull(height)
        return chosen?.url ?: hls
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

    // Innertube clients, as NewPipe's InnertubeClientRequestInfo / YoutubeStreamHelper.
    private class Client(
        val name: String,
        val version: String,
        val ua: (String) -> String,
        val visitorUrl: String,
        val screen: String? = "WATCH",
        val platform: String? = "MOBILE",
        val make: String? = null,
        val model: String? = null,
        val osName: String? = null,
        val osVersion: String? = null,
        val sdk: Int? = null,
        val headerId: String? = null,
        val reel: Boolean = false,
    )

    private class Fmt(val json: JsonElement, val url: String)

    private val visitors = mutableMapOf<String, String>()

    private fun JsonObjectBuilder.context(c: Client, visitorData: String?) {
        val (hl, gl) = locale
        putJsonObject("context") {
            putJsonObject("client") {
                put("clientName", c.name)
                put("clientVersion", c.version)
                c.screen?.let { put("clientScreen", it) }
                c.platform?.let { put("platform", it) }
                visitorData?.let { put("visitorData", it) }
                c.make?.let { put("deviceMake", it) }
                c.model?.let { put("deviceModel", it) }
                c.osName?.let { put("osName", it) }
                c.osVersion?.let { put("osVersion", it) }
                c.sdk?.let { put("androidSdkVersion", it) }
                put("hl", hl)
                put("gl", gl)
                put("utcOffsetMinutes", 0)
            }
            putJsonObject("request") {
                putJsonArray("internalExperimentFlags") {}
                put("useSsl", true)
            }
            putJsonObject("user") { put("lockedSafetyMode", false) }
        }
    }

    private fun JsonObjectBuilder.videoChecks(videoId: String, cpn: String) {
        put("videoId", videoId)
        put("cpn", cpn)
        put("contentCheckOk", true)
        put("racyCheckOk", true)
    }

    private suspend fun innertube(c: Client, url: String, body: JsonObject): JsonElement {
        val r = http.post(url) {
            contentType(ContentType.Application.Json)
            header("User-Agent", c.ua(locale.second))
            header("X-Goog-Api-Format-Version", "2")
            c.headerId?.let { header("X-Youtube-Client-Name", it); header("X-Youtube-Client-Version", c.version) }
            setBody(body.toString())
        }
        if (!r.status.isSuccess()) throw SourceUnavailableException("YouTube: HTTP ${r.status.value}")
        return json.parseToJsonElement(r.bodyAsText())
    }

    private suspend fun visitorData(c: Client): String? {
        visitors[c.name]?.let { return it }
        return runCatching {
            innertube(c, c.visitorUrl, buildJsonObject { context(c, null) }).path("responseContext", "visitorData").str
        }.getOrNull()?.takeIf { it.isNotEmpty() }?.also { visitors[c.name] = it }
    }

    private suspend fun playerResponse(c: Client, videoId: String, cpn: String): JsonElement {
        val vd = visitorData(c)
        val query = "prettyPrint=false&t=${nonce(12)}&id=$videoId"
        if (c.reel) {
            val body = buildJsonObject {
                context(c, vd)
                putJsonObject("playerRequest") { videoChecks(videoId, cpn) }
                put("disablePlayerResponse", false)
            }
            return innertube(c, "${GAPIS}reel/reel_item_watch?$query&\$fields=playerResponse", body).path("playerResponse")
                ?: throw SourceUnavailableException("Video unavailable")
        }
        return innertube(c, "${GAPIS}player?$query", buildJsonObject { context(c, vd); videoChecks(videoId, cpn) })
    }

    private suspend fun metadata(videoId: String): JsonElement? {
        for (c in STREAM_CLIENTS) {
            val r = try { playerResponse(c, videoId, nonce(16)) } catch (e: CancellationException) { throw e } catch (e: Exception) { continue }
            if (r.path("videoDetails", "title").str != null) return r.path("videoDetails")
        }
        return null
    }

    // Formats whose mime type passes [want], with deobfuscated URLs (+cpn), from the first client that has any; plus an HLS fallback.
    private suspend fun ytFormats(videoId: String, want: (String) -> Boolean): Pair<List<Fmt>, String?> {
        var hls: String? = null
        var problem: String? = null
        for (c in STREAM_CLIENTS) {
            val cpn = nonce(16)
            val root = try {
                playerResponse(c, videoId, cpn)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                problem = problem ?: e.message
                continue
            }
            val status = root.path("playabilityStatus", "status").str
            if (root.path("videoDetails", "videoId").str != videoId || (status != null && !status.equals("OK", ignoreCase = true))) {
                problem = root.path("playabilityStatus", "reason").str ?: problem
                continue
            }
            val data = root.path("streamingData")
            if (hls == null) hls = data.path("hlsManifestUrl").str
            val list = data.path("adaptiveFormats").arr.orEmpty().filter { want(it.path("mimeType").str.orEmpty()) }.mapNotNull { f ->
                try {
                    Fmt(f, streamUrl(videoId, f, cpn))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (e !is NoUrl) problem = problem ?: e.message
                    null
                }
            }
            if (list.isNotEmpty()) return list to hls
            hls?.let { return emptyList<Fmt>() to it }
        }
        throw SourceUnavailableException(problem ?: "Video unavailable")
    }

    private class NoUrl : Exception("No stream url")

    // YoutubeStreamExtractor.buildAndAddItagInfoToList: signature, n parameter, cpn.
    private suspend fun streamUrl(videoId: String, f: JsonElement, cpn: String): String {
        val url = f.path("url").str ?: run {
            val cipher = (f.path("signatureCipher").str ?: f.path("cipher").str)?.takeIf { it.isNotEmpty() } ?: throw NoUrl()
            val m = cipher.split('&').associate { it.substringBefore('=') to it.substringAfter('=', "").decodeURLQueryComponent(plusIsSpace = true) }
            val base = m["url"] ?: throw NoUrl()
            "$base&${m["sp"] ?: "signature"}=${playerJs.signature(videoId, m["s"].orEmpty())}"
        }
        return playerJs.throttled(videoId, url) + "&cpn=" + cpn
    }

    private fun nonce(length: Int): String = buildString { repeat(length) { append(NONCE_ALPHABET[Random.nextInt(NONCE_ALPHABET.length)]) } }

    private suspend fun ytStream(url: String, quality: AudioQuality): ResolvedStream {
        // AVPlayer can't play webm/opus, so only AAC (itag 140/139) or HLS.
        val (audio, hls) = ytFormats(videoId(url)) { it.startsWith("audio/mp4") }
        val original = audio.filter { it.json.path("audioTrack", "audioIsDefault").bool != false }.ifEmpty { audio }
        val chosen = pick(original, quality)
            ?: return ResolvedStream(hls ?: throw SourceUnavailableException("No audio stream available"), "application/x-mpegURL", isHls = true)
        val mime = chosen.json.path("mimeType").str.orEmpty()
        return ResolvedStream(
            url = chosen.url,
            mimeType = mime.substringBefore(';').trim(),
            bitrate = kbps(chosen.json),
            codec = Regex("codecs=\"([^\"]+)\"").find(mime)?.groupValues?.get(1),
        )
    }

    private fun kbps(f: JsonElement): Int = ((f.path("averageBitrate").num ?: f.path("bitrate").num ?: 0L) / 1000).toInt()

    private fun pick(formats: List<Fmt>, quality: AudioQuality): Fmt? {
        if (formats.isEmpty()) return null
        val sorted = formats.sortedBy { kbps(it.json) }
        return when (quality) {
            AudioQuality.LOW -> sorted.first()
            AudioQuality.HIGH -> sorted.last()
            AudioQuality.NORMAL -> sorted.minBy { abs(kbps(it.json) - 128) }
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

    // SoundCloud (NewPipe's SoundcloudParsingHelper / SoundcloudStreamExtractor)

    private suspend fun fetchClientId(): String {
        val page = http.get("https://soundcloud.com") { header("User-Agent", SC_UA) }.bodyAsText()
        val scripts = Regex("<script[^>]*\\ssrc=\"([^\"]*sndcdn\\.com/assets/[^\"]*\\.js)\"")
            .findAll(page).map { it.groupValues[1] }.toList()
        // The one containing the client id is likely the last one.
        for (src in scripts.asReversed()) {
            val js = runCatching {
                http.get(src) { header("User-Agent", SC_UA); header("Range", "bytes=0-50000") }.bodyAsBytes().decodeToString()
            }.getOrNull() ?: continue
            Regex(",client_id:\"(.*?)\"").find(js)?.let { return it.groupValues[1] }
        }
        throw SourceUnavailableException("Could not get SoundCloud client id")
    }

    private suspend fun sc(path: String, retry: Boolean = true): JsonElement {
        val id = soundcloudClientId()
        val sep = if ('?' in path) '&' else '?'
        val response = http.get("$SC_API/$path${sep}client_id=$id") { header("User-Agent", SC_UA) }
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
        when (val policy = t.path("policy").str.orEmpty()) {
            "ALLOW", "MONETIZE" -> Unit
            "SNIP" -> throw SourceUnavailableException("This track is only available to SoundCloud Go+ subscribers")
            "BLOCK" -> throw SourceUnavailableException("This track is not available in your country")
            else -> throw SourceUnavailableException("Content not available: policy $policy")
        }
        if (t.path("streamable").bool != true) throw SourceUnavailableException("This track isn't streamable")
        fun protocol(e: JsonElement) = e.path("format", "protocol").str.orEmpty()
        fun preset(e: JsonElement) = e.path("preset").str.orEmpty()
        // Skip DRM ("encrypted") and opus (AVPlayer can't play it); NewPipe only knows mp3, opus and aac_160k presets.
        val usable = t.path("media", "transcodings").arr.orEmpty().filter {
            !it.path("url").str.isNullOrEmpty() && "encrypted" !in protocol(it) && ("mp3" in preset(it) || "aac_160k" in preset(it))
        }
        val ordered = usable.filter { protocol(it) == "progressive" && "mp3" in preset(it) } +
            usable.filter { protocol(it) == "hls" && "aac_160k" in preset(it) } +
            usable.filter { protocol(it) == "hls" && "mp3" in preset(it) }
        if (ordered.isEmpty()) throw SourceUnavailableException("No playable audio stream (only encrypted or opus streams)")
        var failure: String? = null
        for (tr in ordered) {
            val stream = try { scTranscodingUrl(tr.path("url").str!!, t.path("track_authorization").str) } catch (e: CancellationException) { throw e } catch (e: Exception) { failure = e.message; null }
                ?: continue
            val hls = protocol(tr) == "hls"
            val mp3 = "mp3" in preset(tr)
            return ResolvedStream(
                url = stream,
                mimeType = if (hls) "application/x-mpegURL" else if (mp3) "audio/mpeg" else "audio/mp4",
                isHls = hls,
                bitrate = if (mp3) 128 else 160,
                codec = if (mp3) "mp3" else "aac",
            )
        }
        throw SourceUnavailableException("No audio stream: ${failure ?: "unknown error"}")
    }

    private suspend fun scTranscodingUrl(endpoint: String, auth: String?, retry: Boolean = true): String? {
        val id = soundcloudClientId()
        val sep = if ('?' in endpoint) '&' else '?'
        val r = http.get("$endpoint${sep}client_id=$id" + (auth?.takeIf { it.isNotEmpty() }?.let { "&track_authorization=$it" } ?: "")) {
            header("User-Agent", SC_UA)
        }
        if (retry && (r.status.value == 401 || r.status.value == 403)) {
            clientIdLock.withLock { if (clientId == id) clientId = null }
            return scTranscodingUrl(endpoint, auth, retry = false)
        }
        if (!r.status.isSuccess()) error("HTTP ${r.status.value}")
        return json.parseToJsonElement(r.bodyAsText()).path("url").str
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

    companion object {
        private const val GAPIS = "https://youtubei.googleapis.com/youtubei/v1/"
        private const val WWW = "https://www.youtube.com/youtubei/v1/"
        private const val NONCE_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        private var uaCountry = "GB"

        // Values from NewPipe's ClientsConstants. ANDROID (reel endpoint) first, then VISIONOS / IOS, ANDROID_VR last.
        private val ANDROID = Client(
            "ANDROID", "21.03.36", { "com.google.android.youtube/21.03.36 (Linux; U; Android 15; $it) gzip" }, "${GAPIS}visitor_id?prettyPrint=false",
            osName = "Android", osVersion = "16", sdk = 36, reel = true,
        )
        private val VISIONOS = Client(
            "VISIONOS", "1.02", { "com.google.visionos.youtube/1.02(RealityDevice14,1; U; CPU visionOS 25_6_0 like Mac OS X; $it)" },
            "${WWW}visitor_id?prettyPrint=false", make = "Apple", model = "RealityDevice14,1", osName = "visionOS", osVersion = "25.6.0.23O471",
        )
        private val IOS = Client(
            "IOS", "21.03.2", { "com.google.ios.youtube/21.03.2(iPhone16,2; U; CPU iOS 18_7_2 like Mac OS X; $it)" },
            "${WWW}visitor_id?prettyPrint=false", make = "Apple", model = "iPhone16,2", osName = "iOS", osVersion = "18.7.2.22H124",
        )
        private val ANDROID_VR = Client(
            "ANDROID_VR", "1.62.27", { "com.google.android.apps.youtube.vr.oculus/1.62.27 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip" },
            "${GAPIS}visitor_id?prettyPrint=false", screen = null, platform = null, make = "Oculus", model = "Quest 3", osName = "Android",
            osVersion = "12L", sdk = 32, headerId = "28",
        )
        private val STREAM_CLIENTS = listOf(ANDROID, VISIONOS, IOS, ANDROID_VR)

        // User-Agent for googlevideo requests, picked from the client in the url (`c=` query or `/c/` path segment).
        fun uaFor(url: String): String? {
            val c = Regex("(?:[?&]c=|/c/)([A-Z_]+)").find(url)?.groupValues?.get(1) ?: return null
            return STREAM_CLIENTS.firstOrNull { it.name == c }?.ua?.invoke(uaCountry)
        }

        private const val SC_API = "https://api-v2.soundcloud.com"
        private const val SC_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"
        private val CLOCK = Regex("^\\d+(:\\d{2})+$")
        private val TYPE_WORDS = setOf("song", "video", "single", "ep", "album", "playlist", "artist", "episode", "podcast")
    }
}
