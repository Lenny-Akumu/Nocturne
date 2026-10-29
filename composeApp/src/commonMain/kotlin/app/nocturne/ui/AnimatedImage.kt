package app.nocturne.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Draws a still image or an animated GIF, cropped to fill. */
@Composable
expect fun AnimatedImage(bytes: ByteArray, modifier: Modifier = Modifier)
