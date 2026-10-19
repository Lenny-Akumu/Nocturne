package app.nocturne.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.nocturne.model.CollectionRef

sealed interface Screen {
    data object Home : Screen
    data object Search : Screen
    data object Library : Screen
    data object Settings : Screen
    data class Collection(val ref: CollectionRef) : Screen
    data class SettingsPage(val page: app.nocturne.ui.screens.SettingsCategory) : Screen
}

class Navigator {
    private val tabs = listOf(Screen.Home, Screen.Search, Screen.Library)
    val stack = mutableStateListOf<Screen>(Screen.Home)

    var playerExpanded by mutableStateOf(false)
    var homeRefresh by mutableStateOf(0)

    val current: Screen get() = stack.last()
    val currentTab: Screen get() = stack.lastOrNull { it in tabs || it == Screen.Settings } ?: Screen.Home
    val canGoBack: Boolean get() = playerExpanded || stack.size > 1

    fun push(screen: Screen) {
        playerExpanded = false
        if (stack.last() != screen) stack.add(screen)
    }

    fun selectTab(tab: Screen) {
        if (current == tab) {
            if (tab == Screen.Home) homeRefresh++
            return
        }
        stack.clear()
        if (tab != Screen.Home) stack.add(Screen.Home)
        stack.add(tab)
    }

    fun requestCollapse() {
        playerExpanded = false
    }

    fun back(): Boolean {
        if (playerExpanded) {
            playerExpanded = false
            return true
        }
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }
}
