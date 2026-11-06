package app.nocturne.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nocturne.AppGraph
import app.nocturne.PlatformKind
import app.nocturne.data.DesignStyle
import app.nocturne.data.Settings
import app.nocturne.i18n.t
import app.nocturne.model.Section
import app.nocturne.model.Track
import app.nocturne.nowMillis
import app.nocturne.platformKind
import app.nocturne.ui.Screen
import app.nocturne.ui.components.Artwork
import app.nocturne.ui.components.SectionView
import app.nocturne.ui.components.TrackRow
import app.nocturne.ui.kit.LocalDesignStyle

internal val DesignStyle.dashboardPages get() = this == DesignStyle.LINUX || this == DesignStyle.TERMINAL

/** A titled container drawn the way the current design draws its panes. */
@Composable
internal fun StyledBox(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    when (LocalDesignStyle.current) {
        DesignStyle.ARCH -> app.nocturne.ui.TuiBox("[${title.lowercase()}]", modifier) { Column(content = content) }
        DesignStyle.TERMINAL -> app.nocturne.ui.TermBox(title.lowercase(), cs.primary, modifier) { Column(content = content) }
        else -> Column(
            modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.06f)).border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp)).padding(16.dp),
        ) {
            Text(title.uppercase(), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp), color = cs.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
            content()
        }
    }
}

private fun trackShape(style: DesignStyle) = when (style) { DesignStyle.ARCH -> RectangleShape; DesignStyle.TERMINAL -> RoundedCornerShape(6.dp); else -> RoundedCornerShape(14.dp) }

// ---------- Home ----------

@Composable
internal fun DashboardHome(contentPadding: PaddingValues, listState: LazyListState, greeting: String, recommended: List<Track>, sections: List<Section>?, play: (List<Track>, Int, String?) -> Unit) {
    val graph = AppGraph.instance
    val library by graph.library.state.collectAsState()
    val recs = recommended.filterNot { graph.library.isBlocked(it) }.ifEmpty { graph.library.mostPlayed(12) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 900.dp
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = contentPadding) {
            item { Text(greeting, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 12.dp)) }
            item {
                val picks: @Composable (Modifier) -> Unit = { m ->
                    StyledBox(t("Quick picks"), m) {
                        recs.take(9).forEachIndexed { i, tr -> TrackRow(tr, onClick = { play(recs, i, t("Quick picks")) }) }
                    }
                }
                val week: @Composable () -> Unit = { WeekBox() }
                val artists: @Composable () -> Unit = { TopArtistsBox(play) }
                if (wide) Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    picks(Modifier.weight(1.2f))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) { week(); artists() }
                } else Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { week(); picks(Modifier.fillMaxWidth()); artists() }
            }
            item { app.nocturne.ui.components.RadioPodcastRows() }
            homeSections(sections, play)
            if (library.history.isNotEmpty()) item {
                val all = library.history.take(20).map { it.track }
                SectionView(Section("Listen again", all.map { app.nocturne.model.MediaEntry.Song(it) })) { list, _ -> play(all, all.indexOfFirst { it.key == list[0].key }.coerceAtLeast(0), t("Listen again")) }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.homeSections(sections: List<Section>?, play: (List<Track>, Int, String?) -> Unit) {
    val graph = AppGraph.instance
    sections.orEmpty().forEach { raw ->
        val section = raw.copy(items = raw.items.filterNot { it is app.nocturne.model.MediaEntry.Song && graph.library.isBlocked(it.track) })
        if (section.items.isNotEmpty()) item {
            SectionView(section) { list, i ->
                val songs = section.items.filterIsInstance<app.nocturne.model.MediaEntry.Song>().map { it.track }
                if (list.size == 1 && songs.size > 1) play(songs, songs.indexOfFirst { it.key == list[0].key }, t(section.title)) else play(list, i, t(section.title))
            }
        }
    }
    if (sections == null) item { app.nocturne.ui.components.LoadingBox() }
}

@Composable
private fun WeekBox() {
    val graph = AppGraph.instance
    val cs = MaterialTheme.colorScheme
    val library by graph.library.state.collectAsState()
    val now = nowMillis()
    val day = 86_400_000L
    val week = library.plays.filter { now - it.t < 7 * day }
    val perDay = FloatArray(7).also { a -> week.forEach { e -> a[(6 - ((now - e.t) / day).toInt()).coerceIn(0, 6)] += e.d / 60_000f } }
    val minutes = week.sumOf { it.d } / 60_000
    StyledBox(t("Your week")) {
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Column {
                Text(if (minutes >= 60) "${minutes / 60} " + t("h") + " ${minutes % 60} " + t("min") else "$minutes " + t("min"), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold), color = cs.primary)
                Text(t("listened"), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            Column {
                Text("${week.size}", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold), color = cs.tertiary)
                Text(t("tracks"), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
        }
        val peak = (perDay.maxOrNull() ?: 0f).coerceAtLeast(1f)
        val style = LocalDesignStyle.current
        Canvas(Modifier.fillMaxWidth().height(70.dp).padding(top = 10.dp)) {
            val step = size.width / 7
            perDay.forEachIndexed { i, v ->
                val h = (size.height * v / peak).coerceAtLeast(3.dp.toPx())
                val tl = Offset(i * step + step * 0.18f, size.height - h)
                val sz = Size(step * 0.64f, h)
                if (style == DesignStyle.ARCH) drawRect(cs.primary, tl, sz)
                else drawRoundRect(Brush.verticalGradient(listOf(cs.primary, cs.tertiary)), tl, sz, CornerRadius(6.dp.toPx()))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TopArtistsBox(play: (List<Track>, Int, String?) -> Unit) {
    val graph = AppGraph.instance
    val cs = MaterialTheme.colorScheme
    val library by graph.library.state.collectAsState()
    val month = nowMillis() - 30L * 86_400_000L
    val top = remember(library.plays.size) {
        library.plays.filter { it.t >= month }.flatMap { it.a.split(",").map { a -> a.trim() } }.filter { it.isNotEmpty() }
            .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(6).map { it.key }
    }
    if (top.isEmpty()) return
    StyledBox(t("Top artists")) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            top.forEach { name ->
                val tracks = library.history.map { it.track }.filter { tr -> tr.artistNames.contains(name, ignoreCase = true) }.distinctBy { it.key }
                Column(Modifier.width(80.dp).clip(RoundedCornerShape(12.dp)).clickable(enabled = tracks.isNotEmpty()) { play(tracks, 0, name) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Artwork(tracks.firstOrNull()?.artworkUrl, Modifier.size(70.dp), CircleShape)
                    Text(name, style = MaterialTheme.typography.labelMedium, color = cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

/** Arch: mix of the day up top, then the shelves. */
@Composable
internal fun ArchHome(contentPadding: PaddingValues, listState: LazyListState, recommended: List<Track>, sections: List<Section>?, play: (List<Track>, Int, String?) -> Unit) {
    val graph = AppGraph.instance
    val cs = MaterialTheme.colorScheme
    val mixes = smartMixes()
    val recs = recommended.filterNot { graph.library.isBlocked(it) }
    val hero = mixes.firstOrNull()?.let { t(it.first) to it.second } ?: recs.takeIf { it.isNotEmpty() }?.let { t("Recommended for you") to it }
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = contentPadding) {
        hero?.let { (title, tracks) ->
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Artwork(tracks.firstOrNull()?.artworkUrl, Modifier.size(170.dp), RectangleShape)
                    Spacer(Modifier.width(20.dp))
                    Column {
                        Text("# " + t("mix of the day"), color = cs.tertiary, style = MaterialTheme.typography.bodyMedium)
                        Text(title, color = cs.primary, style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(tracks.take(3).joinToString(", ") { it.artistNames } + " · " + t("%s songs", tracks.size), color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(" ▶ " + t("Play") + " ", color = cs.background, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.background(cs.secondary).clickable { play(tracks, 0, title) }.padding(horizontal = 6.dp, vertical = 2.dp))
                            Text("[⤮ " + t("Shuffle") + "]", color = cs.onSurface, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.clickable { graph.player.play(tracks, tracks.indices.random(), title, shuffle = true) }.padding(vertical = 2.dp))
                        }
                    }
                }
            }
        }
        if (mixes.size > 1) item {
            StyledBox(t("Your mixes"), Modifier.padding(horizontal = 14.dp)) {
                mixes.drop(1).forEach { (name, tracks) ->
                    Row(Modifier.fillMaxWidth().clickable { play(tracks, 0, t(name)) }.padding(vertical = 2.dp)) {
                        Text("» " + t(name), color = cs.onSurface, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(t("%s songs", tracks.size), color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        if (recs.isNotEmpty()) item {
            SectionView(Section("Recommended for you", recs.map { app.nocturne.model.MediaEntry.Song(it) })) { list, i -> play(list, i, t("Recommended for you")) }
        }
        item { app.nocturne.ui.components.RadioPodcastRows() }
        homeSections(sections, play)
    }
}

// ---------- Search ----------

/** Linux / Terminal: best match on the left, the rest of the songs on the right, artists below. */
@Composable
internal fun SplitSongResults(query: String, tracks: List<Track>, bottom: Dp, onPlay: (Int) -> Unit) {
    val graph = AppGraph.instance
    val cs = MaterialTheme.colorScheme
    val style = LocalDesignStyle.current
    val artists by androidx.compose.runtime.produceState<List<app.nocturne.model.MediaEntry.Collection>>(emptyList(), query) {
        value = runCatching { graph.youtube.search(query, app.nocturne.model.SearchFilter.ARTISTS) }.getOrDefault(emptyList())
            .filterIsInstance<app.nocturne.model.MediaEntry.Collection>().take(10)
    }
    val top = tracks.firstOrNull() ?: return
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = bottom)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StyledBox(t("Top result"), Modifier.width(300.dp)) {
                    Artwork(top.artworkUrl, Modifier.size(150.dp), trackShape(style))
                    Text(top.title, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold), color = if (style == DesignStyle.LINUX) cs.onSurface else cs.primary, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
                    Text(t("Song") + " · " + top.artistNames, color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "▶  " + t("Play"), color = cs.onPrimary, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(top = 12.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(cs.primary, cs.tertiary))).clickable { onPlay(0) }.padding(horizontal = 18.dp, vertical = 10.dp),
                    )
                }
                StyledBox(t("Songs"), Modifier.weight(1f)) {
                    tracks.drop(1).take(6).forEachIndexed { i, tr -> TrackRow(tr, onClick = { onPlay(i + 1) }) }
                }
            }
        }
        if (artists.isNotEmpty()) item {
            Text(t("Artists"), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(artists) { a ->
                    Column(Modifier.width(110.dp).clip(RoundedCornerShape(12.dp)).clickable { graph.navigator.push(Screen.Collection(a.ref)) }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Artwork(a.ref.artworkUrl, Modifier.size(100.dp), CircleShape)
                        Text(a.ref.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
        if (tracks.size > 7) item {
            Text(t("More songs"), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
        }
        items(tracks.size - minOf(tracks.size, 7)) { n -> TrackRow(tracks[n + 7], onClick = { onPlay(n + 7) }) }
    }
}

/** Arch: fzf-style list with a preview of the hovered result. */
@Composable
internal fun ArchSongResults(tracks: List<Track>, bottom: Dp, onPlay: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var preview by remember(tracks) { mutableStateOf(0) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 820.dp
        Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            app.nocturne.ui.TuiBox("[" + t("results") + " · ${tracks.size}]", Modifier.weight(1f).fillMaxHeight()) {
                LazyColumn(contentPadding = PaddingValues(bottom = bottom)) {
                    items(tracks.size) { i ->
                        val tr = tracks[i]
                        val hover = remember { MutableInteractionSource() }
                        val hovered by hover.collectIsHoveredAsState()
                        androidx.compose.runtime.LaunchedEffect(hovered) { if (hovered) preview = i }
                        val on = i == preview
                        Row(Modifier.fillMaxWidth().background(if (on) cs.surfaceContainerHigh else Color.Transparent).hoverable(hover).clickable { onPlay(i) }.padding(vertical = 2.dp)) {
                            Text(if (on) "> " else "  ", color = cs.primary, style = MaterialTheme.typography.bodyMedium)
                            Text(t("song").padEnd(7), color = cs.tertiary, style = MaterialTheme.typography.bodyMedium)
                            Text(tr.title, color = if (on) cs.primary else cs.onSurface, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text("  " + tr.artistNames, color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(0.6f))
                        }
                    }
                }
            }
            if (wide) tracks.getOrNull(preview)?.let { tr ->
                app.nocturne.ui.TuiBox("[" + t("preview") + "]", Modifier.width(320.dp).fillMaxHeight()) {
                    Column {
                        Artwork(tr.artworkUrl, Modifier.fillMaxWidth().aspectRatio(1f), RectangleShape)
                        Text(tr.title, color = cs.primary, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
                        Text(listOfNotNull(tr.artistNames, tr.album).joinToString(" · "), color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("[⏎] " + t("play"), color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp).clickable { onPlay(preview) })
                    }
                }
            }
        }
    }
}

// ---------- Settings ----------

private class Quick(val label: String, val value: String? = null, val toggle: Boolean? = null, val onToggle: ((Settings) -> Settings)? = null)

private fun quickFor(c: SettingsCategory, s: Settings): List<Quick> = when (c) {
    SettingsCategory.APPEARANCE -> listOf(
        Quick(t("Style"), s.designStyle.label), Quick(t("Font"), s.fontStyle),
        Quick(t("Animated background"), toggle = s.animatedBackground) { it.copy(animatedBackground = !it.animatedBackground) },
    )
    SettingsCategory.PLAYER -> listOf(
        Quick(t("Crossfade"), "${s.crossfadeSec} s"), Quick(t("Speed"), "${s.playbackSpeed}×"),
        Quick(t("Autoplay"), toggle = s.autoplayRadio) { it.copy(autoplayRadio = !it.autoplayRadio) },
    )
    SettingsCategory.LYRICS -> listOf(
        Quick(t("Lyrics"), toggle = s.showLyrics) { it.copy(showLyrics = !it.showLyrics) },
        Quick(t("Translate lyrics"), toggle = s.lyricsTranslate) { it.copy(lyricsTranslate = !it.lyricsTranslate) },
    )
    SettingsCategory.FLOATING -> listOf(Quick(t("Floating player"), toggle = s.floatingPlayer) { it.copy(floatingPlayer = !it.floatingPlayer) })
    SettingsCategory.DISCORD -> listOf(Quick("Rich Presence", toggle = s.discordRpc) { it.copy(discordRpc = !it.discordRpc) })
    SettingsCategory.CONTENT -> listOf(
        Quick(t("Search suggestions"), toggle = s.searchSuggestions) { it.copy(searchSuggestions = !it.searchSuggestions) },
        Quick(t("Hide explicit"), toggle = s.hideExplicit) { it.copy(hideExplicit = !it.hideExplicit) },
    )
    SettingsCategory.LANGUAGE -> listOf(Quick(t("Language"), app.nocturne.i18n.I18n.languages.firstOrNull { it.first == s.uiLanguage }?.second ?: s.uiLanguage))
    SettingsCategory.STORAGE -> listOf(Quick(t("Auto-download"), toggle = s.autoDownload) { it.copy(autoDownload = !it.autoDownload) })
    SettingsCategory.ABOUT -> listOf(Quick(t("Version"), app.nocturne.BuildInfo.VERSION))
    else -> emptyList()
}

private fun visibleCategories() = SettingsCategory.entries.filter {
    it !in setOf(SettingsCategory.SHORTCUTS, SettingsCategory.DISCORD, SettingsCategory.FLOATING) || platformKind == PlatformKind.DESKTOP
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CardSettings(contentPadding: PaddingValues) {
    val graph = AppGraph.instance
    val cs = MaterialTheme.colorScheme
    val s by graph.settings.state.collectAsState()
    var query by remember { mutableStateOf("") }
    val q = query.trim()
    val cats = visibleCategories().filter { c -> q.isEmpty() || t(c.title).contains(q, true) || t(c.summary).contains(q, true) || quickFor(c, s).any { it.label.contains(q, true) } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cols = when { maxWidth >= 1100.dp -> 3; maxWidth >= 640.dp -> 2; else -> 1 }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
            item { Text(t("Settings"), style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(start = 18.dp, top = 8.dp, bottom = 10.dp)) }
            item {
                app.nocturne.ui.kit.OutlinedTextField(
                    query, { query = it }, singleLine = true, placeholder = { Text(t("Find a setting")) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
            items(cats.chunked(cols)) { row ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEach { c ->
                        StyledBox(t(c.title), Modifier.weight(1f).clickable { graph.navigator.push(Screen.SettingsPage(c)) }) {
                            val quick = quickFor(c, s)
                            if (quick.isEmpty()) Text(t(c.summary), color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            quick.forEach { qk ->
                                Row(
                                    Modifier.fillMaxWidth().then(if (qk.onToggle != null) Modifier.clickable { graph.settings.update(qk.onToggle) } else Modifier).padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(qk.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (qk.toggle != null) QuickToggle(qk.toggle)
                                    else Text(qk.value.orEmpty(), color = cs.primary, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                }
                            }
                            Text(t("Open") + " ›", color = cs.primary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                    repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun QuickToggle(on: Boolean) {
    val cs = MaterialTheme.colorScheme
    when (LocalDesignStyle.current) {
        DesignStyle.ARCH -> Text(if (on) "[x]" else "[ ]", color = if (on) cs.secondary else cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        DesignStyle.TERMINAL -> Text(if (on) "● on" else "○ off", color = if (on) Color(0xFFA6E3A1) else cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        else -> Box(Modifier.size(40.dp, 22.dp).clip(CircleShape).background(if (on) cs.primary else Color.White.copy(alpha = 0.18f))) {
            Box(Modifier.align(if (on) Alignment.CenterEnd else Alignment.CenterStart).padding(3.dp).size(16.dp).clip(CircleShape).background(Color.White))
        }
    }
}

/** Arch: the settings shown as a config file; booleans flip on click, the rest open their page. */
@Composable
internal fun ConfigSettings(contentPadding: PaddingValues) {
    val graph = AppGraph.instance
    val cs = MaterialTheme.colorScheme
    val s by graph.settings.state.collectAsState()
    val nav = graph.navigator
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 900.dp
        Row(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding(), start = 10.dp, end = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            app.nocturne.ui.TuiBox("[~/.config/nocturne/config.toml]", Modifier.weight(1f).fillMaxHeight()) {
                LazyColumn(contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding())) {
                    visibleCategories().forEach { c ->
                        item {
                            Text(
                                "[" + c.name.lowercase() + "]", color = cs.tertiary, style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.fillMaxWidth().clickable { nav.push(Screen.SettingsPage(c)) }.padding(top = 10.dp, bottom = 2.dp),
                            )
                        }
                        val quick = quickFor(c, s)
                        if (quick.isEmpty()) item {
                            Text("# " + t(c.summary), color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.clickable { nav.push(Screen.SettingsPage(c)) })
                        }
                        items(quick) { qk ->
                            val key = qk.label.lowercase().replace(' ', '_')
                            Row(Modifier.fillMaxWidth().clickable { if (qk.onToggle != null) graph.settings.update(qk.onToggle) else nav.push(Screen.SettingsPage(c)) }) {
                                Text(key, color = cs.primary, style = MaterialTheme.typography.bodyMedium)
                                Text(" = ", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                                val v = qk.toggle?.toString() ?: "\"${qk.value}\""
                                Text(v, color = if (qk.toggle == true) cs.secondary else if (qk.toggle == false) GRedish else cs.onSurface, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
            if (wide) app.nocturne.ui.TuiBox("[" + t("sections") + "]", Modifier.width(300.dp).fillMaxHeight()) {
                Column {
                    visibleCategories().forEach { c ->
                        Text("> " + t(c.title), color = cs.onSurface, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth().clickable { nav.push(Screen.SettingsPage(c)) }.padding(vertical = 3.dp))
                    }
                }
            }
        }
    }
}

private val GRedish = Color(0xFFFB4934)
