package app.nocturne.ui.kit

import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import app.nocturne.ui.icons.pixelFor

// True with the Minecraft font: icons swap to their pixel-art versions.
val LocalPixelIcons = staticCompositionLocalOf { false }

// Fluent UI icon set in place of Material (Settings → Appearance).
val LocalFluentIcons = staticCompositionLocalOf { false }

// Drop-in for material3.Icon that honours [LocalPixelIcons].
@Composable
fun Icon(imageVector: ImageVector, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    val v = when {
        LocalPixelIcons.current -> pixelFor(imageVector) ?: imageVector
        LocalFluentIcons.current -> app.nocturne.ui.icons.fluentFor(imageVector) ?: imageVector
        else -> imageVector
    }
    androidx.compose.material3.Icon(v, contentDescription, modifier, tint)
}

@Composable
fun Icon(painter: Painter, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) =
    androidx.compose.material3.Icon(painter, contentDescription, modifier, tint)

@Composable
fun Icon(bitmap: ImageBitmap, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) =
    androidx.compose.material3.Icon(bitmap, contentDescription, modifier, tint)
