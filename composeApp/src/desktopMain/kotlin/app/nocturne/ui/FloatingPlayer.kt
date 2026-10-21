package app.nocturne.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.using
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.ThumbDown
import app.nocturne.ui.kit.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import app.nocturne.AppGraph
import app.nocturne.MainWindow
import app.nocturne.i18n.t
import app.nocturne.model.Track
import app.nocturne.model.formatDuration
import app.nocturne.ui.components.Artwork
import app.nocturne.ui.theme.NocturneTheme
import app.nocturne.ui.theme.rememberArtworkSeed
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

private val BASE = DpSize(820.dp, 132.dp)
private val BTN = 40.dp

@Composable
fun FloatingPlayerWindow() {
    val graph = AppGraph.instance
    val settings by graph.settings.state.collectAsState()
    val scale = settings.floatingScale.coerceIn(0.5f, 2f)
    val editing = FloatingEdit.editing
    val bounds = remember { java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds }
    val anchor = settings.floatingAnchor
    val ax = if (settings.floatingX < 0) bounds.x + bounds.width / 2 + when (anchor) { 0 -> -180; 2 -> 180; else -> 0 } else settings.floatingX
    val ay = if (settings.floatingX < 0) bounds.y + 6 else settings.floatingY
    val state = rememberDialogState(position = WindowPosition((ax - 200).dp, ay.dp), size = DpSize(400.dp, 80.dp))
    // Read only from snapshotFlow: per-frame size changes must not recompose the window.
    val pillPx = remember { mutableStateOf(IntSize.Zero) }
    val busy = remember { mutableStateOf(false) }
    // The pill may only animate open once the window has its fixed max size (no resizes mid-animation).
    var full by remember { mutableStateOf(false) }
    DialogWindow(
        onCloseRequest = { graph.settings.update { it.copy(floatingPlayer = false) } },
        state = state,
        title = "Nocturne",
        undecorated = true,
        transparent = true,
        resizable = false,
        focusable = false,
    ) {
        LaunchedEffect(Unit) { window.isAlwaysOnTop = true }
        LaunchedEffect(ax, ay, anchor, editing, scale) {
            val k = window.graphicsConfiguration?.defaultTransform?.scaleX ?: 1.0
            val margin = 4
            fun place(w: Int, h: Int) {
                val x = when (anchor) { 0 -> ax - margin; 2 -> ax - w + margin; else -> ax - w / 2 }
                if (window.x != x || window.y != ay || window.width != w || window.height != h) window.setBounds(x, ay, w, h)
            }
            snapshotFlow<IntSize?> { if (editing || busy.value) null else pillPx.value }.collectLatest { px ->
                if (px == null) {
                    place((BASE.width.value * scale).toInt(), (BASE.height.value * scale).toInt())
                    full = true
                    return@collectLatest
                }
                if (px == IntSize.Zero) return@collectLatest
                // Collapse already finished (busy=false); short grace period against hover flicker.
                if (full) delay(160)
                full = false
                place((px.width / k).toInt() + margin * 2, (px.height / k).toInt() + margin * 2)
            }
        }
        val base = LocalDensity.current
        NocturneTheme(settings, forceDark = true) {
            CompositionLocalProvider(LocalDensity provides Density(base.density * scale, base.fontScale)) {
                val align = when (anchor) {
                    0 -> Alignment.TopStart
                    2 -> Alignment.TopEnd
                    else -> Alignment.TopCenter
                }
                Box(Modifier.fillMaxSize(), contentAlignment = align) {
                    val pillModifier = Modifier
                        .wrapContentSize(align, unbounded = true)
                        .padding(4.dp)
                        .onSizeChanged { pillPx.value = it }
                    if (editing) {
                        WindowDraggableArea(Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp)).background(Color(0x55000000))) {
                            Box(Modifier.fillMaxSize(), contentAlignment = align) { Box(pillModifier) { Pill(interactive = false, window = null, canOpen = false) {} } }
                        }
                        FilledTonalButton(
                            onClick = {
                                val r = window.bounds
                                val nx = when (anchor) { 0 -> r.x + 4; 2 -> r.x + r.width - 4; else -> r.x + r.width / 2 }
                                graph.settings.update { it.copy(floatingX = nx, floatingY = r.y) }
                                FloatingEdit.editing = false
                            },
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
                        ) { Text(t("Done")) }
                    } else {
                        Box(pillModifier) { Pill(interactive = true, window = window, canOpen = full) { busy.value = it } }
                    }
                }
            }
        }
    }
}

@Composable
private fun Pill(interactive: Boolean, window: java.awt.Window?, canOpen: Boolean, onBusy: (Boolean) -> Unit) {
    val graph = AppGraph.instance
    val state by graph.player.state.collectAsState()
    val settings by graph.settings.state.collectAsState()
    val library by graph.library.state.collectAsState()
    val track = state.current
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    // Debounced hover: ignores exit/enter blips caused by the window being resized under the cursor.
    var hoverOn by remember { mutableStateOf(false) }
    LaunchedEffect(hovered, interactive) {
        if (hovered && interactive) hoverOn = true else { delay(90); hoverOn = false }
    }
    val open = hoverOn && canOpen
    // Single driver for the whole expand/collapse; read only in layout/draw, never in composition.
    val progress = animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec = if (open) spring(dampingRatio = 1f, stiffness = 420f) else spring(dampingRatio = 1f, stiffness = 700f),
        label = "pill",
    )
    val hoverState = rememberUpdatedState(hoverOn)
    val busy by remember { derivedStateOf { hoverState.value || progress.value > 0f } }
    LaunchedEffect(busy) { onBusy(busy) }
    val glass = settings.floatingStyle == 1
    val accent by animateColorAsState(if (glass) Color.White else rememberArtworkSeed(track?.artworkUrl) ?: MaterialTheme.colorScheme.primary, tween(450), label = "accent")
    val shape = RoundedCornerShape(30.dp)
    val b = settings.floatingButtons
    val anchor = settings.floatingAnchor

    val extras = buildList<@Composable () -> Unit> {
        if (track != null && "like" in b) add {
            val liked = library.liked.any { it.key == track.key }
            PillButton(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (liked) accent else Color.White) { graph.toggleLike(track) }
        }
        if (track != null && "dislike" in b) add { PillButton(Icons.Rounded.ThumbDown, Color.White) { graph.notInterested(track) } }
        if ("volume" in b) add { Volume(accent) }
        if ("open" in b) add { PillButton(Icons.AutoMirrored.Rounded.OpenInNew, Color.White) { MainWindow.show() } }
    }
    val leftExtras = when (anchor) {
        0 -> emptyList()
        2 -> extras.reversed()
        else -> extras.take(extras.size / 2)
    }
    val rightExtras = when (anchor) {
        0 -> extras
        2 -> emptyList()
        else -> extras.drop(extras.size / 2)
    }
    val volW = if ("volume" in b) 120.dp else 0.dp
    fun widthOf(list: List<@Composable () -> Unit>, hasVolume: Boolean): Dp = BTN * (list.size - if (hasVolume) 1 else 0) + if (hasVolume) volW else 0.dp
    val rightHasVol = (anchor == 0 || anchor == 1) && "volume" in b && rightExtras.isNotEmpty()
    val sideW = if (anchor == 1) maxOf(widthOf(leftExtras, false), widthOf(rightExtras, rightHasVol)) else 0.dp

    Box(
        Modifier
            .then(if (glass) Modifier else Modifier.shadow(14.dp, shape))
            .clip(shape)
            .background(if (glass) Color(0x8C1C1C22) else Color(0xCC0E0E12))
            .hoverable(hover)
            .pointerInput(window) {
                val w = window ?: return@pointerInput
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        if (e.type != PointerEventType.Press || !e.buttons.isTertiaryPressed) continue
                        val start = java.awt.MouseInfo.getPointerInfo()?.location ?: continue
                        val origin = w.location
                        do {
                            val m = awaitPointerEvent()
                            val p = java.awt.MouseInfo.getPointerInfo()?.location ?: break
                            w.setLocation(origin.x + p.x - start.x, origin.y + p.y - start.y)
                        } while (m.buttons.isTertiaryPressed)
                        val r = w.bounds
                        val anchorNow = AppGraph.instance.settings.value.floatingAnchor
                        val nx = when (anchorNow) { 0 -> r.x + 4; 2 -> r.x + r.width - 4; else -> r.x + r.width / 2 }
                        AppGraph.instance.settings.update { it.copy(floatingX = nx, floatingY = r.y) }
                    }
                }
            },
    ) {
        PillLayout(progress) {
            if (!glass) {
                Crossfade(track?.artworkUrl, Modifier.layoutId("bg"), tween(500), label = "backdrop") { url ->
                    if (url != null) AsyncImage(url, null, Modifier.fillMaxSize().blur(36.dp).alpha(0.75f), contentScale = ContentScale.Crop)
                }
                Box(Modifier.layoutId("bg").background(Color.Black.copy(alpha = 0.45f)))
            }
            Side("left", Alignment.End, if (anchor == 1) sideW else null, leftExtras)
            Row(Modifier.layoutId("core"), verticalAlignment = Alignment.CenterVertically) {
                Crossfade(track?.artworkUrl, Modifier.size(40.dp), tween(350), label = "art") { url ->
                    Artwork(url, Modifier.size(40.dp), CircleShape, Icons.Rounded.MusicNote)
                }
                Spacer(Modifier.width(10.dp))
                AnimatedContent(
                    targetState = track,
                    modifier = Modifier.width(170.dp).clickable(enabled = interactive) { MainWindow.show() },
                    transitionSpec = {
                        (fadeIn(tween(320, delayMillis = 60)) + slideInVertically(tween(320)) { it / 4 }) togetherWith fadeOut(tween(160)) using null
                    },
                    contentKey = { it?.key },
                    label = "title",
                ) { t -> TrackText(t) }
                if ("prev" in b) PillButton(Icons.Rounded.SkipPrevious, Color.White, interactive) { graph.player.previous() }
                PillButton(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, accent, interactive) { graph.player.toggle() }
                if ("next" in b) PillButton(Icons.Rounded.SkipNext, Color.White, interactive) { graph.player.next() }
            }
            Side("right", Alignment.Start, if (anchor == 1) sideW else null, rightExtras)
            if ("seek" in b) {
                Row(Modifier.layoutId("seek").fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(state.positionMs.formatDuration(), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.width(8.dp))
                    PillBar(state.progress, accent, Modifier.weight(1f)) { f ->
                        if (state.durationMs > 0) graph.player.seekTo((f * state.durationMs).toLong())
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(state.durationMs.formatDuration(), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun TrackText(track: Track?) {
    Column(Modifier.fillMaxWidth()) {
        Text(track?.title ?: "Nocturne", color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, maxLines = 1, modifier = Modifier.basicMarquee())
        Text(track?.artistNames.orEmpty(), color = Color.White.copy(alpha = 0.65f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Children are always measured at full size (cached across frames); [progress] only changes the
 * reported size and placement, so expand/collapse is a cheap relayout + layer alpha, the core
 * (artwork..next) never moves and the extras are revealed by the pill's clip.
 */
@Composable
private fun PillLayout(progress: State<Float>, content: @Composable () -> Unit) {
    Layout(content) { ms, _ ->
        val loose = Constraints()
        val padH = 8.dp.roundToPx()
        val padV = 6.dp.roundToPx()
        val core = ms.first { it.layoutId == "core" }.measure(loose)
        val left = ms.firstOrNull { it.layoutId == "left" }?.measure(loose)
        val right = ms.firstOrNull { it.layoutId == "right" }?.measure(loose)
        val lw = left?.width ?: 0
        val rw = right?.width ?: 0
        val topH = maxOf(core.height, left?.height ?: 0, right?.height ?: 0) + padV * 2
        val fullW = padH * 2 + lw + core.width + rw
        val seek = ms.firstOrNull { it.layoutId == "seek" }?.measure(Constraints(minWidth = fullW, maxWidth = fullW))
        val seekH = seek?.height ?: 0
        val bg = ms.filter { it.layoutId == "bg" }.map { it.measure(Constraints.fixed(fullW, topH + seekH)) }
        val p = progress.value.coerceIn(0f, 1f)
        val cl = (lw * p).roundToInt()
        val cr = if (rw == lw) cl else (rw * p).roundToInt()
        layout(padH * 2 + cl + core.width + cr, topH + (seekH * p).roundToInt()) {
            val ox = cl - lw
            bg.forEach { it.place(ox, 0) }
            core.place(padH + cl, (topH - core.height) / 2)
            if (p > 0f) {
                val a = p * p * (3 - 2 * p)
                left?.placeWithLayer(ox + padH, (topH - left.height) / 2) { alpha = a }
                right?.placeWithLayer(padH + cl + core.width, (topH - right.height) / 2) { alpha = a }
                seek?.placeWithLayer(ox, topH) { alpha = a }
            }
        }
    }
}

@Composable
private fun Side(id: String, from: Alignment.Horizontal, width: Dp?, items: List<@Composable () -> Unit>) {
    if (items.isEmpty() && width == null) return
    Row(
        Modifier.layoutId(id).then(if (width != null) Modifier.width(width) else Modifier),
        horizontalArrangement = if (from == Alignment.End) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) { items.forEach { it() } }
}

@Composable
private fun PillButton(icon: ImageVector, tint: Color, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(BTN)) { Icon(icon, null, tint = tint) }
}

@Composable
private fun Volume(accent: Color) {
    val graph = AppGraph.instance
    val settings by graph.settings.state.collectAsState()
    Row(Modifier.width(120.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (settings.volume <= 0.01f) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp, null,
            Modifier.size(18.dp), tint = Color.White.copy(alpha = 0.8f),
        )
        Spacer(Modifier.width(6.dp))
        var vol by remember(settings.volume) { mutableFloatStateOf(settings.volume) }
        PillBar(vol, accent, Modifier.weight(1f), live = true) { vol = it; graph.player.setVolume(it) }
    }
}

@Composable
private fun PillBar(value: Float, color: Color, modifier: Modifier, live: Boolean = false, onChange: (Float) -> Unit) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val shown = drag ?: value
    Canvas(
        modifier
            .height(16.dp)
            .pointerHoverIcon(PointerIcon.Hand)
            .pointerInput(Unit) {
                detectTapGestures { o -> onChange((o.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(live) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> drag = (o.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = { drag?.let(onChange); drag = null },
                    onDragCancel = { drag = null },
                ) { change, _ ->
                    drag = (change.position.x / size.width).coerceIn(0f, 1f)
                    if (live) drag?.let(onChange)
                }
            },
    ) {
        val y = size.height / 2
        val h = 3.dp.toPx()
        drawLine(Color.White.copy(alpha = 0.2f), Offset(0f, y), Offset(size.width, y), h, StrokeCap.Round)
        drawLine(color, Offset(0f, y), Offset(size.width * shown, y), h, StrokeCap.Round)
        drawCircle(Color.White, 5.dp.toPx(), Offset(size.width * shown, y))
    }
}
