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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.kiosk.KioskInfo
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.TimeUnit
import kotlin.math.abs

actual fun createNewPipeBackend(): NewPipeBackend = NewPipeBackendImpl()

private class OkHttpDownloader(private val client: OkHttpClient) : Downloader() {
    override fun execute(request: Request): Response {
        val data = request.dataToSend()
        val body = data?.toRequestBody() ?: if (request.httpMethod() == "POST") ByteArray(0).toRequestBody() else null
        val builder = okhttp3.Request.Builder()
            .method(request.httpMethod(), body)
            .url(request.url())
            .addHeader("User-Agent", InnerTube.USER_AGENT)
        request.headers().forEach { (name, values) ->
            builder.removeHeader(name)
            values.forEach { builder.addHeader(name, it) }
        }
        client.newCall(builder.build()).execute().use { r ->
            if (r.code == 429) throw ReCaptchaException("reCaptcha challenge requested", request.url())
            return Response(r.code, r.message, r.headers.toMultimap(), r.body?.string(), r.request.url.toString())
        }
    }
}

class NewPipeBackendImpl : NewPipeBackend {
    private val client = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()
    private val downloader = OkHttpDownloader(client)

    override fun init(language: String, country: String) {
        NewPipe.init(downloader, Localization(language, country), ContentCountry(country))
    }

    private fun service(source: Source): StreamingService = when (source) {
        Source.SOUNDCLOUD -> ServiceList.SoundCloud
        else -> ServiceList.YouTube
    }

    private fun contentFilter(source: Source, filter: SearchFilter) = when (source) {
        Source.SOUNDCLOUD -> when (filter) {
            SearchFilter.SONGS -> "tracks"
            SearchFilter.ALBUMS, SearchFilter.PLAYLISTS -> "playlists"
            SearchFilter.ARTISTS -> "users"
        }
        else -> when (filter) {
            SearchFilter.SONGS -> "music_songs"
            SearchFilter.ALBUMS -> "music_albums"
            SearchFilter.PLAYLISTS -> "music_playlists"
            SearchFilter.ARTISTS -> "music_artists"
        }
    }

    override suspend fun search(source: Source, query: String, filter: SearchFilter): List<MediaEntry> = io {
        val svc = service(source)
        val handler = svc.searchQHFactory.fromQuery(query, listOf(contentFilter(source, filter)), "")
        val info = SearchInfo.getInfo(svc, handler)
        info.relatedItems.mapNotNull { toEntry(source, it, filter) }
    }

    override suspend fun stream(source: Source, url: String, quality: AudioQuality): ResolvedStream = io {
        val extractor = service(source).getStreamExtractor(url)
        extractor.fetchPage()
        val audio = extractor.audioStreams
        val candidates = audio.filter { it.isUrl && it.content.isNotBlank() }
            .filter { it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL }
            .ifEmpty { audio.filter { it.isUrl && it.content.isNotBlank() } }
        val progressive = candidates.filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
        val pool = progressive.ifEmpty { candidates.filter { it.deliveryMethod == DeliveryMethod.HLS } }
        val chosen = pick(pool, quality)
        val hls: String? = runCatching { extractor.hlsUrl }.getOrNull()
        when {
            chosen != null -> ResolvedStream(
                url = chosen.content,
                mimeType = chosen.format?.mimeType,
                isHls = chosen.deliveryMethod == DeliveryMethod.HLS,
                bitrate = chosen.averageBitrate,
                codec = chosen.codec,
            )
            !hls.isNullOrBlank() -> ResolvedStream(hls, "application/x-mpegURL", isHls = true)
            else -> throw SourceUnavailableException("No audio stream available")
        }
    }

    private val commentPages = java.util.concurrent.ConcurrentHashMap<String, Page>()

    override suspend fun comments(videoId: String, token: String?): CommentsPage? = io {
        val url = "https://www.youtube.com/watch?v=$videoId"
        val service = ServiceList.YouTube
        var count: String? = null
        val (items, next) = if (token == null) {
            val info = CommentsInfo.getInfo(service, url)
            if (info.isCommentsDisabled) throw CommentsDisabledException()
            count = info.commentsCount.takeIf { it > 0 }?.let(::compactCount)
            info.relatedItems to info.nextPage
        } else {
            val page = commentPages.remove(token) ?: return@io null
            val more = CommentsInfo.getMoreItems(service, url, page)
            more.items to more.nextPage
        }
        val nextKey = next?.takeIf { Page.isValid(it) }?.let { p -> "np:" + java.util.UUID.randomUUID().toString().also { commentPages["np:$it"] = p } }
        CommentsPage(
            items.map { c ->
                YtComment(
                    id = c.commentId ?: c.url ?: c.hashCode().toString(),
                    author = c.uploaderName.orEmpty(),
                    avatar = c.uploaderAvatars.maxByOrNull { it.height }?.url,
                    text = plain(c.commentText?.content.orEmpty()),
                    time = c.textualUploadDate,
                    likes = c.likeCount.takeIf { it > 0 }?.let(::compactCount),
                    replies = c.replyCount.takeIf { it > 0 }?.toString(),
                )
            },
            nextKey,
            count = count,
        )
    }

    private fun plain(html: String) = html.replace(Regex("<br\\s*/?>"), "\n").replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")

    private fun compactCount(n: Int): String = when {
        n >= 1_000_000 -> "${(n / 100_000) / 10.0}M".replace(".0M", "M")
        n >= 1_000 -> "${(n / 100) / 10.0}K".replace(".0K", "K")
        else -> n.toString()
    }

    private fun pick(streams: List<AudioStream>, quality: AudioQuality): AudioStream? {
        if (streams.isEmpty()) return null
        val sorted = streams.sortedBy { it.averageBitrate }
        return when (quality) {
            AudioQuality.LOW -> sorted.first()
            AudioQuality.HIGH -> sorted.last()
            AudioQuality.NORMAL -> sorted.minBy { abs(it.averageBitrate - 128) }
        }
    }

    override suspend fun track(source: Source, url: String): Track = io {
        val info = StreamInfo.getInfo(service(source), url)
        Track(
            id = trackId(source, info.url) ?: url,
            source = source,
            title = info.name,
            artists = listOf(ArtistRef(info.uploaderName, artistId(source, info.uploaderUrl))),
            durationMs = info.duration * 1000,
            artworkUrl = best(info.thumbnails),
            url = info.url,
        )
    }

    override suspend fun playlist(source: Source, url: String, ref: CollectionRef?): CollectionPage = io {
        val svc = service(source)
        val info = PlaylistInfo.getInfo(svc, url)
        val items = info.relatedItems.toMutableList()
        var next: Page? = info.nextPage
        while (Page.isValid(next) && items.size < 500) {
            val page = PlaylistInfo.getMoreItems(svc, url, next)
            items += page.items
            next = page.nextPage
        }
        val art = best(info.thumbnails)
        val resolved = CollectionRef(
            id = ref?.id ?: if (source == Source.SOUNDCLOUD) info.url else playlistId(info.url),
            source = source,
            type = ref?.type ?: CollectionType.PLAYLIST,
            title = info.name.ifBlank { ref?.title ?: "Playlist" },
            subtitle = info.uploaderName?.takeIf { it.isNotBlank() } ?: ref?.subtitle,
            artworkUrl = ref?.artworkUrl ?: art,
        )
        val tracks = items.mapNotNull { toTrack(source, it) }.map { t ->
            if (resolved.type == CollectionType.ALBUM) t.copy(artworkUrl = resolved.artworkUrl ?: t.artworkUrl) else t
        }
        CollectionPage(resolved, tracks, description = info.description?.content?.takeIf { it.isNotBlank() })
    }

    override suspend fun channel(source: Source, url: String, ref: CollectionRef?): CollectionPage = io {
        val svc = service(source)
        val info = ChannelInfo.getInfo(svc, url)
        val resolved = CollectionRef(
            id = ref?.id ?: artistId(source, info.url) ?: info.url,
            source = source,
            type = CollectionType.ARTIST,
            title = info.name,
            subtitle = info.subscriberCount.takeIf { it > 0 }?.let { "${compact(it)} subscribers" } ?: "Artist",
            artworkUrl = best(info.avatars) ?: ref?.artworkUrl,
        )
        fun tab(filter: String) = info.tabs.firstOrNull { filter in it.contentFilters }
        val songsTab = tab(ChannelTabs.TRACKS) ?: tab(ChannelTabs.VIDEOS) ?: info.tabs.firstOrNull()
        val tracks = songsTab?.let { t ->
            runCatching { ChannelTabInfo.getInfo(svc, t).relatedItems.mapNotNull { toTrack(source, it) } }.getOrNull()
        }.orEmpty()
        val sections = listOf(ChannelTabs.ALBUMS to "Albums", ChannelTabs.PLAYLISTS to "Playlists").mapNotNull { (f, title) ->
            val t = tab(f) ?: return@mapNotNull null
            val entries = runCatching {
                ChannelTabInfo.getInfo(svc, t).relatedItems.mapNotNull {
                    toEntry(source, it, if (f == ChannelTabs.ALBUMS) SearchFilter.ALBUMS else SearchFilter.PLAYLISTS)
                }
            }.getOrNull().orEmpty()
            if (entries.isEmpty()) null else Section(title, entries, source = source)
        }
        CollectionPage(resolved, tracks, description = info.description?.takeIf { it.isNotBlank() }, sections = sections)
    }

    override suspend fun related(source: Source, url: String): List<Track> = io {
        StreamInfo.getInfo(service(source), url).relatedItems.mapNotNull { toTrack(source, it) }
    }

    override suspend fun charts(source: Source): List<Track> = io {
        val extractor = service(source).kioskList.defaultKioskExtractor
        extractor.fetchPage()
        KioskInfo.getInfo(extractor).relatedItems.mapNotNull { toTrack(source, it) }.take(40)
    }

    override suspend fun videoStream(source: Source, url: String, maxHeight: Int): String? = io {
        val extractor = service(source).getStreamExtractor(url)
        extractor.fetchPage()
        val progressive = { s: org.schabi.newpipe.extractor.stream.VideoStream ->
            s.isUrl && s.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && s.format == org.schabi.newpipe.extractor.MediaFormat.MPEG_4
        }
        val candidates = extractor.videoOnlyStreams.filter(progressive).ifEmpty { extractor.videoStreams.filter(progressive) }
        val fitting = candidates.filter { it.height in 1..maxHeight }
        (fitting.maxByOrNull { it.height } ?: candidates.minByOrNull { it.height })?.content
    }

    override suspend fun soundcloudClientId(): String = io {
        org.schabi.newpipe.extractor.services.soundcloud.SoundcloudParsingHelper.clientId()
    }

    private fun toEntry(source: Source, item: InfoItem, filter: SearchFilter): MediaEntry? = when (item) {
        is StreamInfoItem -> toTrack(source, item)?.let { MediaEntry.Song(it) }
        is PlaylistInfoItem -> {
            val type = if (filter == SearchFilter.ALBUMS && source == Source.YOUTUBE_MUSIC) CollectionType.ALBUM else CollectionType.PLAYLIST
            val id = if (source == Source.SOUNDCLOUD) item.url else playlistId(item.url)
            MediaEntry.Collection(CollectionRef(id, source, type, item.name, item.uploaderName, best(item.thumbnails)))
        }
        is ChannelInfoItem -> {
            val id = artistId(source, item.url) ?: item.url
            val subs = item.subscriberCount.takeIf { it > 0 }?.let { "${compact(it)} subscribers" } ?: "Artist"
            MediaEntry.Collection(CollectionRef(id, source, CollectionType.ARTIST, item.name, subs, best(item.thumbnails)))
        }
        else -> null
    }

    private fun toTrack(source: Source, item: InfoItem): Track? {
        val s = item as? StreamInfoItem ?: return null
        val id = trackId(source, s.url) ?: return null
        return Track(
            id = id,
            source = source,
            title = s.name,
            artists = s.uploaderName?.takeIf { it.isNotBlank() }?.let { listOf(ArtistRef(it.removeSuffix(" - Topic"), artistId(source, s.uploaderUrl))) }.orEmpty(),
            durationMs = s.duration.coerceAtLeast(0) * 1000,
            artworkUrl = best(s.thumbnails),
            url = s.url,
        )
    }

    private fun trackId(source: Source, url: String?): String? {
        if (url == null) return null
        return if (source == Source.SOUNDCLOUD) url.substringBefore('?')
        else Regex("(?:v=|youtu\\.be/)([A-Za-z0-9_-]{11})").find(url)?.groupValues?.get(1)
    }

    private fun artistId(source: Source, url: String?): String? {
        if (url.isNullOrBlank()) return null
        return if (source == Source.SOUNDCLOUD) url
        else Regex("channel/(UC[A-Za-z0-9_-]+)").find(url)?.groupValues?.get(1)
    }

    private fun playlistId(url: String) = Regex("list=([A-Za-z0-9_-]+)").find(url)?.groupValues?.get(1) ?: url

    private fun best(images: List<Image>?): String? {
        if (images.isNullOrEmpty()) return null
        val img = images.maxByOrNull { it.height } ?: images.last()
        return upscaleArtwork(img.url)
    }

    private fun compact(n: Long): String = when {
        n >= 1_000_000 -> "${(n / 100_000) / 10.0}M"
        n >= 1_000 -> "${(n / 100) / 10.0}K"
        else -> n.toString()
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }
}
