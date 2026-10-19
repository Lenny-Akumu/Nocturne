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

@Serializable
enum class ThemeMode(val label: String) { SYSTEM("Follow system"), LIGHT("Light"), DARK("Dark") }

@Serializable
enum class PlayerBackground(val label: String) { GRADIENT("Gradient"), BLUR("Blurred artwork"), SOLID("Solid") }

@Serializable
enum class WindowBackdrop(val label: String) { MICA("Mica"), MICA_ALT("Mica Alt"), ACRYLIC("Acrylic"), NONE("None") }

@Serializable
enum class LyricsAlign(val label: String) { START("Left"), CENTER("Center") }

@Serializable
data class Settings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val accentColor: Long = 0xFF7C5CFF,
    val pureBlack: Boolean = false,
    val playerBackground: PlayerBackground = PlayerBackground.GRADIENT,
    val playerArtworkTint: Boolean = true,
    val showSourceBadges: Boolean = true,
    val windowBackdrop: WindowBackdrop = WindowBackdrop.MICA,
    val compactLists: Boolean = false,

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
    val showVideo: Boolean = false,
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
    val uiLanguage: String = "system",

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
)

class SettingsStore {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val _state = MutableStateFlow(load())
    val state: StateFlow<Settings> = _state.asStateFlow()
    val value: Settings get() = _state.value

    private fun load(): Settings =
        FileStore.read(FILE)?.let { runCatching { json.decodeFromString<Settings>(it) }.getOrNull() } ?: Settings()

    fun update(transform: (Settings) -> Settings) {
        _state.update(transform)
        FileStore.write(FILE, json.encodeToString(Settings.serializer(), _state.value))
    }

    fun reset() = update { Settings() }

    companion object {
        private const val FILE = "settings.json"
    }
}
