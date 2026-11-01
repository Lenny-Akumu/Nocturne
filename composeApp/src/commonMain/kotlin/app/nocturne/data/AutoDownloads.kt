package app.nocturne.data

import app.nocturne.AppGraph
import app.nocturne.model.Source
import app.nocturne.nowMillis
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/** Wi-Fi or Ethernet (not a phone's mobile data or a metered hotspot). */
expect fun unmeteredNetwork(): Boolean

/** Settings → Storage → Auto-download: keeps liked songs (and smart playlists) offline. Off by default. */
object AutoDownloads {
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    fun start(graph: AppGraph) {
        graph.scope.launch {
            combine(graph.library.state, graph.settings.state) { l, s -> l to s }.debounce(5000).collect { run(graph) }
        }
        // network can change without any library edit
        graph.scope.launch {
            while (true) {
                delay(10 * 60_000L)
                run(graph)
            }
        }
    }

    private fun run(graph: AppGraph) {
        val s = graph.settings.value
        if (!s.autoDownload) return
        if (s.autoDownloadWifiOnly && !runCatching { unmeteredNetwork() }.getOrDefault(false)) return
        val lib = graph.library.state.value
        val smart = if (s.autoDownloadSmart) s.smartPlaylists.flatMap { SmartPlaylists.evaluate(it, lib, nowMillis()) } else emptyList()
        val failed = graph.downloads.errors.value.keys
        val todo = (lib.liked + smart).distinctBy { it.key }
            .filter { it.source != Source.LOCAL && !it.isPodcast && !graph.downloads.isDownloaded(it) && it.key !in failed }
        if (todo.isNotEmpty()) graph.downloads.enqueue(todo)
    }
}
