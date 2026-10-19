package app.nocturne.source

import app.nocturne.authorizeInBrowser
import app.nocturne.data.AccountsStore
import app.nocturne.model.AccountLibrary
import app.nocturne.model.ArtistRef
import app.nocturne.model.AudioQuality
import app.nocturne.model.CollectionPage
import app.nocturne.model.CollectionRef
import app.nocturne.model.CollectionType
import app.nocturne.model.MediaEntry
import app.nocturne.model.ResolvedStream
import app.nocturne.model.SearchFilter
import app.nocturne.model.Section
import app.nocturne.model.SectionLayout
import app.nocturne.model.Source
import app.nocturne.model.Track
import app.nocturne.nowMillis
import app.nocturne.secureRandomBytes
import app.nocturne.sha256
import app.nocturne.spotifyRedirectUri
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import kotlin.io.encoding.Base64
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

class SpotifyProvider(
    private val http: HttpClient,
    private val credentials: () -> Pair<String, String>,
    private val market: () -> String,
    private val youtube: YouTubeMusicProvider,
    private val accounts: AccountsStore,
) : MusicProvider {
    override val source = Source.SPOTIFY

    private val json = Json { ignoreUnknownKeys = true }
    private val tokenLock = Mutex()
    private var token: String? = null
    private var tokenFor: Pair<String, String>? = null
    private var tokenExpiry = 0L
    private val matches = mutableMapOf<String, Track>()

    val isConfigured: Boolean
        get() = credentials().let { it.first.isNotBlank() && (it.second.isNotBlank() || accounts.value.spotifySignedIn) }

    override val isSignedIn: Boolean get() = accounts.value.spotifySignedIn

    private suspend fun accessToken(): String = if (isSignedIn) userToken() else appToken()

    private suspend fun userToken(): String = tokenLock.withLock {
        val acc = accounts.value
        if (acc.spotifyAccessToken.isNotBlank() && nowMillis() < acc.spotifyExpiresAt) return@withLock acc.spotifyAccessToken
        val response = http.submitForm(
            url = "https://accounts.spotify.com/api/token",
            formParameters = parameters {
                append("grant_type", "refresh_token")
                append("refresh_token", acc.spotifyRefreshToken)
                append("client_id", credentials().first)
            },
        )
        if (!response.status.isSuccess()) {
            if (response.status.value == 400) accounts.update { it.copy(spotifyAccessToken = "", spotifyRefreshToken = "", spotifyName = null) }
            throw SourceUnavailableException("Spotify session expired, sign in again")
        }
        saveTokens(json.parseToJsonElement(response.bodyAsText()))
        accounts.value.spotifyAccessToken
    }

    private fun saveTokens(body: JsonElement) {
        val access = body.path("access_token").str ?: throw SourceUnavailableException("Spotify sign-in failed")
        accounts.update {
            it.copy(
                spotifyAccessToken = access,
                spotifyRefreshToken = body.path("refresh_token").str ?: it.spotifyRefreshToken,
                spotifyExpiresAt = nowMillis() + ((body.path("expires_in").num ?: 3600) - 60) * 1000,
            )
        }
    }

    suspend fun signIn() {
        val clientId = credentials().first
        if (clientId.isBlank()) throw SourceUnavailableException("Add your Spotify Client ID first")
        val verifier = Base64.UrlSafe.encode(secureRandomBytes(48)).trimEnd('=')
        val challenge = Base64.UrlSafe.encode(sha256(verifier.encodeToByteArray())).trimEnd('=')
        val state = Base64.UrlSafe.encode(secureRandomBytes(12)).trimEnd('=')
        val url = "https://accounts.spotify.com/authorize?client_id=$clientId&response_type=code" +
            "&redirect_uri=${spotifyRedirectUri.encodeURLParameter()}&code_challenge_method=S256&code_challenge=$challenge" +
            "&state=$state&scope=${SCOPES.encodeURLParameter()}"
        val redirect = authorizeInBrowser(url)
        val params = redirect.substringAfter('?', "").split('&').associate { it.substringBefore('=') to it.substringAfter('=', "") }
        params["error"]?.let { throw SourceUnavailableException("Spotify: $it") }
        if (params["state"] != state) throw SourceUnavailableException("Spotify sign-in was interrupted")
        val code = params["code"] ?: throw SourceUnavailableException("Spotify sign-in was cancelled")
        val response = http.submitForm(
            url = "https://accounts.spotify.com/api/token",
            formParameters = parameters {
                append("grant_type", "authorization_code")
                append("code", code)
                append("redirect_uri", spotifyRedirectUri)
                append("client_id", clientId)
                append("code_verifier", verifier)
            },
        )
        if (!response.status.isSuccess()) throw SourceUnavailableException("Spotify sign-in failed: ${response.status}")
        saveTokens(json.parseToJsonElement(response.bodyAsText()))
        val name = runCatching { api("me").path("display_name").str }.getOrNull()
        accounts.update { it.copy(spotifyName = name ?: "Spotify user") }
    }

    fun signOut() = accounts.update { it.copy(spotifyAccessToken = "", spotifyRefreshToken = "", spotifyExpiresAt = 0, spotifyName = null) }

    override suspend fun library(): AccountLibrary? {
        if (!isSignedIn) return null
        val playlists = runCatching { api("me/playlists?limit=50").path("items").arr.orEmpty().mapNotNull { parsePlaylistRef(it) } }.getOrDefault(emptyList())
        val albums = runCatching { api("me/albums?limit=50&market=${market()}").path("items").arr.orEmpty().mapNotNull { parseAlbumRef(it.path("album")) } }.getOrDefault(emptyList())
        val artists = runCatching { api("me/following?type=artist&limit=50").path("artists", "items").arr.orEmpty().mapNotNull { parseArtistRef(it) } }.getOrDefault(emptyList())
        return AccountLibrary(source, LikedRef, playlists, albums, artists)
    }

    private suspend fun appToken(): String = tokenLock.withLock {
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
        val sections = mutableListOf<Section>()
        if (isSignedIn) {
            runCatching { api("me/top/tracks?limit=24&time_range=short_term").path("items").arr.orEmpty().mapNotNull { parseTrack(it) } }
                .getOrNull()?.takeIf { it.isNotEmpty() }?.let {
                    sections += Section("Your top tracks on Spotify", it.map { t -> MediaEntry.Song(t) }, SectionLayout.QUICK_PICKS, source)
                }
            library()?.let { lib ->
                val own = listOfNotNull(lib.liked) + lib.playlists
                if (own.isNotEmpty()) sections += Section("Your Spotify playlists", own.map { MediaEntry.Collection(it) }, source = source)
                if (lib.albums.isNotEmpty()) sections += Section("Saved albums on Spotify", lib.albums.map { MediaEntry.Collection(it) }, source = source)
            }
        }
        runCatching {
            api("browse/new-releases?limit=20&country=${market()}").path("albums", "items").arr.orEmpty().mapNotNull { parseAlbumRef(it) }
        }.getOrNull()?.takeIf { it.isNotEmpty() }?.let {
            sections += Section("New releases on Spotify", it.map { a -> MediaEntry.Collection(a) }, source = source)
        }
        return sections
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
        CollectionType.PLAYLIST -> if (ref.id == LikedRef.id) {
            val tracks = mutableListOf<Track>()
            var page: JsonElement? = api("me/tracks?limit=50&market=${market()}")
            while (page != null && tracks.size < 1000) {
                page.path("items").arr.orEmpty().mapNotNullTo(tracks) { parseTrack(it.path("track")) }
                page = page.path("next").str?.let { api(it) }
            }
            CollectionPage(LikedRef.copy(subtitle = "${tracks.size} songs", artworkUrl = tracks.firstOrNull()?.artworkUrl), tracks)
        } else {
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

    companion object {
        val LikedRef = CollectionRef("me:liked", Source.SPOTIFY, CollectionType.PLAYLIST, "Liked Songs", "Spotify")
        private const val SCOPES = "user-read-private user-library-read playlist-read-private playlist-read-collaborative user-follow-read user-top-read"
    }
}
