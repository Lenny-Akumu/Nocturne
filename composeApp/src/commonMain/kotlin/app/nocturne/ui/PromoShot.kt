package app.nocturne.ui

import app.nocturne.AppGraph
import app.nocturne.data.DesignStyle
import app.nocturne.model.MediaEntry
import app.nocturne.model.SearchFilter
import app.nocturne.ui.screens.SettingsCategory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Store screenshots / promo recordings from CI: which sheet or pane the player should open by itself.
object PromoShot {
    var open: String? = null
}

/** Puts the app on a given screen ([shot]) in a given design [style]; used by CI screenshot runs. */
fun runPromoShot(g: AppGraph, shot: String?, style: String?) {
    style?.let { name -> DesignStyle.entries.firstOrNull { it.name == name }?.let { st -> g.settings.update { it.copy(designStyle = st) } } }
    if (shot == "demo" || shot == "demo-short") { Demo.start(g, if (shot == "demo") "full" else "short"); return }
    if (shot in setOf("eq", "connect", "lyrics", "queue", "comments")) PromoShot.open = shot
    when (shot) {
        "appearance" -> g.navigator.push(Screen.SettingsPage(SettingsCategory.APPEARANCE))
        "devices" -> g.navigator.push(Screen.SettingsPage(SettingsCategory.DEVICES))
        "search" -> g.navigator.selectTab(Screen.Search)
        "library" -> g.navigator.selectTab(Screen.Library)
        "settings" -> g.navigator.push(Screen.Settings)
        "player", "eq", "connect", "lyrics", "queue", "comments" -> g.scope.launch {
            val tracks = runCatching { g.youtube.search("lofi hip hop", SearchFilter.SONGS) }.getOrDefault(emptyList())
                .filterIsInstance<MediaEntry.Song>().map { it.track }
            if (tracks.isNotEmpty()) {
                g.player.play(tracks, 0, "lofi hip hop")
                delay(1500)
                g.navigator.playerExpanded = true
            }
        }
    }
}
