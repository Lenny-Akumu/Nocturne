package app.nocturne.player

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import app.nocturne.AppGraph
import app.nocturne.MainActivity
import app.nocturne.model.Track
import app.nocturne.nowMillis
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.launch

object AutoBrowse {
    private const val PREFIX = "auto|"
    private val folders = listOf(
        "mixes" to "Your mixes", "liked" to "Liked songs", "repeat" to "On repeat", "recent" to "Recently played",
        "playlists" to "Playlists", "smart" to "Smart playlists", "podcasts" to "Podcasts", "radio" to "Radio", "downloads" to "Downloaded", "local" to "On device",
    )

    // Android Auto layout hints: folders as a grid, songs as a list
    private const val STYLE_BROWSABLE = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
    private const val STYLE_PLAYABLE = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"
    val rootExtras = android.os.Bundle().apply {
        putBoolean("android.media.browse.SEARCH_SUPPORTED", true)
        putInt(STYLE_BROWSABLE, 2)
        putInt(STYLE_PLAYABLE, 1)
    }

    fun root(): MediaItem = folder("root", "Nocturne")

    suspend fun children(parent: String): List<MediaItem> {
        val graph = AppGraph.instance
        val lib = graph.library.state.value
        return when (parent) {
            "root" -> folders.filter { (id, _) -> id != "podcasts" || graph.settings.value.podcasts.isNotEmpty() }
                .map { (id, title) -> folder(id, app.nocturne.i18n.t(title), tracks(id).firstOrNull()?.artworkUrl) }
            "mixes" -> graph.dailyMixes.state.value.mapIndexed { i, m -> folder("mix:$i", m.title, m.tracks.firstOrNull()?.artworkUrl) }
            "playlists" -> lib.playlists.map { folder("pl:${it.id}", it.name, it.tracks.firstOrNull()?.artworkUrl) }
            "smart" -> graph.settings.value.smartPlaylists.map { folder("sp:${it.id}", it.name, tracks("sp:${it.id}").firstOrNull()?.artworkUrl) }
            "podcasts" -> graph.settings.value.podcasts.map { folder("pod:${it.feedUrl}", it.title, it.artworkUrl) }
            else -> {
                val list = tracks(parent).ifEmpty { if (parent.startsWith("pod:")) podcast(parent) else emptyList() }
                val shuffle = if (list.size > 2 && !parent.startsWith("pod:")) listOf(shuffleItem(parent)) else emptyList()
                shuffle + list.mapIndexed { i, t -> item(parent, i, t) }
            }
        }
    }

    fun tracks(parent: String): List<Track> {
        val graph = AppGraph.instance
        val lib = graph.library.state.value
        return when {
            parent == "liked" -> lib.liked
            parent == "recent" -> lib.history.map { it.track }.distinctBy { it.key }
            parent == "repeat" -> lib.plays.filter { it.t > nowMillis() - 30L * 86_400_000L }.groupingBy { it.k }.eachCount()
                .entries.sortedByDescending { it.value }.mapNotNull { graph.library.trackFor(it.key) }
            parent == "downloads" -> graph.downloads.items.value.values.map { it.track }
            parent == "local" -> graph.local.tracks.value.orEmpty()
            parent == "radio" -> graph.settings.value.let { s -> (s.radioFavorites + s.radioRecent).distinctBy { it.id } }.map(app.nocturne.data.Radio::toTrack)
            parent.startsWith("mix:") -> graph.dailyMixes.state.value.getOrNull(parent.removePrefix("mix:").toIntOrNull() ?: -1)?.tracks.orEmpty()
            parent.startsWith("pl:") -> lib.playlists.firstOrNull { it.id == parent.removePrefix("pl:") }?.tracks.orEmpty()
            parent.startsWith("sp:") -> graph.settings.value.smartPlaylists.firstOrNull { it.id == parent.removePrefix("sp:") }
                ?.let { app.nocturne.data.SmartPlaylists.evaluate(it, lib, nowMillis()) }.orEmpty()
            parent.startsWith("pod:") -> podcastCache[parent].orEmpty()
            else -> emptyList()
        }.take(200)
    }

    private val podcastCache = mutableMapOf<String, List<Track>>()

    private suspend fun podcast(parent: String): List<Track> {
        val sub = AppGraph.instance.settings.value.podcasts.firstOrNull { "pod:${it.feedUrl}" == parent } ?: return emptyList()
        val eps = runCatching { app.nocturne.data.Podcasts.episodes(sub) }.getOrDefault(emptyList()).take(50)
        return eps.map { app.nocturne.data.Podcasts.toTrack(sub, it) }.also { podcastCache[parent] = it }
    }

    /** Voice / on-screen search in the car. */
    suspend fun search(query: String): List<MediaItem> {
        val graph = AppGraph.instance
        val found = runCatching { graph.youtube.search(query, app.nocturne.model.SearchFilter.SONGS) }.getOrDefault(emptyList())
            .filterIsInstance<app.nocturne.model.MediaEntry.Song>().map { it.track }.take(30)
        searchCache = found
        return found.mapIndexed { i, t -> item("search", i, t) }
    }

    private var searchCache: List<Track> = emptyList()

    fun play(items: List<MediaItem>, index: Int): Boolean {
        val first = items.getOrNull(index) ?: return false
        // Assistant search request: handled in onAddMediaItems
        if (first.mediaId.isEmpty() && first.requestMetadata.searchQuery != null) return true
        val id = first.mediaId
        if (!id.startsWith(PREFIX)) return false
        val parts = id.split("|")
        val parent = parts.getOrElse(1) { "" }
        val pos = parts.getOrElse(2) { "0" }
        val list = if (parent == "search") searchCache else tracks(parent)
        if (list.isEmpty()) return true
        val title = folders.firstOrNull { it.first == parent }?.second?.let { app.nocturne.i18n.t(it) }
        val player = AppGraph.instance.player
        if (pos == "shuffle") player.play(list, 0, title, shuffle = true)
        else player.play(list, (pos.toIntOrNull() ?: 0).coerceIn(0, list.lastIndex), title)
        return true
    }

    /** "Play X on Nocturne" from the Assistant: search and start a radio from the best hit. */
    suspend fun playFromSearch(query: String): Boolean {
        val graph = AppGraph.instance
        val q = query.trim()
        if (q.isEmpty()) {
            val liked = graph.library.state.value.liked
            if (liked.isEmpty()) return false
            graph.player.play(liked, 0, app.nocturne.i18n.t("Liked songs"), shuffle = true)
            return true
        }
        val hit = runCatching { graph.youtube.search(q, app.nocturne.model.SearchFilter.SONGS) }.getOrDefault(emptyList())
            .filterIsInstance<app.nocturne.model.MediaEntry.Song>().firstOrNull()?.track ?: return false
        graph.player.startRadio(hit)
        return true
    }

    private fun folder(id: String, title: String, art: String? = null) = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder().setTitle(title).setArtworkUri(art?.let(Uri::parse))
                .setIsBrowsable(true).setIsPlayable(false).setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED).build(),
        )
        .build()

    private fun shuffleItem(parent: String) = MediaItem.Builder()
        .setMediaId("$PREFIX$parent|shuffle")
        .setMediaMetadata(
            MediaMetadata.Builder().setTitle(app.nocturne.i18n.t("Shuffle")).setIsBrowsable(false).setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST).build(),
        )
        .build()

    private fun item(parent: String, index: Int, t: Track) = MediaItem.Builder()
        .setMediaId("$PREFIX$parent|$index")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(t.title)
                .setArtist(t.artistNames)
                .setArtworkUri(t.artworkUrl?.let(Uri::parse))
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .build(),
        )
        .build()
}

class PlaybackService : MediaLibraryService() {
    private var session: MediaLibrarySession? = null

    private fun <T> async(block: suspend () -> T): ListenableFuture<T> {
        val f = com.google.common.util.concurrent.SettableFuture.create<T>()
        AppGraph.instance.scope.launch {
            runCatching { block() }.onSuccess { f.set(it) }.onFailure { f.setException(it) }
        }
        return f
    }

    override fun onCreate() {
        super.onCreate()
        val engine = AppGraph.instance.engine as AndroidAudioEngine
        val intent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaLibrarySession.Builder(this, engine.sessionPlayer, object : MediaLibrarySession.Callback {
            override fun onGetLibraryRoot(
                session: MediaLibrarySession, browser: MediaSession.ControllerInfo, params: LibraryParams?,
            ): ListenableFuture<LibraryResult<MediaItem>> =
                Futures.immediateFuture(LibraryResult.ofItem(AutoBrowse.root(), LibraryParams.Builder().setExtras(AutoBrowse.rootExtras).build()))

            override fun onGetChildren(
                session: MediaLibrarySession, browser: MediaSession.ControllerInfo, parentId: String,
                page: Int, pageSize: Int, params: LibraryParams?,
            ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = async {
                LibraryResult.ofItemList(ImmutableList.copyOf(AutoBrowse.children(parentId)), params)
            }

            override fun onSearch(
                session: MediaLibrarySession, browser: MediaSession.ControllerInfo, query: String, params: LibraryParams?,
            ): ListenableFuture<LibraryResult<Void>> = async {
                val n = AutoBrowse.search(query).size
                session.notifySearchResultChanged(browser, query, n, params)
                LibraryResult.ofVoid()
            }

            override fun onGetSearchResult(
                session: MediaLibrarySession, browser: MediaSession.ControllerInfo, query: String,
                page: Int, pageSize: Int, params: LibraryParams?,
            ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = async {
                LibraryResult.ofItemList(ImmutableList.copyOf(AutoBrowse.search(query)), params)
            }

            override fun onAddMediaItems(
                mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>,
            ): ListenableFuture<MutableList<MediaItem>> {
                val query = mediaItems.firstOrNull()?.requestMetadata?.searchQuery
                if (query != null && mediaItems.size == 1 && mediaItems[0].mediaId.isEmpty()) {
                    AppGraph.instance.scope.launch { AutoBrowse.playFromSearch(query) }
                }
                return Futures.immediateFuture(mediaItems)
            }
        })
            .setSessionActivity(intent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val graph = AppGraph.instance
        if (graph.settings.value.stopOnTaskRemoved || !graph.player.state.value.isPlaying) {
            graph.player.pause()
            stopSelf()
        }
    }

    override fun onDestroy() {
        session?.run {
            release()
            session = null
        }
        super.onDestroy()
    }
}
