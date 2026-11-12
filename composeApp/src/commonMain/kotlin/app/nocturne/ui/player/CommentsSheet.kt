package app.nocturne.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.withLink
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ThumbUpOffAlt
import app.nocturne.ui.kit.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nocturne.AppGraph
import app.nocturne.i18n.t
import app.nocturne.model.Track
import app.nocturne.source.CommentSort
import app.nocturne.source.CommentsDisabledException
import app.nocturne.source.CommentsPage
import app.nocturne.source.YtComment
import app.nocturne.source.comments
import app.nocturne.source.commentsPage
import app.nocturne.ui.components.MessageBox
import app.nocturne.ui.kit.CircularProgressIndicator
import app.nocturne.ui.kit.FilterChip
import app.nocturne.ui.kit.ModalBottomSheet
import app.nocturne.ui.kit.TextButton
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

// Comments for [track] (pinned to the track it was opened for): YouTube with Top / Newest sort, or SoundCloud's timed ones.
// Shown 10 at a time; the next 10 appear as you reach the bottom, more pages load behind them.
@Composable
fun CommentsSheet(track: Track, onDismiss: () -> Unit) {
    val graph = AppGraph.instance
    val api = graph.youtube.innerTube
    val videoId = remember { track.id }
    val soundcloud = track.source == app.nocturne.model.Source.SOUNDCLOUD
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val comments = remember { mutableStateListOf<YtComment>() }
    val buffer = remember { mutableStateListOf<YtComment>() }
    var sorts by remember { mutableStateOf<List<CommentSort>>(emptyList()) }
    var sort by remember { mutableIntStateOf(0) }
    var count by remember { mutableStateOf<String?>(null) }
    var next by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var job by remember { mutableStateOf<Job?>(null) }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()

    fun reveal() {
        val n = minOf(PAGE, buffer.size)
        if (n == 0) return
        comments += buffer.take(n)
        repeat(n) { buffer.removeAt(0) }
    }

    fun load(reset: Boolean, block: suspend () -> CommentsPage) {
        job?.cancel()
        loading = true
        error = null
        job = scope.launch {
            val r = runCatching { withContext(Dispatchers.Default) { withTimeout(25_000) { block() } } }
            ensureActive()
            r.onSuccess { p ->
                if (reset) { comments.clear(); buffer.clear(); list.scrollToItem(0) }
                val seen = (comments + buffer).mapTo(HashSet<String>()) { it.id }
                val fresh = p.comments.filter { it.id !in seen }
                buffer += fresh
                // a page with nothing new means the feed is done (stops endless spinners)
                next = p.next?.takeIf { fresh.isNotEmpty() }
                if (p.sorts.isNotEmpty()) sorts = p.sorts
                p.count?.let { count = it }
                if (comments.isEmpty() || reset) reveal()
            }.onFailure { error = it }
            loading = false
        }
    }

    suspend fun page(token: String?): CommentsPage =
        if (soundcloud) graph.soundcloud.comments(track, token) else graph.comments(videoId, token, "${track.artistNames} ${track.title}".trim())

    LaunchedEffect(attempt) { load(true) { page(null) } }
    LaunchedEffect(list) {
        snapshotFlow { Triple(list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0, comments.size, buffer.size) }
            .collect { (last, size, buffered) ->
                if (size == 0 || last < size - 2) return@collect
                if (buffered > 0) reveal()
                // keep a page in reserve so the next 10 show without a wait
                if (buffer.size < PAGE) next?.let { tk -> if (!loading && error == null) load(false) { page(tk) } }
            }
    }

    fun seek(ms: Long) {
        val p = graph.player
        if (p.state.value.current?.key == track.key) { p.seekTo(ms); return }
        p.play(listOf(track), 0, null)
        scope.launch {
            kotlinx.coroutines.withTimeoutOrNull(20_000) { p.state.first { it.current?.key == track.key && it.durationMs > 0 && !it.isBuffering } }
            p.seekTo(ms)
        }
    }

    val style = app.nocturne.ui.kit.LocalDesignStyle.current
    val body: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {
            Row(Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(t("Comments"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                count?.let { Text("  $it", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (sorts.size > 1) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    sorts.forEachIndexed { i, s ->
                        FilterChip(
                            selected = sort == i,
                            onClick = { if (sort != i) { sort = i; load(true) { api.commentsPage(s.token) } } },
                            label = { Text(s.title) },
                        )
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val err = error
                when {
                    comments.isEmpty() && loading -> SlowLoading(videoId, soundcloud, Modifier.align(Alignment.Center))
                    comments.isEmpty() && err is CommentsDisabledException -> MessageBox(Icons.Rounded.ChatBubbleOutline, "Comments are turned off")
                    // the real reason, so a screenshot tells what broke
                    comments.isEmpty() && err != null -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        MessageBox(Icons.Rounded.ErrorOutline, "Couldn't load comments", message = (err.message ?: err::class.simpleName)?.take(160), actionLabel = "Retry", onAction = { attempt++ })
                        if (!soundcloud) OpenOnYouTube(videoId)
                    }
                    comments.isEmpty() -> MessageBox(Icons.Rounded.ChatBubbleOutline, "No comments yet")
                    else -> LazyColumn(Modifier.fillMaxSize(), state = list) {
                        items(comments, key = { it.id }) { CommentRow(it, ::seek) }
                        val more = buffer.isNotEmpty() || next != null
                        if (more || err != null) item(key = "footer") {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                when {
                                    err != null -> TextButton(onClick = { next?.let { tk -> load(false) { page(tk) } } }) { Text(t("Retry")) }
                                    loading && buffer.isEmpty() -> CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                                    else -> TextButton(onClick = { reveal(); if (buffer.size < PAGE) next?.let { tk -> if (!loading) load(false) { page(tk) } } }) { Text(t("Show more")) }
                                }
                            }
                        }
                    }
                }
            }
    }
    if (style.hasOwnPlayer()) StyledPanel(style, t("Comments").lowercase(), onDismiss) { body() }
    else ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.8f).navigationBarsPadding(), content = body)
    }
}

@Composable
private fun SlowLoading(videoId: String, soundcloud: Boolean, modifier: Modifier) {
    var slow by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(7000); slow = true }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        androidx.compose.animation.AnimatedVisibility(slow && !soundcloud) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 16.dp)) {
                Text(t("YouTube is slow to answer…"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OpenOnYouTube(videoId)
            }
        }
    }
}

@Composable
private fun OpenOnYouTube(videoId: String) {
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    TextButton(onClick = { runCatching { uri.openUri("https://www.youtube.com/watch?v=$videoId") } }) { Text(t("Open on YouTube")) }
}

private const val PAGE = 10

// 1:23 / 01:02:03 in a comment
private val timecode = Regex("""(?<![\d:])(\d{1,2}:)?\d{1,2}:\d{2}(?![\d:])""")

private fun parseTime(s: String): Long? {
    val parts = s.split(':').map { it.toLongOrNull() ?: return null }
    if (parts.drop(1).any { it >= 60 }) return null
    return parts.fold(0L) { acc, v -> acc * 60 + v } * 1000
}

private fun formatAt(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "${s / 3600}:${(s / 60 % 60).toString().padStart(2, '0')}:${(s % 60).toString().padStart(2, '0')}"
    else "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

@Composable
private fun linkedText(text: String, seek: (Long) -> Unit): androidx.compose.ui.text.AnnotatedString {
    val color = MaterialTheme.colorScheme.primary
    return remember(text, color) {
        androidx.compose.ui.text.buildAnnotatedString {
            var at = 0
            for (m in timecode.findAll(text)) {
                val ms = parseTime(m.value) ?: continue
                append(text.substring(at, m.range.first))
                withLink(
                    androidx.compose.ui.text.LinkAnnotation.Clickable(
                        "t$ms",
                        androidx.compose.ui.text.TextLinkStyles(androidx.compose.ui.text.SpanStyle(color = color, fontWeight = FontWeight.SemiBold)),
                    ) { seek(ms) },
                ) { append(m.value) }
                at = m.range.last + 1
            }
            append(text.substring(at))
        }
    }
}

@Composable
private fun CommentRow(c: YtComment, seek: (Long) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        AsyncImage(c.avatar, null, Modifier.size(36.dp).clip(CircleShape).background(cs.surfaceVariant), contentScale = ContentScale.Crop)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.author, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                c.time?.let { Text(" · $it", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, maxLines = 1) }
            }
            c.atMs?.let { ms ->
                Text(
                    t("at %s", formatAt(ms)), style = MaterialTheme.typography.labelMedium, color = cs.primary, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 2.dp).clip(CircleShape).clickable { seek(ms) }.background(cs.primary.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(linkedText(c.text, seek), style = MaterialTheme.typography.bodyMedium)
            if (c.likes != null || c.replies != null) {
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    c.likes?.let {
                        Icon(Icons.Rounded.ThumbUpOffAlt, null, Modifier.size(14.dp), tint = cs.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                        Text(it, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                        Spacer(Modifier.width(16.dp))
                    }
                    c.replies?.let {
                        Icon(Icons.Rounded.ChatBubbleOutline, null, Modifier.size(14.dp), tint = cs.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                        Text(t("%s replies", it), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
