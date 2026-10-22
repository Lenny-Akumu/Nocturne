package app.nocturne.ui.style.glass

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Airplay
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.ThumbDownOffAlt
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import app.nocturne.ui.kit.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nocturne.AppGraph
import app.nocturne.data.PlayerBackground
import app.nocturne.i18n.t
import app.nocturne.model.Source
import app.nocturne.nowMillis
import app.nocturne.player.RepeatMode
import app.nocturne.ui.components.ArtistLinks
import app.nocturne.ui.components.LocalTrackMenu
import app.nocturne.ui.components.MenuExtra
import app.nocturne.ui.components.SourceBadge
import app.nocturne.ui.components.TrackMenuContext
import app.nocturne.ui.kit.CircularProgressIndicator
import app.nocturne.ui.player.ArtworkView
import app.nocturne.ui.player.EqBars
import app.nocturne.ui.player.LocalOverlays
import app.nocturne.ui.player.Pane
import app.nocturne.ui.player.PaneContent
import app.nocturne.ui.player.PlayerSeekBar
import app.nocturne.ui.player.TransportButton
import app.nocturne.ui.player.pressAmount
import app.nocturne.ui.theme.LocalDarkTheme
import coil3.compose.AsyncImage

// Solid gradient derived from the artwork-tinted scheme (blurred artwork if the user picked that background).
@Composable
fun GlassPlayerBackground(artworkUrl: String?) {
    val settings by AppGraph.instance.settings.state.collectAsState()
    val cs = MaterialTheme.colorScheme
    val dark = LocalDarkTheme.current
    if (settings.playerBackground == PlayerBackground.BLUR) {
        AsyncImage(artworkUrl, null, Modifier.fillMaxSize().blur(80.dp), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.3f), Color.Black.copy(alpha = 0.45f), Color.Black.copy(alpha = 0.7f)))))
        return
    }
    val end = cs.surface
    val c0 = if (dark) lerp(cs.primary, cs.primaryContainer, 0.55f) else cs.primaryContainer
    Box(
        Modifier.fillMaxSize().drawBehind {
            drawRect(end)
            drawRect(
                Brush.radialGradient(
                    0f to c0, 0.4f to lerp(c0, end, 0.5f), 0.7f to lerp(c0, end, 0.85f), 1f to end,
                    center = Offset(size.width / 2, size.height * 0.28f),
                    radius = maxOf(size.width, size.height) * 0.62f,
                ),
            )
        },
    )
}

// Phone layout of the Liquid Glass player: glass top bar, artwork, meta + reaction pill, scrubber,
// transport capsule and action capsule (selected = inner glass highlight).
@Composable
internal fun GlassPlayerLayout(onCollapse: () -> Unit, pane: Pane, onPane: (Pane) -> Unit, noLyrics: Boolean, tall: Boolean) {
    val graph = AppGraph.instance
    val state by graph.player.state.collectAsState()
    val settings by graph.settings.state.collectAsState()
    val library by graph.library.state.collectAsState()
    val track = state.current ?: return
    val overlays = LocalOverlays.current
    val menu = LocalTrackMenu.current
    val cs = MaterialTheme.colorScheme
    val tint = cs.primary.copy(alpha = 0.22f)
    val fg2 = cs.onSurface.copy(alpha = 0.66f)

    Column(Modifier.fillMaxSize()) {
        // Top bar: collapse circle, devices / more pill.
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassCircle(Icons.Rounded.KeyboardArrowDown, t("Collapse"), onCollapse, Modifier.glassAppear(), tint = tint)
            Box(Modifier.weight(1f).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                val label = state.remoteName?.let { t("Playing on %s", it) } ?: t("Instrumental version").takeIf { state.instrumental }
                if (label != null) Text(label, style = MaterialTheme.typography.labelSmall, color = cs.primary, maxLines = 1)
            }
            val castDevice by graph.cast.device.collectAsState()
            GlassSurface(Modifier.glassAppear(40).height(44.dp), RoundedCornerShape(50), tint) {
                Row(Modifier.padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassInnerButton({ overlays.connect = true }, Modifier.size(40.dp), contentDescription = t("Devices")) {
                        Icon(if (castDevice != null) Icons.Rounded.CastConnected else Icons.Rounded.Airplay, null, Modifier.size(20.dp), tint = if (castDevice != null) cs.primary else cs.onSurface)
                    }
                    GlassInnerButton({
                        val s = graph.player.state.value
                        val sleep = s.sleepAtMs?.let { t("Sleep timer · %s min", ((it - nowMillis()) / 60_000).coerceAtLeast(0) + 1) }
                            ?: if (s.sleepAfterTrack) t("Sleep timer") + " · " + t("End of track") else t("Sleep timer")
                        menu(track, TrackMenuContext(extras = listOf(MenuExtra(Icons.Rounded.Bedtime, sleep) { overlays.sleep = true })))
                    }, Modifier.size(40.dp), contentDescription = t("More")) {
                        Icon(Icons.Rounded.MoreHoriz, null, Modifier.size(22.dp), tint = cs.onSurface)
                    }
                }
            }
        }

        // Artwork / lyrics / queue.
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            AnimatedContent(pane, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "pane") { p ->
                if (p == Pane.ARTWORK) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ArtworkView(track, state.isPlaying) { if (settings.showLyrics) onPane(if (noLyrics) Pane.QUEUE else Pane.LYRICS) }
                } else PaneContent(p)
            }
        }

        // Title / artist + reactions pill.
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(track.title, style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold), color = cs.onSurface, maxLines = 1, modifier = Modifier.basicMarquee())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (settings.showSourceBadges) { SourceBadge(track.source, size = 14.dp); Spacer(Modifier.width(6.dp)) }
                    ArtistLinks(track, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp), color = fg2)
                }
            }
            Spacer(Modifier.width(10.dp))
            val liked = library.liked.any { it.key == track.key }
            GlassSurface(Modifier.glassAppear(80).height(42.dp), RoundedCornerShape(50), tint) {
                Row(Modifier.padding(horizontal = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (track.source == Source.YOUTUBE_MUSIC) {
                        PillButton(Icons.Rounded.ChatBubbleOutline, t("Comments"), cs.onSurface) { overlays.comments = true }
                        Box(Modifier.padding(horizontal = 2.dp).size(1.dp, 20.dp).background(cs.onSurface.copy(alpha = 0.22f)))
                    }
                    PillButton(Icons.Rounded.ThumbDownOffAlt, t("Not interested"), cs.onSurface) { graph.notInterested(track) }
                    PillButton(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, t("Like"), if (liked) cs.primary else cs.onSurface) { graph.toggleLike(track) }
                }
            }
        }

        PlayerSeekBar(state.positionMs, state.durationMs, state.progress, { graph.player.seekTo(it) }, Modifier.padding(horizontal = 24.dp).padding(top = 6.dp))

        // Transport capsule.
        GlassSurface(
            Modifier.glassAppear(120).padding(horizontal = 16.dp).padding(top = 10.dp).fillMaxWidth().height(if (tall) 108.dp else 90.dp),
            RoundedCornerShape(34.dp), tint,
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                ModeButton(Icons.Rounded.Shuffle, t("Shuffle"), state.shuffle) { graph.player.toggleShuffle() }
                TransportButton(Icons.Rounded.FastRewind, "Previous", graph.player::previous, size = 56.dp, iconSize = 36.dp, tint = cs.onSurface)
                PlayCircle(state.isPlaying, state.isBuffering, if (tall) 70.dp else 60.dp, graph.player::toggle)
                TransportButton(Icons.Rounded.FastForward, "Next", graph.player::next, size = 56.dp, iconSize = 36.dp, tint = cs.onSurface)
                ModeButton(if (state.repeat == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, t("Repeat"), state.repeat != RepeatMode.OFF) { graph.player.cycleRepeat() }
            }
        }

        // Action capsule.
        GlassSurface(
            Modifier.glassAppear(160).padding(horizontal = 16.dp).padding(top = 12.dp, bottom = if (tall) 48.dp else 22.dp).fillMaxWidth().height(74.dp),
            RoundedCornerShape(26.dp), tint,
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
                val c = track.source != Source.SOUNDCLOUD && track.source != Source.LOCAL
                if (c) ActionItem(settings.showVideo, if (settings.showVideo) "Show artwork" else "Show video", { graph.settings.update { it.copy(showVideo = !it.showVideo) } }) { col ->
                    Icon(if (settings.showVideo) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff, null, Modifier.size(25.dp), tint = col)
                }
                ActionItem(settings.eqEnabled || settings.playbackSpeed != 1f, "Equalizer and speed", { overlays.audio = true }) { col ->
                    EqBars(state.isPlaying, Modifier.size(25.dp), col)
                }
                if (settings.showLyrics) ActionItem(pane == Pane.LYRICS, "Lyrics", { onPane(if (pane == Pane.LYRICS) Pane.ARTWORK else Pane.LYRICS) }) { col ->
                    Icon(Icons.Rounded.Lyrics, null, Modifier.size(25.dp), tint = col)
                }
                ActionItem(pane == Pane.QUEUE, "Queue", { onPane(if (pane == Pane.QUEUE) Pane.ARTWORK else Pane.QUEUE) }) { col ->
                    Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, Modifier.size(25.dp), tint = col)
                }
            }
        }
    }
}

@Composable
private fun PillButton(icon: ImageVector, cd: String, tint: Color, onClick: () -> Unit) {
    GlassInnerButton(onClick, Modifier.size(40.dp, 42.dp), contentDescription = cd) {
        Icon(icon, cd, Modifier.size(20.dp), tint = tint)
    }
}

// Shuffle / repeat: accent glyph plus inner glass when on.
@Composable
private fun ModeButton(icon: ImageVector, cd: String, on: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    GlassInnerButton(onClick, Modifier.size(46.dp), selected = on, contentDescription = cd) {
        Icon(icon, cd, Modifier.size(22.dp), tint = if (on) cs.primary else cs.onSurface.copy(alpha = 0.72f))
    }
}

@Composable
private fun ActionItem(selected: Boolean, cd: String, onClick: () -> Unit, glyph: @Composable (Color) -> Unit) {
    val cs = MaterialTheme.colorScheme
    GlassInnerButton(onClick, Modifier.size(72.dp, 56.dp), selected = selected, shape = RoundedCornerShape(50), contentDescription = t(cd)) {
        glyph(if (selected) cs.onSurface else cs.onSurface.copy(alpha = 0.82f))
    }
}

// Big tinted play circle: accent gradient, specular top rim, colored glow, press squish.
@Composable
private fun PlayCircle(playing: Boolean, buffering: Boolean, d: Dp, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val src = remember { MutableInteractionSource() }
    val press = pressAmount(src)
    val top = lerp(cs.primary, Color.White, 0.18f)
    val bottom = lerp(cs.primary, cs.primaryContainer, 0.45f)
    val fg = if (lerp(top, bottom, 0.5f).luminance() > 0.5f) Color(0xFF141020) else Color.White
    Box(
        Modifier.size(d)
            .graphicsLayer { val s = 1f - 0.08f * press.value; scaleX = s; scaleY = s }
            .shadow(14.dp, CircleShape, ambientColor = cs.primary, spotColor = cs.primary)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(top, bottom)))
            .drawWithContent {
                drawContent()
                drawCircle(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent), 0f, size.height * 0.6f), style = Stroke(1.dp.toPx()), radius = size.minDimension / 2 - 0.5.dp.toPx())
                if (press.value > 0f) drawCircle(Color.White.copy(alpha = 0.18f * press.value.coerceIn(0f, 1f)))
            }
            .combinedClickable(interactionSource = src, indication = null, role = Role.Button, onClickLabel = t(if (playing) "Pause" else "Play"), onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (buffering) CircularProgressIndicator(Modifier.size(d * 0.5f), color = fg, strokeWidth = 3.dp)
        else AnimatedContent(
            playing,
            transitionSpec = { (fadeIn(tween(140)) + scaleIn(spring(dampingRatio = 0.6f, stiffness = 500f), 0.6f)) togetherWith (fadeOut(tween(100)) + scaleOut(tween(100), 0.6f)) },
            label = "play",
        ) { on -> Icon(if (on) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, t(if (on) "Pause" else "Play"), Modifier.size(d * 0.46f), tint = fg) }
    }
}
