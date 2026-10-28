package app.nocturne.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import app.nocturne.model.Track

/** Stable keys for lists that may hold the same track twice: "key#n" for its n-th copy. */
fun occurrenceKeys(tracks: List<Track>): List<String> {
    val seen = HashMap<String, Int>()
    return tracks.map { t -> val n = seen[t.key] ?: 0; seen[t.key] = n + 1; t.key + "#" + n }
}

/** Removed rows fade away, the rest glide into place. */
fun LazyItemScope.rowMotion(): Modifier = Modifier.animateItem(
    fadeInSpec = tween(220),
    placementSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset(1, 1)),
    fadeOutSpec = tween(260),
)

/** Quick heart "pop" when [liked] turns on. */
fun Modifier.likePop(liked: Boolean): Modifier = composed {
    val k = remember { Animatable(1f) }
    var prev by remember { mutableStateOf(liked) }
    LaunchedEffect(liked) {
        if (liked && !prev) {
            k.snapTo(0.7f)
            k.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 500f))
        }
        prev = liked
    }
    graphicsLayer { scaleX = k.value; scaleY = k.value }
}
