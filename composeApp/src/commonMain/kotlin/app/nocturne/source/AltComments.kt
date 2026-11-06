package app.nocturne.source

import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * YouTube comments through public Piped / Invidious mirrors, raced next to our own parser and NewPipe
 * so one slow or blocked path doesn't leave the sheet spinning. Page tokens carry their mirror: "pp:<host>|…", "iv:<host>|…".
 */
class AltComments(private val http: HttpClient) {
    private val json = Json { ignoreUnknownKeys = true }

    val piped = listOf("https://pipedapi.kavin.rocks", "https://api.piped.private.coffee")
    val invidious = listOf("https://inv.nadeko.net", "https://invidious.nerdvpn.de", "https://yewtu.be")

    private suspend fun get(url: String): JsonElement? {
        val r = http.get(url) { timeout { requestTimeoutMillis = 12_000; connectTimeoutMillis = 6_000 } }
        if (!r.status.isSuccess()) return null
        return json.parseToJsonElement(r.bodyAsText())
    }

    suspend fun page(token: String, videoId: String): CommentsPage? {
        val host = token.substring(3).substringBefore('|')
        val rest = token.substringAfter('|')
        return if (token.startsWith("pp:")) piped(host, videoId, rest) else invidious(host, videoId, rest)
    }

    suspend fun piped(host: String, videoId: String, next: String? = null): CommentsPage? {
        val root = get(if (next == null) "$host/comments/$videoId" else "$host/nextpage/comments/$videoId?nextpage=${next.encodeURLParameter()}") ?: return null
        if (root.path("disabled").str == "true" || (root.path("disabled") as? kotlinx.serialization.json.JsonPrimitive)?.content == "true") throw CommentsDisabledException()
        val items = root.path("comments").arr.orEmpty().mapNotNull { c ->
            YtComment(
                id = c.path("commentId").str ?: return@mapNotNull null,
                author = c.path("author").str.orEmpty().removePrefix("@").let { "@$it" },
                avatar = c.path("thumbnail").str,
                text = stripHtml(c.path("commentText").str.orEmpty()),
                time = c.path("commentedTime").str,
                likes = c.path("likeCount").num?.takeIf { it > 0 }?.let(::compact),
                replies = c.path("replyCount").num?.takeIf { it > 0 }?.toString(),
            )
        }
        val nx = root.path("nextpage").str?.takeIf { it.isNotBlank() && items.isNotEmpty() }
        val count = root.path("commentCount").num?.takeIf { it > 0 }?.let(::compact)
        return CommentsPage(items, nx?.let { "pp:$host|$it" }, count = count)
    }

    suspend fun invidious(host: String, videoId: String, next: String? = null): CommentsPage? {
        val root = get("$host/api/v1/comments/$videoId" + (next?.let { "?continuation=${it.encodeURLParameter()}" } ?: "")) ?: return null
        root.path("error").str?.let { e -> if (e.contains("disabled", true)) throw CommentsDisabledException() else return null }
        val items = root.path("comments").arr.orEmpty().mapNotNull { c ->
            YtComment(
                id = c.path("commentId").str ?: return@mapNotNull null,
                author = c.path("author").str.orEmpty(),
                avatar = c.path("authorThumbnails").arr?.lastOrNull().path("url").str?.let { if (it.startsWith("/")) host + it else it },
                text = c.path("content").str.orEmpty(),
                time = c.path("publishedText").str,
                likes = c.path("likeCount").num?.takeIf { it > 0 }?.let(::compact),
                replies = c.path("replies", "replyCount").num?.takeIf { it > 0 }?.toString(),
            )
        }
        val nx = root.path("continuation").str?.takeIf { it.isNotBlank() && items.isNotEmpty() }
        val count = root.path("commentCount").num?.takeIf { it > 0 }?.let(::compact)
        return CommentsPage(items, nx?.let { "iv:$host|$it" }, count = count)
    }

    private fun compact(n: Long): String = when {
        n >= 1_000_000 -> "${n / 100_000 / 10.0}M".replace(".0M", "M")
        n >= 1_000 -> "${n / 100 / 10.0}K".replace(".0K", "K")
        else -> n.toString()
    }

    private fun stripHtml(s: String) = s.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n").replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")
}
