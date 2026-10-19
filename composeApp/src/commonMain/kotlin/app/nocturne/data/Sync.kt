package app.nocturne.data

import app.nocturne.AppGraph
import app.nocturne.aesDecrypt
import app.nocturne.aesEncrypt
import app.nocturne.platformKind
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.plugins.timeout
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64

@Serializable
data class SyncBundle(
    val accounts: Accounts,
    val spotifyClientId: String = "",
    val library: LibraryData? = null,
    val from: String = "",
)

data class PairTarget(val hosts: List<String>, val port: Int, val key: ByteArray)

object Sync {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    fun qr(hosts: List<String>, port: Int, key: ByteArray): String =
        "nocturne://pair?h=${hosts.joinToString(",")}&p=$port&k=${Base64.UrlSafe.encode(key).trimEnd('=')}"

    fun parse(text: String): PairTarget? {
        if (!text.startsWith("nocturne://pair?")) return null
        val q = text.substringAfter('?').split('&').associate { it.substringBefore('=') to it.substringAfter('=', "") }
        val hosts = q["h"]?.split(',')?.filter { it.isNotBlank() }.orEmpty()
        val port = q["p"]?.toIntOrNull() ?: return null
        val key = runCatching { Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(q["k"].orEmpty()) }.getOrNull() ?: return null
        if (hosts.isEmpty() || key.size != 32) return null
        return PairTarget(hosts, port, key)
    }

    fun bundle(graph: AppGraph) = SyncBundle(
        accounts = graph.accounts.value,
        spotifyClientId = graph.settings.value.spotifyClientId,
        library = graph.library.state.value.copy(searchHistory = emptyList(), searchPlays = emptyList()),
        from = platformKind.name,
    )

    fun seal(key: ByteArray, bundle: SyncBundle): String =
        Base64.encode(aesEncrypt(key, json.encodeToString(SyncBundle.serializer(), bundle).encodeToByteArray()))

    fun open(key: ByteArray, body: String): SyncBundle =
        json.decodeFromString(SyncBundle.serializer(), aesDecrypt(key, Base64.decode(body.trim())).decodeToString())

    suspend fun send(graph: AppGraph, target: PairTarget) {
        val body = seal(target.key, bundle(graph))
        var last: Throwable? = null
        for (host in target.hosts) {
            try {
                val r = graph.http.post("http://$host:${target.port}/pair") {
                    setBody(body)
                    timeout { connectTimeoutMillis = 4000; requestTimeoutMillis = 15000 }
                }
                if (r.status.isSuccess()) return
                last = IllegalStateException(r.bodyAsText().ifBlank { "PC refused the data" })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                last = e
            }
        }
        throw IllegalStateException("Couldn't reach the PC. Make sure both devices are on the same Wi-Fi and Nocturne is allowed through the firewall.", last)
    }

    suspend fun apply(graph: AppGraph, b: SyncBundle) {
        val a = b.accounts
        graph.accounts.update { cur ->
            cur.copy(
                youtubeCookie = a.youtubeCookie.ifBlank { cur.youtubeCookie },
                youtubeName = if (a.youtubeSignedIn) a.youtubeName else cur.youtubeName,
                soundcloudToken = a.soundcloudToken.ifBlank { cur.soundcloudToken },
                soundcloudUserId = if (a.soundcloudSignedIn) a.soundcloudUserId else cur.soundcloudUserId,
                soundcloudName = if (a.soundcloudSignedIn) a.soundcloudName else cur.soundcloudName,
                spotifyAccessToken = if (a.spotifySignedIn) a.spotifyAccessToken else cur.spotifyAccessToken,
                spotifyRefreshToken = a.spotifyRefreshToken.ifBlank { cur.spotifyRefreshToken },
                spotifyExpiresAt = if (a.spotifySignedIn) a.spotifyExpiresAt else cur.spotifyExpiresAt,
                spotifyName = if (a.spotifySignedIn) a.spotifyName else cur.spotifyName,
            )
        }
        if (b.spotifyClientId.isNotBlank() && (a.spotifySignedIn || graph.settings.value.spotifyClientId.isBlank())) {
            graph.settings.update { it.copy(spotifyClientId = b.spotifyClientId) }
        }
        b.library?.let { graph.library.merge(it) }
    }
}
