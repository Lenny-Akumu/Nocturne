package app.nocturne.ui.player

import androidx.compose.foundation.background
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

// YouTube comments for [track] (pinned to the track it was opened for), paged on scroll, Top / Newest sort.
@Composable
fun CommentsSheet(track: Track, onDismiss: () -> Unit) {
    val api = AppGraph.instance.youtube.innerTube
    val videoId = remember { track.id }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val comments = remember { mutableStateListOf<YtComment>() }
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

    suspend fun commentsPageOf(token: String) = AppGraph.instance.comments(videoId, token)

    fun load(reset: Boolean, block: suspend () -> CommentsPage) {
        job?.cancel()
        loading = true
        error = null
        job = scope.launch {
            val r = runCatching {
                withContext(Dispatchers.Default) {
                    withTimeout(25_000) {
                        val first = block()
                        // first screen gets ~30, then pages of ~20 as you hit the bottom
                        if (reset && first.comments.size < 30 && first.next != null) {
                            runCatching { commentsPageOf(first.next) }.getOrNull()
                                ?.let { first.copy(comments = first.comments + it.comments, next = it.next) } ?: first
                        } else first
                    }
                }
            }
            ensureActive()
            r.onSuccess { p ->
                if (reset) { comments.clear(); list.scrollToItem(0) }
                val seen = comments.mapTo(HashSet<String>()) { it.id }
                comments += p.comments.filter { it.id !in seen }
                next = p.next
                if (p.sorts.isNotEmpty()) sorts = p.sorts
                p.count?.let { count = it }
            }.onFailure { error = it }
            loading = false
        }
    }

    LaunchedEffect(attempt) { load(true) { AppGraph.instance.comments(videoId) } }
    LaunchedEffect(list) {
        snapshotFlow { Triple(list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0, comments.size, next) }
            .collect { (last, size, token) ->
                if (token != null && size > 0 && !loading && error == null && last >= size - 2) load(false) { AppGraph.instance.comments(videoId, token) }
            }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.8f).navigationBarsPadding()) {
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
                    comments.isEmpty() && loading -> SlowLoading(videoId, Modifier.align(Alignment.Center))
                    comments.isEmpty() && err is CommentsDisabledException -> MessageBox(Icons.Rounded.ChatBubbleOutline, "Comments are turned off")
                    // the real reason, so a screenshot tells what broke
                    comments.isEmpty() && err != null -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        MessageBox(Icons.Rounded.ErrorOutline, "Couldn't load comments", message = (err.message ?: err::class.simpleName)?.take(160), actionLabel = "Retry", onAction = { attempt++ })
                        OpenOnYouTube(videoId)
                    }
                    comments.isEmpty() -> MessageBox(Icons.Rounded.ChatBubbleOutline, "No comments yet")
                    else -> LazyColumn(Modifier.fillMaxSize(), state = list) {
                        items(comments, key = { it.id }) { CommentRow(it) }
                        if (loading || err != null) item(key = "footer") {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                if (loading) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                                else TextButton(onClick = { next?.let { tk -> load(false) { AppGraph.instance.comments(videoId, tk) } } }) { Text(t("Retry")) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SlowLoading(videoId: String, modifier: Modifier) {
    var slow by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(7000); slow = true }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        androidx.compose.animation.AnimatedVisibility(slow) {
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

@Composable
private fun CommentRow(c: YtComment) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        AsyncImage(c.avatar, null, Modifier.size(36.dp).clip(CircleShape).background(cs.surfaceVariant), contentScale = ContentScale.Crop)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.author, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                c.time?.let { Text(" · $it", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, maxLines = 1) }
            }
            Spacer(Modifier.height(2.dp))
            Text(c.text, style = MaterialTheme.typography.bodyMedium)
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
