package app.nocturne.ui

import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import app.nocturne.ui.theme.LocalDarkTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

val LocalGlass = staticCompositionLocalOf<HazeState?> { null }

@Composable
fun Modifier.liquidGlass(shape: Shape): Modifier {
    val state = LocalGlass.current ?: return this
    val dark = LocalDarkTheme.current
    val surface = MaterialTheme.colorScheme.surface
    return this
        .shadow(18.dp, shape, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.25f))
        .clip(shape)
        .hazeEffect(
            state,
            HazeStyle(
                backgroundColor = surface,
                tints = listOf(HazeTint(surface.copy(alpha = if (dark) 0.42f else 0.5f))),
                blurRadius = 26.dp,
                noiseFactor = 0f,
            ),
        )
        .border(
            1.dp,
            Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) 0.32f else 0.7f), Color.White.copy(alpha = 0.06f))),
            shape,
        )
}
