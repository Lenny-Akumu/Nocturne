package app.nocturne.ui.screens

import app.nocturne.ui.demo
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Web
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.NotificationsActive
import app.nocturne.model.isOnline
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.PictureInPicture
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.LinearScale
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import kotlin.math.roundToInt
import app.nocturne.ui.FloatingEdit
import app.nocturne.i18n.t
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Explicit
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.SmartButton
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Window
import androidx.compose.material.icons.rounded.Speed
import app.nocturne.ui.kit.AlertDialog
import androidx.compose.material3.HorizontalDivider
import app.nocturne.ui.kit.Icon
import androidx.compose.material3.IconButton
import app.nocturne.ui.kit.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import app.nocturne.ui.kit.OutlinedTextField
import app.nocturne.ui.kit.RadioButton
import app.nocturne.ui.kit.Slider
import app.nocturne.ui.kit.Switch
import androidx.compose.material3.Text
import app.nocturne.ui.kit.TextButton
import app.nocturne.ui.kit.GroupedColumn
import app.nocturne.ui.kit.OptionHeader
import app.nocturne.ui.kit.LocalDesignStyle
import app.nocturne.ui.kit.optionRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.nocturne.AppGraph
import app.nocturne.PlatformKind
import app.nocturne.data.DesignStyle
import app.nocturne.data.LyricsAlign
import app.nocturne.data.PlayerBackground
import app.nocturne.data.Settings
import app.nocturne.data.ThemeMode
import app.nocturne.data.WindowBackdrop
import app.nocturne.model.AudioQuality
import app.nocturne.model.Source
import app.nocturne.platformKind
import app.nocturne.ui.Screen
import app.nocturne.ui.components.SourceBadge
import app.nocturne.ui.components.DiscordPreview
import app.nocturne.DiscordPresence
import app.nocturne.ui.theme.AccentPresets
import kotlinx.coroutines.launch

@Composable
expect fun ShortcutSettings()

enum class SettingsCategory(val title: String, val summary: String, val icon: ImageVector) {
    ACCOUNTS("Accounts", "YouTube Music, Spotify, SoundCloud sign-in", Icons.Rounded.AccountCircle),
    LANGUAGE("Language", "App language", Icons.Rounded.Language),
    DEVICES("Devices & sync", "Your phone, PC and iPhone in sync", Icons.Rounded.Devices),
    APPEARANCE("Appearance", "Theme, colors, player style", Icons.Rounded.Palette),
    CONTENT("Content & sources", "YouTube Music, Spotify, SoundCloud, region", Icons.Rounded.Public),
    PLAYER("Player & audio", "Quality, autoplay, queue", Icons.Rounded.PlayCircle),
    LYRICS("Lyrics", "Synced lyrics display", Icons.Rounded.Lyrics),
    FLOATING("Floating player", "Always-on-top mini player", Icons.Rounded.PictureInPicture),
    SHORTCUTS("Keyboard shortcuts", "Customize hotkeys", Icons.Rounded.Keyboard),
    DISCORD("Discord", "Rich Presence — show what you're listening to", Icons.Rounded.SportsEsports),
    SPOTIFY("Spotify", "Spotify app (advanced)", Icons.Rounded.Key),
    STORAGE("Storage", "Cache and data", Icons.Rounded.Storage),
    PRIVACY("Privacy", "History and search", Icons.Rounded.Security),
    ABOUT("About", "Version and info", Icons.Rounded.Info),
}

@Composable
fun SettingsScreen(contentPadding: PaddingValues) {
    val nav = AppGraph.instance.navigator
    val ios = LocalDesignStyle.current == DesignStyle.GLASS
    val fluent = LocalDesignStyle.current == DesignStyle.WINDOWS
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item {
            Text(t("Settings"), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 18.dp, top = 8.dp, bottom = 12.dp))
        }
        val desktopOnly = setOf(SettingsCategory.SHORTCUTS, SettingsCategory.DISCORD, SettingsCategory.FLOATING)
        val visible = SettingsCategory.entries.filter { it !in desktopOnly || platformKind == PlatformKind.DESKTOP }
        val groups = listOf(
            visible.filter { it in listOf(SettingsCategory.ACCOUNTS, SettingsCategory.LANGUAGE, SettingsCategory.DEVICES, SettingsCategory.APPEARANCE, SettingsCategory.CONTENT, SettingsCategory.PLAYER, SettingsCategory.LYRICS) },
            visible.filter { it in desktopOnly },
            visible.filter { it in listOf(SettingsCategory.SPOTIFY, SettingsCategory.STORAGE, SettingsCategory.PRIVACY, SettingsCategory.ABOUT) },
        ).filter { it.isNotEmpty() }
        items(groups) { group ->
            Column(
                Modifier.padding(horizontal = if (ios) 16.dp else 14.dp, vertical = 6.dp).clip(RoundedCornerShape(if (ios) 22.dp else if (fluent) 8.dp else 26.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .then(if (fluent) Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)) else Modifier),
            ) {
                group.forEachIndexed { i, c ->
                    ListItem(
                        headlineContent = { Text(t(c.title), fontWeight = if (ios) FontWeight.Normal else FontWeight.Medium) },
                        supportingContent = { Text(t(c.summary), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingContent = {
                            if (ios) IosBadge(c.icon, categoryColor(c))
                            else Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                                Icon(c.icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
                            }
                        },
                        trailingContent = { Chevron() },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.demo("settings_" + c.name) { nav.push(Screen.SettingsPage(c)) }.clickable { nav.push(Screen.SettingsPage(c)) }.padding(horizontal = if (ios) 0.dp else 4.dp, vertical = 2.dp),
                    )
                    if (i < group.lastIndex) {
                        if (ios) HorizontalDivider(Modifier.padding(start = 61.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                        else HorizontalDivider(Modifier.padding(start = 76.dp, end = 20.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsPage(category: SettingsCategory, contentPadding: PaddingValues) {
    val graph = AppGraph.instance
    val s by graph.settings.state.collectAsState()
    val set: ((Settings) -> Settings) -> Unit = { graph.settings.update(it) }
    val desktop = platformKind == PlatformKind.DESKTOP

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item {
            Row(Modifier.padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { graph.navigator.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                Text(t(category.title), style = MaterialTheme.typography.headlineSmall)
            }
        }
        item {
            val body: @Composable () -> Unit = {
                when (category) {
                    SettingsCategory.ACCOUNTS -> AccountsSettings()
                    SettingsCategory.LANGUAGE -> {
                        // native names, so anyone can find their language whatever the current one is
                        app.nocturne.i18n.I18n.languages.forEach { (code, name) ->
                            val label = if (code == "system") t(name) else name
                            Row(
                                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).selectable(s.uiLanguage == code) { set { it.copy(uiLanguage = code) } }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = s.uiLanguage == code, onClick = null)
                                Spacer(Modifier.size(14.dp))
                                Text(label, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                    SettingsCategory.DEVICES -> {
                        app.nocturne.ui.DevicesSettings()
                        Header("This device")
                        TextPref("Device name", app.nocturne.deviceName(), Icons.Rounded.Devices, s.deviceName) { v -> set { it.copy(deviceName = v.trim().take(40)) } }
                        Toggle("Allow remote control", "Your other devices can play, pause and take over playback here", Icons.Rounded.SettingsRemote, s.remoteControl) { v -> set { it.copy(remoteControl = v) } }
                        Header("Sync")
                        Toggle("Liked songs", null, Icons.Rounded.FavoriteBorder, s.syncLikes) { v -> set { it.copy(syncLikes = v) } }
                        Toggle("Playlists and saved albums", null, Icons.Rounded.LibraryMusic, s.syncPlaylists) { v -> set { it.copy(syncPlaylists = v) } }
                        Toggle("Listening history and stats", "Recommendations learn from every device", Icons.Rounded.History, s.syncHistory) { v -> set { it.copy(syncHistory = v) } }
                    }
                    SettingsCategory.SHORTCUTS -> ShortcutSettings()
                    SettingsCategory.FLOATING -> {
                        Toggle("Floating mini player", "Small always-on-top player on your screen", Icons.Rounded.PictureInPicture, s.floatingPlayer) { v -> set { it.copy(floatingPlayer = v) } }
                        SliderPref("Size", "${(s.floatingScale * 100).roundToInt()}%", s.floatingScale, 0.7f..1.6f, 8) { v -> set { it.copy(floatingScale = (v * 10).roundToInt() / 10f) } }
                        Choice("Style", Icons.Rounded.Palette, s.floatingStyle, listOf(0, 1), { if (it == 0) "Artwork colors" else "Subtle glass" }) { v -> set { it.copy(floatingStyle = v) } }
                        Choice("Orientation", Icons.Rounded.UnfoldMore, s.floatingVertical, listOf(false, true), { if (it) "Vertical" else "Horizontal" }) { v -> set { it.copy(floatingVertical = v) } }
                        if (!s.floatingVertical) Choice("Expands to", Icons.Rounded.UnfoldMore, s.floatingAnchor, listOf(0, 1, 2), { when (it) { 0 -> "Right"; 2 -> "Left"; else -> "Both sides" } }) { v -> set { it.copy(floatingAnchor = v) } }
                        Header("Position")
                        Action("Edit position", if (FloatingEdit.editing) "Drag the player, then press Done" else "Move the player anywhere on the screen", Icons.Rounded.OpenWith) {
                            set { it.copy(floatingPlayer = true) }
                            FloatingEdit.editing = true
                        }
                        Action("Reset position", "Top center of the screen", Icons.Rounded.Restore) { set { it.copy(floatingX = -1, floatingY = -1) } }
                        Header("Buttons")
                        listOf(
                            Triple("prev", "Previous", Icons.Rounded.SkipPrevious),
                            Triple("next", "Next", Icons.Rounded.SkipNext),
                            Triple("like", "Like", Icons.Rounded.FavoriteBorder),
                            Triple("dislike", "Not interested", Icons.Rounded.ThumbDown),
                            Triple("volume", "Volume", Icons.AutoMirrored.Rounded.VolumeUp),
                            Triple("seek", "Timeline", Icons.Rounded.LinearScale),
                            Triple("open", "Open Nocturne", Icons.AutoMirrored.Rounded.OpenInNew),
                        ).forEach { (key, label, icon) ->
                            Toggle(label, null, icon, key in s.floatingButtons) { v ->
                                set { it.copy(floatingButtons = if (v) it.floatingButtons + key else it.floatingButtons - key) }
                            }
                        }
                    }
                    SettingsCategory.DISCORD -> {
                        val player by graph.player.state.collectAsState()
                        Toggle("Discord Rich Presence", DiscordPresence.status.ifBlank { null }, Icons.Rounded.SportsEsports, s.discordRpc) { v -> set { it.copy(discordRpc = v) } }
                        TextPref("Application ID", "Built-in Nocturne app", Icons.Rounded.Key, s.discordAppId) { v -> set { it.copy(discordAppId = v.filter(Char::isDigit)) } }
                        Header("Preview")
                        DiscordPreview(s, player.current)
                        Header("Text")
                        Text(
                            t("Placeholders: %s", "{title} {artist} {album} {source} {app}"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp),
                        )
                        TextPref("First line", "{title}", Icons.Rounded.TextFields, s.discordDetails) { v -> set { it.copy(discordDetails = v) } }
                        TextPref("Second line", "{artist}", Icons.Rounded.TextFields, s.discordState) { v -> set { it.copy(discordState = v) } }
                        TextPref("Artwork tooltip", "{album}", Icons.Rounded.Image, s.discordLargeText) { v -> set { it.copy(discordLargeText = v) } }
                        TextPref("Source icon tooltip", "{source}", Icons.Rounded.Image, s.discordSmallText) { v -> set { it.copy(discordSmallText = v) } }
                        TextPref("Button text", "Listen on {source}", Icons.Rounded.SmartButton, s.discordButton) { v -> set { it.copy(discordButton = v) } }
                        Header("Display")
                        Choice("Activity type", Icons.Rounded.SportsEsports, s.discordActivityType, listOf(2, 0), { if (it == 2) "Listening to" else "Playing" }) { v -> set { it.copy(discordActivityType = v) } }
                        Choice("Status in member list", Icons.Rounded.TextFields, s.discordStatusDisplay, listOf(0, 2, 1), { when (it) { 0 -> "App name"; 2 -> "First line"; else -> "Second line" } }) { v -> set { it.copy(discordStatusDisplay = v) } }
                        Toggle("Show artwork", null, Icons.Rounded.Image, s.discordShowArtwork) { v -> set { it.copy(discordShowArtwork = v) } }
                        Toggle("Show source icon", "YouTube Music, Spotify or SoundCloud logo", Icons.Rounded.Image, s.discordShowSource) { v -> set { it.copy(discordShowSource = v) } }
                        Toggle("Show time", "Elapsed and remaining time", Icons.Rounded.Timer, s.discordShowTime) { v -> set { it.copy(discordShowTime = v) } }
                        Toggle("Show button", "Opens the song for your friends", Icons.Rounded.SmartButton, s.discordShowButton) { v -> set { it.copy(discordShowButton = v) } }
                        Toggle("Show while paused", null, Icons.Rounded.Pause, s.discordShowPaused) { v -> set { it.copy(discordShowPaused = v) } }
                        Action("Reset to defaults", null, Icons.Rounded.Restore) {
                            val d = Settings()
                            set {
                                it.copy(
                                    discordDetails = d.discordDetails, discordState = d.discordState, discordLargeText = d.discordLargeText,
                                    discordSmallText = d.discordSmallText, discordButton = d.discordButton, discordActivityType = d.discordActivityType,
                                    discordStatusDisplay = d.discordStatusDisplay, discordShowArtwork = true, discordShowSource = true,
                                    discordShowTime = true, discordShowButton = true, discordShowPaused = false,
                                )
                            }
                        }
                    }
                    SettingsCategory.APPEARANCE -> {
                        Header("Language")
                        Choice("Interface language", Icons.Rounded.Language, s.uiLanguage, app.nocturne.i18n.I18n.languages.map { it.first }, { code ->
                            app.nocturne.i18n.I18n.languages.firstOrNull { it.first == code }?.second ?: code
                        }) { v -> set { it.copy(uiLanguage = v) } }
                        if (desktop) {
                            Toggle("Frameless window", "Window buttons in a frosted capsule (Windows) · restart to apply", Icons.Rounded.Web, s.customTitleBar) { v -> set { it.copy(customTitleBar = v) } }
                            SliderPref("Interface scale", "${(s.uiScale * 100).roundToInt()}%  ·  Ctrl + / Ctrl −", s.uiScale, 0.6f..1.8f, 11) { v -> set { it.copy(uiScale = (v * 10).roundToInt() / 10f) } }
                        }
                        Header("Theme")
                        Choice("Theme", Icons.Rounded.DarkMode, s.themeMode, ThemeMode.entries, { it.label }) { v -> set { it.copy(themeMode = v) } }
                        Toggle(
                            if (desktop) "Use Windows accent color" else "Dynamic color (Material You)",
                            if (desktop) "Follow your system accent" else "Colors from your wallpaper (Android 12+)",
                            Icons.Rounded.Wallpaper, s.dynamicColor,
                        ) { v -> set { it.copy(dynamicColor = v) } }
                        if (!s.dynamicColor) AccentPicker(s.accentColor) { v -> set { it.copy(accentColor = v) } }
                        Choice("Color style", Icons.Rounded.Palette, s.colorStyle, listOf("TonalSpot", "Vibrant", "Expressive", "Fidelity", "Content", "Rainbow", "FruitSalad", "Neutral", "Monochrome"), {
                            when (it) { "TonalSpot" -> "Balanced"; "FruitSalad" -> "Fruit salad"; else -> it }
                        }) { v -> set { it.copy(colorStyle = v) } }
                        Toggle("Fluent icons", "Cleaner icon set from Windows 11", Icons.Rounded.Palette, s.fluentIcons) { v -> set { it.copy(fluentIcons = v) } }
                        Header("Atmosphere")
                        Toggle("Startup animation", null, Icons.Rounded.AutoAwesome, s.startupIntro) { v -> set { it.copy(startupIntro = v) } }
                        Toggle("Startup sound", "A short chime when the app opens", Icons.Rounded.MusicNote, s.startupSound) { v -> set { it.copy(startupSound = v) } }
                        Toggle("Seasonal effects", "Falling leaves in autumn, snow in winter, fresh leaves in spring, sun rays in summer", Icons.Rounded.AcUnit, s.seasonalEffects) { v -> set { it.copy(seasonalEffects = v) } }
                        Choice("Font", Icons.Rounded.FormatSize, s.fontStyle, listOf("default", "serif", "mono", "cursive", "minecraft"), {
                            when (it) { "serif" -> "Serif"; "mono" -> "Monospace"; "cursive" -> "Handwritten"; "minecraft" -> "Minecraft"; else -> "Default" }
                        }) { v -> set { it.copy(fontStyle = v) } }
                        Choice("Design style", Icons.Rounded.BlurOn, s.designStyle, DesignStyle.entries, { it.label }) { v -> set { it.copy(designStyle = v) } }
                        Toggle("Pure black", "AMOLED-friendly dark theme", Icons.Rounded.Contrast, s.pureBlack) { v -> set { it.copy(pureBlack = v) } }
                        // desktop follows the monitor through vsync
                        if (!desktop) {
                            Choice("Refresh rate", Icons.Rounded.Speed, s.refreshRate, listOf(0, 60, 90, 120), { if (it == 0) "Auto" else "$it Hz" }) { v -> set { it.copy(refreshRate = v) } }
                        }
                        if (desktop) {
                            Choice("Window effect", Icons.Rounded.Window, s.windowBackdrop, WindowBackdrop.entries, { it.label }) { v -> set { it.copy(windowBackdrop = v) }; graph.toast(t("Restart Nocturne to apply the window effect")) }
                            app.nocturne.ui.WindowChrome.effectStatus?.let { st ->
                                Text(st, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 56.dp, end = 16.dp, bottom = 8.dp))
                            }
                        }
                        Header("App icon")
                        AppIconSettings(s, set)
                        Header("Player")
                        Choice("Player background", Icons.Rounded.Wallpaper, s.playerBackground, PlayerBackground.entries, { it.label }) { v -> set { it.copy(playerBackground = v) } }
                        if (s.playerBackground != PlayerBackground.SOLID) {
                            Toggle("Animated", "Slowly flowing colors over the background", Icons.Rounded.Animation, s.animatedBackground) { v -> set { it.copy(animatedBackground = v) } }
                        }
                        if (s.designStyle == app.nocturne.data.DesignStyle.MATERIAL) {
                            Choice("Seek bar", Icons.Rounded.Tune, s.seekBarStyle, app.nocturne.data.SeekBarStyle.entries, { it.label }) { v -> set { it.copy(seekBarStyle = v) } }
                        }
                        Toggle("Artwork-tinted player", "Theme the player with album colors", Icons.Rounded.Palette, s.playerArtworkTint) { v -> set { it.copy(playerArtworkTint = v) } }
                        Header("Lists")
                        Toggle("Source badges", "Show YouTube Music / Spotify / SoundCloud icons", Icons.Rounded.Tune, s.showSourceBadges) { v -> set { it.copy(showSourceBadges = v) } }
                        Toggle("Compact lists", "Denser song rows", Icons.Rounded.FormatSize, s.compactLists) { v -> set { it.copy(compactLists = v) } }
                    }
                    SettingsCategory.CONTENT -> {
                        Header("Sources")
                        Source.entries.filter { it.isOnline }.forEach { src ->
                            Toggle(src.label, sourceSummary(src, s), null, src in s.enabledSources, leading = { SourceBadge(src, size = 28.dp) }) { v ->
                                set { it.copy(enabledSources = if (v) it.enabledSources + src else (it.enabledSources - src).ifEmpty { setOf(Source.YOUTUBE_MUSIC) }) }
                            }
                        }
                        Choice("Default search source", Icons.Rounded.Search, s.defaultSearchSource, s.enabledSources.toList(), { it.label }) { v -> set { it.copy(defaultSearchSource = v) } }
                        Header("Region")
                        TextPref("Content language", "e.g. en, uk, de", Icons.Rounded.Language, s.contentLanguage) { v -> set { it.copy(contentLanguage = v.trim().lowercase().take(5)) } }
                        TextPref("Content country", "Two-letter code, e.g. US, UA", Icons.Rounded.Public, s.contentCountry) { v -> set { it.copy(contentCountry = v.trim().uppercase().take(2)) } }
                        Header("Filters")
                        Toggle("Hide explicit content", null, Icons.Rounded.Explicit, s.hideExplicit) { v -> set { it.copy(hideExplicit = v) } }
                        Toggle("Search suggestions", null, Icons.Rounded.Search, s.searchSuggestions) { v -> set { it.copy(searchSuggestions = v) } }
                    }
                    SettingsCategory.PLAYER -> {
                        Header("Audio")
                        Choice("Audio quality", Icons.Rounded.HighQuality, s.audioQuality, AudioQuality.entries, { it.label }) { v -> set { it.copy(audioQuality = v) } }
                        if (!desktop) {
                            Toggle("Skip silence", "Trim silent parts of tracks", Icons.Rounded.FastForward, s.skipSilence) { v -> set { it.copy(skipSilence = v) }; graph.player.applyAudioSettings() }
                        }
                        SliderPref("Seek step", "${s.seekStepSeconds}s", s.seekStepSeconds.toFloat(), 5f..30f, 4) { v -> set { it.copy(seekStepSeconds = v.toInt()) } }
                        Header("Queue")
                        Toggle("New release alerts", "Notify when artists you follow release something", Icons.Rounded.NotificationsActive, s.releaseAlerts) { v -> set { it.copy(releaseAlerts = v) } }
                        Toggle("Autoplay", "Keep playing similar songs when the queue ends", Icons.Rounded.Radio, s.autoplayRadio) { v -> set { it.copy(autoplayRadio = v) } }
                        Toggle("Remember queue", "Restore the last queue on launch", Icons.Rounded.Restore, s.persistQueue) { v -> set { it.copy(persistQueue = v) } }
                        Toggle("Resume playback on launch", null, Icons.Rounded.PlayCircle, s.resumeOnStart) { v -> set { it.copy(resumeOnStart = v) } }
                        if (!desktop) {
                            Toggle("Stop when app is swiped away", null, Icons.Rounded.Delete, s.stopOnTaskRemoved) { v -> set { it.copy(stopOnTaskRemoved = v) } }
                        }
                    }
                    SettingsCategory.LYRICS -> {
                        Toggle("Show lyrics button", "Synced lyrics from LRCLIB", Icons.Rounded.Lyrics, s.showLyrics) { v -> set { it.copy(showLyrics = v) } }
                        SliderPref("Text size", "${s.lyricsTextSize}sp", s.lyricsTextSize.toFloat(), 16f..40f, 11) { v -> set { it.copy(lyricsTextSize = v.toInt()) } }
                        Choice("Alignment", Icons.Rounded.FormatSize, s.lyricsAlign, LyricsAlign.entries, { it.label }) { v -> set { it.copy(lyricsAlign = v) } }
                    }
                    SettingsCategory.SPOTIFY -> {
                        Text(
                            t("Spotify uses the official Web API. Sign in under Accounts — Nocturne uses Spotify's PKCE sign-in, so only a Client ID is needed. To use your own Spotify app, create one at developer.spotify.com, add the redirect URIs nocturne://spotify-callback and http://127.0.0.1:43821/callback, and paste its Client ID here. Audio is matched and streamed from YouTube Music."),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
                        )
                        TextPref("Client ID", null, Icons.Rounded.Key, s.spotifyClientId) { v -> set { it.copy(spotifyClientId = v.trim()) } }
                        TextPref("Client secret (optional, search without sign-in)", null, Icons.Rounded.Security, s.spotifyClientSecret, secret = true) { v -> set { it.copy(spotifyClientSecret = v.trim()) } }
                        val uri = LocalUriHandler.current
                        Action("Open Spotify developer dashboard", null, Icons.AutoMirrored.Rounded.OpenInNew) { uri.openUri("https://developer.spotify.com/dashboard") }
                    }
                    SettingsCategory.STORAGE -> {
                        if (!desktop) {
                            SliderPref("Audio cache", t("%s MB (applies after restart)", s.cacheSizeMb), s.cacheSizeMb.toFloat(), 128f..4096f, 30) { v -> set { it.copy(cacheSizeMb = (v / 128).toInt() * 128) } }
                        }
                        val ctx = coil3.compose.LocalPlatformContext.current
                        val cacheScope = androidx.compose.runtime.rememberCoroutineScope()
                        var cacheInfo by remember { mutableStateOf<String?>(null) }
                        Action("Clear cache", cacheInfo ?: "Cached images, audio and videos", Icons.Rounded.CleaningServices) {
                            cacheInfo = t("Cleaning…")
                            cacheScope.launch {
                                val freed = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                                    val loader = coil3.SingletonImageLoader.get(ctx)
                                    val img = loader.diskCache?.size ?: 0L
                                    loader.diskCache?.clear()
                                    loader.memoryCache?.clear()
                                    img + runCatching { graph.engine.clearCache() }.getOrDefault(0L)
                                }
                                graph.clearMemoryCaches()
                                cacheInfo = t("Freed %s MB", (freed / (1024 * 1024)).toString())
                            }
                        }
                        if (desktop) {
                            TextPref("Music folders", "Your Music folder", Icons.Rounded.Storage, s.localFolders.joinToString("; ")) { v ->
                                set { it.copy(localFolders = v.split(";").map { f -> f.trim() }.filter { f -> f.isNotEmpty() }) }
                            }
                        }
                        Choice("Download quality", Icons.Rounded.HighQuality, s.downloadQuality, AudioQuality.entries, { it.label }) { v -> set { it.copy(downloadQuality = v) } }
                        val dlItems by graph.downloads.items.collectAsState()
                        Action("Downloads", t("%s songs · %s MB", dlItems.size, (dlItems.values.sumOf { it.bytes } / (1024 * 1024)).toString()), Icons.Rounded.Storage) {}
                        if (desktop) Locations(dlItems.size)
                        Action("Clear listening history", null, Icons.Rounded.History) { graph.library.clearHistory() }
                        Action("Clear search history", null, Icons.Rounded.Search) { graph.library.clearSearchHistory() }
                        Action("Reset all settings", null, Icons.Rounded.Restore) { graph.settings.reset() }
                    }
                    SettingsCategory.PRIVACY -> {
                        Toggle("Pause listening history", null, Icons.Rounded.History, s.pauseHistory) { v -> set { it.copy(pauseHistory = v) } }
                        Toggle("Pause search history", null, Icons.Rounded.Search, s.pauseSearchHistory) { v -> set { it.copy(pauseSearchHistory = v) } }
                        val lib by graph.library.state.collectAsState()
                        Action("Reset “Not interested”", t("%s hidden songs", lib.notInterested.size), Icons.Rounded.Restore) { graph.library.clearNotInterested() }
                    }
                    SettingsCategory.ABOUT -> {
                        val uri = LocalUriHandler.current
                        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            app.nocturne.ui.NocturneLogo(Modifier.size(96.dp))
                            Spacer(Modifier.height(12.dp))
                            Text(t("Nocturne"), style = MaterialTheme.typography.headlineSmall)
                            Text(t("Version %s", app.nocturne.BuildInfo.VERSION), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Source.entries.filter { it.isOnline }.forEach { SourceBadge(it, size = 22.dp) } }
                        }
                        Credits()
                        val scope = androidx.compose.runtime.rememberCoroutineScope()
                        var checkResult by remember { mutableStateOf<String?>(null) }
                        Action("Check for updates", checkResult ?: "Look for a newer version on GitHub", Icons.Rounded.SystemUpdate) {
                            checkResult = "Checking…"
                            scope.launch {
                                checkResult = runCatching { graph.updater.check(manual = true) }
                                    .fold({ if (it == null) "You're on the latest version" else t("Version %s is available", it.version) }, { t("Check failed: %s", it.message) })
                            }
                        }
                        Toggle("Check for updates automatically", null, Icons.Rounded.Restore, s.autoCheckUpdates) { v -> set { it.copy(autoCheckUpdates = v) } }
                        Action("Source code", "github.com/lenny-akumu/nocturne", Icons.Rounded.Code) { uri.openUri("https://github.com/lenny-akumu/nocturne") }
                        Action("Lyrics by LRCLIB", "lrclib.net", Icons.Rounded.Lyrics) { uri.openUri("https://lrclib.net") }
                        Action("Stream extraction by NewPipe Extractor", null, Icons.Rounded.Code) { uri.openUri("https://github.com/TeamNewPipe/NewPipeExtractor") }
                    }
                }
            }
            GroupedColumn(Modifier.padding(top = 4.dp, bottom = 16.dp), segmented = true, content = body)
        }
    }
}

private fun sourceSummary(src: Source, s: Settings) = when (src) {
    Source.YOUTUBE_MUSIC -> "Home feed, search, radio and streaming"
    Source.SOUNDCLOUD -> "Tracks, sets and artists"
    Source.LOCAL -> "Music stored on this device"
    Source.SPOTIFY -> if (!AppGraph.instance.spotify.isConfigured) "Connect Spotify in Settings → Accounts" else "Catalog via Web API, audio via YouTube Music"
}

@Composable
private fun Header(text: String) = OptionHeader(t(text))

@Composable
private fun Locations(downloads: Int) {
    val graph = AppGraph.instance
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var askDownloads by remember { mutableStateOf<String?>(null) }
    var askApp by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    val installDir = remember { app.nocturne.data.appInstallDir() }
    Action("Downloads folder", status ?: app.nocturne.data.downloadsDir(), Icons.Rounded.FolderOpen) {
        app.nocturne.data.pickFolder(t("Downloads folder"))?.let { askDownloads = it }
    }
    if (installDir != null) {
        Action("App location", installDir, Icons.Rounded.Computer) {
            app.nocturne.data.pickFolder(t("App location"))?.let { askApp = it }
        }
    }
    askDownloads?.let { dir ->
        AlertDialog(
            onDismissRequest = { askDownloads = null },
            title = { Text(t("Move downloads?")) },
            text = { Text(t("New downloads will be saved to %s.", dir) + if (downloads > 0) "\n\n" + t("%s downloaded songs will be moved there.", downloads) else "") },
            confirmButton = {
                TextButton(onClick = {
                    askDownloads = null
                    graph.settings.update { it.copy(downloadFolder = dir) }
                    if (downloads > 0) scope.launch {
                        val n = graph.downloads.moveAll(dir) { i, total -> status = t("Moving %s / %s…", i, total) }
                        status = null
                        graph.toast(t("Moved %s songs", n))
                    }
                }) { Text(t("Move")) }
            },
            dismissButton = { TextButton(onClick = { askDownloads = null }) { Text(t("Cancel")) } },
        )
    }
    askApp?.let { dir ->
        var busy by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { if (busy == null) askApp = null },
            title = { Text(t("Move Nocturne?")) },
            text = { Text(busy ?: t("Nocturne will reinstall itself into %s and restart. Your library, settings and downloads stay as they are.", dir)) },
            confirmButton = {
                TextButton(enabled = busy == null, onClick = {
                    busy = t("Preparing…")
                    scope.launch {
                        runCatching { app.nocturne.data.relocateApp(dir) { busy = it } }
                            .onFailure { e -> busy = null; askApp = null; graph.toast(e.message ?: t("Couldn't move the app")) }
                    }
                }) { Text(t("Move and restart")) }
            },
            dismissButton = { TextButton(enabled = busy == null, onClick = { askApp = null }) { Text(t("Cancel")) } },
        )
    }
}

private val credits = listOf(
    "Developer" to listOf("Lenny" to "581182871969071115"),
    "Ideas & tests" to listOf("birtoy" to "1221049274222252085", "redmonkeyyyyy" to "449816910733901834"),
)

@Composable
private fun Credits() {
    val uri = LocalUriHandler.current
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(20.dp)).background(cs.surfaceContainerHigh).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        credits.forEach { (role, people) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t(role) + ":", style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, modifier = Modifier.padding(end = 10.dp))
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    people.forEach { (nick, id) ->
                        Row(
                            Modifier.clip(CircleShape).background(Color(0x295865F2)).clickable { uri.openUri("https://discord.com/users/$id") }
                                .padding(start = 8.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(app.nocturne.ui.icons.DiscordIcon, "Discord", Modifier.size(16.dp), tint = Color(0xFF5865F2))
                            Spacer(Modifier.width(6.dp))
                            Text(nick, style = MaterialTheme.typography.labelLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Toggle(title: String, summary: String?, icon: ImageVector?, checked: Boolean, leading: (@Composable () -> Unit)? = null, onChange: (Boolean) -> Unit) {
    val ios = LocalDesignStyle.current == DesignStyle.GLASS
    ListItem(
        headlineContent = { Text(t(title)) },
        supportingContent = summary?.let { { Text(t(it)) } },
        leadingContent = leading ?: icon?.let { { if (ios) IosBadge(it, badgeColor(title)) else Icon(it, null) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = rowModifier(leading != null || icon != null).clickable { onChange(!checked) },
    )
}

@Composable
private fun Action(title: String, summary: String?, icon: ImageVector, chevron: Boolean = false, onClick: () -> Unit) {
    val ios = LocalDesignStyle.current == DesignStyle.GLASS
    ListItem(
        headlineContent = { Text(t(title)) },
        supportingContent = summary?.let { { Text(t(it)) } },
        leadingContent = { if (ios) IosBadge(icon, badgeColor(title)) else Icon(icon, null) },
        trailingContent = if (ios && chevron) { { Chevron() } } else null,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = rowModifier(true).demo(title, onClick).clickable(onClick = onClick),
    )
}

// Every option row sits in a grouped container; [leading] = has an icon (separator inset).
private fun rowModifier(leading: Boolean): Modifier = Modifier.optionRow(if (leading) 61.dp else 16.dp)

@Composable
private fun Chevron() {
    val ios = LocalDesignStyle.current == DesignStyle.GLASS
    Icon(
        Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (ios) 0.7f else 1f),
    )
}

// iOS Settings-style rounded icon tile.
@Composable
private fun IosBadge(icon: ImageVector, color: Color) {
    Box(Modifier.size(29.dp).clip(RoundedCornerShape(7.dp)).background(color), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(19.dp))
    }
}

private val IosBadgeColors = listOf(
    Color(0xFF007AFF), Color(0xFF34C759), Color(0xFFFF9500), Color(0xFFFF3B30), Color(0xFF5856D6),
    Color(0xFFAF52DE), Color(0xFFFF2D55), Color(0xFF32ADE6), Color(0xFF8E8E93), Color(0xFF00C7BE),
)

private fun badgeColor(key: String) = IosBadgeColors[(key.hashCode() and 0x7fffffff) % IosBadgeColors.size]

private fun categoryColor(c: SettingsCategory) = when (c) {
    SettingsCategory.ACCOUNTS -> Color(0xFF007AFF)
    SettingsCategory.LANGUAGE -> Color(0xFF5856D6)
    SettingsCategory.DEVICES -> Color(0xFF34C759)
    SettingsCategory.APPEARANCE -> Color(0xFF5856D6)
    SettingsCategory.CONTENT -> Color(0xFF34C759)
    SettingsCategory.PLAYER -> Color(0xFFFA2D48)
    SettingsCategory.LYRICS -> Color(0xFFFF2D55)
    SettingsCategory.FLOATING -> Color(0xFF32ADE6)
    SettingsCategory.SHORTCUTS -> Color(0xFF8E8E93)
    SettingsCategory.DISCORD -> Color(0xFF5865F2)
    SettingsCategory.SPOTIFY -> Color(0xFF1DB954)
    SettingsCategory.STORAGE -> Color(0xFF8E8E93)
    SettingsCategory.PRIVACY -> Color(0xFF007AFF)
    SettingsCategory.ABOUT -> Color(0xFF8E8E93)
}

@Composable
private fun <T> Choice(title: String, icon: ImageVector, value: T, options: List<T>, label: (T) -> String, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Action(title, t(label(value)), icon, chevron = true) { open = true }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(t(title)) },
            text = {
                Column {
                    options.forEach { o ->
                        Row(
                            Modifier.fillMaxWidth().demo("opt_" + label(o)) { onSelect(o); open = false }.clip(MaterialTheme.shapes.small).selectable(o == value) { onSelect(o); open = false }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = o == value, onClick = null)
                            Spacer(Modifier.size(12.dp))
                            Text(t(label(o)))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text(t("Close")) } },
        )
    }
}

@Composable
private fun TextPref(title: String, hint: String?, icon: ImageVector, value: String, secret: Boolean = false, onSave: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val shown = when {
        value.isBlank() -> hint ?: "Not set"
        secret -> "•".repeat(minOf(value.length, 16))
        else -> value
    }
    Action(title, shown, icon, chevron = true) { open = true }
    if (open) {
        var text by remember { mutableStateOf(value) }
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(t(title)) },
            text = {
                OutlinedTextField(
                    text, { text = it }, singleLine = true, placeholder = hint?.let { { Text(t(it)) } },
                    visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else KeyboardType.Text),
                )
            },
            confirmButton = { TextButton(onClick = { onSave(text); open = false }) { Text(t("Save")) } },
            dismissButton = { TextButton(onClick = { open = false }) { Text(t("Cancel")) } },
        )
    }
}

@Composable
private fun SliderPref(title: String, valueLabel: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onChange: (Float) -> Unit) {
    var local by remember(value) { mutableFloatStateOf(value) }
    Column(rowModifier(false).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row {
            Text(t(title), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(valueLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = local, onValueChange = { local = it }, onValueChangeFinished = { onChange(local) }, valueRange = range, steps = steps)
    }
}

@Composable
private fun AccentPicker(selected: Long, onSelect: (Long) -> Unit) {
    val ios = LocalDesignStyle.current == DesignStyle.GLASS
    FlowRow(rowModifier(false).padding(horizontal = 16.dp, vertical = if (ios) 14.dp else 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AccentPresets.forEach { c ->
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Color(c))
                    .border(if (c == selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                    .clickable { onSelect(c) },
                contentAlignment = Alignment.Center,
            ) { if (c == selected) Icon(Icons.Rounded.Check, null, tint = Color.White) }
        }
    }
}
