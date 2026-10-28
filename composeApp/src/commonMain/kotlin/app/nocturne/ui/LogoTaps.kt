package app.nocturne.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import app.nocturne.AppGraph
import app.nocturne.nowMillis
import app.nocturne.ui.sound.Sfx

/** Logo: back to Home and reload it; ten quick taps say hi. */
object LogoTaps {
    var refresh by mutableIntStateOf(0)
        private set
    var at = 0L
        private set
    private var count = 0
    private var last = 0L

    fun tap() {
        val now = nowMillis()
        count = if (now - last < 1200) count + 1 else 1
        last = now
        if (count >= 10) {
            count = 0
            Sfx.voice()
        }
        val nav = AppGraph.instance.navigator
        nav.selectTab(Screen.Home)
        at = now
        refresh++
    }
}
