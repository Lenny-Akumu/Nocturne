package app.nocturne.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.nocturne.PlatformKind
import app.nocturne.platformKind

// Pull-to-refresh on phones only; on desktop a fast scroll-up would just flash the spinner.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    state: PullToRefreshState = rememberPullToRefreshState(),
    indicator: @Composable BoxScope.() -> Unit = { PullToRefreshDefaults.Indicator(state = state, isRefreshing = isRefreshing, modifier = Modifier.align(Alignment.TopCenter)) },
    content: @Composable BoxScope.() -> Unit,
) {
    if (platformKind == PlatformKind.DESKTOP) Box(modifier, content = content)
    else PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, modifier = modifier, state = state, indicator = indicator, content = content)
}
