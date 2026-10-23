package app.nocturne

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.preferredFrameRate
import androidx.compose.ui.window.ComposeUIViewController
import app.nocturne.model.MediaEntry
import app.nocturne.model.SearchFilter
import app.nocturne.player.IosAudioEngine
import app.nocturne.ui.App
import app.nocturne.ui.Screen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIViewController

private val graph by lazy { AppGraph.init(IosAudioEngine()) }

// Called from SwiftUI onOpenURL (nocturne://join?code=…).
fun handleUrl(url: String) {
    app.nocturne.party.PartyLinks.handle(url)
}

fun MainViewController(): UIViewController {
    val g = graph
    val shot = NSProcessInfo.processInfo.arguments.filterIsInstance<String>().firstOrNull { it.startsWith("--shot=") }?.substringAfter('=')
    when (shot) {
        "search" -> g.navigator.selectTab(Screen.Search)
        "library" -> g.navigator.selectTab(Screen.Library)
        "settings" -> g.navigator.push(Screen.Settings)
        "player" -> g.scope.launch {
            val tracks = runCatching { g.youtube.search("lofi hip hop", SearchFilter.SONGS) }.getOrDefault(emptyList())
                .filterIsInstance<MediaEntry.Song>().map { it.track }
            if (tracks.isNotEmpty()) {
                g.player.play(tracks, 0, "lofi hip hop")
                delay(1500)
                g.navigator.playerExpanded = true
            }
        }
    }
    return ComposeUIViewController {
        // Settings → Refresh rate: Compose's display link follows this vote (ProMotion is unlocked in Info.plist).
        val hz = g.settings.state.collectAsState().value.refreshRate
        Box(if (hz > 0) Modifier.fillMaxSize().preferredFrameRate(hz.toFloat()) else Modifier.fillMaxSize()) { App() }
    }
}

// ---- home screen widget (Swift side writes the App Group files and reloads WidgetKit) ----

fun widgetState(): String {
    val s = graph.player.state.value
    val t = s.current
    return kotlinx.serialization.json.buildJsonObject {
        put("title", kotlinx.serialization.json.JsonPrimitive(t?.title ?: "Nocturne"))
        put("artist", kotlinx.serialization.json.JsonPrimitive(t?.artistNames.orEmpty()))
        put("playing", kotlinx.serialization.json.JsonPrimitive(s.isPlaying))
        put("art", kotlinx.serialization.json.JsonPrimitive(t?.artworkUrl.orEmpty()))
        put("style", kotlinx.serialization.json.JsonPrimitive(graph.settings.value.designStyle.name))
    }.toString()
}

fun observeWidget(onChange: () -> Unit) {
    graph.scope.launch {
        graph.player.state.collect { s ->
            val key = listOf(s.current?.key, s.isPlaying, s.current?.artworkUrl, graph.settings.value.designStyle)
            if (key != lastWidget) { lastWidget = key; onChange() }
        }
    }
}

private var lastWidget: Any? = null

fun widgetCommand(cmd: String) {
    val p = graph.player
    when (cmd) {
        "toggle" -> p.toggle()
        "next" -> p.next()
        "prev" -> p.previous()
    }
}
