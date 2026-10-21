package app.nocturne.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import app.nocturne.AppGraph
import app.nocturne.data.IconBackground

/** The app icon as chosen in Settings → Appearance → App icon. */
@Composable
fun NocturneLogo(modifier: Modifier = Modifier, background: Boolean = true) {
    val s by AppGraph.instance.settings.state.collectAsState()
    NocturneIcon(if (background) s.iconBackground else IconBackground.NONE, s.iconPalette, modifier)
}
