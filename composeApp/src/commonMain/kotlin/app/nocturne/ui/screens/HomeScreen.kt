package app.nocturne.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.nocturne.AppGraph
import app.nocturne.model.MediaEntry
import app.nocturne.model.Section
import app.nocturne.model.SectionLayout
import app.nocturne.ui.components.LoadingBox
import app.nocturne.ui.components.MessageBox
import app.nocturne.ui.components.SectionView
import app.nocturne.ui.components.SourceBadge
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

private object HomeCache {
    var sections: List<Section>? = null
}

@Composable
fun HomeScreen(contentPadding: PaddingValues) {
    val graph = AppGraph.instance
    val library by graph.library.state.collectAsState()
    var sections by remember { mutableStateOf(HomeCache.sections) }
    var errors by remember { mutableStateOf<List<String>>(emptyList()) }
    var refreshing by remember { mutableStateOf(false) }
    var reload by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(reload) {
        if (sections != null && reload == 0) return@LaunchedEffect
        refreshing = true
        val errs = mutableListOf<String>()
        val loaded = coroutineScope {
            graph.enabledProviders().map { p ->
                async {
                    runCatching { p.home() }.getOrElse {
                        errs += "${p.source.label}: ${it.message}"
                        emptyList()
                    }
                }
            }.awaitAll().flatten()
        }
        sections = loaded
        HomeCache.sections = loaded
        errors = errs
        refreshing = false
    }

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = { reload++ }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
            item {
                Text(
                    greeting(),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 8.dp),
                )
            }
            if (library.history.isNotEmpty()) {
                item {
                    SectionView(
                        Section("Listen again", library.history.take(24).map { MediaEntry.Song(it.track) }, SectionLayout.QUICK_PICKS),
                    ) { list, i -> graph.player.play(list, i, "Listen again") }
                }
            }
            val mostPlayed = graph.library.mostPlayed(16)
            if (mostPlayed.size >= 4) {
                item {
                    SectionView(Section("Your top tracks", mostPlayed.map { MediaEntry.Song(it) })) { list, i ->
                        if (list.size == 1) graph.player.play(mostPlayed, mostPlayed.indexOfFirst { it.key == list[0].key }, "Your top tracks")
                        else graph.player.play(list, i)
                    }
                }
            }
            if (library.saved.isNotEmpty()) {
                item {
                    SectionView(Section("From your library", library.saved.take(20).map { MediaEntry.Collection(it) })) { _, _ -> }
                }
            }
            val s = sections
            when {
                s == null -> item { LoadingBox() }
                s.isEmpty() && library.history.isEmpty() -> item {
                    MessageBox(
                        Icons.Rounded.CloudOff,
                        "Nothing here yet",
                        errors.joinToString("\n").ifBlank { "Search for something you love to get started." },
                        actionLabel = "Retry",
                        onAction = { reload++ },
                    )
                }
                else -> s.forEach { section ->
                    item {
                        SectionView(section) { list, i ->
                            val songs = section.items.filterIsInstance<MediaEntry.Song>().map { it.track }
                            if (list.size == 1 && songs.size > 1) graph.player.play(songs, songs.indexOfFirst { it.key == list[0].key }, section.title)
                            else graph.player.play(list, i, section.title)
                        }
                    }
                }
            }
            if (errors.isNotEmpty() && !sections.isNullOrEmpty()) {
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(errors) { e ->
                            AssistChip(onClick = { reload++ }, label = { Text(e, maxLines = 1) }, leadingIcon = { Icon(Icons.Rounded.History, null, Modifier.size(18.dp)) })
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun greeting(): String {
    val hour = remember { app.nocturne.currentHour() }
    return when (hour) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        in 18..22 -> "Good evening"
        else -> "Good night"
    }
}

@Composable
fun SourceRow(modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        app.nocturne.model.Source.entries.forEach { SourceBadge(it, size = 14.dp) }
    }
}
