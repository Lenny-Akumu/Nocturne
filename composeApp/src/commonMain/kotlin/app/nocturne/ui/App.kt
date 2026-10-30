package app.nocturne.ui

import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.animation.animateColorAsState
import app.nocturne.i18n.t
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import app.nocturne.ui.kit.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import app.nocturne.ui.kit.Snackbar
import androidx.compose.material3.Text
import app.nocturne.ui.kit.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.nocturne.AppGraph
import app.nocturne.ui.components.TrackMenuHost
import app.nocturne.ui.player.FullPlayer
import app.nocturne.ui.player.MiniPlayer
import app.nocturne.ui.screens.CollectionScreen
import app.nocturne.ui.screens.HomeScreen
import app.nocturne.ui.screens.LibraryScreen
import app.nocturne.ui.screens.SearchScreen
import app.nocturne.ui.screens.SettingsPage
import app.nocturne.ui.screens.SettingsScreen
import app.nocturne.ui.kit.LocalDesignStyle
import app.nocturne.ui.theme.LocalBackdrop
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.ui.draw.drawBehind
import app.nocturne.ui.theme.NocturneTheme

@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)

expect val supportsWebLogin: Boolean

@Composable
expect fun WebLoginDialog(
    title: String,
    url: String,
    cookieUrls: List<String>,
    cookieKey: String,
    desktopMode: Boolean,
    onCookies: (String) -> Unit,
    onDismiss: () -> Unit,
)

expect fun clearWebLogins()

@Composable
expect fun PairingCard()

internal data class Tab(val screen: Screen, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

internal val tabs = listOf(
    Tab(Screen.Home, "Home", Icons.Outlined.Home, Icons.Rounded.Home),
    Tab(Screen.Search, "Search", Icons.Outlined.Search, Icons.Rounded.Search),
    Tab(Screen.Library, "Library", Icons.Outlined.LibraryMusic, Icons.Rounded.LibraryMusic),
)

internal val settingsTab = Tab(Screen.Settings, "Settings", Icons.Outlined.Settings, Icons.Rounded.Settings)

/** Desktop without a system title bar: the app draws its own caption strip on top. */
object WindowChrome {
    var bar: (@Composable () -> Unit)? by androidx.compose.runtime.mutableStateOf(null)
    val height = 34.dp
    /** Desktop: what happened when the window effect was applied (shown in Settings). */
    var effectStatus: String? by androidx.compose.runtime.mutableStateOf(null)
}

@Composable
fun App(backdrop: Boolean = false) {
    val graph = AppGraph.instance
    val settings by graph.settings.state.collectAsState()
    val wallpaper by app.nocturne.data.AppBackground.state.collectAsState()
    // a custom background makes the chrome translucent the same way a window backdrop does
    NocturneTheme(settings, seedOverride = app.nocturne.ui.style.material.rememberMaterialSeed(settings), backdrop = backdrop || wallpaper != null) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            wallpaper?.let { bytes ->
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    AnimatedImage(bytes, Modifier.fillMaxSize().then(if (settings.backgroundBlur > 0.5f) Modifier.blur(settings.backgroundBlur.dp) else Modifier))
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.45f)))
                }
            }
            val base = androidx.compose.ui.platform.LocalDensity.current
            val landscapePhone = app.nocturne.platformKind != app.nocturne.PlatformKind.DESKTOP && maxHeight < 480.dp && maxWidth > maxHeight
            val k = settings.uiScale.coerceIn(0.5f, 2f) * if (landscapePhone) 0.82f else 1f
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(base.density * k, base.fontScale),
            ) {
                // Root haze state so any overlay can blur the content (Liquid Glass only).
                val haze = remember { dev.chrisbanes.haze.HazeState() }
                CompositionLocalProvider(LocalGlass provides haze.takeIf { settings.designStyle == app.nocturne.data.DesignStyle.GLASS }) {
                    // phones/tablets with a keyboard: the root holds focus so player keys always arrive
                    val keyFocus = remember { androidx.compose.ui.focus.FocusRequester() }
                    if (app.nocturne.platformKind != app.nocturne.PlatformKind.DESKTOP) androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { keyFocus.requestFocus() } }
                    val tilt = rememberSixSevenTilt()
                    Box(
                        Modifier.fillMaxSize().graphicsLayer { rotationZ = tilt; val k = 1f - kotlin.math.abs(tilt) * 0.012f; scaleX = k; scaleY = k }.then(
                            if (app.nocturne.platformKind == app.nocturne.PlatformKind.DESKTOP) Modifier
                            else Modifier.focusRequester(keyFocus).focusable().onKeyEvent { handleAppKey(it) },
                        ),
                    ) {
                        TrackMenuHost { AppScaffold() }
                        // window buttons float over the content, like a Windows 11 app
                        WindowChrome.bar?.invoke()
                        ScareOverlay()
                        UpdateBanner(Modifier.align(Alignment.TopCenter))
                        WhatsNewDialog()
                        StartupIntro()
                        // seasonal launcher icon goes back to the regular one once the season is over
                        androidx.compose.runtime.LaunchedEffect(Unit) {
                            val s0 = graph.settings.value
                            if (s0.halloweenIcon && !isHalloween()) {
                                graph.settings.update { it.copy(halloweenIcon = false) }
                                applyAppIcon(s0.iconBackground, s0.iconPalette, false)
                            }
                        }
                        if (Demo.flash) Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.White))
                        // Opened by a nocturne://join link.
                        val partySheet by graph.party.sheet.collectAsState()
                        if (partySheet) app.nocturne.ui.player.ConnectSheet(onDismiss = { graph.party.sheet.value = false }, onCarMode = { graph.navigator.playerExpanded = true })
                        PairLinkDialog()
                    }
                }
            }
        }
    }
}

@Composable
private fun AppScaffold() {
    val graph = AppGraph.instance
    val nav = graph.navigator
    val player by graph.player.state.collectAsState()
    val backdrop = LocalBackdrop.current
    val bg = if (backdrop) Color.Transparent else MaterialTheme.colorScheme.background

    PlatformBackHandler(enabled = nav.canGoBack) { nav.back() }

    val aurora = LocalDesignStyle.current == app.nocturne.data.DesignStyle.WINDOWS
    BoxWithConstraints(Modifier.fillMaxSize().background(bg)) {
        if (aurora && !backdrop) AuroraGlow(Modifier.fillMaxSize())
        // desktop without a title bar: the top strip drags the window, keep content clear of it
        val caption = if (WindowChrome.bar != null) WindowChrome.height else 0.dp
        val wide = maxWidth >= 720.dp
        val hasPlayer = player.current != null
        if (wide && aurora) {
            GlassSource(Modifier.fillMaxSize()) { Row(Modifier.fillMaxSize()) {
                AuroraDock(nav, top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + if (caption > 0.dp) caption + 2.dp else 12.dp)
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    ScreenHost(PaddingValues(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + caption + 8.dp, bottom = if (hasPlayer) 112.dp else 16.dp))
                }
            } }
            AuroraIsland(onExpand = { nav.playerExpanded = true }, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 102.dp, end = 12.dp, bottom = 12.dp))
            RemoteStrip(Modifier.align(Alignment.BottomCenter).padding(start = 102.dp, bottom = 12.dp).navigationBarsPadding().widthIn(max = 560.dp))
        } else if (wide) {
            GlassSource(Modifier.fillMaxSize()) { Row(Modifier.fillMaxSize()) {
                NavigationRail(
                    containerColor = if (backdrop) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxHeight().statusBarsPadding(),
                    header = {
                        NocturneLogo(Modifier.padding(top = 12.dp + caption, bottom = 16.dp).size(44.dp).clip(androidx.compose.foundation.shape.CircleShape).clickable { LogoTaps.tap() })
                    },
                ) {
                    tabs.forEach { RailItem(it, nav) }
                    Spacer(Modifier.weight(1f))
                    RailItem(settingsTab, nav)
                    Spacer(Modifier.height(16.dp))
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    val pad = PaddingValues(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + caption + 8.dp, bottom = if (hasPlayer) 100.dp else 16.dp)
                    ScreenHost(pad)
                }
            } }
            MiniPlayer(onExpand = { nav.playerExpanded = true }, wide = true, modifier = Modifier.align(Alignment.BottomCenter).padding(start = 80.dp).navigationBarsPadding())
            // Only while nothing plays here, so it never overlaps the mini player.
            RemoteStrip(Modifier.align(Alignment.BottomCenter).padding(start = 80.dp, bottom = 12.dp).navigationBarsPadding().widthIn(max = 560.dp))
        } else {
            val glass = LocalGlass.current != null
            val bottomPad = if (glass) WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + (if (hasPlayer) 146.dp else 82.dp)
                else if (aurora) WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + (if (hasPlayer) 168.dp else 90.dp)
                else if (hasPlayer) 160.dp else 90.dp
            // Glass: observe scrolling so the tab bar minimizes / expands (iOS 26).
            GlassSource(Modifier.fillMaxSize().then(if (glass) Modifier.nestedScroll(app.nocturne.ui.style.glass.GlassChrome.scroll) else Modifier)) {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) {
                        ScreenHost(PaddingValues(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + caption, bottom = bottomPad))
                    }
                }
            }
            if (glass) {
                app.nocturne.ui.style.glass.GlassInlineBar(Modifier.align(Alignment.TopCenter))
                app.nocturne.ui.style.glass.GlassBottomBar(nav, onExpand = { nav.playerExpanded = true }, modifier = Modifier.align(Alignment.BottomCenter))
            } else if (aurora) Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()) {
                RemoteStrip()
                AuroraPhoneBar(nav, onExpand = { nav.playerExpanded = true })
            } else Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                RemoteStrip()
                MiniPlayer(onExpand = { nav.playerExpanded = true }, wide = false, modifier = Modifier.demo("mini") { nav.playerExpanded = true })
                if (LocalDesignStyle.current == app.nocturne.data.DesignStyle.MATERIAL) app.nocturne.ui.style.material.MaterialNavBar(nav)
                else NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = if (backdrop) 0.7f else 0.97f)) {
                    tabs.forEach { tab ->
                        val selected = nav.currentTab == tab.screen
                        NavigationBarItem(
                            selected = selected,
                            onClick = { nav.selectTab(tab.screen) },
                            icon = { Icon(if (selected) tab.selectedIcon else tab.icon, tab.label) },
                            label = { Text(t(tab.label)) },
                        )
                    }
                }
            }
        }

        player.error?.let { msg ->
            Snackbar(
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(12.dp),
                action = { TextButton(onClick = graph.player::dismissError) { Text(t("OK")) } },
            ) { Text(msg, maxLines = 2) }
        }

        AnimatedVisibility(
            visible = nav.playerExpanded && hasPlayer,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            // Own glass layer above the screens, so sheets opened from the player blur the player.
            GlassSource(Modifier.fillMaxSize(), zIndex = 1f) { FullPlayer(onCollapse = { nav.playerExpanded = false }) }
        }
    }
}

@Composable
private fun RailItem(tab: Tab, nav: Navigator) {
    val selected = nav.currentTab == tab.screen
    if (LocalDesignStyle.current == app.nocturne.data.DesignStyle.WINDOWS) {
        // Fluent NavigationView item: subtle fill on hover/selection, 3dp accent pill on the left.
        val cs = MaterialTheme.colorScheme
        val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        val hovered by src.collectIsHoveredAsState()
        val dark = app.nocturne.ui.theme.LocalDarkTheme.current
        val fill = when {
            selected || hovered -> if (dark) Color.White.copy(alpha = if (selected) 0.061f else 0.042f) else Color.Black.copy(alpha = if (selected) 0.037f else 0.024f)
            else -> Color.Transparent
        }
        val pill by androidx.compose.animation.core.animateDpAsState(if (selected) 16.dp else 0.dp, label = "pill")
        val accent = cs.primary
        Box(
            Modifier.padding(vertical = 4.dp).size(56.dp, 40.dp).clip(RoundedCornerShape(4.dp)).background(fill)
                .drawBehind {
                    if (pill > 0.dp) {
                        val w = 3.dp.toPx()
                        val h = pill.toPx()
                        drawRoundRect(accent, androidx.compose.ui.geometry.Offset(0f, (size.height - h) / 2), androidx.compose.ui.geometry.Size(w, h), androidx.compose.ui.geometry.CornerRadius(w / 2))
                    }
                }
                .hoverable(src)
                .clickable(interactionSource = src, indication = null) { nav.selectTab(tab.screen) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (selected) tab.selectedIcon else tab.icon, tab.label, Modifier.size(20.dp), tint = cs.onSurface)
        }
        return
    }
    NavigationRailItem(
        selected = selected,
        onClick = { nav.selectTab(tab.screen) },
        icon = { Icon(if (selected) tab.selectedIcon else tab.icon, tab.label) },
        alwaysShowLabel = false,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun ScreenHost(padding: PaddingValues) {
    val nav = AppGraph.instance.navigator
    AnimatedContent(
        targetState = nav.current,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "screen",
    ) { screen ->
        Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            val isTab = screen == Screen.Home || screen == Screen.Search || screen == Screen.Library
            // Material home has its own search bar on top.
            val ownTop = screen == Screen.Home && LocalDesignStyle.current == app.nocturne.data.DesignStyle.MATERIAL
            // Glass: home brings its own scrolling large title; other tabs get a large title header.
            val glassStyle = LocalDesignStyle.current == app.nocturne.data.DesignStyle.GLASS
            val inner = if (isTab && glassStyle) {
                if (screen != Screen.Home) app.nocturne.ui.style.glass.GlassLargeTitle(if (screen == Screen.Search) "Search" else "Library", padding) { nav.push(Screen.Settings) }
                if (screen == Screen.Home) padding else PaddingValues(bottom = padding.calculateBottomPadding())
            } else if (isTab && !ownTop) {
                TopBar(padding)
                PaddingValues(bottom = padding.calculateBottomPadding())
            } else padding
            Box(Modifier.weight(1f)) {
                when (screen) {
                    Screen.Home -> HomeScreen(inner)
                    Screen.Search -> SearchScreen(inner)
                    Screen.Library -> LibraryScreen(inner)
                    Screen.Settings -> SettingsScreen(inner)
                    is Screen.Collection -> CollectionScreen(screen.ref, inner)
                    is Screen.SettingsPage -> SettingsPage(screen.page, inner)
                }
            }
        }
        if (screen == Screen.Home) SeasonalLayer(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun TopBar(padding: PaddingValues) {
    val nav = AppGraph.instance.navigator
    androidx.compose.foundation.layout.BoxWithConstraints {
    // wide layout already shows the logo on the rail
    val showLogo = maxWidth < 720.dp
    Row(
        Modifier.fillMaxWidth().padding(top = padding.calculateTopPadding()).padding(start = 18.dp, end = 6.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp)).clickable { LogoTaps.tap() }.padding(end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showLogo) {
                    NocturneLogo(Modifier.size(30.dp))
                    Spacer(Modifier.width(10.dp))
                }
                Text(t("Nocturne"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
        IconButton(onClick = { nav.push(Screen.Settings) }) { Icon(Icons.Rounded.Settings, "Settings") }
    }
    }
}
