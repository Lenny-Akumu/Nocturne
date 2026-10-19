package app.nocturne.source

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
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.http.HttpMethod
import io.ktor.http.encodeURLParameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

class SoundCloudProvider(
    private val newPipe: NewPipeBackend,
    private val http: HttpClient,
    private val accounts: AccountsStore,
) : MusicProvider {
    override val source = Source.SOUNDCLOUD

    private val json = Json { ignoreUnknownKeys = true }

    override val isSignedIn: Boolean get() = accounts.value.soundcloudSignedIn

    private suspend fun api(path: String): JsonElement {
        val clientId = newPipe.soundcloudClientId()
        val sep = if (path.contains('?')) '&' else '?'
        val response = http.get("https://api-v2.soundcloud.com/$path${sep}client_id=$clientId") {
            header("Authorization", "OAuth ${accounts.value.soundcloudToken}")
        }
        if (response.status.value == 401) throw SourceUnavailableException("SoundCloud session expired, sign in again")
        if (!response.status.isSuccess()) throw SourceUnavailableException("SoundCloud: ${response.status}")
        return json.parseToJsonElement(response.bodyAsText())
    }

    suspend fun signIn(token: String) {
        val clean = token.trim().removePrefix("OAuth ").substringAfter("oauth_token=").substringBefore(';').trim()
        accounts.update { it.copy(soundcloudToken = clean) }
        val me = runCatching { api("me") }.getOrElse {
            accounts.update { a -> a.copy(soundcloudToken = "") }
            throw it
        }
        accounts.update { it.copy(soundcloudUserId = me.path("id").num, soundcloudName = me.path("username").str ?: "SoundCloud user") }
    }

    fun signOut() = accounts.update { it.copy(soundcloudToken = "", soundcloudUserId = null, soundcloudName = null) }

    override suspend fun library(): AccountLibrary? {
        val id = accounts.value.soundcloudUserId ?: return null
        if (!isSignedIn) return null
        val own = runCatching { api("users/$id/playlists_without_albums?limit=50").path("collection").arr.orEmpty().mapNotNull { playlistRef(it) } }.getOrDefault(emptyList())
        val liked = runCatching { api("users/$id/playlist_likes?limit=50").path("collection").arr.orEmpty().mapNotNull { playlistRef(it.path("playlist")) } }.getOrDefault(emptyList())
        val albums = runCatching { api("users/$id/albums?limit=50").path("collection").arr.orEmpty().mapNotNull { playlistRef(it)?.copy(type = CollectionType.ALBUM) } }.getOrDefault(emptyList())
        val artists = runCatching {
            api("users/$id/followings?limit=50").path("collection").arr.orEmpty().mapNotNull { u ->
                val url = u.path("permalink_url").str ?: return@mapNotNull null
                CollectionRef(url, source, CollectionType.ARTIST, u.path("username").str ?: return@mapNotNull null, "Artist", u.path("avatar_url").str?.let(::upscaleArtwork))
            }
        }.getOrDefault(emptyList())
        return AccountLibrary(source, LikesRef, own + liked, albums, artists)
    }

    private fun playlistRef(e: JsonElement?): CollectionRef? {
        val url = e.path("permalink_url").str ?: return null
        val art = e.path("artwork_url").str ?: e.path("tracks", 0, "artwork_url").str
        return CollectionRef(url, source, CollectionType.PLAYLIST, e.path("title").str ?: return null, e.path("user", "username").str, art?.let(::upscaleArtwork))
    }

    private fun scTrack(e: JsonElement?): Track? {
        val url = e.path("permalink_url").str ?: return null
        return Track(
            id = url,
            source = source,
            title = e.path("title").str ?: return null,
            artists = listOfNotNull(e.path("user", "username").str?.let { ArtistRef(it, e.path("user", "permalink_url").str) }),
            durationMs = e.path("full_duration").num ?: e.path("duration").num ?: 0,
            artworkUrl = (e.path("artwork_url").str ?: e.path("user", "avatar_url").str)?.let(::upscaleArtwork),
            url = url,
        )
    }

    override suspend fun rate(track: Track, rating: Rating) {
        val uid = accounts.value.soundcloudUserId ?: return
        if (!isSignedIn) return
        val url = track.url ?: track.id
        val trackId = api("resolve?url=${url.encodeURLParameter()}").path("id").num ?: return
        val clientId = newPipe.soundcloudClientId()
        http.request("https://api-v2.soundcloud.com/users/$uid/track_likes/$trackId?client_id=$clientId") {
            method = if (rating == Rating.LIKE) HttpMethod.Put else HttpMethod.Delete
            header("Authorization", "OAuth ${accounts.value.soundcloudToken}")
        }
    }

    override suspend fun likedTracks(): List<Track> {
        val id = accounts.value.soundcloudUserId ?: return emptyList()
        val tracks = mutableListOf<Track>()
        var next: String? = "users/$id/track_likes?limit=100"
        while (next != null && tracks.size < 1000) {
            val page = api(next)
            page.path("collection").arr.orEmpty().mapNotNullTo(tracks) { scTrack(it.path("track")) }
            next = page.path("next_href").str?.substringAfter("api-v2.soundcloud.com/")?.replace(Regex("[&?]client_id=[^&]*"), "")
        }
        return tracks
    }

    override suspend fun search(query: String, filter: SearchFilter) = newPipe.search(source, query, filter)

    override suspend fun home(): List<Section> {
        val mine = if (isSignedIn) runCatching {
            val likes = likedTracks().take(24)
            val lib = library()
            listOfNotNull(
                likes.takeIf { it.isNotEmpty() }?.let { Section("Your SoundCloud likes", it.map { t -> MediaEntry.Song(t) }, SectionLayout.QUICK_PICKS, source) },
                lib?.playlists?.takeIf { it.isNotEmpty() }?.let { Section("Your SoundCloud playlists", it.map { p -> MediaEntry.Collection(p) }, source = source) },
            )
        }.getOrDefault(emptyList()) else emptyList()
        val charts = runCatching { newPipe.charts(source) }.getOrDefault(emptyList())
        if (charts.isEmpty()) return mine
        return mine + Section("SoundCloud charts", charts.map { MediaEntry.Song(it) }, SectionLayout.QUICK_PICKS, source)
    }

    override suspend fun collection(ref: CollectionRef): CollectionPage = when {
        ref.id == LikesRef.id -> likedTracks().let { CollectionPage(LikesRef.copy(subtitle = "${it.size} songs", artworkUrl = it.firstOrNull()?.artworkUrl), it) }
        ref.type == CollectionType.ARTIST -> newPipe.channel(source, ref.id, ref)
        else -> newPipe.playlist(source, ref.id, ref)
    }

    override suspend fun stream(track: Track, quality: AudioQuality): ResolvedStream =
        newPipe.stream(source, track.url ?: track.id, quality)

    override suspend fun related(track: Track): List<Track> =
        runCatching { newPipe.related(source, track.url ?: track.id) }.getOrDefault(emptyList())

    override fun canOpen(url: String) = url.contains("soundcloud.com")

    override suspend fun open(url: String): MediaEntry? {
        val clean = url.substringBefore('?').trimEnd('/')
        val path = clean.substringAfter("soundcloud.com/", "")
        val segments = path.split('/').filter { it.isNotEmpty() }
        return when {
            segments.size >= 3 && segments[1] == "sets" -> MediaEntry.Collection(newPipe.playlist(source, clean, null).ref)
            segments.size == 1 -> MediaEntry.Collection(newPipe.channel(source, clean, null).ref)
            segments.size >= 2 -> MediaEntry.Song(newPipe.track(source, clean))
            else -> null
        }
    }

    companion object {
        val LikesRef = CollectionRef("me:likes", Source.SOUNDCLOUD, CollectionType.PLAYLIST, "Liked tracks", "SoundCloud")
    }
}
