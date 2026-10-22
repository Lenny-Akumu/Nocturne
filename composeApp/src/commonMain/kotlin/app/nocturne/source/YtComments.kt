package app.nocturne.source

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

// YouTube comments via the WEB `next` endpoint (old commentThreadRenderer and new commentViewModel/entity layouts).

data class YtComment(
    val id: String,
    val author: String,
    val avatar: String?,
    val text: String,
    val time: String?,
    val likes: String?,
    val replies: String?,
)

data class CommentSort(val title: String, val token: String)

data class CommentsPage(val comments: List<YtComment>, val next: String?, val sorts: List<CommentSort> = emptyList(), val count: String? = null)

class CommentsDisabledException : Exception("Comments are turned off")

// First page; falls back to the track's music video when the audio-only (ATV) video has no comments.
suspend fun InnerTube.comments(videoId: String): CommentsPage {
    val token = commentsToken(videoId)
        ?: runCatching { musicVideoId(videoId) }.getOrNull()?.takeIf { it != videoId }?.let { commentsToken(it) }
        ?: throw CommentsDisabledException()
    return commentsPage(token)
}

// Next page, or a re-sorted first page for a [CommentSort.token].
suspend fun InnerTube.commentsPage(token: String): CommentsPage = parseComments(webNext(mapOf("continuation" to JsonPrimitive(token))))

private suspend fun InnerTube.commentsToken(videoId: String): String? {
    val root = webNext(mapOf("videoId" to JsonPrimitive(videoId)))
    fun JsonElement?.token() = this?.findAll("continuationCommand")?.firstNotNullOfOrNull { it.path("token").str }
    root.findAll("itemSectionRenderer").firstOrNull { it.path("sectionIdentifier").str == "comment-item-section" }.token()?.let { return it }
    return root.findAll("engagementPanelSectionListRenderer")
        .firstOrNull { it.path("panelIdentifier").str == "engagement-panel-comments-section" }
        .path("content").token()
}

internal fun parseComments(root: JsonElement): CommentsPage {
    root.path("error")?.let { throw IllegalStateException(it.path("message").str ?: "Request failed") }
    val payloads = HashMap<String, JsonElement>()
    for (ms in root.findAll("mutations")) {
        for (m in ms.arr.orEmpty()) {
            val p = m.path("payload", "commentEntityPayload") ?: continue
            m.path("entityKey").str?.let { payloads[it] = p }
            p.path("key").str?.let { payloads[it] = p }
        }
    }
    val out = mutableListOf<YtComment>()
    var next: String? = null
    for (items in root.findAll("continuationItems")) {
        val list = items.arr ?: continue
        for (item in list) {
            item.path("commentThreadRenderer")?.let { th -> parseThread(th, payloads)?.let(out::add) }
            item.path("continuationItemRenderer")?.let { c ->
                next = c.path("continuationEndpoint", "continuationCommand", "token").str
                    ?: c.path("button", "buttonRenderer", "command", "continuationCommand", "token").str ?: next
            }
        }
    }
    if (out.isEmpty()) payloads.values.distinct().filter { (it.path("properties", "replyLevel").num ?: 0L) == 0L }.mapNotNullTo(out) { fromPayload(it) }
    val sorts = root.findAll("sortFilterSubMenuRenderer").firstOrNull().path("subMenuItems").arr?.mapNotNull { s ->
        val title = s.path("title").str ?: return@mapNotNull null
        val token = s.path("serviceEndpoint", "continuationCommand", "token").str
            ?: s.path("continuation", "reloadContinuationData", "continuation").str ?: return@mapNotNull null
        CommentSort(title, token)
    }.orEmpty()
    val header = root.findAll("commentsHeaderRenderer").firstOrNull()
    val count = header.path("countText").runsText() ?: header.path("commentsCount").runsText()
    return CommentsPage(out.distinctBy { it.id }, next, sorts, count)
}

private fun parseThread(th: JsonElement, payloads: Map<String, JsonElement>): YtComment? {
    th.path("commentViewModel", "commentViewModel")?.let { vm ->
        return vm.path("commentKey").str?.let { payloads[it] }?.let { fromPayload(it) }
    }
    val c = th.path("comment", "commentRenderer") ?: return null
    return YtComment(
        id = c.path("commentId").str ?: return null,
        author = c.path("authorText").runsText().orEmpty(),
        avatar = c.path("authorThumbnail", "thumbnails").arr?.lastOrNull().path("url").str,
        text = c.path("contentText").runsText().orEmpty(),
        time = c.path("publishedTimeText").runsText(),
        likes = c.path("voteCount").runsText()?.trim()?.takeIf { it.isNotEmpty() && it != "0" },
        replies = c.path("replyCount").num?.takeIf { it > 0 }?.toString(),
    )
}

private fun fromPayload(p: JsonElement): YtComment? {
    val props = p.path("properties")
    fun count(v: String?) = v?.trim()?.takeIf { it.isNotEmpty() && it != "0" }
    return YtComment(
        id = props.path("commentId").str ?: p.path("key").str ?: return null,
        author = p.path("author", "displayName").str.orEmpty(),
        avatar = p.path("author", "avatarThumbnailUrl").str ?: p.path("avatar", "image", "sources", 0, "url").str,
        text = props.path("content", "content").str.orEmpty(),
        time = props.path("publishedTime").str,
        likes = count(p.path("toolbar", "likeCountNotliked").str),
        replies = count(p.path("toolbar", "replyCount").str),
    )
}
