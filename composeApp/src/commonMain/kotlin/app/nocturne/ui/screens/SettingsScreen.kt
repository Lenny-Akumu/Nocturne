package app.nocturne.ui.screens

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    APPEARANCE("Appearance", "Theme, colors, player style", Icons.Rounded.Palette),
    CONTENT("Content & sources", "YouTube Music, Spotify, SoundCloud, region", Icons.Rounded.Public),
    PLAYER("Player & audio", "Quality, autoplay, queue", Icons.Rounded.PlayCircle),
    LYRICS("Lyrics", "Synced lyrics display", Icons.Rounded.Lyrics),
    SHORTCUTS("Keyboard shortcuts", "Customize hotkeys", Icons.Rounded.Keyboard),
    DISCORD("Discord", "Rich Presence — show what you're listening to", Icons.Rounded.SportsEsports),
    SPOTIFY("Spotify", "API credentials", Icons.Rounded.Key),
    STORAGE("Storage", "Cache and data", Icons.Rounded.Storage),
    PRIVACY("Privacy", "History and search", Icons.Rounded.Security),
    ABOUT("About", "Version and info", Icons.Rounded.Info),
}

@Composable
fun SettingsScreen(contentPadding: PaddingValues) {
    val nav = AppGraph.instance.navigator
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item {
            Text(t("Settings"), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 18.dp, top = 8.dp, bottom = 12.dp))
        }
        items(SettingsCategory.entries.filter { (it != SettingsCategory.SHORTCUTS && it != SettingsCategory.DISCORD) || platformKind == PlatformKind.DESKTOP }) { c ->
            ListItem(
                headlineContent = { Text(t(c.title)) },
                supportingContent = { Text(t(c.summary)) },
                leadingContent = {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                        Icon(c.icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { nav.push(Screen.SettingsPage(c)) }.padding(horizontal = 8.dp),
            )
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
            Column {
                when (category) {
                    SettingsCategory.ACCOUNTS -> AccountsSettings()
                    SettingsCategory.SHORTCUTS -> ShortcutSettings()
                    SettingsCategory.DISCORD -> {
                        val player by graph.player.state.collectAsState()
                        val uri = LocalUriHandler.current
                        Toggle("Discord Rich Presence", DiscordPresence.status.ifBlank { null }, Icons.Rounded.SportsEsports, s.discordRpc) { v -> set { it.copy(discordRpc = v) } }
                        TextPref("Application ID", "Required — copy it from the Discord developer portal", Icons.Rounded.Key, s.discordAppId) { v -> set { it.copy(discordAppId = v.filter(Char::isDigit)) } }
                        Action("Open Discord developer portal", "Create an app named “Nocturne” and copy its Application ID", Icons.AutoMirrored.Rounded.OpenInNew) { uri.openUri("https://discord.com/developers/applications") }
                        Header("Preview")
                        DiscordPreview(s, player.current)
                        Header("Text")
                        Text(
                            t("Placeholders: %s", "{title} {artist} {album} {source} {app}"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
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
                        Header("Theme")
                        Choice("Theme", Icons.Rounded.DarkMode, s.themeMode, ThemeMode.entries, { it.label }) { v -> set { it.copy(themeMode = v) } }
                        Toggle(
                            if (desktop) "Use Windows accent color" else "Dynamic color (Material You)",
                            if (desktop) "Follow your system accent" else "Colors from your wallpaper (Android 12+)",
                            Icons.Rounded.Wallpaper, s.dynamicColor,
                        ) { v -> set { it.copy(dynamicColor = v) } }
                        if (!s.dynamicColor) AccentPicker(s.accentColor) { v -> set { it.copy(accentColor = v) } }
                        Toggle("Pure black", "AMOLED-friendly dark theme", Icons.Rounded.Contrast, s.pureBlack) { v -> set { it.copy(pureBlack = v) } }
                        if (desktop) {
                            Choice("Window effect", Icons.Rounded.Window, s.windowBackdrop, WindowBackdrop.entries, { it.label }) { v -> set { it.copy(windowBackdrop = v) } }
                            Toggle("Floating mini player", "Small always-on-top player at the top of the screen", Icons.Rounded.PlayCircle, s.floatingPlayer) { v -> set { it.copy(floatingPlayer = v) } }
                        }
                        Header("Player")
                        Choice("Player background", Icons.Rounded.Wallpaper, s.playerBackground, PlayerBackground.entries, { it.label }) { v -> set { it.copy(playerBackground = v) } }
                        Toggle("Artwork-tinted player", "Theme the player with album colors", Icons.Rounded.Palette, s.playerArtworkTint) { v -> set { it.copy(playerArtworkTint = v) } }
                        Header("Lists")
                        Toggle("Source badges", "Show YouTube Music / Spotify / SoundCloud icons", Icons.Rounded.Tune, s.showSourceBadges) { v -> set { it.copy(showSourceBadges = v) } }
                        Toggle("Compact lists", "Denser song rows", Icons.Rounded.FormatSize, s.compactLists) { v -> set { it.copy(compactLists = v) } }
                    }
                    SettingsCategory.CONTENT -> {
                        Header("Sources")
                        Source.entries.forEach { src ->
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
                            t("Spotify search, albums, playlists and artists use the official Web API. Create a free app at developer.spotify.com, then paste its Client ID and Secret here. Audio is matched and streamed from YouTube Music."),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        )
                        TextPref("Client ID", null, Icons.Rounded.Key, s.spotifyClientId) { v -> set { it.copy(spotifyClientId = v.trim()) } }
                        TextPref("Client secret", null, Icons.Rounded.Security, s.spotifyClientSecret, secret = true) { v -> set { it.copy(spotifyClientSecret = v.trim()) } }
                        val uri = LocalUriHandler.current
                        Action("Open Spotify developer dashboard", null, Icons.AutoMirrored.Rounded.OpenInNew) { uri.openUri("https://developer.spotify.com/dashboard") }
                    }
                    SettingsCategory.STORAGE -> {
                        if (!desktop) {
                            SliderPref("Audio cache", "${s.cacheSizeMb} MB (applies after restart)", s.cacheSizeMb.toFloat(), 128f..4096f, 30) { v -> set { it.copy(cacheSizeMb = (v / 128).toInt() * 128) } }
                        }
                        Action("Clear listening history", null, Icons.Rounded.History) { graph.library.clearHistory() }
                        Action("Clear search history", null, Icons.Rounded.Search) { graph.library.clearSearchHistory() }
                        Action("Reset all settings", null, Icons.Rounded.Restore) { graph.settings.reset() }
                    }
                    SettingsCategory.PRIVACY -> {
                        Toggle("Pause listening history", null, Icons.Rounded.History, s.pauseHistory) { v -> set { it.copy(pauseHistory = v) } }
                        Toggle("Pause search history", null, Icons.Rounded.Search, s.pauseSearchHistory) { v -> set { it.copy(pauseSearchHistory = v) } }
                        val lib by graph.library.state.collectAsState()
                        Action("Reset “Not interested”", "${lib.notInterested.size} hidden songs", Icons.Rounded.Restore) { graph.library.clearNotInterested() }
                    }
                    SettingsCategory.ABOUT -> {
                        val uri = LocalUriHandler.current
                        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            app.nocturne.ui.NocturneLogo(Modifier.size(96.dp))
                            Spacer(Modifier.height(12.dp))
                            Text(t("Nocturne"), style = MaterialTheme.typography.headlineSmall)
                            Text(t("Version %s", app.nocturne.BuildInfo.VERSION), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Source.entries.forEach { SourceBadge(it, size = 22.dp) } }
                        }
                        val scope = androidx.compose.runtime.rememberCoroutineScope()
                        var checkResult by remember { mutableStateOf<String?>(null) }
                        Action("Check for updates", checkResult ?: "Look for a newer version on GitHub", Icons.Rounded.SystemUpdate) {
                            checkResult = "Checking…"
                            scope.launch {
                                checkResult = runCatching { graph.updater.check(manual = true) }
                                    .fold({ if (it == null) "You're on the latest version" else "Version ${it.version} is available" }, { "Check failed: ${it.message}" })
                            }
                        }
                        Toggle("Check for updates automatically", null, Icons.Rounded.Restore, s.autoCheckUpdates) { v -> set { it.copy(autoCheckUpdates = v) } }
                        Action("Source code", "github.com/lenny-akumu/nocturne", Icons.Rounded.Code) { uri.openUri("https://github.com/lenny-akumu/nocturne") }
                        Action("Lyrics by LRCLIB", "lrclib.net", Icons.Rounded.Lyrics) { uri.openUri("https://lrclib.net") }
                        Action("Stream extraction by NewPipe Extractor", null, Icons.Rounded.Code) { uri.openUri("https://github.com/TeamNewPipe/NewPipeExtractor") }
                    }
                }
            }
        }
    }
}

private fun sourceSummary(src: Source, s: Settings) = when (src) {
    Source.YOUTUBE_MUSIC -> "Home feed, search, radio and streaming"
    Source.SOUNDCLOUD -> "Tracks, sets and artists"
    Source.SPOTIFY -> if (s.spotifyClientId.isBlank()) "Needs API credentials (Settings → Spotify)" else "Catalog via Web API, audio via YouTube Music"
}

@Composable
private fun Header(text: String) {
    Text(t(text), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 6.dp))
}

@Composable
private fun Toggle(title: String, summary: String?, icon: ImageVector?, checked: Boolean, leading: (@Composable () -> Unit)? = null, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(t(title)) },
        supportingContent = summary?.let { { Text(t(it)) } },
        leadingContent = leading ?: icon?.let { { Icon(it, null) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { onChange(!checked) }.padding(horizontal = 8.dp),
    )
}

@Composable
private fun Action(title: String, summary: String?, icon: ImageVector, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(t(title)) },
        supportingContent = summary?.let { { Text(t(it)) } },
        leadingContent = { Icon(icon, null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp),
    )
}

@Composable
private fun <T> Choice(title: String, icon: ImageVector, value: T, options: List<T>, label: (T) -> String, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Action(title, t(label(value)), icon) { open = true }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(t(title)) },
            text = {
                Column {
                    options.forEach { o ->
                        Row(
                            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).selectable(o == value) { onSelect(o); open = false }.padding(vertical = 10.dp),
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
    Action(title, shown, icon) { open = true }
    if (open) {
        var text by remember { mutableStateOf(value) }
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(t(title)) },
            text = {
                OutlinedTextField(
                    text, { text = it }, singleLine = true, placeholder = hint?.let { { Text(it) } },
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
    Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        Row {
            Text(t(title), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(valueLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = local, onValueChange = { local = it }, onValueChangeFinished = { onChange(local) }, valueRange = range, steps = steps)
    }
}

@Composable
private fun AccentPicker(selected: Long, onSelect: (Long) -> Unit) {
    FlowRow(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AccentPresets.forEach { c ->
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Color(c))
                    .border(if (c == selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                    .clickable { onSelect(c) },
                contentAlignment = Alignment.Center,
            ) { if (c == selected) Icon(Icons.Rounded.Check, null, tint = Color.White) }
        }
    }
    HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
}
