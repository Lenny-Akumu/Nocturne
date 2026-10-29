package app.nocturne.mpris

import app.nocturne.AppGraph
import app.nocturne.MainWindow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.types.Variant

@DBusInterfaceName("org.mpris.MediaPlayer2")
interface MediaPlayer2 : DBusInterface {
    fun Raise()
    fun Quit()
}

@DBusInterfaceName("org.mpris.MediaPlayer2.Player")
interface MediaPlayer2Player : DBusInterface {
    fun Next()
    fun Previous()
    fun Pause()
    fun PlayPause()
    fun Stop()
    fun Play()
    fun Seek(offset: Long)
    fun SetPosition(trackId: DBusPath, position: Long)
    fun OpenUri(uri: String)
}

private const val PATH = "/org/mpris/MediaPlayer2"
private const val ROOT = "org.mpris.MediaPlayer2"
private const val PLAYER = "org.mpris.MediaPlayer2.Player"

/** MPRIS2 on the session bus: GNOME / KDE media widgets, media keys and playerctl drive Nocturne. */
object Mpris {
    private var conn: DBusConnection? = null

    fun start(scope: CoroutineScope) {
        if (conn != null) return
        val c = runCatching { DBusConnectionBuilder.forSessionBus().build() }.getOrNull() ?: return
        val obj = Obj(scope)
        runCatching {
            c.requestBusName("org.mpris.MediaPlayer2.nocturne")
            c.exportObject(obj)
        }.onFailure { runCatching { c.close() }; return }
        conn = c
        val player = AppGraph.instance.player
        scope.launch {
            player.state.map { Triple(it.current?.key, it.isPlaying, it.current?.artworkUrl) }.distinctUntilChanged().collect {
                runCatching {
                    c.sendMessage(Properties.PropertiesChanged(PATH, PLAYER, obj.playerProps(), emptyList()))
                }
            }
        }
    }

    private class Obj(private val scope: CoroutineScope) : MediaPlayer2, MediaPlayer2Player, Properties {
        private val graph get() = AppGraph.instance
        private val p get() = graph.player
        private fun ui(block: () -> Unit) { scope.launch { block() } }

        override fun getObjectPath() = PATH
        override fun isRemote() = false

        override fun Raise() = ui { MainWindow.show() }
        override fun Quit() = Unit
        override fun Next() = ui { p.next() }
        override fun Previous() = ui { p.previous() }
        override fun Pause() = ui { p.pause() }
        override fun PlayPause() = ui { p.toggle() }
        override fun Stop() = ui { p.pause() }
        override fun Play() = ui { p.resume() }
        override fun Seek(offset: Long) = ui { p.seekBy(offset / 1000) }
        override fun SetPosition(trackId: DBusPath, position: Long) = ui { p.seekTo(position / 1000) }
        override fun OpenUri(uri: String) = Unit

        private fun trackPath(key: String?) = DBusPath("/app/nocturne/track/" + (key ?: "none").replace(Regex("[^A-Za-z0-9_]"), "_"))

        fun playerProps(): Map<String, Variant<*>> {
            val s = p.state.value
            val t = s.current
            val meta = buildMap<String, Variant<*>> {
                put("mpris:trackid", Variant(trackPath(t?.key)))
                if (t != null) {
                    put("xesam:title", Variant(t.title))
                    put("xesam:artist", Variant(t.artists.map { it.name }.ifEmpty { listOf("") }, "as"))
                    t.album?.let { put("xesam:album", Variant(it)) }
                    t.artworkUrl?.let { put("mpris:artUrl", Variant(it)) }
                    val len = (if (s.durationMs > 0) s.durationMs else t.durationMs) * 1000
                    if (len > 0) put("mpris:length", Variant(len))
                }
            }
            return mapOf(
                "PlaybackStatus" to Variant(if (t == null) "Stopped" else if (s.isPlaying) "Playing" else "Paused"),
                "LoopStatus" to Variant("None"),
                "Rate" to Variant(1.0),
                "Shuffle" to Variant(s.shuffle),
                "Metadata" to Variant(meta, "a{sv}"),
                "Volume" to Variant(graph.settings.value.volume.toDouble()),
                "Position" to Variant(s.positionMs * 1000),
                "MinimumRate" to Variant(1.0),
                "MaximumRate" to Variant(1.0),
                "CanGoNext" to Variant(true),
                "CanGoPrevious" to Variant(true),
                "CanPlay" to Variant(t != null),
                "CanPause" to Variant(t != null),
                "CanSeek" to Variant(t != null),
                "CanControl" to Variant(true),
            )
        }

        private fun rootProps(): Map<String, Variant<*>> = mapOf(
            "CanQuit" to Variant(false),
            "CanRaise" to Variant(true),
            "HasTrackList" to Variant(false),
            "Identity" to Variant("Nocturne"),
            "DesktopEntry" to Variant("nocturne"),
            "SupportedUriSchemes" to Variant(listOf("nocturne"), "as"),
            "SupportedMimeTypes" to Variant(listOf("audio/mpeg"), "as"),
        )

        override fun GetAll(iface: String): Map<String, Variant<*>> = if (iface == ROOT) rootProps() else playerProps()

        @Suppress("UNCHECKED_CAST")
        override fun <A : Any?> Get(iface: String, prop: String): A = GetAll(iface)[prop]?.value as A

        override fun <A : Any?> Set(iface: String, prop: String, value: A) {
            if (prop == "Volume") (value as? Number)?.let { v -> ui { p.setVolume(v.toFloat().coerceIn(0f, 1f)) } }
        }
    }
}
