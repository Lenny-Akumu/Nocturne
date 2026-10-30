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
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.ThumbDown
import app.nocturne.ui.kit.FilledTonalButton
import app.nocturne.ui.kit.Icon
import app.nocturne.ui.player.PlayPauseButton
import app.nocturne.ui.player.TransportButton
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
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

private val BASE = DpSize(820.dp, 132.dp)
private val BASE_VERTICAL = DpSize(72.dp, 560.dp)
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
    run {
        val gw = (340 * scale).toInt()
        val gh = (64 * scale).toInt()
        WindowMorph.pillGuess = java.awt.Rectangle(when (anchor) { 0 -> ax; 2 -> ax - gw; else -> ax - gw / 2 }, ay, gw, gh)
    }
    // Read only from snapshotFlow: per-frame size changes must not recompose the window.
    val pillPx = remember { mutableStateOf(IntSize.Zero) }
    val busy = remember { mutableStateOf(false) }
    // The pill may only animate open once the window has its fixed max size (no resizes mid-animation).
    var full by remember { mutableStateOf(false) }
    // Tucked away: the pill slides off the nearest screen edge, a small tab stays to pull it back.
    var hidden by remember { mutableStateOf(false) }
    var edge by remember { mutableStateOf(Edge.TOP) }
    val slide = remember { androidx.compose.animation.core.Animatable(0f) }
    val tabIn = remember { androidx.compose.animation.core.Animatable(0f) }
    var lastRect by remember { mutableStateOf(java.awt.Rectangle(0, 0, 0, 0)) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // steps aside while the app window is in front, pops back in when it goes away (Alt+Tab, minimize)
    val show = editing || (app.nocturne.MainWindow.seen && !app.nocturne.MainWindow.active && (WindowMorph.spec == null || WindowMorph.handoff))
    var winVisible by remember { mutableStateOf(show) }
    val appear = remember { androidx.compose.animation.core.Animatable(if (show) 1f else 0f) }
    // taking over from the minimize morph: plain fade, the overlay already did the motion
    var soft by remember { mutableStateOf(false) }
    LaunchedEffect(show) {
        if (show) {
            soft = WindowMorph.handoff
            winVisible = true
            if (soft) appear.animateTo(1f, androidx.compose.animation.core.tween(90))
            else appear.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.62f, stiffness = 420f))
        } else {
            soft = WindowMorph.spec != null
            if (soft) appear.snapTo(0f) else appear.animateTo(0f, androidx.compose.animation.core.tween(130))
            winVisible = false
        }
    }
    DialogWindow(
        visible = winVisible,
        onCloseRequest = { graph.settings.update { it.copy(floatingPlayer = false) } },
        state = state,
        title = "Nocturne",
        undecorated = true,
        transparent = true,
        resizable = false,
        focusable = false,
    ) {
        LaunchedEffect(Unit) { window.isAlwaysOnTop = true }
        androidx.compose.runtime.DisposableEffect(Unit) { onDispose { WindowMorph.pill = null; WindowMorph.pillGuess = null } }
        LaunchedEffect(settings.iconBackground, settings.iconPalette, settings.halloweenIcon) { app.nocturne.AppIcon.update(settings.iconBackground, settings.iconPalette, settings.halloweenIcon && isHalloween()).takeIf { it.isNotEmpty() }?.let { window.iconImages = it } }
        val base0 = if (settings.floatingVertical) BASE_VERTICAL else BASE
        LaunchedEffect(ax, ay, anchor, editing, scale, base0) {
            val k = window.graphicsConfiguration?.defaultTransform?.scaleX ?: 1.0
            val margin = 4
            fun place(w: Int, h: Int) {
                if (hidden) return
                val x = when (anchor) { 0 -> ax - margin; 2 -> ax - w + margin; else -> ax - w / 2 }
                lastRect = java.awt.Rectangle(x, ay, w, h)
                val (dx, dy) = slideOffset(edge, slide.value, lastRect, bounds)
                if (window.x != x + dx || window.y != ay + dy || window.width != w || window.height != h) window.setBounds(x + dx, ay + dy, w, h)
            }
            launch { snapshotFlow { slide.value }.collect { if (!hidden && lastRect.width > 0) place(lastRect.width, lastRect.height) } }
            val fw = (base0.width.value * scale).toInt()
            val fh = (base0.height.value * scale).toInt()
            // Preferred: the window keeps its full size and never moves; a shape trims it to the pill so
            // clicks around it fall through. Without shape support it shrinks to the pill instead.
            var shaped = true
            fun trim(px: IntSize?) {
                if (!shaped) return
                runCatching {
                    window.shape = px?.let {
                        val pw = it.width / k + margin * 2
                        val ph = it.height / k + margin * 2
                        val x = when (anchor) { 0 -> 0.0; 2 -> fw - pw; else -> (fw - pw) / 2 }
                        WindowMorph.pill = java.awt.Rectangle(window.x + x.toInt(), window.y, pw.toInt(), ph.toInt())
                        java.awt.geom.Rectangle2D.Double(x, 0.0, pw, ph)
                    }
                }.onFailure { shaped = false }
            }
            snapshotFlow<IntSize?> { if (editing || busy.value) null else if (hidden) IntSize.Zero else pillPx.value }.collectLatest { px ->
                if (px == null) {
                    trim(null)
                    place(fw, fh)
                    full = true
                    return@collectLatest
                }
                if (px == IntSize.Zero) { WindowMorph.pill = null; return@collectLatest }
                // Collapse already finished (busy=false); short grace period against hover flicker.
                if (full) delay(160)
                if (shaped) {
                    place(fw, fh)
                    trim(px)
                    if (shaped) { full = true; return@collectLatest }
                }
                full = false
                place((px.width / k).toInt() + margin * 2, (px.height / k).toInt() + margin * 2)
                WindowMorph.pill = window.bounds
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
                    } else if (hidden) {
                        HiddenTab(edge, tabIn.value) {
                            scope.launch {
                                tabIn.animateTo(0f, androidx.compose.animation.core.tween(160))
                                slide.snapTo(1f)
                                hidden = false
                                slide.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = 0.78f, stiffness = 320f))
                            }
                        }
                    } else {
                        Box(pillModifier.graphicsLayer {
                            val a = appear.value
                            alpha = a.coerceIn(0f, 1f)
                            if (!soft) {
                                scaleX = 0.72f + 0.28f * a
                                scaleY = scaleX
                                translationY = -14.dp.toPx() * (1f - a)
                            }
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(when (anchor) { 0 -> 0f; 2 -> 1f; else -> 0.5f }, 0f)
                        }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                HideHandle {
                                    scope.launch {
                                        edge = nearestEdge(window.bounds, bounds)
                                        slide.animateTo(1f, androidx.compose.animation.core.tween(320, easing = androidx.compose.animation.core.FastOutSlowInEasing))
                                        hidden = true
                                        val t = tabRect(edge, lastRect, bounds)
                                        runCatching { window.shape = null }
                                        window.setBounds(t.x, t.y, t.width, t.height)
                                        tabIn.snapTo(0f)
                                        tabIn.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.7f, stiffness = 420f))
                                    }
                                }
                                Pill(interactive = true, window = window, canOpen = full) { busy.value = it }
                            }
                        }
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
    if (settings.floatingVertical) return VerticalPill(interactive, window, canOpen, hoverOn, hover, progress, glass)
    val accent by animateColorAsState(if (glass) Color.White else rememberArtworkSeed(track?.artworkUrl) ?: MaterialTheme.colorScheme.primary, tween(450), label = "accent")
    val shape = RoundedCornerShape(30.dp)
    val b = settings.floatingButtons
    val anchor = settings.floatingAnchor

    val like: (@Composable () -> Unit)? = if (track != null && "like" in b) ({
        val liked = library.liked.any { it.key == track.key }
        PillButton(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (liked) accent else Color.White) { graph.toggleLike(track) }
    }) else null
    val dislike: (@Composable () -> Unit)? = if (track != null && "dislike" in b) ({ PillButton(Icons.Rounded.ThumbDown, Color.White) { graph.notInterested(track) } }) else null
    val volume: (@Composable () -> Unit)? = if ("volume" in b) ({ Volume(accent) }) else null
    val openBtn: (@Composable () -> Unit)? = if ("open" in b) ({ PillButton(Icons.AutoMirrored.Rounded.OpenInNew, Color.White) { MainWindow.show() } }) else null
    val extras = listOfNotNull(like, dislike, volume, openBtn)
    // both ways: track actions on the left, playback and volume on the right
    val leftExtras = when (anchor) {
        0 -> emptyList()
        2 -> extras.reversed()
        else -> listOfNotNull(openBtn, dislike, like)
    }
    val rightExtras = when (anchor) {
        0 -> extras
        2 -> emptyList()
        else -> listOfNotNull(volume)
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
                if ("prev" in b) PillButton(Icons.Rounded.SkipPrevious, Color.White, interactive, Icons.Rounded.FastRewind) { graph.player.previous() }
                PlayPauseButton(state.isPlaying, { graph.player.toggle() }, size = BTN, enabled = interactive, tint = accent, accent = accent)
                if ("next" in b) PillButton(Icons.Rounded.SkipNext, Color.White, interactive, Icons.Rounded.FastForward) { graph.player.next() }
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
private fun PillButton(icon: ImageVector, tint: Color, enabled: Boolean = true, glassIcon: ImageVector = icon, onClick: () -> Unit) {
    TransportButton(icon, null, onClick, size = BTN, tint = tint, glassIcon = glassIcon, enabled = enabled)
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

// Thin vertical bar: artwork with a progress ring, prev / play / next; hovering slides the extras out below.
@Composable
private fun VerticalPill(
    interactive: Boolean,
    window: java.awt.Window?,
    canOpen: Boolean,
    hoverOn: Boolean,
    hover: MutableInteractionSource,
    progress: State<Float>,
    glass: Boolean,
) {
    val graph = AppGraph.instance
    val state by graph.player.state.collectAsState()
    val settings by graph.settings.state.collectAsState()
    val library by graph.library.state.collectAsState()
    val track = state.current
    val b = settings.floatingButtons
    val accent by animateColorAsState(if (glass) Color.White else rememberArtworkSeed(track?.artworkUrl) ?: MaterialTheme.colorScheme.primary, tween(450), label = "accent")
    val shape = RoundedCornerShape(30.dp)
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
        Layout(
            content = {
                Column(Modifier.layoutId("core").padding(vertical = 8.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(44.dp).clickable(enabled = interactive) { MainWindow.show() }, contentAlignment = Alignment.Center) {
                        Crossfade(track?.artworkUrl, Modifier.size(36.dp), tween(350), label = "art") { url ->
                            Artwork(url, Modifier.size(36.dp), CircleShape, Icons.Rounded.MusicNote)
                        }
                        // playback progress as a ring around the cover
                        androidx.compose.foundation.Canvas(Modifier.size(44.dp)) {
                            val sw = 2.5.dp.toPx()
                            drawArc(Color.White.copy(alpha = 0.18f), 0f, 360f, false, style = androidx.compose.ui.graphics.drawscope.Stroke(sw))
                            drawArc(accent, -90f, 360f * state.progress, false, style = androidx.compose.ui.graphics.drawscope.Stroke(sw, cap = StrokeCap.Round))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    if ("prev" in b) PillButton(Icons.Rounded.SkipPrevious, Color.White, interactive, Icons.Rounded.FastRewind) { graph.player.previous() }
                    PlayPauseButton(state.isPlaying, { graph.player.toggle() }, size = BTN, enabled = interactive, tint = accent, accent = accent)
                    if ("next" in b) PillButton(Icons.Rounded.SkipNext, Color.White, interactive, Icons.Rounded.FastForward) { graph.player.next() }
                }
                Column(Modifier.layoutId("more").padding(bottom = 8.dp, start = 6.dp, end = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.padding(vertical = 4.dp).size(20.dp, 1.dp).background(Color.White.copy(alpha = 0.2f)))
                    if (track != null && "like" in b) {
                        val liked = library.liked.any { it.key == track.key }
                        PillButton(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (liked) accent else Color.White) { graph.toggleLike(track) }
                    }
                    if (track != null && "dislike" in b) PillButton(Icons.Rounded.ThumbDown, Color.White) { graph.notInterested(track) }
                    if ("volume" in b) {
                        PillButton(if (settings.volume <= 0.01f) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp, Color.White) {
                            graph.player.setVolume(if (settings.volume > 0.01f) 0f else 0.6f)
                        }
                    }
                    if ("open" in b) PillButton(Icons.AutoMirrored.Rounded.OpenInNew, Color.White) { MainWindow.show() }
                }
            },
        ) { ms, _ ->
            val loose = Constraints()
            val core = ms.first { it.layoutId == "core" }.measure(loose)
            val more = ms.first { it.layoutId == "more" }.measure(loose)
            val w = maxOf(core.width, more.width)
            val p = if (canOpen) progress.value.coerceIn(0f, 1f) else 0f
            layout(w, core.height + (more.height * p).roundToInt()) {
                core.place((w - core.width) / 2, 0)
                if (p > 0f) more.placeWithLayer((w - more.width) / 2, core.height) { alpha = p * p * (3 - 2 * p) }
            }
        }
    }
}

internal enum class Edge { TOP, BOTTOM, LEFT, RIGHT }

private const val TAB_LONG = 72
private const val TAB_SHORT = 20

private fun nearestEdge(r: java.awt.Rectangle, screen: java.awt.Rectangle): Edge {
    val d = mapOf(
        Edge.TOP to r.y - screen.y,
        Edge.BOTTOM to screen.y + screen.height - (r.y + r.height),
        Edge.LEFT to r.x - screen.x,
        Edge.RIGHT to screen.x + screen.width - (r.x + r.width),
    )
    return d.minBy { it.value }.key
}

// How far the window travels (at progress p) to leave the screen through [edge].
private fun slideOffset(edge: Edge, p: Float, r: java.awt.Rectangle, screen: java.awt.Rectangle): Pair<Int, Int> {
    if (p <= 0f || r.width == 0) return 0 to 0
    val k = p.coerceIn(0f, 1f)
    return when (edge) {
        Edge.TOP -> 0 to -((r.y - screen.y + r.height + 8) * k).roundToInt()
        Edge.BOTTOM -> 0 to ((screen.y + screen.height - r.y + 8) * k).roundToInt()
        Edge.LEFT -> -((r.x - screen.x + r.width + 8) * k).roundToInt() to 0
        Edge.RIGHT -> ((screen.x + screen.width - r.x + 8) * k).roundToInt() to 0
    }
}

private fun tabRect(edge: Edge, r: java.awt.Rectangle, screen: java.awt.Rectangle): java.awt.Rectangle = when (edge) {
    Edge.TOP -> java.awt.Rectangle(r.x + r.width / 2 - TAB_LONG / 2, screen.y, TAB_LONG, TAB_SHORT)
    Edge.BOTTOM -> java.awt.Rectangle(r.x + r.width / 2 - TAB_LONG / 2, screen.y + screen.height - TAB_SHORT, TAB_LONG, TAB_SHORT)
    Edge.LEFT -> java.awt.Rectangle(screen.x, r.y + r.height / 2 - TAB_LONG / 2, TAB_SHORT, TAB_LONG)
    Edge.RIGHT -> java.awt.Rectangle(screen.x + screen.width - TAB_SHORT, r.y + r.height / 2 - TAB_LONG / 2, TAB_SHORT, TAB_LONG)
}

// Grab bar above the pill: click to tuck the player away.
@Composable
private fun HideHandle(onHide: () -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val a by animateFloatAsState(if (hovered) 0.95f else 0.45f, tween(180), label = "handle")
    Box(
        Modifier.padding(bottom = 3.dp).size(56.dp, 12.dp).clip(RoundedCornerShape(6.dp)).hoverable(hover).clickable(onClick = onHide),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(if (hovered) 40.dp else 32.dp, 5.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = a)))
    }
}

// What stays on screen while the player is tucked away; slides in from the edge, click to bring the player back.
@Composable
private fun HiddenTab(edge: Edge, progress: Float, onShow: () -> Unit) {
    val vertical = edge == Edge.LEFT || edge == Edge.RIGHT
    val shape = when (edge) {
        Edge.TOP -> RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp)
        Edge.BOTTOM -> RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)
        Edge.LEFT -> RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp)
        Edge.RIGHT -> RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp)
    }
    Box(
        Modifier.fillMaxSize().graphicsLayer {
            val off = (1f - progress)
            when (edge) {
                Edge.TOP -> translationY = -size.height * off
                Edge.BOTTOM -> translationY = size.height * off
                Edge.LEFT -> translationX = -size.width * off
                Edge.RIGHT -> translationX = size.width * off
            }
        }.clip(shape).background(Color(0xE6141218)).clickable(onClick = onShow),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(if (vertical) 4.dp else 30.dp, if (vertical) 30.dp else 4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.8f)))
    }
}
