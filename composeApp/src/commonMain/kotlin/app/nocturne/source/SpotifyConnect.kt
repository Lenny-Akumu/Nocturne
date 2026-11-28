package app.nocturne.source

import app.nocturne.PlatformKind
import app.nocturne.i18n.t
import app.nocturne.model.Source
import app.nocturne.model.Track
import app.nocturne.platformKind
import app.nocturne.player.ExternalPlayer
import app.nocturne.player.ExternalState
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Spotify Connect: the official Spotify app on this device plays the real recording, Nocturne steers it
 * through the Web API. Spotify allows this with Premium only; anything else falls back to the YouTube Music copy.
 */
class SpotifyConnect(private val spotify: SpotifyProvider, private val enabled: () -> Boolean) : ExternalPlayer {
    override val name = "Spotify"
    private val json = Json { ignoreUnknownKeys = true }
    // Premium missing or old sign-in without playback rights: stop asking until the next sign-in
    @kotlin.concurrent.Volatile private var blockedFor: Int? = null
    private var device: String? = null
    // loaded paused (restored queue): nothing is sent to Spotify until play
    private var pending: Pair<List<Track>, Long>? = null

    override fun handles(track: Track) = track.source == Source.SPOTIFY && enabled() && spotify.isSignedIn && blockedFor != spotify.session

    override suspend fun start(tracks: List<Track>, ms: Long, play: Boolean) {
        if (tracks.isEmpty()) return
        if (!play) { pending = tracks to ms; return }
        pending = null
        val body = JsonObject(mapOf(
            "uris" to JsonArray(tracks.map { JsonPrimitive("spotify:track:${it.id}") }),
            "position_ms" to JsonPrimitive(ms.coerceAtLeast(0)),
        )).toString()
        val id = device ?: pickDevice().also { device = it }
        var r = spotify.player(HttpMethod.Put, "me/player/play?device_id=$id", body)
        if (r.status.value == 404) {
            device = pickDevice()
            r = spotify.player(HttpMethod.Put, "me/player/play?device_id=$device", body)
        }
        check(r)
    }

    override suspend fun command(op: String, ms: Long, vol: Float) {
        pending?.let { (tracks, at) ->
            when (op) {
                "play" -> start(tracks, at, true)
                "seek" -> pending = tracks to ms
            }
            return
        }
        val dev = device?.let { "device_id=$it" }
        val path = when (op) {
            "play" -> "me/player/play"
            "pause" -> "me/player/pause"
            "seek" -> "me/player/seek?position_ms=${ms.coerceAtLeast(0)}"
            "vol" -> "me/player/volume?volume_percent=${(vol * 100).toInt().coerceIn(0, 100)}"
            else -> return
        }
        val full = if (dev == null) path else path + (if ('?' in path) "&" else "?") + dev
        val r = spotify.player(HttpMethod.Put, full, null)
        // pausing something already paused is fine
        if (op == "pause" && r.status.value in setOf(403, 404)) return
        check(r)
    }

    override suspend fun poll(): ExternalState? {
        pending?.let { (tracks, at) -> return ExternalState(tracks.first().id, at, tracks.first().durationMs, false) }
        val r = spotify.player(HttpMethod.Get, "me/player", null)
        if (r.status.value == 204 || !r.status.isSuccess()) return null
        val o = runCatching { json.parseToJsonElement(r.bodyAsText()) }.getOrNull() ?: return null
        return ExternalState(
            trackId = o.path("item", "id").str,
            positionMs = o.path("progress_ms").num ?: 0,
            durationMs = o.path("item", "duration_ms").num ?: 0,
            playing = o.path("is_playing").bool ?: false,
        )
    }

    // the device that is already playing, else the Spotify app on this kind of device
    private suspend fun pickDevice(): String {
        val r = spotify.player(HttpMethod.Get, "me/player/devices", null)
        check(r)
        val list = runCatching { json.parseToJsonElement(r.bodyAsText()).path("devices").arr.orEmpty() }.getOrDefault(emptyList())
            .filter { it.path("is_restricted").bool != true && it.path("id").str != null }
        val kind = if (platformKind == PlatformKind.DESKTOP) "computer" else "smartphone"
        val d = list.firstOrNull { it.path("is_active").bool == true }
            ?: list.firstOrNull { it.path("type").str.equals(kind, true) }
            ?: list.firstOrNull()
        return d.path("id").str ?: throw SourceUnavailableException(t("Open the Spotify app on this device for the original Spotify audio. Playing the same recording from YouTube Music for now."))
    }

    private suspend fun check(r: HttpResponse) {
        if (r.status.isSuccess()) return
        val text = runCatching { r.bodyAsText() }.getOrDefault("")
        val err = runCatching { json.parseToJsonElement(text).path("error") }.getOrNull()
        val reason = err.path("reason").str.orEmpty()
        val msg = err.path("message").str ?: text.take(160)
        val code = r.status.value
        when {
            reason == "PREMIUM_REQUIRED" || msg.contains("premium", true) -> {
                blockedFor = spotify.session
                throw SourceUnavailableException(t("The original Spotify audio needs Spotify Premium. Playing the same recording from YouTube Music."))
            }
            code == 401 || msg.contains("scope", true) || msg.contains("permissions missing", true) -> {
                blockedFor = spotify.session
                throw SourceUnavailableException(t("Sign in to Spotify again (Settings → Accounts) to play the original Spotify audio."))
            }
            code == 404 -> {
                device = null
                throw SourceUnavailableException(t("Open the Spotify app on this device for the original Spotify audio. Playing the same recording from YouTube Music for now."))
            }
            else -> throw SourceUnavailableException("Spotify: $msg")
        }
    }
}
