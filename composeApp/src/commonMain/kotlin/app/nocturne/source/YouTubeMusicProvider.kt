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
    private val owner: () -> String? = { null },
) : MusicProvider {
    override val source = Source.YOUTUBE_MUSIC

    override val isSignedIn: Boolean get() = signedIn()

    override suspend fun rate(track: Track, rating: Rating) {
        if (isSignedIn) innerTube.rate(track.id, rating)
    }

    private val videoCache = mutableMapOf<String, kotlinx.coroutines.CompletableDeferred<String?>>()

    fun clearVideoCache() = videoCache.clear()

    override suspend fun details(track: Track) = innerTube.details(track.id, track.albumId)

    override suspend fun videoStream(track: Track): String? {
        videoCache[track.id]?.let { return it.await() }
        val pending = kotlinx.coroutines.CompletableDeferred<String?>()
        videoCache[track.id] = pending
        val id = runCatching { innerTube.musicVideoId(track.id) }.getOrDefault(track.id)
        val url = id?.let { runCatching { newPipe.videoStream(source, watchUrl(it), 480) }.getOrNull() }
        pending.complete(url)
        if (url == null) videoCache.remove(track.id)
        return url
    }

    override suspend fun likedTracks(): List<Track> =
        if (isSignedIn) innerTube.playlistTracks("LM").second else emptyList()

    override suspend fun library(): AccountLibrary? {
        if (!isSignedIn) return null
        // playlists are the core of the page: their failure (signed out, refused) is shown, not hidden
        val playlists = innerTube.libraryCollections("FEmusic_liked_playlists")
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

    override suspend fun editablePlaylists(): List<CollectionRef> =
        if (isSignedIn) innerTube.editablePlaylists(owner()) else emptyList()

    override suspend fun addToPlaylist(playlistId: String, tracks: List<Track>) = innerTube.addToPlaylist(playlistId, tracks.map { it.id })

    override suspend fun removeFromPlaylist(playlistId: String, track: Track) = innerTube.removeFromPlaylist(playlistId, track.id)

    override suspend fun createPlaylist(name: String, tracks: List<Track>): CollectionRef {
        val id = innerTube.createPlaylist(name, tracks.map { it.id })
        return CollectionRef("VL$id", source, CollectionType.PLAYLIST, name, owner(), tracks.firstOrNull()?.artworkUrl)
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
            val account = if (isSignedIn || id == "LM") runCatching { innerTube.playlistTracks(id) } else null
            val viaAccount = account?.getOrNull()
            if (viaAccount != null && viaAccount.second.isNotEmpty()) {
                CollectionPage(
                    ref.copy(title = viaAccount.first ?: ref.title, artworkUrl = ref.artworkUrl ?: viaAccount.second.first().artworkUrl),
                    viaAccount.second,
                )
            } else if (id == "LM") {
                // "Liked music" exists only behind the account; the public extractor rejects it
                val synced = app.nocturne.AppGraph.instance.library.state.value.liked.filter { it.source == source }
                if (synced.isNotEmpty()) CollectionPage(ref, synced)
                else throw SourceUnavailableException(
                    if (!isSignedIn) app.nocturne.i18n.t("Sign in to YouTube Music to see your liked music")
                    else app.nocturne.i18n.t("YouTube Music didn't answer. Sign in again in Accounts if this keeps happening") +
                        (account?.exceptionOrNull()?.message?.let { " ($it)" } ?: ""),
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

    /**
     * The YouTube Music upload of a song from another service. [isrc] (the recording's code) finds the exact
     * recording when YouTube knows it; otherwise title, version tags, artists and length decide.
     */
    suspend fun bestMatch(title: String, artist: String, durationMs: Long, artists: List<String> = listOf(artist), isrc: String? = null): Track? {
        if (!isrc.isNullOrBlank()) {
            val byCode = runCatching { search(isrc, SearchFilter.SONGS) }.getOrDefault(emptyList())
                .filterIsInstance<MediaEntry.Song>().map { it.track }
                .firstOrNull { sure(title, artists, durationMs, it) && matchScore(title, artists, durationMs, it) >= 6.0 }
            if (byCode != null) return byCode
        }
        val names = artists.filter { it.isNotBlank() }.ifEmpty { listOf(artist) }
        fun best(list: List<Track>) = list.take(12).withIndex().maxByOrNull { (i, c) -> matchScore(title, names, durationMs, c) - i * 0.05 }?.value
        val q1 = "$title ${names.take(2).joinToString(" ")}"
        val top = best(search(q1, SearchFilter.SONGS).filterIsInstance<MediaEntry.Song>().map { it.track })
        if (top != null && matchScore(title, names, durationMs, top) >= 7.0 && sure(title, names, durationMs, top)) return top
        // nothing convincing: ask again in other words (the title alone with the main artist, then the bare title)
        val more = listOf("$title $artist", title).distinct().filter { it != q1 }.flatMap { q ->
            runCatching { search(q, SearchFilter.SONGS) }.getOrDefault(emptyList()).filterIsInstance<MediaEntry.Song>().map { it.track }.take(8)
        }
        // a wrong song is worse than none: only the same title by the same artist goes through
        return best((listOfNotNull(top) + more).distinctBy { it.id })?.takeIf { sure(title, names, durationMs, it) }
    }

    companion object {
        private val versionWords = listOf(
            "remix", "sped up", "speed up", "slowed", "reverb", "nightcore", "live", "acoustic", "instrumental", "karaoke",
            "cover", "remaster", "extended", "radio edit", "edit", "version", "mix", "hardtekk", "hardstyle", "phonk", "8d",
            "bass boosted", "demo", "unplugged", "orchestral", "piano", "techno", "house", "vip", "bootleg", "mashup",
        )

        private fun normalize(s: String) = s.lowercase()
            .replace(Regex("\\(.*?\\)|\\[.*?]|feat\\..*|ft\\..*| - .*"), "")
            .replace(Regex("[^\\p{L}\\p{N} ]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

        // "(hardtekk)", "- Sped Up", "[Live]" — what makes this version this version
        private fun versionTags(s: String): Set<String> {
            val l = s.lowercase()
            return versionWords.filter { w -> Regex("(^|[^\\p{L}])" + Regex.escape(w) + "($|[^\\p{L}])").containsMatchIn(l) }.toSet()
                .let { tags -> if ("radio edit" in tags) tags - "edit" else tags }
        }

        private fun sameArtist(artists: List<String>, c: Track): Boolean {
            val theirs = c.artists.map { normalize(it.name) }.filter { it.isNotEmpty() }
            return artists.map { normalize(it) }.filter { it.isNotEmpty() }
                .any { a -> theirs.any { b -> a == b || a.contains(b) || b.contains(a) } || normalize(c.title).contains(a) }
        }

        private fun sameTitle(title: String, c: Track): Boolean {
            val nt = normalize(title)
            val ct = normalize(c.title)
            return ct == nt || (nt.isNotEmpty() && ct.isNotEmpty() && (ct.contains(nt) || nt.contains(ct)))
        }

        /** Good enough to play in place of the original: same title, same artist, about the same length. */
        fun sure(title: String, artists: List<String>, durationMs: Long, c: Track): Boolean =
            sameTitle(title, c) && sameArtist(artists, c) &&
                (durationMs <= 0 || c.durationMs <= 0 || kotlin.math.abs(durationMs - c.durationMs) <= 40_000) &&
                matchScore(title, artists, durationMs, c) >= 5.0

        /** How sure we are that [c] is the song [title] by [artists], about [durationMs] long. */
        fun matchScore(title: String, artists: List<String>, durationMs: Long, c: Track): Double {
            var score = 0.0
            val nt = normalize(title)
            val ct = normalize(c.title)
            if (ct == nt) score += 4 else if (nt.isNotEmpty() && (ct.contains(nt) || nt.contains(ct))) score += 2 else score -= 2
            // the same version: a remix isn't the original, sped up isn't normal speed
            val want = versionTags(title)
            val got = versionTags(c.title + " " + c.album.orEmpty().takeIf { want.isNotEmpty() }.orEmpty())
            score += 2.5 * (want intersect got).size - 3.0 * (want - got).size - 3.0 * (versionTags(c.title) - want).size
            val theirs = c.artists.map { normalize(it.name) }.filter { it.isNotEmpty() }
            val ours = artists.map { normalize(it) }.filter { it.isNotEmpty() }
            val same = ours.count { a -> theirs.any { b -> a == b || a.contains(b) || b.contains(a) } || normalize(c.title).contains(a) }
            score += if (same > 0) 3.0 + (same - 1) * 0.5 else -2.0
            if (durationMs > 0 && c.durationMs > 0) {
                val diff = kotlin.math.abs(durationMs - c.durationMs) / 1000.0
                score += when {
                    diff < 2 -> 3.5
                    diff < 5 -> 2.0
                    diff < 12 -> 0.5
                    diff > 40 -> -4.0
                    else -> -1.5
                }
            }
            return score
        }

        fun watchUrl(id: String) = "https://www.youtube.com/watch?v=$id"
        fun playlistUrl(id: String) = "https://www.youtube.com/playlist?list=$id"
    }
}
