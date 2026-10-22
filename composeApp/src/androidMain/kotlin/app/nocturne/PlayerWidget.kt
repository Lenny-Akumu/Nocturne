package app.nocturne

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.RemoteViews
import app.nocturne.player.PlayerState
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch

class PlayerWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Widgets.refresh(context, AppGraph.instance.player.state.value, null)
    }

    override fun onReceive(context: Context, intent: Intent) {
        val player = AppGraph.instance.player
        when (intent.action) {
            ACTION_PLAY -> player.toggle()
            ACTION_NEXT -> player.next()
            ACTION_PREV -> player.previous()
            else -> super.onReceive(context, intent)
        }
    }

    companion object {
        const val ACTION_PLAY = "app.nocturne.widget.PLAY"
        const val ACTION_NEXT = "app.nocturne.widget.NEXT"
        const val ACTION_PREV = "app.nocturne.widget.PREV"
    }
}

class PlayTileService : TileService() {
    override fun onStartListening() {
        Widgets.updateTile(qsTile, AppGraph.instance.player.state.value)
    }

    override fun onClick() {
        val player = AppGraph.instance.player
        if (player.state.value.current == null) {
            val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
            return
        }
        player.toggle()
        Widgets.updateTile(qsTile, player.state.value.let { it.copy(isPlaying = !it.isPlaying) })
    }
}

object Widgets {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var art: Pair<String?, Bitmap?> = null to null

    fun start(context: Context) {
        scope.launch {
            AppGraph.instance.player.state
                .distinctUntilChangedBy { Triple(it.current?.key, it.isPlaying, it.current?.artworkUrl) }
                .collect { s ->
                    val url = s.current?.artworkUrl
                    if (art.first != url) art = url to loadArt(context, url)
                    refresh(context, s, art.second)
                    runCatching { TileService.requestListeningState(context, ComponentName(context, PlayTileService::class.java)) }
                }
        }
    }

    private suspend fun loadArt(context: Context, url: String?): Bitmap? {
        if (url == null) return null
        val request = ImageRequest.Builder(context).data(url).size(256, 256).allowHardware(false).build()
        val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult ?: return null
        return runCatching { result.image.toBitmap() }.getOrNull()
    }

    fun refresh(context: Context, s: PlayerState, bitmap: Bitmap?) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, PlayerWidget::class.java))
        if (ids.isEmpty()) return
        val views = RemoteViews(context.packageName, R.layout.widget_player)
        views.setTextViewText(R.id.widget_title, s.current?.title ?: "Nocturne")
        views.setTextViewText(R.id.widget_artist, s.current?.artistNames.orEmpty())
        if (bitmap != null) views.setImageViewBitmap(R.id.widget_art, bitmap)
        else views.setImageViewResource(R.id.widget_art, R.drawable.ic_launcher_foreground)
        views.setImageViewResource(R.id.widget_play, if (s.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
        views.setOnClickPendingIntent(R.id.widget_play, action(context, PlayerWidget.ACTION_PLAY, 1))
        views.setOnClickPendingIntent(R.id.widget_next, action(context, PlayerWidget.ACTION_NEXT, 2))
        views.setOnClickPendingIntent(R.id.widget_prev, action(context, PlayerWidget.ACTION_PREV, 3))
        val open = PendingIntent.getActivity(
            context, 4, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        views.setOnClickPendingIntent(R.id.widget_root, open)
        manager.updateAppWidget(ids, views)
    }

    private fun action(context: Context, action: String, code: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, code, Intent(context, PlayerWidget::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun updateTile(tile: Tile?, s: PlayerState) {
        tile ?: return
        tile.state = if (s.isPlaying) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = s.current?.title ?: "Nocturne"
        if (Build.VERSION.SDK_INT >= 29) tile.subtitle = app.nocturne.i18n.t(if (s.isPlaying) "Now playing" else "Paused")
        tile.updateTile()
    }
}
