package app.nocturne

import android.app.Application
import app.nocturne.player.AndroidAudioEngine

class NocturneApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppDirs.root = filesDir
        AndroidContext.app = this
        val cacheMb = runCatching { app.nocturne.data.SettingsStore().value.cacheSizeMb }.getOrDefault(512)
        AppGraph.init(AndroidAudioEngine(this, cacheMb))
        Widgets.start(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level < TRIM_MEMORY_UI_HIDDEN) return
        runCatching { coil3.SingletonImageLoader.get(this).memoryCache?.let { if (level >= TRIM_MEMORY_BACKGROUND) it.clear() else it.trimToSize(it.maxSize / 2) } }
        runCatching { AppGraph.instance.clearMemoryCaches() }
    }
}
