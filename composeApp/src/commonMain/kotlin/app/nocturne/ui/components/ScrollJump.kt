package app.nocturne.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import app.nocturne.i18n.t
import app.nocturne.ui.kit.Icon
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A list with a centred arrow that shows up while scrolling: back to the playing item, or to the top. */
@Composable
fun JumpBox(state: LazyListState, active: Int? = null, modifier: Modifier = Modifier.fillMaxSize(), content: @Composable BoxScope.() -> Unit) {
    Box(modifier) {
        content()
        ScrollJump(state, active, Modifier.align(Alignment.TopCenter).padding(top = 10.dp))
    }
}

@Composable
fun ScrollJump(state: LazyListState, active: Int?, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val activeHidden by remember(active) {
        derivedStateOf { active != null && active >= 0 && state.layoutInfo.visibleItemsInfo.none { it.index == active } }
    }
    val far by remember { derivedStateOf { state.firstVisibleItemIndex >= 3 } }
    var awake by remember { mutableStateOf(false) }
    LaunchedEffect(state.isScrollInProgress) {
        if (state.isScrollInProgress) awake = true
        else { delay(2500); awake = false }
    }
    val toActive = activeHidden
    val target = if (toActive) active!! else 0
    val up = target <= state.firstVisibleItemIndex
    val cs = MaterialTheme.colorScheme
    AnimatedVisibility(awake && (toActive || far), modifier, enter = fadeIn() + scaleIn(initialScale = 0.7f), exit = fadeOut() + scaleOut(targetScale = 0.7f)) {
        Row(
            Modifier.shadow(8.dp, CircleShape).background(cs.primaryContainer, CircleShape)
                .clickable { scope.launch { if (kotlin.math.abs(state.firstVisibleItemIndex - target) > 40) state.scrollToItem(target) else state.animateScrollToItem(target) } }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (up) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward, null, Modifier.size(18.dp), tint = cs.onPrimaryContainer)
            Spacer(Modifier.width(6.dp))
            Text(if (toActive) t("Now playing") else t("To the top"), color = cs.onPrimaryContainer, style = MaterialTheme.typography.labelLarge)
        }
    }
}
