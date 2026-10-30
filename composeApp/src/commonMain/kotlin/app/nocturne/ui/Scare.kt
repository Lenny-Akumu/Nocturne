package app.nocturne.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.StrokeCap
import app.nocturne.ui.sound.Sfx
import kotlinx.coroutines.launch
import kotlin.math.sin

/** Black cat jump scare (liking a song, 50/50; off in Settings → Appearance). No strobing. */
object Scare {
    var id by mutableIntStateOf(0)
        private set

    fun boo() {
        id++
        app.nocturne.data.Achievements.unlock("blackcat")
        Sfx.meow()
    }
}

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

@Composable
fun ScareOverlay() {
    val id = Scare.id
    if (id == 0) return
    val p = remember(id) { Animatable(0f) }
    val shake = remember(id) { Animatable(0f) }
    var done by remember(id) { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(id) {
        launch { shake.animateTo(1f, tween(1500, easing = LinearEasing)) }
        p.animateTo(1f, tween(1500, easing = FastOutSlowInEasing))
        done = true
    }
    if (done) return
    Canvas(Modifier.fillMaxSize()) {
        val v = p.value
        // in fast, hold, fade out
        val alpha = when {
            v < 0.08f -> v / 0.08f
            v < 0.7f -> 1f
            else -> (1f - v) / 0.3f
        }.coerceIn(0f, 1f)
        val grow = if (v < 0.12f) 0.35f + (v / 0.12f) * 0.8f else 1.15f - 0.08f * ((v - 0.12f) / 0.88f)
        drawRect(Brush.radialGradient(listOf(Color(0xFF2A0000).copy(alpha = 0.85f * alpha), Color.Black.copy(alpha = 0.95f * alpha)), center, size.maxDimension * 0.7f))
        val jitter = if (v < 0.5f) sin(shake.value * 160f) * 6f * (1f - v * 2) else 0f
        val k = size.minDimension / 200f * grow
        translate(center.x - 100f * k + jitter, center.y - 100f * k) {
            scale(k, k, Offset.Zero) { drawCat(alpha) }
        }
    }
}

// 200×200 unit cat face: ears, head, glowing slit eyes, fangs.
private fun DrawScope.drawCat(alpha: Float) {
    val black = Color(0xFF050505).copy(alpha = alpha)
    val head = Path().apply {
        moveTo(30f, 40f); lineTo(62f, 70f)
        quadraticTo(100f, 52f, 138f, 70f); lineTo(170f, 40f)
        lineTo(176f, 118f)
        quadraticTo(170f, 186f, 100f, 192f)
        quadraticTo(30f, 186f, 24f, 118f); close()
    }
    drawPath(head, black)
    // eyes: glow + slit pupils
    for (cx in listOf(70f, 130f)) {
        drawOval(Brush.radialGradient(listOf(Color(0xFFFFF06A).copy(alpha = alpha), Color(0xFFB8FF3A).copy(alpha = 0.0f)), Offset(cx, 108f), 34f), Offset(cx - 34f, 74f), Size(68f, 68f))
        drawOval(Color(0xFFE9FF5A).copy(alpha = alpha), Offset(cx - 18f, 96f), Size(36f, 24f))
        drawOval(Color.Black.copy(alpha = alpha), Offset(cx - 3.5f, 96f), Size(7f, 24f))
    }
    // open mouth with fangs
    val mouth = Path().apply { moveTo(78f, 150f); quadraticTo(100f, 178f, 122f, 150f); quadraticTo(100f, 160f, 78f, 150f); close() }
    drawPath(mouth, Color(0xFF5A0000).copy(alpha = alpha))
    val fang = Color.White.copy(alpha = alpha)
    drawPath(Path().apply { moveTo(84f, 151f); lineTo(90f, 151f); lineTo(87f, 163f); close() }, fang)
    drawPath(Path().apply { moveTo(110f, 151f); lineTo(116f, 151f); lineTo(113f, 163f); close() }, fang)
    // whiskers
    val w = Color(0xFF2A2A2A).copy(alpha = alpha)
    for (s in listOf(-1f, 1f)) for (dy in listOf(-6f, 2f, 10f)) {
        drawLine(w, Offset(100f + s * 26f, 140f + dy * 0.3f), Offset(100f + s * 92f, 132f + dy), strokeWidth = 1.6f, cap = StrokeCap.Round)
    }
}
