package app.nocturne.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.math.sin

/** "67" in search: the whole screen tips like scales for 2 s (right corner up, then left). */
object SixSeven {
    var id by mutableIntStateOf(0)
        private set

    fun go() { id++; app.nocturne.data.Achievements.unlock("sixseven") }
}

@Composable
fun rememberSixSevenTilt(): Float {
    val id = SixSeven.id
    val t = remember { Animatable(0f) }
    LaunchedEffect(id) {
        if (id == 0) return@LaunchedEffect
        t.snapTo(0f)
        t.animateTo(1f, tween(2000, easing = LinearEasing))
        t.snapTo(0f)
    }
    val v = t.value
    if (v <= 0f || v >= 1f) return 0f
    // four swings, easing out at the end
    return -5f * sin(v * 4f * kotlin.math.PI.toFloat()) * (1f - v * v)
}
