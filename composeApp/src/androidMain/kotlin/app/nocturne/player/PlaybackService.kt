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

object AutoBrowse {
    private const val PREFIX = "auto|"
    private val folders = listOf("liked" to "Liked songs", "recent" to "Recently played", "repeat" to "On repeat", "downloads" to "Downloaded", "local" to "On device")

    fun root(): MediaItem = folder("root", "Nocturne")

    fun children(parent: String): List<MediaItem> = when (parent) {
        "root" -> folders.map { (id, title) -> folder(id, app.nocturne.i18n.t(title)) }
        else -> tracks(parent).mapIndexed { i, t -> item(parent, i, t) }
    }

    fun tracks(parent: String): List<Track> {
        val graph = AppGraph.instance
        val lib = graph.library.state.value
        return when (parent) {
            "liked" -> lib.liked
            "recent" -> lib.history.map { it.track }
            "repeat" -> lib.plays.filter { it.t > nowMillis() - 30L * 86_400_000L }.groupingBy { it.k }.eachCount()
                .entries.sortedByDescending { it.value }.mapNotNull { graph.library.trackFor(it.key) }
            "downloads" -> graph.downloads.items.value.values.map { it.track }
            "local" -> graph.local.tracks.value.orEmpty()
            else -> emptyList()
        }.take(200)
    }

    fun play(items: List<MediaItem>, index: Int): Boolean {
        val id = items.getOrNull(index)?.mediaId ?: return false
        if (!id.startsWith(PREFIX)) return false
        val (_, parent, pos) = id.split("|").let { Triple(it[0], it.getOrElse(1) { "" }, it.getOrElse(2) { "0" }.toIntOrNull() ?: 0) }
        val list = tracks(parent)
        if (list.isEmpty()) return true
        AppGraph.instance.player.play(list, pos.coerceIn(0, list.lastIndex), folders.firstOrNull { it.first == parent }?.second?.let { app.nocturne.i18n.t(it) })
        return true
    }

    private fun folder(id: String, title: String) = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setIsBrowsable(true).setIsPlayable(false).setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED).build())
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
            ): ListenableFuture<LibraryResult<MediaItem>> = Futures.immediateFuture(LibraryResult.ofItem(AutoBrowse.root(), params))

            override fun onGetChildren(
                session: MediaLibrarySession, browser: MediaSession.ControllerInfo, parentId: String,
                page: Int, pageSize: Int, params: LibraryParams?,
            ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
                Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.copyOf(AutoBrowse.children(parentId)), params))

            override fun onAddMediaItems(
                mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>,
            ): ListenableFuture<MutableList<MediaItem>> = Futures.immediateFuture(mediaItems)
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
