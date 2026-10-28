package app.nocturne.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nocturne.AppGraph
import app.nocturne.ui.sound.Sfx
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private var introShown = false

/** Short splash on launch: the logo swells in with a ripple, the name fades up, then it all lifts away. */
@Composable
fun StartupIntro() {
    val settings = AppGraph.instance.settings.value
    var visible by remember { mutableStateOf(!introShown && settings.startupIntro && !Demo.enabled) }
    if (!visible) return
    val scale = remember { Animatable(0.55f) }
    val ring = remember { Animatable(0f) }
    val name = remember { Animatable(0f) }
    val out = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        introShown = true
        if (settings.startupSound) Sfx.startup()
        launch { scale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 260f)) }
        launch { ring.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }
        delay(280)
        name.animateTo(1f, tween(500, easing = FastOutSlowInEasing))
        delay(650)
        out.animateTo(0f, tween(380, easing = FastOutSlowInEasing))
        visible = false
    }
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.fillMaxSize()
            .graphicsLayer { alpha = out.value }
            .background(cs.background),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(260.dp)) {
            val r = size.minDimension / 2 * (0.35f + 0.65f * ring.value)
            drawCircle(cs.primary.copy(alpha = 0.35f * (1f - ring.value)), r, style = Stroke(3.dp.toPx()))
            drawCircle(cs.primary.copy(alpha = 0.18f * (1f - ring.value)), r * 0.78f, style = Stroke(2.dp.toPx()))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            NocturneLogo(
                Modifier.size(104.dp).graphicsLayer {
                    scaleX = scale.value * (1f + 0.08f * (1f - out.value))
                    scaleY = scaleX
                    alpha = ((scale.value - 0.55f) / 0.45f).coerceIn(0f, 1f)
                },
            )
            Spacer(Modifier.height(18.dp))
            Text(
                "Nocturne",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = (6f * (1f - name.value) + 1f).sp,
                modifier = Modifier.graphicsLayer {
                    alpha = name.value
                    translationY = (1f - name.value) * 14.dp.toPx()
                },
            )
        }
    }
}
