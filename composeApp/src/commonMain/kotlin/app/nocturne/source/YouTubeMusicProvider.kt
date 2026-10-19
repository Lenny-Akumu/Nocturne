package app.nocturne.source

import app.nocturne.model.AccountLibrary
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

class YouTubeMusicProvider(
    val innerTube: InnerTube,
    private val newPipe: NewPipeBackend,
    private val signedIn: () -> Boolean = { false },
) : MusicProvider {
    override val source = Source.YOUTUBE_MUSIC

    override val isSignedIn: Boolean get() = signedIn()

    override suspend fun rate(track: Track, rating: Rating) {
        if (isSignedIn) innerTube.rate(track.id, rating)
    }

    private val videoCache = mutableMapOf<String, String?>()

    override suspend fun videoStream(track: Track): String? {
        if (videoCache.containsKey(track.id)) return videoCache[track.id]
        val url = runCatching { newPipe.videoStream(source, watchUrl(track.id), 480) }.getOrNull()
        videoCache[track.id] = url
        return url
    }

    override suspend fun likedTracks(): List<Track> =
        if (isSignedIn) innerTube.playlistTracks("LM").second else emptyList()

    override suspend fun library(): AccountLibrary? {
        if (!isSignedIn) return null
        val playlists = runCatching { innerTube.libraryCollections("FEmusic_liked_playlists") }.getOrDefault(emptyList())
            .filter { it.type == CollectionType.PLAYLIST && it.id.removePrefix("VL") != "LM" }
        val albums = runCatching { innerTube.libraryCollections("FEmusic_liked_albums") }.getOrDefault(emptyList())
            .filter { it.type == CollectionType.ALBUM }
        val artists = runCatching { innerTube.libraryCollections("FEmusic_library_corpus_track_artists") }.getOrDefault(emptyList())
            .filter { it.type == CollectionType.ARTIST }
        return AccountLibrary(
            source,
            liked = CollectionRef("LM", source, CollectionType.PLAYLIST, "Liked music", "YouTube Music"),
            playlists = playlists,
            albums = albums,
            artists = artists,
        )
    }

    override suspend fun search(query: String, filter: SearchFilter) = newPipe.search(source, query, filter)

    override suspend fun home(): List<Section> {
        val feed = innerTube.home()
        if (!isSignedIn) return feed
        val mine = runCatching { library() }.getOrNull()
        val own = listOfNotNull(mine?.liked) + mine?.playlists.orEmpty()
        val section = if (own.isEmpty()) null
        else Section("Your YouTube Music playlists", own.map { MediaEntry.Collection(it) }, source = source)
        return listOfNotNull(section) + feed
    }

    override suspend fun collection(ref: CollectionRef): CollectionPage = when (ref.type) {
        CollectionType.ALBUM -> {
            val playlistId = if (ref.id.startsWith("MPREb")) {
                innerTube.albumPlaylistId(ref.id) ?: throw SourceUnavailableException("Album not found")
            } else ref.id
            val page = newPipe.playlist(source, playlistUrl(playlistId), ref)
            page.copy(tracks = page.tracks.map { it.copy(album = it.album ?: ref.title, albumId = it.albumId ?: ref.id) })
        }
        CollectionType.PLAYLIST -> {
            val id = ref.id.removePrefix("VL")
            val viaAccount = if (isSignedIn || id == "LM") runCatching { innerTube.playlistTracks(id) }.getOrNull() else null
            if (viaAccount != null && viaAccount.second.isNotEmpty()) {
                CollectionPage(
                    ref.copy(title = viaAccount.first ?: ref.title, artworkUrl = ref.artworkUrl ?: viaAccount.second.first().artworkUrl),
                    viaAccount.second,
                )
            } else newPipe.playlist(source, playlistUrl(id), ref)
        }
        CollectionType.ARTIST -> {
            val page = runCatching { innerTube.artist(ref.id, ref) }.getOrNull()
            if (page != null && (page.tracks.isNotEmpty() || page.sections.isNotEmpty())) page
            else newPipe.channel(source, "https://www.youtube.com/channel/${ref.id}", ref).let {
                it.copy(ref = it.ref.copy(title = it.ref.title.removeSuffix(" - Topic")))
            }
        }
        CollectionType.LOCAL -> error("Local collections are handled by the library")
    }

    override suspend fun stream(track: Track, quality: AudioQuality): ResolvedStream =
        newPipe.stream(source, watchUrl(track.id), quality)

    override suspend fun related(track: Track): List<Track> =
        runCatching { innerTube.radio(track.id) }.getOrNull()?.takeIf { it.isNotEmpty() }
            ?: newPipe.related(source, watchUrl(track.id))

    override suspend fun suggestions(query: String): List<String> =
        runCatching { innerTube.suggestions(query) }.getOrDefault(emptyList())

    override fun canOpen(url: String) = url.contains("youtube.com") || url.contains("youtu.be")

    override suspend fun open(url: String): MediaEntry? {
        Regex("[?&]list=([A-Za-z0-9_-]+)").find(url)?.let {
            val id = it.groupValues[1]
            if (!url.contains("watch")) {
                val page = newPipe.playlist(source, playlistUrl(id), null)
                return MediaEntry.Collection(page.ref)
            }
        }
        Regex("browse/(MPREb[A-Za-z0-9_-]+)").find(url)?.let {
            return MediaEntry.Collection(CollectionRef(it.groupValues[1], source, CollectionType.ALBUM, "Album"))
        }
        Regex("channel/(UC[A-Za-z0-9_-]+)").find(url)?.let {
            val page = newPipe.channel(source, "https://www.youtube.com/channel/${it.groupValues[1]}", null)
            return MediaEntry.Collection(page.ref)
        }
        val videoId = Regex("(?:v=|youtu\\.be/|shorts/)([A-Za-z0-9_-]{11})").find(url)?.groupValues?.get(1) ?: return null
        return MediaEntry.Song(newPipe.track(source, watchUrl(videoId)))
    }

    suspend fun bestMatch(title: String, artist: String, durationMs: Long): Track? {
        val candidates = search("$title $artist", SearchFilter.SONGS)
            .filterIsInstance<MediaEntry.Song>().map { it.track }
        if (candidates.isEmpty()) return null
        val nt = normalize(title)
        val na = normalize(artist)
        return candidates.take(10).maxByOrNull { c ->
            var score = 0.0
            val ct = normalize(c.title)
            if (ct == nt) score += 4 else if (ct.contains(nt) || nt.contains(ct)) score += 2
            if (c.artists.any { normalize(it.name).let { a -> a.isNotEmpty() && (na.contains(a) || a.contains(na)) } }) score += 3
            if (durationMs > 0 && c.durationMs > 0) {
                val diff = kotlin.math.abs(durationMs - c.durationMs) / 1000.0
                score += when {
                    diff < 3 -> 3.0
                    diff < 10 -> 1.5
                    diff > 60 -> -3.0
                    else -> 0.0
                }
            }
            score
        }
    }

    private fun normalize(s: String) = s.lowercase()
        .replace(Regex("\\(.*?\\)|\\[.*?]|feat\\..*|ft\\..*"), "")
        .replace(Regex("[^\\p{L}\\p{N} ]"), "")
        .trim()

    companion object {
        fun watchUrl(id: String) = "https://www.youtube.com/watch?v=$id"
        fun playlistUrl(id: String) = "https://www.youtube.com/playlist?list=$id"
    }
}
