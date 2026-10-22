package app.nocturne.data

import app.nocturne.AppGraph
import app.nocturne.aesDecrypt
import app.nocturne.aesEncrypt
import app.nocturne.platformKind
import app.nocturne.secureRandomBytes
import app.nocturne.sync.ChaChaPoly
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

data class PairTarget(val hosts: List<String>, val port: Int, val key: ByteArray, val v: Int = 1)

object Sync {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    /** [group]: device-sync secret, so scanning also pairs the devices. v=2 means the receiver opens ChaCha20-Poly1305 bundles. */
    fun qr(hosts: List<String>, port: Int, key: ByteArray, group: String? = null): String =
        "nocturne://pair?h=${hosts.joinToString(",")}&p=$port&k=${Base64.UrlSafe.encode(key).trimEnd('=')}" +
            (group?.let { "&g=$it" } ?: "") + "&v=2"

    private fun query(text: String): Map<String, String>? {
        if (!text.trim().startsWith("nocturne://pair?")) return null
        return text.trim().substringAfter('?').split('&').associate { it.substringBefore('=') to it.substringAfter('=', "") }
    }

    /** Device-sync group secret in a pairing link, or null. */
    fun groupOf(text: String): String? {
        val g = query(text)?.get("g")?.trim() ?: return null
        val raw = runCatching { Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(g) }.getOrNull()
        return g.takeIf { raw?.size == 32 }
    }

    fun parse(text: String): PairTarget? {
        val q = query(text) ?: return null
        val hosts = q["h"]?.split(',')?.filter { it.isNotBlank() }.orEmpty()
        val port = q["p"]?.toIntOrNull() ?: return null
        val key = runCatching { Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(q["k"].orEmpty()) }.getOrNull() ?: return null
        if (hosts.isEmpty() || key.size != 32) return null
        return PairTarget(hosts, port, key, q["v"]?.toIntOrNull() ?: 1)
    }

    fun bundle(graph: AppGraph) = SyncBundle(
        accounts = graph.accounts.value,
        spotifyClientId = graph.settings.value.spotifyClientId,
        library = graph.library.state.value.copy(searchHistory = emptyList(), searchPlays = emptyList()),
        from = platformKind.name,
    )

    // v2: "2." + base64(nonce || ChaCha20-Poly1305), works on every platform; v1: AES-GCM (JVM only).
    fun seal(key: ByteArray, bundle: SyncBundle, v: Int = 2): String {
        val plain = json.encodeToString(SyncBundle.serializer(), bundle).encodeToByteArray()
        if (v < 2) return Base64.encode(aesEncrypt(key, plain))
        val nonce = secureRandomBytes(ChaChaPoly.NONCE)
        return "2." + Base64.encode(nonce + ChaChaPoly.seal(key, nonce, plain))
    }

    fun open(key: ByteArray, body: String): SyncBundle {
        val b = body.trim()
        val plain = if (b.startsWith("2.")) {
            val raw = Base64.decode(b.substring(2))
            ChaChaPoly.open(key, raw.copyOfRange(0, ChaChaPoly.NONCE), raw.copyOfRange(ChaChaPoly.NONCE, raw.size)) ?: error("bad key")
        } else aesDecrypt(key, Base64.decode(b))
        return json.decodeFromString(SyncBundle.serializer(), plain.decodeToString())
    }

    suspend fun send(graph: AppGraph, target: PairTarget) {
        val body = seal(target.key, bundle(graph), target.v)
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
                youtubeAvatar = if (a.youtubeSignedIn) a.youtubeAvatar ?: cur.youtubeAvatar else cur.youtubeAvatar,
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
