package app.nocturne

import android.app.Activity
import android.os.Build
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Applies Settings.refreshRate (0 = the screen's highest rate) to the activity window. */
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
        val same = display.supportedModes.filter { it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight }
        // auto: the fastest rate the screen supports (many phones keep apps at 60 Hz unless asked)
        val mode = if (hz <= 0) same.maxByOrNull { it.refreshRate } else same.minByOrNull { abs(it.refreshRate - hz) }
        attrs.preferredDisplayModeId = mode?.modeId ?: 0
        attrs.preferredRefreshRate = mode?.refreshRate ?: 0f
        window.attributes = attrs
    }
}
