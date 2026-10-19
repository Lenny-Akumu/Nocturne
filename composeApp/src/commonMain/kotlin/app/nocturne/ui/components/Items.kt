package app.nocturne.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.runtime.rememberCoroutineScope
import app.nocturne.PlatformKind
import app.nocturne.platformKind
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explicit
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nocturne.AppGraph
import app.nocturne.model.CollectionRef
import app.nocturne.model.MediaEntry
import app.nocturne.model.Section
import app.nocturne.model.SectionLayout
import app.nocturne.model.Track
import app.nocturne.model.formatDuration
import app.nocturne.ui.Screen

@Composable
fun TrackRow(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    index: Int? = null,
    menuContext: TrackMenuContext = TrackMenuContext(),
) {
    val graph = AppGraph.instance
    val settings by graph.settings.state.collectAsState()
    val player by graph.player.state.collectAsState()
    val menu = LocalTrackMenu.current
    val isCurrent = player.current?.key == track.key
    val dense = settings.compactLists
    Row(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(onClick = onClick, onLongClick = { menu(track, menuContext) })
            .padding(horizontal = 12.dp, vertical = if (dense) 4.dp else 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (index != null) {
            Text(
                "$index",
                Modifier.width(28.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.width(8.dp))
        }
        Box(Modifier.size(if (dense) 42.dp else 52.dp)) {
            Artwork(track.artworkUrl, Modifier.fillMaxSize(), RoundedCornerShape(10.dp))
            if (isCurrent) {
                Box(
                    Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.GraphicEq, null, tint = Color.White) }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (settings.showSourceBadges) {
                    SourceBadge(track.source, size = 13.dp)
                    Spacer(Modifier.width(5.dp))
                }
                if (track.explicit) {
                    Icon(Icons.Rounded.Explicit, "Explicit", Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(3.dp))
                }
                Text(
                    listOfNotNull(track.artistNames.takeIf { it.isNotBlank() }, track.album, track.durationMs.takeIf { it > 0 }?.formatDuration())
                        .joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = { menu(track, menuContext) }) {
            Icon(Icons.Rounded.MoreVert, "More", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun CollectionCard(ref: CollectionRef, onClick: () -> Unit, modifier: Modifier = Modifier, width: androidx.compose.ui.unit.Dp? = 156.dp) {
    val settings by AppGraph.instance.settings.state.collectAsState()
    Column(
        modifier.then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .clip(MaterialTheme.shapes.medium).combinedClickable(onClick = onClick).padding(6.dp),
        horizontalAlignment = if (ref.type == app.nocturne.model.CollectionType.ARTIST) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Box {
            Artwork(ref.artworkUrl, Modifier.fillMaxWidth().aspectRatio(1f), ref.type.shape(), ref.type.fallbackIcon())
            if (settings.showSourceBadges) {
                SourceBadge(ref.source, Modifier.align(Alignment.BottomEnd).padding(6.dp), 18.dp)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(ref.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        ref.subtitle?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun SongCard(track: Track, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val menu = LocalTrackMenu.current
    val settings by AppGraph.instance.settings.state.collectAsState()
    Column(modifier.width(156.dp).clip(MaterialTheme.shapes.medium).combinedClickable(onClick = onClick, onLongClick = { menu(track, TrackMenuContext()) }).padding(6.dp)) {
        Box {
            Artwork(track.artworkUrl, Modifier.fillMaxWidth().aspectRatio(1f), MaterialTheme.shapes.medium)
            if (settings.showSourceBadges) SourceBadge(track.source, Modifier.align(Alignment.BottomEnd).padding(6.dp), 18.dp)
        }
        Spacer(Modifier.height(8.dp))
        Text(track.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(track.artistNames, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, badge: (@Composable () -> Unit)? = null, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        badge?.let { it(); Spacer(Modifier.width(8.dp)) }
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        action?.invoke()
    }
}

@Composable
fun SectionView(section: Section, subtitle: String? = null, onPlay: (List<Track>, Int) -> Unit) {
    val nav = AppGraph.instance.navigator
    val src = section.source
    val scope = rememberCoroutineScope()
    val quick = section.layout == SectionLayout.QUICK_PICKS
    val gridState = rememberLazyGridState()
    val rowState = rememberLazyListState()
    val desktop = platformKind == PlatformKind.DESKTOP
    fun scroll(direction: Int) {
        scope.launch {
            if (quick) gridState.animateScrollBy(direction * gridState.layoutInfo.viewportSize.width * 0.85f)
            else rowState.animateScrollBy(direction * rowState.layoutInfo.viewportSize.width * 0.85f)
        }
    }
    val canBack = if (quick) gridState.canScrollBackward else rowState.canScrollBackward
    val canForward = if (quick) gridState.canScrollForward else rowState.canScrollForward
    SectionHeader(
        section.title,
        badge = if (src != null) {
            { SourceBadge(src, size = 18.dp) }
        } else null,
        action = if (desktop && (canBack || canForward)) {
            {
                Row {
                    FilledTonalIconButton(onClick = { scroll(-1) }, enabled = canBack, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Scroll left")
                    }
                    Spacer(Modifier.width(6.dp))
                    FilledTonalIconButton(onClick = { scroll(1) }, enabled = canForward, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Scroll right")
                    }
                }
            }
        } else null,
    )
    subtitle?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 18.dp, bottom = 6.dp))
    }
    if (quick) {
        val songs = section.items.filterIsInstance<MediaEntry.Song>().map { it.track }
        LazyHorizontalGrid(
            rows = GridCells.Fixed(4),
            state = gridState,
            modifier = Modifier.fillMaxWidth().height(272.dp),
            contentPadding = PaddingValues(horizontal = 6.dp),
        ) {
            items(songs.size) { i ->
                TrackRow(songs[i], onClick = { onPlay(songs, i) }, modifier = Modifier.widthIn(max = 340.dp).width(320.dp))
            }
        }
    } else {
        LazyRow(state = rowState, contentPadding = PaddingValues(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            items(section.items) { item ->
                when (item) {
                    is MediaEntry.Song -> SongCard(item.track, onClick = { onPlay(listOf(item.track), 0) })
                    is MediaEntry.Collection -> CollectionCard(item.ref, onClick = { nav.push(Screen.Collection(item.ref)) })
                }
            }
        }
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun MessageBox(icon: ImageVector, title: String, message: String? = null, modifier: Modifier = Modifier, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        message?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}
