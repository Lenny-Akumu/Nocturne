package app.nocturne.data

import app.nocturne.FileStore
import app.nocturne.model.AudioQuality
import app.nocturne.model.Source
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
enum class ThemeMode(val label: String) { SYSTEM("Follow system"), LIGHT("Light"), DARK("Dark") }

@Serializable
enum class PlayerBackground(val label: String) { GRADIENT("Gradient"), BLUR("Blurred artwork"), LIVE("Live video"), SOLID("Solid") }

@Serializable
enum class WindowBackdrop(val label: String) { MICA("Mica"), MICA_ALT("Mica Alt"), ACRYLIC("Acrylic"), NONE("None") }

@Serializable
enum class DesignStyle(val label: String) { MATERIAL("Material You 3"), GLASS("Liquid Glass"), WINDOWS("Windows") }

fun defaultDesignStyle(): DesignStyle = when (app.nocturne.platformKind) {
    app.nocturne.PlatformKind.IOS -> DesignStyle.GLASS
    app.nocturne.PlatformKind.DESKTOP -> DesignStyle.WINDOWS
    else -> DesignStyle.MATERIAL
}

@Serializable
enum class IconBackground(val label: String) { NONE("None"), ACRYLIC("Acrylic circle"), CIRCLE("Black circle"), SQUARE("Black rounded square") }

@Serializable
enum class IconPalette(val label: String) {
    STANDARD("Standard"), SYSTEM("System"), OCEAN("Ocean"), SUNSET("Sunset"), EMERALD("Emerald"),
    ROSE("Rose"), GOLD("Gold"), MONO("Mono"), AURORA("Aurora"),
}

@Serializable
enum class SeekBarStyle(val label: String) { WAVE("Wave"), BAR("Line"), DOT("Dot"), SPECTRUM("Spectrum") }

@Serializable
enum class LyricsAlign(val label: String) { START("Left"), CENTER("Center") }

@Serializable
data class Settings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val accentColor: Long = 0xFF7C5CFF,
    val pureBlack: Boolean = false,
    val playerBackground: PlayerBackground = PlayerBackground.GRADIENT,
    val seekBarStyle: SeekBarStyle = SeekBarStyle.WAVE,
    val animatedBackground: Boolean = true,
    val playerArtworkTint: Boolean = true,
    val showSourceBadges: Boolean = true,
    val windowBackdrop: WindowBackdrop = WindowBackdrop.MICA,
    val compactLists: Boolean = false,
    val uiScale: Float = 1f,
    val refreshRate: Int = 0, // display refresh in Hz; 0 = auto
    val designStyle: DesignStyle = defaultDesignStyle(),
    val colorStyle: String = "TonalSpot",
    val fontStyle: String = "default",
    val iconBackground: IconBackground = IconBackground.SQUARE,
    val iconPalette: IconPalette = IconPalette.STANDARD,

    val enabledSources: Set<Source> = setOf(Source.YOUTUBE_MUSIC, Source.SOUNDCLOUD, Source.SPOTIFY),
    val defaultSearchSource: Source = Source.YOUTUBE_MUSIC,
    val contentLanguage: String = "en",
    val contentCountry: String = "US",
    val localeConfigured: Boolean = false,
    val hideExplicit: Boolean = false,
    val searchSuggestions: Boolean = true,

    val audioQuality: AudioQuality = AudioQuality.HIGH,
    val autoplayRadio: Boolean = true,
    val persistQueue: Boolean = true,
    val skipSilence: Boolean = false,
    val resumeOnStart: Boolean = false,
    val stopOnTaskRemoved: Boolean = false,
    val seekStepSeconds: Int = 10,
    val volume: Float = 1f,
    val playbackSpeed: Float = 1f,
    val eqEnabled: Boolean = false,
    val eqPreset: String = "Default",
    val eqGains: List<Float> = List(6) { 0f },
    val customEqPresets: Map<String, List<Float>> = emptyMap(),
    val startupIntro: Boolean = true,
    val startupSound: Boolean = true,
    val seasonalEffects: Boolean = true,
    val customTitleBar: Boolean = true,
    val downloadFolder: String = "",
    val halloweenIcon: Boolean = false,
    val fluentIcons: Boolean = true,
    val alarmOn: Boolean = false,
    val alarmMinute: Int = 7 * 60,
    val showVideo: Boolean = false,
    val crossfadeSec: Int = 0,
    val releaseAlerts: Boolean = true,
    val downloadQuality: AudioQuality = AudioQuality.HIGH,
    val localFolders: List<String> = emptyList(),
    val normalizeVolume: Boolean = false,
    val spatialAudio: Boolean = false,
    val eightD: Boolean = false,
    val vocalRemover: Boolean = false,
    val videoFullWidth: Boolean = false,

    val showLyrics: Boolean = true,
    val lyricsTextSize: Int = 26,
    val lyricsAlign: LyricsAlign = LyricsAlign.START,

    val cacheSizeMb: Int = 512,

    val spotifyClientId: String = "",
    val spotifyClientSecret: String = "",

    val hotkeys: Map<String, String> = emptyMap(),
    val globalHotkeys: Boolean = true,
    val floatingPlayer: Boolean = false,
    val floatingScale: Float = 1f,
    val floatingStyle: Int = 0,
    /** Thin vertical bar instead of the horizontal pill. */
    val floatingVertical: Boolean = false,
    val floatingAnchor: Int = 1,
    val floatingButtons: Set<String> = setOf("prev", "next", "like", "volume", "seek"),
    val floatingX: Int = -1,
    val windowX: Int = Int.MIN_VALUE,
    val windowY: Int = Int.MIN_VALUE,
    val windowW: Int = 1280,
    val windowH: Int = 820,
    val windowMaximized: Boolean = false,
    val floatingY: Int = -1,
    val uiLanguage: String = "system",
    /** Version whose "What's new" was shown; empty = fresh install. */
    val lastSeenVersion: String = "",

    val discordRpc: Boolean = true,
    val discordAppId: String = "",
    val discordDetails: String = "{title}",
    val discordState: String = "{artist}",
    val discordLargeText: String = "{album}",
    val discordSmallText: String = "{source}",
    val discordButton: String = "Listen on {source}",
    val discordActivityType: Int = 2,
    val discordStatusDisplay: Int = 2,
    val discordShowArtwork: Boolean = true,
    val discordShowSource: Boolean = true,
    val discordShowTime: Boolean = true,
    val discordShowButton: Boolean = true,
    val discordShowPaused: Boolean = false,

    val autoCheckUpdates: Boolean = true,
    val skippedVersion: String = "",

    val pauseHistory: Boolean = false,
    val pauseSearchHistory: Boolean = false,

    val deviceName: String = "",
    val syncLikes: Boolean = true,
    val syncPlaylists: Boolean = true,
    val syncHistory: Boolean = true,
    val remoteControl: Boolean = true,
    /** Keep controlling the device we played on (Spotify Connect style). */
    val stayConnected: Boolean = false,
    val connectedDevice: String? = null,
)

class SettingsStore {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val _state = MutableStateFlow(load())
    val state: StateFlow<Settings> = _state.asStateFlow()
    val value: Settings get() = _state.value

    private fun load(): Settings {
        val text = FileStore.read(FILE) ?: return Settings()
        return runCatching {
            val s = json.decodeFromString<Settings>(text)
            val obj = json.parseToJsonElement(text).jsonObject
            // Migrate the old liquidGlass toggle.
            if ("designStyle" !in obj && obj["liquidGlass"]?.jsonPrimitive?.booleanOrNull == true) s.copy(designStyle = DesignStyle.GLASS) else s
        }.getOrNull() ?: Settings()
    }

    fun update(transform: (Settings) -> Settings) {
        _state.update(transform)
        FileStore.write(FILE, json.encodeToString(Settings.serializer(), _state.value))
    }

    fun reset() = update { Settings() }

    companion object {
        private const val FILE = "settings.json"
    }
}
