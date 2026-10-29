package app.nocturne.ui.screens

import app.nocturne.ui.textInput
import app.nocturne.i18n.t
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import app.nocturne.data.DesignStyle
import app.nocturne.ui.kit.LocalDesignStyle
import app.nocturne.ui.liquidGlass
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import app.nocturne.ui.kit.FilterChip
import app.nocturne.ui.kit.Icon
import androidx.compose.material3.IconButton
import app.nocturne.ui.kit.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.nocturne.AppGraph
import app.nocturne.model.MediaEntry
import app.nocturne.model.SearchFilter
import app.nocturne.model.Source
import app.nocturne.ui.Screen
import app.nocturne.ui.components.CollectionCard
import app.nocturne.ui.components.LoadingBox
import app.nocturne.ui.components.MessageBox
import app.nocturne.ui.components.SourceBadge
import app.nocturne.ui.components.TrackRow
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private class SearchState {
    var query by mutableStateOf("")
    var submitted by mutableStateOf<String?>(null)
    var source by mutableStateOf<Source?>(null)
    var filter by mutableStateOf(SearchFilter.SONGS)
    var results by mutableStateOf<List<MediaEntry>?>(null)
    var error by mutableStateOf<String?>(null)
    var loading by mutableStateOf(false)
    var initialized = false
}

private val searchState = SearchState()

@Composable
fun SearchScreen(contentPadding: PaddingValues) {
    val graph = AppGraph.instance
    val settings by graph.settings.state.collectAsState()
    val library by graph.library.state.collectAsState()
    val s = searchState
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var liveTracks by remember { mutableStateOf<List<app.nocturne.model.Track>>(emptyList()) }

    fun playFromSearch(track: app.nocturne.model.Track, list: List<app.nocturne.model.Track>, index: Int) {
        graph.library.addSearchPlay(track)
        if (settings.autoplayRadio) graph.player.startRadio(track) else graph.player.play(list, index, t("Search"))
    }

    LaunchedEffect(s.query, s.source) {
        if (s.query.isBlank() || s.query == s.submitted || linkIn(s.query) != null) { liveTracks = emptyList(); return@LaunchedEffect }
        delay(400)
        val provider = s.source?.let { graph.provider(it) } ?: graph.youtube
        liveTracks = runCatching { provider.search(s.query, SearchFilter.SONGS) }.getOrDefault(emptyList())
            .filterIsInstance<MediaEntry.Song>().map { it.track }.take(6)
    }
    if (!s.initialized) {
        s.source = null
        s.initialized = true
    }
    val sources = Source.entries.filter { it in settings.enabledSources }

    fun submit(q: String = s.query) {
        val text = q.trim()
        if (text.isEmpty()) return
        s.query = text
        s.submitted = text
        focus.clearFocus()
        graph.library.addSearch(text)
    }

    LaunchedEffect(s.query, settings.searchSuggestions) {
        if (!settings.searchSuggestions || s.query.isBlank() || s.query == s.submitted) { suggestions = emptyList(); return@LaunchedEffect }
        delay(250)
        suggestions = runCatching { graph.youtube.suggestions(s.query) }.getOrDefault(emptyList()).take(6)
    }

    LaunchedEffect(s.submitted, s.source, s.filter) {
        val q = s.submitted ?: return@LaunchedEffect
        s.loading = true
        s.error = null
        s.results = null
        try {
            val link = linkIn(q)
            val entry = link?.let { u -> runCatching { graph.openUrl(u) }.getOrNull() }
            val query = if (link != null && entry == null) graph.linkQuery(link) else q
            if (entry != null) {
                when (entry) {
                    is MediaEntry.Collection -> { graph.navigator.push(Screen.Collection(entry.ref)); s.results = emptyList() }
                    else -> s.results = listOf(entry)
                }
            } else if (query == null) {
                s.error = t("This link isn't supported")
            } else {
                val target = s.source
                val providers = if (target == null) graph.enabledProviders() else listOf(graph.provider(target))
                val errs = mutableListOf<String>()
                val lists = coroutineScope {
                    providers.map { p ->
                        async { runCatching { p.search(query, s.filter) }.getOrElse { errs += "${p.source.label}: ${it.message}"; emptyList() } }
                    }.awaitAll()
                }
                var merged = interleave(lists)
                if (settings.hideExplicit) merged = merged.filterNot { it is MediaEntry.Song && it.track.explicit }
                s.results = merged
                if (merged.isEmpty() && errs.isNotEmpty()) s.error = errs.joinToString("\n")
            }
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            s.error = e.message ?: t("Search failed")
        }
        s.loading = false
    }

    val fieldFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    // fresh search: jump straight into typing
    LaunchedEffect(Unit) {
        if (s.submitted != null) return@LaunchedEffect
        delay(120)
        runCatching { fieldFocus.requestFocus() }
        keyboard?.show()
    }

    val design = LocalDesignStyle.current
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        LaunchedEffect(s.query) { if (s.query.trim() == "67") app.nocturne.ui.SixSeven.go() }
        TextField(
            value = s.query,
            onValueChange = { s.query = it },
            modifier = Modifier.fillMaxWidth().focusRequester(fieldFocus).textInput().padding(horizontal = 16.dp, vertical = 8.dp)
                .then(if (design == DesignStyle.GLASS) Modifier.liquidGlass(CircleShape, 6.dp) else Modifier),
            placeholder = { Text(t("Search songs, artists"), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                if (s.query.isNotEmpty()) IconButton(onClick = { s.query = ""; s.submitted = null; s.results = null }) { Icon(Icons.Rounded.Clear, "Clear") }
            },
            singleLine = true,
            shape = if (design == DesignStyle.WINDOWS) RoundedCornerShape(4.dp) else CircleShape,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submit() }),
            colors = when (design) {
                // Frosted capsule; the glass modifier supplies the fill.
                DesignStyle.GLASS -> TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                )
                // Fluent TextBox: layer fill, accent underline when focused.
                DesignStyle.WINDOWS -> TextFieldDefaults.colors(
                    focusedContainerColor = cs.surfaceContainerLow,
                    unfocusedContainerColor = cs.surfaceContainerHigh,
                    disabledContainerColor = cs.surfaceContainerHigh,
                    focusedIndicatorColor = cs.primary,
                    unfocusedIndicatorColor = cs.onSurfaceVariant.copy(alpha = 0.6f),
                    disabledIndicatorColor = Color.Transparent,
                )
                DesignStyle.MATERIAL -> TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                )
            },
        )
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(selected = s.source == null, onClick = { s.source = null }, label = { Text(t("All")) })
            }
            items(sources) { src ->
                FilterChip(
                    selected = s.source == src,
                    onClick = { s.source = src },
                    label = { Text(src.label) },
                    leadingIcon = { SourceBadge(src, size = 16.dp) },
                )
            }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(SearchFilter.entries) { f ->
                FilterChip(selected = s.filter == f, onClick = { s.filter = f }, label = { Text(t(f.label)) })
            }
        }

        val typing = s.query.isNotBlank() && s.query != s.submitted
        when {
            typing || (s.submitted == null && s.query.isBlank()) -> {
                LazyColumn(contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding())) {
                    if (s.query.startsWith("http")) {
                        item { SuggestionRow(Icons.Rounded.Link, t("Open link")) { submit() } }
                    }
                    if (typing) {
                        items(suggestions) { q -> SuggestionRow(Icons.Rounded.Search, q) { submit(q) } }
                        if (liveTracks.isNotEmpty()) {
                            item { SearchLabel("Songs") }
                            items(liveTracks.size, key = { "live" + liveTracks[it].key }) { i ->
                                TrackRow(liveTracks[i], onClick = { playFromSearch(liveTracks[i], liveTracks, i) }, modifier = Modifier.padding(horizontal = 4.dp))
                            }
                        }
                    } else {
                        if (library.searchPlays.isNotEmpty()) {
                            item { SearchLabel("Recently played") }
                            items(library.searchPlays.size, key = { "played" + library.searchPlays[it].key }) { i ->
                                val played = library.searchPlays[i]
                                TrackRow(played, onClick = { playFromSearch(played, library.searchPlays, i) }, modifier = Modifier.padding(horizontal = 4.dp))
                            }
                        }
                        if (library.searchHistory.isNotEmpty()) item { SearchLabel("Recent searches") }
                        items(library.searchHistory) { q ->
                            SuggestionRow(Icons.Rounded.History, q, onRemove = { graph.library.removeSearch(q) }) { submit(q) }
                        }
                        if (library.searchHistory.isEmpty() && library.searchPlays.isEmpty()) item {
                            MessageBox(Icons.AutoMirrored.Rounded.TrendingUp, "Find your sound", "Search across YouTube Music, Spotify and SoundCloud — or paste a link.")
                        }
                    }
                }
            }
            s.loading -> LoadingBox()
            s.error != null -> MessageBox(Icons.Rounded.SearchOff, "Something went wrong", s.error)
            s.results.isNullOrEmpty() -> MessageBox(Icons.Rounded.SearchOff, "No results", "Try another source or filter.")
            else -> {
                val results = s.results.orEmpty()
                if (s.filter == SearchFilter.SONGS) {
                    val tracks = results.filterIsInstance<MediaEntry.Song>().map { it.track }
                    LazyColumn(contentPadding = PaddingValues(start = 4.dp, end = 4.dp, bottom = contentPadding.calculateBottomPadding())) {
                        items(tracks.size, key = { tracks[it].key + it }) { i ->
                            TrackRow(tracks[i], onClick = { playFromSearch(tracks[i], tracks, i) })
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(150.dp),
                        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = contentPadding.calculateBottomPadding()),
                    ) {
                        items(results.filterIsInstance<MediaEntry.Collection>()) { c ->
                            CollectionCard(c.ref, onClick = { graph.navigator.push(Screen.Collection(c.ref)) }, width = null)
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}

private fun <T> interleave(lists: List<List<T>>): List<T> {
    val out = mutableListOf<T>()
    val max = lists.maxOfOrNull { it.size } ?: 0
    for (i in 0 until max) for (l in lists) l.getOrNull(i)?.let { out += it }
    return out
}

@Composable
private fun SearchLabel(text: String) {
    Text(
        t(text),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 4.dp),
    )
}

@Composable
private fun SuggestionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onRemove: (() -> Unit)? = null, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(text, maxLines = 1) },
        leadingContent = { Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingContent = if (onRemove != null) {
            { IconButton(onClick = onRemove) { Icon(Icons.Rounded.Close, "Remove") } }
        } else null,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 4.dp),
    )
}

private val linkRegex = Regex("""(https?://\S+|spotify:[a-z]+:[A-Za-z0-9]+|(?:open\.spotify\.com|music\.youtube\.com|youtu\.be|soundcloud\.com|music\.apple\.com|deezer\.com)/\S+)""")

// First link in the pasted text (share sheets often add "Listen to … on Spotify:" around it).
private fun linkIn(text: String): String? = linkRegex.find(text.trim())?.value?.trimEnd('.', ',', ')', '"')?.let {
    if (it.startsWith("http") || it.startsWith("spotify:")) it else "https://$it"
}
