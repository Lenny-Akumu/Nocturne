package app.nocturne

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
    return ComposeUIViewController { App() }
}
