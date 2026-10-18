package app.nocturne

import app.nocturne.data.Library
import app.nocturne.data.SettingsStore
import app.nocturne.model.MediaEntry
import app.nocturne.model.Source
import app.nocturne.player.AudioEngine
import app.nocturne.player.PlayerController
import app.nocturne.source.InnerTube
import app.nocturne.source.LyricsProvider
import app.nocturne.source.MusicProvider
import app.nocturne.source.SoundCloudProvider
import app.nocturne.source.SpotifyProvider
import app.nocturne.source.YouTubeMusicProvider
import app.nocturne.source.createNewPipeBackend
import app.nocturne.ui.Navigator
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class AppGraph(val engine: AudioEngine) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val settings = SettingsStore()
    val library = Library(settings)
    val http = HttpClient {
        install(HttpTimeout) {
            requestTimeoutMillis = 20_000
            connectTimeoutMillis = 10_000
        }
    }

    private val newPipe = createNewPipeBackend().also {
        it.init(settings.value.contentLanguage, settings.value.contentCountry)
    }
    private val innerTube = InnerTube(http) { settings.value.contentLanguage to settings.value.contentCountry }

    val youtube = YouTubeMusicProvider(innerTube, newPipe)
    val soundcloud = SoundCloudProvider(newPipe)
    val spotify = SpotifyProvider(
        http,
        credentials = { settings.value.spotifyClientId.trim() to settings.value.spotifyClientSecret.trim() },
        market = { settings.value.contentCountry },
        youtube = youtube,
    )
    val lyrics = LyricsProvider(http)

    private val allProviders: Map<Source, MusicProvider> = mapOf(
        Source.YOUTUBE_MUSIC to youtube,
        Source.SPOTIFY to spotify,
        Source.SOUNDCLOUD to soundcloud,
    )

    val player = PlayerController(engine, allProviders, settings, library, scope)
    val navigator = Navigator()

    init {
        scope.launch {
            settings.state.distinctUntilChangedBy { it.contentLanguage to it.contentCountry }.drop(1).collect {
                newPipe.init(it.contentLanguage, it.contentCountry)
            }
        }
    }

    fun provider(source: Source): MusicProvider = allProviders.getValue(source)

    fun enabledProviders(): List<MusicProvider> =
        Source.entries.filter { it in settings.value.enabledSources }.map { allProviders.getValue(it) }

    suspend fun openUrl(url: String): MediaEntry? =
        allProviders.values.firstOrNull { it.canOpen(url) }?.open(url)

    companion object {
        lateinit var instance: AppGraph
            private set

        fun init(engine: AudioEngine): AppGraph {
            if (!::instance.isInitialized) instance = AppGraph(engine)
            return instance
        }
    }
}
