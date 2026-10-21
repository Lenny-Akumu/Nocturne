package app.nocturne

import app.nocturne.data.AccountsStore
import app.nocturne.data.Library
import app.nocturne.data.SettingsStore
import app.nocturne.data.Updater
import app.nocturne.model.MediaEntry
import app.nocturne.model.Source
import app.nocturne.model.Track
import app.nocturne.source.Rating
import app.nocturne.player.AudioEngine
import app.nocturne.player.PlayerController
import app.nocturne.source.InnerTube
import app.nocturne.source.LyricsProvider
import app.nocturne.source.MusicProvider
import app.nocturne.source.SoundCloudProvider
import app.nocturne.source.SourceUnavailableException
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

class AppGraph(val engine: AudioEngine) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val settings = SettingsStore().also { store ->
        if (!store.value.localeConfigured) {
            val (lang, country) = systemLocale()
            store.update { it.copy(contentLanguage = lang, contentCountry = country, localeConfigured = true) }
        }
    }
    val library = Library(settings)
    val accounts = AccountsStore()
    val http = HttpClient {
        install(HttpTimeout) {
            requestTimeoutMillis = 20_000
            connectTimeoutMillis = 10_000
        }
    }

    private val newPipe = createNewPipeBackend().also {
        it.init(settings.value.contentLanguage, settings.value.contentCountry)
    }
    private val innerTube = InnerTube(
        http,
        locale = { settings.value.contentLanguage to settings.value.contentCountry },
        cookie = { accounts.value.youtubeCookie },
    )

    val youtube = YouTubeMusicProvider(innerTube, newPipe, { accounts.value.youtubeSignedIn }, { accounts.value.youtubeName })
    val soundcloud = SoundCloudProvider(newPipe, http, accounts)
    val spotify = SpotifyProvider(
        http,
        credentials = { settings.value.spotifyClientId.trim().ifBlank { BuildInfo.SPOTIFY_CLIENT_ID } to settings.value.spotifyClientSecret.trim() },
        market = { settings.value.contentCountry },
        youtube = youtube,
        accounts = accounts,
    )
    val lyrics = LyricsProvider(http, innerTube)
    val local = app.nocturne.source.LocalProvider(settings)

    private val allProviders: Map<Source, MusicProvider> = mapOf(
        Source.YOUTUBE_MUSIC to youtube,
        Source.SPOTIFY to spotify,
        Source.SOUNDCLOUD to soundcloud,
        Source.LOCAL to local,
    )

    val player = PlayerController(engine, allProviders, settings, library, scope)
    val remotePlaylists = app.nocturne.data.RemotePlaylists(
        scope,
        accounts = { enabledProviders().filter { it.source != Source.LOCAL && it.isSignedIn } },
        provider = { provider(it) },
        notify = { toast(it) },
    )
    private var toastJob: kotlinx.coroutines.Job? = null

    // Short auto-dismissing notice on the app snackbar.
    fun toast(msg: String) {
        player.showMessage(msg)
        toastJob?.cancel()
        toastJob = scope.launch {
            kotlinx.coroutines.delay(4000)
            if (player.state.value.error == msg) player.dismissError()
        }
    }
    val cast = app.nocturne.cast.CastSession(this, scope)
    val party = app.nocturne.party.Party(this, scope)
    val downloads = app.nocturne.data.Downloads(scope) { t -> provider(t.source).stream(t, settings.value.downloadQuality) }.also { d ->
        player.localOverride = { t -> d.uriFor(t) }
    }
    val navigator = Navigator()
    val updater = Updater(http, settings)

    init {
        app.nocturne.i18n.I18n.apply(settings.value.uiLanguage)
        scope.launch {
            settings.state.distinctUntilChangedBy { it.uiLanguage }.drop(1).collect { app.nocturne.i18n.I18n.apply(it.uiLanguage) }
        }
        scope.launch {
            kotlinx.coroutines.delay(4000)
            while (true) {
                if (settings.value.autoCheckUpdates && updater.state.value is app.nocturne.data.UpdateState.Idle) {
                    runCatching { updater.check(manual = false) }
                }
                kotlinx.coroutines.delay(6 * 60 * 60_000L)
            }
        }
        scope.launch {
            kotlinx.coroutines.delay(6000)
            runCatching { syncLikes(force = true) }
        }
        scope.launch {
            kotlinx.coroutines.delay(45_000)
            val watcher = app.nocturne.data.ReleaseWatcher(this@AppGraph)
            while (true) {
                if (settings.value.releaseAlerts) runCatching { watcher.check() }
                kotlinx.coroutines.delay(6 * 60 * 60_000L)
            }
        }
        scope.launch {
            kotlinx.coroutines.flow.combine(player.state, settings.state) { p, s ->
                if (s.showVideo) listOfNotNull(p.current, p.queue.getOrNull(p.index + 1)) else emptyList()
            }.distinctUntilChangedBy { list -> list.map { it.key } }.collect { list ->
                list.forEach { t -> launch { runCatching { provider(t.source).videoStream(t) } } }
            }
        }
        scope.launch {
            accounts.state.distinctUntilChangedBy { Triple(it.youtubeSignedIn, it.spotifySignedIn, it.soundcloudUserId) }.drop(1).collect {
                remotePlaylists.invalidate()
                runCatching { syncLikes(force = true) }
            }
        }
        scope.launch {
            settings.state.distinctUntilChangedBy { listOf(it.playbackSpeed, it.eqEnabled, it.eqGains, it.skipSilence, it.normalizeVolume, it.spatialAudio, it.eightD, it.vocalRemover) }.drop(1).collect {
                player.applyAudioSettings()
            }
        }
        scope.launch {
            settings.state.distinctUntilChangedBy { it.contentLanguage to it.contentCountry }.drop(1).collect {
                newPipe.init(it.contentLanguage, it.contentCountry)
            }
        }
    }

    suspend fun signInYouTube(cookie: String) {
        val clean = cookie.trim().removePrefix("Cookie:").trim()
        if (!clean.contains("SAPISID")) throw SourceUnavailableException("These cookies don't contain SAPISID — sign in on music.youtube.com first")
        accounts.update { it.copy(youtubeCookie = clean) }
        val name = runCatching { innerTube.accountName() }.getOrNull()
        accounts.update { it.copy(youtubeName = name ?: "YouTube Music user") }
    }

    private var recommendationCache: Pair<Long, List<Track>>? = null

    suspend fun recommendations(force: Boolean = false): List<Track> {
        recommendationCache?.let { (at, list) -> if (!force && nowMillis() - at < 30 * 60_000 && list.isNotEmpty()) return list }
        val d = library.state.value
        val enabled = settings.value.enabledSources
        val seeds = (d.history.take(10).map { it.track } + d.liked.take(10))
            .distinctBy { it.key }
            .filter { it.source in enabled && !library.isBlocked(it) }
            .shuffled()
            .take(3)
        if (seeds.isEmpty()) return emptyList()
        val lists = kotlinx.coroutines.coroutineScope {
            seeds.map { seed -> async { runCatching { provider(seed.source).related(seed) }.getOrDefault(emptyList()) } }.awaitAll()
        }
        val recent = d.history.take(40).map { it.track.key }.toSet()
        val seen = mutableSetOf<String>()
        val perArtist = mutableMapOf<String, Int>()
        val out = mutableListOf<Track>()
        val longest = lists.maxOfOrNull { it.size } ?: 0
        for (i in 0 until longest) for (l in lists) {
            val t = l.getOrNull(i) ?: continue
            if (!seen.add(t.key) || t.key in recent || library.isBlocked(t)) continue
            if (t.durationMs != 0L && t.durationMs !in 45_000L..15 * 60_000L) continue
            if (settings.value.hideExplicit && t.explicit) continue
            val artist = t.artists.firstOrNull()?.name?.lowercase().orEmpty()
            if ((perArtist[artist] ?: 0) >= 2) continue
            perArtist[artist] = (perArtist[artist] ?: 0) + 1
            out += t
        }
        val result = out.take(40)
        recommendationCache = nowMillis() to result
        return result
    }

    private var lastSync = 0L

    suspend fun syncLikes(force: Boolean = false) {
        if (!force && nowMillis() - lastSync < 10 * 60_000) return
        lastSync = nowMillis()
        val remote = kotlinx.coroutines.coroutineScope {
            enabledProviders().filter { it.isSignedIn }.map { p -> async { runCatching { p.likedTracks() }.getOrDefault(emptyList()) } }.awaitAll()
        }
        remote.forEach { library.mergeLiked(it) }
    }

    fun toggleLike(track: Track) {
        val nowLiked = !library.isLiked(track)
        library.toggleLike(track)
        scope.launch { runCatching { provider(track.source).rate(track, if (nowLiked) Rating.LIKE else Rating.NONE) } }
    }

    fun notInterested(track: Track) {
        library.markNotInterested(track)
        player.dropTrack(track)
        scope.launch { runCatching { provider(track.source).rate(track, Rating.DISLIKE) } }
    }

    fun signOutYouTube() = accounts.update { it.copy(youtubeCookie = "", youtubeName = null) }

    fun provider(source: Source): MusicProvider = allProviders.getValue(source)

    fun clearMemoryCaches() {
        youtube.clearVideoCache()
        lyrics.clearCache()
        recommendationCache = null
    }

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
