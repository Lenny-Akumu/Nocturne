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
import coil3.request.transformations
import coil3.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch

// Shared receiver logic; each subclass is one entry in the launcher's widget picker.
abstract class NocturneWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        if (AppGraph.ready) Widgets.refreshNow(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in setOf(ACTION_PLAY, ACTION_NEXT, ACTION_PREV) && !AppGraph.ready) {
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        when (intent.action) {
            ACTION_PLAY -> AppGraph.instance.player.toggle()
            ACTION_NEXT -> AppGraph.instance.player.next()
            ACTION_PREV -> AppGraph.instance.player.previous()
            else -> super.onReceive(context, intent)
        }
    }

    companion object {
        const val ACTION_PLAY = "app.nocturne.widget.PLAY"
        const val ACTION_NEXT = "app.nocturne.widget.NEXT"
        const val ACTION_PREV = "app.nocturne.widget.PREV"
    }
}

class PlayerWidget : NocturneWidget()
class MinimalWidget : NocturneWidget()
class CoverWidget : NocturneWidget()

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
    var art: Pair<String?, Bitmap?> = null to null
        private set

    fun start(context: Context) {
        scope.launch {
            AppGraph.instance.settings.state.distinctUntilChangedBy { it.designStyle }.collect { refreshNow(context) }
        }
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
        val request = ImageRequest.Builder(context).data(url).size(384, 384).allowHardware(false)
            .transformations(coil3.transform.RoundedCornersTransformation(36f)).build()
        val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult ?: return null
        return runCatching { result.image.toBitmap() }.getOrNull()
    }

    fun refreshNow(context: Context) = refresh(context, AppGraph.instance.player.state.value, art.second)

    // Layout per widget size (Android 12+ picks the biggest one that fits): 1×1 up to the whole screen.
    private val variants = listOf(
        PlayerWidget::class.java to listOf(
            40f to 40f to R.layout.widget_tiny,
            100f to 40f to R.layout.widget_small,
            100f to 100f to R.layout.widget_square,
            180f to 40f to R.layout.widget_player,
            180f to 120f to R.layout.widget_player_large,
            200f to 230f to R.layout.widget_huge,
        ),
        MinimalWidget::class.java to listOf(
            40f to 40f to R.layout.widget_minimal_tiny,
            120f to 40f to R.layout.widget_minimal,
        ),
        CoverWidget::class.java to listOf(
            40f to 40f to R.layout.widget_tiny,
            100f to 100f to R.layout.widget_square,
        ),
    )

    fun refresh(context: Context, s: PlayerState, bitmap: Bitmap?) {
        val manager = AppWidgetManager.getInstance(context)
        for ((cls, sizes) in variants) {
            val ids = manager.getAppWidgetIds(ComponentName(context, cls))
            if (ids.isEmpty()) continue
            val views = if (Build.VERSION.SDK_INT >= 31) RemoteViews(
                sizes.associate { (size, layout) -> android.util.SizeF(size.first, size.second) to build(context, layout, s, bitmap) },
            ) else build(context, sizes.first { it.second == R.layout.widget_player || it.second == R.layout.widget_minimal || it.second == R.layout.widget_square }.second, s, bitmap)
            manager.updateAppWidget(ids, views)
        }
    }

    // follows the app's design style: Material (system colors), Liquid Glass (frosted), Windows (acrylic)
    private fun background(): Int = when (AppGraph.instance.settings.value.designStyle) {
        app.nocturne.data.DesignStyle.GLASS -> R.drawable.widget_bg_glass
        app.nocturne.data.DesignStyle.WINDOWS -> R.drawable.widget_bg_windows
        else -> R.drawable.widget_bg
    }

    private fun build(context: Context, layout: Int, s: PlayerState, bitmap: Bitmap?): RemoteViews {
        val views = RemoteViews(context.packageName, layout)
        views.setInt(R.id.widget_root, "setBackgroundResource", background())
        val light = layout == R.layout.widget_minimal || layout == R.layout.widget_minimal_tiny
        views.setTextViewText(R.id.widget_title, s.current?.title ?: "Nocturne")
        views.setTextViewText(R.id.widget_artist, s.current?.artistNames.orEmpty())
        if (bitmap != null) views.setImageViewBitmap(R.id.widget_art, bitmap)
        else views.setImageViewResource(R.id.widget_art, R.drawable.ic_launcher_foreground)
        views.setImageViewResource(
            R.id.widget_play,
            if (light) (if (s.isPlaying) R.drawable.ic_w_pause_light else R.drawable.ic_w_play_light)
            else (if (s.isPlaying) R.drawable.ic_w_pause else R.drawable.ic_w_play),
        )
        views.setOnClickPendingIntent(R.id.widget_play, action(context, NocturneWidget.ACTION_PLAY, 1))
        views.setOnClickPendingIntent(R.id.widget_next, action(context, NocturneWidget.ACTION_NEXT, 2))
        views.setOnClickPendingIntent(R.id.widget_prev, action(context, NocturneWidget.ACTION_PREV, 3))
        val open = PendingIntent.getActivity(
            context, 4, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        views.setOnClickPendingIntent(R.id.widget_root, open)
        return views
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
