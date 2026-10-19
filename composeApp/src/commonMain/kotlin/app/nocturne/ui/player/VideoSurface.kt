package app.nocturne.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun VideoSurface(url: String, positionMs: Long, playing: Boolean, speed: Float, modifier: Modifier = Modifier)
