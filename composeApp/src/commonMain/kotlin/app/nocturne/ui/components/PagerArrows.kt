package app.nocturne.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.nocturne.ui.kit.Icon
import kotlinx.coroutines.launch

// Prev / next page buttons for sideways shelves (mouse users can't swipe).
@Composable
fun PagerArrows(state: LazyListState, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    fun step() = (state.layoutInfo.visibleItemsInfo.count { it.offset >= 0 && it.offset + it.size <= state.layoutInfo.viewportEndOffset }).coerceAtLeast(1)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(false, true).forEach { fwd ->
            val enabled = if (fwd) state.canScrollForward else state.canScrollBackward
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(cs.surfaceContainerHigh).alpha(if (enabled) 1f else 0.4f)
                    .clickable(enabled = enabled) {
                        scope.launch {
                            val target = state.firstVisibleItemIndex + if (fwd) step() else -step()
                            state.animateScrollToItem(target.coerceIn(0, (state.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)))
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (fwd) Icons.AutoMirrored.Rounded.KeyboardArrowRight else Icons.AutoMirrored.Rounded.KeyboardArrowLeft, null, Modifier.size(22.dp), tint = cs.onSurface)
            }
        }
    }
}

/** Desktop only: round prev/next buttons over the edges of a sideways row; wrap the row and this in a Box. */
@Composable
fun androidx.compose.foundation.layout.BoxScope.EdgeArrows(state: LazyListState) {
    if (app.nocturne.platformKind != app.nocturne.PlatformKind.DESKTOP) return
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    fun step() = (state.layoutInfo.visibleItemsInfo.count { it.offset >= 0 && it.offset + it.size <= state.layoutInfo.viewportEndOffset } - 1).coerceAtLeast(1)
    listOf(false, true).forEach { fwd ->
        val enabled = if (fwd) state.canScrollForward else state.canScrollBackward
        if (enabled) Box(
            Modifier.align(if (fwd) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(horizontal = 6.dp).size(40.dp).clip(CircleShape)
                .background(cs.surfaceContainerHighest.copy(alpha = 0.92f))
                .clickable {
                    scope.launch {
                        val target = state.firstVisibleItemIndex + if (fwd) step() else -step()
                        state.animateScrollToItem(target.coerceIn(0, (state.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)))
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (fwd) Icons.AutoMirrored.Rounded.KeyboardArrowRight else Icons.AutoMirrored.Rounded.KeyboardArrowLeft, null, Modifier.size(24.dp), tint = cs.onSurface)
        }
    }
}
