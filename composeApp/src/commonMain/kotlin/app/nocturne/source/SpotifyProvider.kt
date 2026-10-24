package app.nocturne.source

import app.nocturne.FileStore
import app.nocturne.authorizeInBrowser
import app.nocturne.i18n.t
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
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.HttpMethod
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import io.ktor.http.parseQueryString
import kotlin.io.encoding.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

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
        get() = accounts.value.spotifySignedIn || credentials().let { it.first.isNotBlank() && it.second.isNotBlank() }

    /** Client id for PKCE sign-in: Settings override, else BuildInfo. */
    val clientId: String get() = credentials().first

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
                append("client_id", acc.spotifyClientId.ifBlank { clientId })
            },
        )
        if (!response.status.isSuccess()) {
            if (response.status.value == 400) signOut()
            throw SourceUnavailableException(t("Spotify session expired, sign in again"))
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

    // PKCE Authorization Code flow; verifier/state persist so Android can finish after process death.
    suspend fun signIn() {
        val id = clientId
        if (id.isBlank()) throw SourceUnavailableException(t("Add your Spotify Client ID first"))
        val verifier = Base64.UrlSafe.encode(secureRandomBytes(48)).trimEnd('=')
        val challenge = Base64.UrlSafe.encode(sha256(verifier.encodeToByteArray())).trimEnd('=')
        val state = Base64.UrlSafe.encode(secureRandomBytes(12)).trimEnd('=')
        FileStore.write(PKCE_FILE, listOf(verifier, state, id).joinToString("\n"))
        val url = "https://accounts.spotify.com/authorize?client_id=${id.encodeURLParameter()}&response_type=code" +
            "&redirect_uri=${spotifyRedirectUri.encodeURLParameter()}&code_challenge_method=S256&code_challenge=$challenge" +
            "&state=$state&scope=${SCOPES.encodeURLParameter()}"
        val redirect = try {
            authorizeInBrowser(url)
        } catch (e: TimeoutCancellationException) {
            throw SourceUnavailableException(t("Spotify sign-in timed out. Try again."))
        }
        completeSignIn(redirect)
    }

    /** Finishes sign-in from a redirect like nocturne://spotify-callback?code=…&state=… */
    suspend fun completeSignIn(redirect: String) {
        val saved = FileStore.read(PKCE_FILE)?.lines()?.takeIf { it.size >= 3 }
            ?: throw SourceUnavailableException(t("Spotify sign-in expired, try again"))
        val (verifier, state, id) = saved
        val params = parseQueryString(redirect.substringAfter('?', "").substringBefore('#'))
        params["error"]?.let {
            FileStore.delete(PKCE_FILE)
            throw SourceUnavailableException(if (it == "access_denied") t("Spotify sign-in was cancelled") else "Spotify: $it")
        }
        if (params["state"] != state) throw SourceUnavailableException(t("Spotify sign-in was interrupted, try again"))
        val code = params["code"] ?: throw SourceUnavailableException(t("Spotify sign-in was cancelled"))
        FileStore.delete(PKCE_FILE)
        val response = withTimeout(30_000) {
            http.submitForm(
                url = "https://accounts.spotify.com/api/token",
                formParameters = parameters {
                    append("grant_type", "authorization_code")
                    append("code", code)
                    append("redirect_uri", spotifyRedirectUri)
                    append("client_id", id)
                    append("code_verifier", verifier)
                },
            )
        }
        if (!response.status.isSuccess()) {
            val body = runCatching { json.parseToJsonElement(response.bodyAsText()) }.getOrNull()
            val why = body.path("error_description").str ?: body.path("error").str ?: response.status.toString()
            throw SourceUnavailableException(t("Spotify sign-in failed: %s", why))
        }
        saveTokens(json.parseToJsonElement(response.bodyAsText()))
        accounts.update { it.copy(spotifyClientId = id) }
        me = null
        val profile = runCatching { api("me") }.getOrNull()
        accounts.update {
            it.copy(
                spotifyName = profile.path("display_name").str?.takeIf { n -> n.isNotBlank() } ?: profile.path("id").str ?: "Spotify user",
                spotifyAvatar = images(profile),
            )
        }
    }

    fun signOut() = accounts.update { it.copy(spotifyAccessToken = "", spotifyRefreshToken = "", spotifyExpiresAt = 0, spotifyName = null, spotifyAvatar = null) }

    override suspend fun library(): AccountLibrary? {
        if (!isSignedIn) return null
        val playlists = runCatching { api("me/playlists?limit=50").path("items").arr.orEmpty().mapNotNull { parsePlaylistRef(it) } }.getOrDefault(emptyList())
        val albums = runCatching { api("me/albums?limit=50&market=${market()}").path("items").arr.orEmpty().mapNotNull { parseAlbumRef(it.path("album")) } }.getOrDefault(emptyList())
        val artists = runCatching { api("me/following?type=artist&limit=50").path("artists", "items").arr.orEmpty().mapNotNull { parseArtistRef(it) } }.getOrDefault(emptyList())
        return AccountLibrary(source, LikedRef, playlists, albums, artists)
    }

    private var me: Pair<String, String>? = null

    private suspend fun userId(): String {
        val rt = accounts.value.spotifyRefreshToken
        me?.let { if (it.first == rt) return it.second }
        val id = api("me").path("id").str ?: throw SourceUnavailableException("Spotify: unknown user")
        me = rt to id
        return id
    }

    override suspend fun editablePlaylists(): List<CollectionRef> {
        if (!isSignedIn) return emptyList()
        val uid = userId()
        val out = mutableListOf<CollectionRef>()
        var page: JsonElement? = api("me/playlists?limit=50")
        var pages = 0
        while (page != null && pages++ < 10) {
            page.path("items").arr.orEmpty()
                .filter { it.path("owner", "id").str == uid || it.path("collaborative").bool == true }
                .mapNotNullTo(out) { parsePlaylistRef(it) }
            page = page.path("next").str?.let { api(it) }
        }
        return out
    }

    private suspend fun raw(method: HttpMethod, path: String, body: String?): HttpResponse =
        http.request("https://api.spotify.com/v1/$path") {
            this.method = method
            header("Authorization", "Bearer ${accessToken()}")
            if (body != null) { contentType(ContentType.Application.Json); setBody(body) }
        }

    private suspend fun ok(r: HttpResponse): JsonElement? {
        if (r.status.value == 403) throw SourceUnavailableException("Spotify denied the change. Sign in to Spotify again to allow playlist editing.")
        if (!r.status.isSuccess()) throw SourceUnavailableException("Spotify: ${r.status}")
        return r.bodyAsText().takeIf { it.isNotBlank() }?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() }
    }

    // POST/DELETE playlist items; retries the newer /items path if /tracks is gone.
    private suspend fun editItems(method: HttpMethod, id: String, body: (String) -> String) {
        val r = raw(method, "playlists/$id/tracks", body("tracks"))
        ok(if (r.status.value == 404 || r.status.value == 410) raw(method, "playlists/$id/items", body("items")) else r)
    }

    private fun uri(t: Track) = JsonPrimitive("spotify:track:${t.id}")

    override suspend fun addToPlaylist(playlistId: String, tracks: List<Track>) {
        tracks.chunked(100).forEach { chunk ->
            editItems(HttpMethod.Post, playlistId) { JsonObject(mapOf("uris" to JsonArray(chunk.map { uri(it) }))).toString() }
        }
    }

    override suspend fun removeFromPlaylist(playlistId: String, track: Track) =
        editItems(HttpMethod.Delete, playlistId) { k -> JsonObject(mapOf(k to JsonArray(listOf(JsonObject(mapOf("uri" to uri(track))))))).toString() }

    override suspend fun createPlaylist(name: String, tracks: List<Track>): CollectionRef {
        val body = JsonObject(mapOf("name" to JsonPrimitive(name), "public" to JsonPrimitive(false))).toString()
        val r = raw(HttpMethod.Post, "me/playlists", body)
        val created = ok(if (r.status.value == 404 || r.status.value == 405) raw(HttpMethod.Post, "users/${userId()}/playlists", body) else r)
        val ref = parsePlaylistRef(created) ?: throw SourceUnavailableException("Couldn't create playlist")
        if (tracks.isNotEmpty()) addToPlaylist(ref.id, tracks)
        return ref.copy(artworkUrl = ref.artworkUrl ?: tracks.firstOrNull()?.artworkUrl)
    }

    private suspend fun appToken(): String = tokenLock.withLock {
        val creds = credentials()
        if (creds.first.isBlank() || creds.second.isBlank()) {
            throw SourceUnavailableException(t("Connect Spotify in Settings → Accounts"))
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

    override suspend fun rate(track: Track, rating: Rating) {
        if (!isSignedIn) return
        val method = if (rating == Rating.LIKE) HttpMethod.Put else HttpMethod.Delete
        http.request("https://api.spotify.com/v1/me/tracks?ids=${track.id}") {
            this.method = method
            header("Authorization", "Bearer ${accessToken()}")
        }
    }

    override suspend fun likedTracks(): List<Track> =
        if (!isSignedIn) emptyList() else pagedTracks(robust("me/tracks?limit=50&market=${market()}"), 2000)

    private suspend fun api(path: String): JsonElement {
        val url = if (path.startsWith("http")) path else "https://api.spotify.com/v1/$path"
        var tries = 0
        while (true) {
            val response = http.get(url) { header("Authorization", "Bearer ${accessToken()}") }
            val code = response.status.value
            if (code == 429 && tries++ < 3) {
                delay((response.headers["Retry-After"]?.toLongOrNull() ?: 2).coerceIn(1, 15) * 1000)
                continue
            }
            if (code >= 500 && tries++ < 2) {
                delay(1500)
                continue
            }
            if (code == 401 && isSignedIn && tries++ < 1) { // stale access token: force a refresh once
                accounts.update { it.copy(spotifyExpiresAt = 0) }
                continue
            }
            if (!response.status.isSuccess()) {
                // 403 bodies are often plain text ("Check settings on developer.spotify.com/dashboard, the user may not be registered.")
                val body = runCatching { response.bodyAsText() }.getOrDefault("")
                val msg = runCatching { json.parseToJsonElement(body).path("error", "message").str }.getOrNull() ?: body.take(200).takeIf { it.isNotBlank() }
                val notRegistered = msg?.let { it.contains("dashboard", true) || it.contains("not be registered", true) || it.contains("user may not", true) } == true
                throw SourceUnavailableException(
                    when {
                        code == 403 && notRegistered -> t("Your Spotify account isn't added to the Spotify app's User Management list")
                        code == 403 -> t("Spotify refused access (403). Open developer.spotify.com/dashboard → your app → User Management and add the email of your Spotify account, then sign in again.")
                        else -> "Spotify: ${msg ?: response.status.toString()}"
                    },
                )
            }
            return json.parseToJsonElement(response.bodyAsText())
        }
    }

    // api() with a per-request timeout and retries for flaky networks.
    private suspend fun robust(path: String): JsonElement {
        var last: Exception? = null
        for (attempt in 0 until 3) {
            try {
                return withTimeout(30_000) { api(path) }
            } catch (e: TimeoutCancellationException) {
                last = SourceUnavailableException(t("Spotify is not responding. Check your connection and try again."))
            } catch (e: CancellationException) {
                throw e
            } catch (e: SourceUnavailableException) {
                throw e
            } catch (e: Exception) {
                last = e
            }
            delay(1500L * (attempt + 1))
        }
        throw last ?: SourceUnavailableException("Spotify")
    }

    // Follows `next` links; items are {track}/{item} wrappers (playlists, likes) or plain tracks (albums).
    private suspend fun pagedTracks(
        first: JsonElement?,
        max: Int,
        fallbackArt: String? = null,
        album: String? = null,
        albumId: String? = null,
        onProgress: (Int, Int?) -> Unit = { _, _ -> },
    ): List<Track> {
        val tracks = mutableListOf<Track>()
        var page = first
        while (page != null && tracks.size < max) {
            page.path("items").arr.orEmpty().mapNotNullTo(tracks) { item ->
                val e = item.path("track") as? JsonObject ?: item.path("item") as? JsonObject ?: item
                if (e.path("type").str == "episode") null else parseTrack(e, fallbackArt, album, albumId)
            }
            onProgress(tracks.size, page.path("total").num?.toInt())
            page = page.path("next").str?.let { robust(it) }
        }
        return tracks.distinctBy { it.id }
    }

    private suspend fun playlistTracks(id: String, pl: JsonElement, max: Int, onProgress: (Int, Int?) -> Unit = { _, _ -> }): List<Track> {
        val embedded = (pl.path("tracks") ?: pl.path("items"))?.takeIf { it.path("items") is JsonArray }
        val first = embedded ?: try {
            robust("playlists/$id/tracks?limit=100&market=${market()}")
        } catch (e: SourceUnavailableException) {
            robust("playlists/$id/items?limit=100&market=${market()}")
        }
        return pagedTracks(first, max, onProgress = onProgress)
    }

    /** Liked Songs for import, with progress (done, total). */
    suspend fun importLiked(onProgress: (Int, Int?) -> Unit): List<Track> {
        if (!isSignedIn) throw SourceUnavailableException(t("Connect Spotify first"))
        return pagedTracks(robust("me/tracks?limit=50&market=${market()}"), 10_000, onProgress = onProgress)
    }

    /** The user's playlists (own and followed) for the import picker. */
    suspend fun myPlaylists(): List<CollectionRef> {
        if (!isSignedIn) return emptyList()
        val out = mutableListOf<CollectionRef>()
        var page: JsonElement? = robust("me/playlists?limit=50")
        while (page != null && out.size < 500) {
            page.path("items").arr.orEmpty().mapNotNullTo(out) { parsePlaylistRef(it) }
            page = page.path("next").str?.let { robust(it) }
        }
        return out
    }

    /** Playlist or album by link/URI (or "playlist:<id>"); falls back to the public embed page. */
    suspend fun importCollection(link: String, onProgress: (Int, Int?) -> Unit = { _, _ -> }): Pair<String, List<Track>> {
        val m = Regex("(album|playlist)[/:]([A-Za-z0-9]{22})").find(link)
            ?: throw SourceUnavailableException(t("Paste a link to a Spotify playlist or album"))
        val (type, id) = m.destructured
        if (!isConfigured) return importLink(link)
        return try {
            val result = if (type == "album") {
                val a = robust("albums/$id?market=${market()}")
                val ref = parseAlbumRef(a)
                (ref?.title ?: "Spotify") to pagedTracks(a.path("tracks"), 2000, ref?.artworkUrl, ref?.title, id, onProgress)
            } else {
                val pl = robust("playlists/$id?market=${market()}")
                (pl.path("name").str ?: "Spotify") to playlistTracks(id, pl, 10_000, onProgress)
            }
            if (result.second.isEmpty()) throw SourceUnavailableException(t("This playlist is empty or private"))
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            runCatching { importLink(link) }.getOrElse { throw e }
        }
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
        if (isSignedIn) coroutineScope {
            val top = async { runCatching { api("me/top/tracks?limit=24&time_range=short_term").path("items").arr.orEmpty().mapNotNull { parseTrack(it) } }.getOrNull() }
            val recent = async {
                runCatching { api("me/player/recently-played?limit=40").path("items").arr.orEmpty().mapNotNull { parseTrack(it.path("track")) }.distinctBy { it.id } }.getOrNull()
            }
            val lib = async { runCatching { library() }.getOrNull() }
            top.await()?.takeIf { it.isNotEmpty() }?.let {
                sections += Section("Your top tracks on Spotify", it.map { t -> MediaEntry.Song(t) }, SectionLayout.QUICK_PICKS, source)
            }
            recent.await()?.takeIf { it.isNotEmpty() }?.let {
                sections += Section("Recently played on Spotify", it.map { t -> MediaEntry.Song(t) }, source = source)
            }
            lib.await()?.let { lib ->
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
            val tracks = pagedTracks(robust("me/tracks?limit=50&market=${market()}"), 1000)
            CollectionPage(LikedRef.copy(subtitle = app.nocturne.i18n.t("%s songs", tracks.size), artworkUrl = tracks.firstOrNull()?.artworkUrl), tracks)
        } else {
            val pl = api("playlists/${ref.id}?market=${market()}")
            val fullRef = parsePlaylistRef(pl) ?: ref
            val tracks = playlistTracks(ref.id, pl, 1000)
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

    override suspend fun videoStream(track: Track): String? = youtube.videoStream(match(track))

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

    // Public playlist/album without the Web API: scrapes the embed page.
    private suspend fun importLink(url: String): Pair<String, List<Track>> {
        val m = Regex("(album|playlist|track)[/:]([A-Za-z0-9]{22})").find(url) ?: throw SourceUnavailableException(t("Paste a link to a Spotify playlist or album"))
        val (type, id) = m.destructured
        val html = http.get("https://open.spotify.com/embed/$type/$id") {
            header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36")
        }.bodyAsText()
        val raw = Regex("<script id=\"__NEXT_DATA__\"[^>]*>(.*?)</script>", RegexOption.DOT_MATCHES_ALL).find(html)?.groupValues?.get(1)
            ?: throw SourceUnavailableException(t("Spotify didn't return this playlist. Make sure it's public."))
        val entity = json.parseToJsonElement(raw).findAll("entity").firstOrNull() ?: throw SourceUnavailableException("Nothing to import")
        val name = entity.path("name").str ?: entity.path("title").str ?: "Spotify"
        val cover = entity.path("coverArt", "sources").arr?.maxByOrNull { it.path("width").num ?: 0 }.path("url").str
            ?: entity.path("visualIdentity", "image").arr?.maxByOrNull { it.path("maxWidth").num ?: 0 }.path("url").str
        val items = entity.path("trackList").arr?.toList() ?: listOf(entity)
        val tracks = items.mapNotNull { t ->
            val tid = t.path("uri").str?.substringAfterLast(':') ?: t.path("id").str ?: return@mapNotNull null
            val title = t.path("title").str ?: t.path("name").str ?: return@mapNotNull null
            val artists = (t.path("subtitle").str ?: t.path("artists").arr?.joinToString(", ") { it.path("name").str.orEmpty() }.orEmpty())
                .split(",").map { it.trim().replace("\u00a0", " ") }.filter { it.isNotEmpty() }.map { ArtistRef(it) }
            Track(
                id = tid,
                source = source,
                title = title,
                artists = artists,
                album = if (type == "album") name else null,
                albumId = if (type == "album") id else null,
                durationMs = t.path("duration").num ?: t.path("duration_ms").num ?: 0,
                artworkUrl = if (type == "album" || type == "track") cover else null,
                explicit = t.path("isExplicit").bool ?: false,
            )
        }
        if (tracks.isEmpty()) throw SourceUnavailableException(t("This playlist is empty or private"))
        return name to tracks
    }

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
        private const val PKCE_FILE = "spotify_pkce.txt"
        private const val SCOPES = "user-read-private user-read-email user-library-read user-library-modify playlist-read-private " +
            "playlist-read-collaborative playlist-modify-public playlist-modify-private user-top-read user-read-recently-played user-follow-read"
    }
}
