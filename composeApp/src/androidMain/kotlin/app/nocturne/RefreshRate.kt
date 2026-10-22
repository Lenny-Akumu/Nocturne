package app.nocturne

import android.app.Activity
import android.os.Build
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Applies Settings.refreshRate (0 = system default) to the activity window. */
internal object RefreshRate {
    fun observe(activity: Activity): Job = AppGraph.instance.scope.launch {
        AppGraph.instance.settings.state.map { it.refreshRate }.distinctUntilChanged().collect { hz ->
            runCatching { apply(activity, hz) }
        }
    }

    fun apply(activity: Activity, hz: Int) {
        if (activity.isFinishing || activity.isDestroyed) return
        val window = activity.window
        val attrs = window.attributes
        @Suppress("DEPRECATION")
        val display = (if (Build.VERSION.SDK_INT >= 30) activity.display else null) ?: window.windowManager.defaultDisplay
        val cur = display.mode
        // a mode at the current resolution whose rate is closest to the wish
        attrs.preferredDisplayModeId = if (hz <= 0) 0 else display.supportedModes
            .filter { it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight }
            .minByOrNull { abs(it.refreshRate - hz) }?.modeId ?: 0
        attrs.preferredRefreshRate = if (hz <= 0) 0f else hz.toFloat()
        window.attributes = attrs
    }
}
