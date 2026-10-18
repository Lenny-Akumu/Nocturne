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
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.io.encoding.Base64
import app.nocturne.nowMillis

class SpotifyProvider(
    private val http: HttpClient,
    private val credentials: () -> Pair<String, String>,
    private val market: () -> String,
    private val youtube: YouTubeMusicProvider,
) : MusicProvider {
    override val source = Source.SPOTIFY

    private val json = Json { ignoreUnknownKeys = true }
    private val tokenLock = Mutex()
    private var token: String? = null
    private var tokenFor: Pair<String, String>? = null
    private var tokenExpiry = 0L
    private val matches = mutableMapOf<String, Track>()

    val isConfigured: Boolean get() = credentials().let { it.first.isNotBlank() && it.second.isNotBlank() }

    private suspend fun accessToken(): String = tokenLock.withLock {
        val creds = credentials()
        if (creds.first.isBlank() || creds.second.isBlank()) {
            throw SourceUnavailableException("Add your Spotify Client ID and Secret in Settings → Spotify")
        }
        val now = nowMillis()
        token?.let { if (tokenFor == creds && now < tokenExpiry) return@withLock it }
        val basic = Base64.encode("${creds.first}:${creds.second}".encodeToByteArray())
        val response = http.submitForm(
            url = "https://accounts.spotify.com/api/token",
            formParameters = parameters { append("grant_type", "client_credentials") },
        ) { header("Authorization", "Basic $basic") }
        if (!response.status.isSuccess()) throw SourceUnavailableException("Spotify auth failed: ${response.status}")
        val body = json.parseToJsonElement(response.bodyAsText())
        val t = body.path("access_token").str ?: throw SourceUnavailableException("Spotify auth failed")
        token = t
        tokenFor = creds
        tokenExpiry = now + ((body.path("expires_in").num ?: 3600) - 60) * 1000
        t
    }

    private suspend fun api(path: String): JsonElement {
        val url = if (path.startsWith("http")) path else "https://api.spotify.com/v1/$path"
        val response = http.get(url) { header("Authorization", "Bearer ${accessToken()}") }
        if (!response.status.isSuccess()) throw SourceUnavailableException("Spotify: ${response.status}")
        return json.parseToJsonElement(response.bodyAsText())
    }

    override suspend fun search(query: String, filter: SearchFilter): List<MediaEntry> {
        val type = when (filter) {
            SearchFilter.SONGS -> "track"
            SearchFilter.ALBUMS -> "album"
            SearchFilter.PLAYLISTS -> "playlist"
            SearchFilter.ARTISTS -> "artist"
        }
        val res = api("search?q=${query.encodeURLParameter()}&type=$type&limit=20&market=${market()}")
        val items = res.path("${type}s", "items").arr.orEmpty()
        return items.mapNotNull { item ->
            when (filter) {
                SearchFilter.SONGS -> parseTrack(item)?.let { MediaEntry.Song(it) }
                SearchFilter.ALBUMS -> parseAlbumRef(item)?.let { MediaEntry.Collection(it) }
                SearchFilter.PLAYLISTS -> parsePlaylistRef(item)?.let { MediaEntry.Collection(it) }
                SearchFilter.ARTISTS -> parseArtistRef(item)?.let { MediaEntry.Collection(it) }
            }
        }
    }

    override suspend fun home(): List<Section> {
        if (!isConfigured) return emptyList()
        val res = api("browse/new-releases?limit=20&country=${market()}")
        val albums = res.path("albums", "items").arr.orEmpty().mapNotNull { parseAlbumRef(it) }
        return if (albums.isEmpty()) emptyList()
        else listOf(Section("New releases on Spotify", albums.map { MediaEntry.Collection(it) }, source = source))
    }

    override suspend fun collection(ref: CollectionRef): CollectionPage = when (ref.type) {
        CollectionType.ALBUM -> {
            val album = api("albums/${ref.id}?market=${market()}")
            val fullRef = parseAlbumRef(album) ?: ref
            val tracks = album.path("tracks", "items").arr.orEmpty().mapNotNull {
                parseTrack(it, fallbackArt = fullRef.artworkUrl, album = fullRef.title, albumId = fullRef.id)
            }
            CollectionPage(fullRef, tracks)
        }
        CollectionType.PLAYLIST -> {
            val pl = api("playlists/${ref.id}?market=${market()}")
            val fullRef = parsePlaylistRef(pl) ?: ref
            val tracks = mutableListOf<Track>()
            var page: JsonElement? = pl.path("tracks")
            while (page != null && tracks.size < 500) {
                page.path("items").arr.orEmpty().mapNotNullTo(tracks) { parseTrack(it.path("track")) }
                page = page.path("next").str?.let { api(it) }
            }
            CollectionPage(fullRef, tracks, description = pl.path("description").str?.takeIf { it.isNotBlank() })
        }
        CollectionType.ARTIST -> {
            val artist = api("artists/${ref.id}")
            val fullRef = parseArtistRef(artist) ?: ref
            val top = api("artists/${ref.id}/top-tracks?market=${market()}")
                .path("tracks").arr.orEmpty().mapNotNull { parseTrack(it) }
            val albums = runCatching {
                api("artists/${ref.id}/albums?include_groups=album,single&limit=20&market=${market()}")
                    .path("items").arr.orEmpty().mapNotNull { parseAlbumRef(it) }
            }.getOrDefault(emptyList())
            CollectionPage(
                fullRef, top,
                sections = if (albums.isEmpty()) emptyList()
                else listOf(Section("Albums & singles", albums.map { MediaEntry.Collection(it) }, source = source)),
            )
        }
        CollectionType.LOCAL -> error("Local collections are handled by the library")
    }

    suspend fun match(track: Track): Track {
        matches[track.key]?.let { return it }
        val m = youtube.bestMatch(track.title, track.artists.firstOrNull()?.name.orEmpty(), track.durationMs)
            ?: throw SourceUnavailableException("No playable match for “${track.title}”")
        matches[track.key] = m
        return m
    }

    override suspend fun stream(track: Track, quality: AudioQuality): ResolvedStream =
        youtube.stream(match(track), quality)

    override suspend fun related(track: Track): List<Track> = youtube.related(match(track))

    override fun canOpen(url: String) = url.contains("open.spotify.com") || url.startsWith("spotify:")

    override suspend fun open(url: String): MediaEntry? {
        val m = Regex("(track|album|playlist|artist)[/:]([A-Za-z0-9]{22})").find(url) ?: return null
        val (type, id) = m.destructured
        return when (type) {
            "track" -> parseTrack(api("tracks/$id?market=${market()}"))?.let { MediaEntry.Song(it) }
            "album" -> MediaEntry.Collection(CollectionRef(id, source, CollectionType.ALBUM, "Album"))
            "playlist" -> MediaEntry.Collection(CollectionRef(id, source, CollectionType.PLAYLIST, "Playlist"))
            else -> MediaEntry.Collection(CollectionRef(id, source, CollectionType.ARTIST, "Artist"))
        }
    }

    private fun images(e: JsonElement?): String? = e.path("images").arr?.firstOrNull().path("url").str

    private fun parseTrack(e: JsonElement?, fallbackArt: String? = null, album: String? = null, albumId: String? = null): Track? {
        val id = e.path("id").str ?: return null
        val name = e.path("name").str ?: return null
        return Track(
            id = id,
            source = source,
            title = name,
            artists = e.path("artists").arr.orEmpty().mapNotNull { a ->
                a.path("name").str?.let { ArtistRef(it, a.path("id").str) }
            },
            album = e.path("album", "name").str ?: album,
            albumId = e.path("album", "id").str ?: albumId,
            durationMs = e.path("duration_ms").num ?: 0,
            artworkUrl = images(e.path("album")) ?: fallbackArt,
            explicit = e.path("explicit").bool ?: false,
            url = e.path("external_urls", "spotify").str,
        )
    }

    private fun parseAlbumRef(e: JsonElement?): CollectionRef? {
        val id = e.path("id").str ?: return null
        val artists = e.path("artists").arr.orEmpty().mapNotNull { it.path("name").str }.joinToString(", ")
        val year = e.path("release_date").str?.take(4)
        return CollectionRef(
            id, source, CollectionType.ALBUM, e.path("name").str ?: return null,
            listOfNotNull(artists.takeIf { it.isNotBlank() }, year).joinToString(" • "), images(e),
        )
    }

    private fun parsePlaylistRef(e: JsonElement?): CollectionRef? {
        val id = e.path("id").str ?: return null
        return CollectionRef(
            id, source, CollectionType.PLAYLIST, e.path("name").str ?: return null,
            e.path("owner", "display_name").str, images(e),
        )
    }

    private fun parseArtistRef(e: JsonElement?): CollectionRef? {
        val id = e.path("id").str ?: return null
        return CollectionRef(id, source, CollectionType.ARTIST, e.path("name").str ?: return null, "Artist", images(e))
    }
}
