package app.nocturne

import android.app.Application
import app.nocturne.player.AndroidAudioEngine

class NocturneApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppDirs.root = filesDir
        val cacheMb = runCatching { app.nocturne.data.SettingsStore().value.cacheSizeMb }.getOrDefault(512)
        AppGraph.init(AndroidAudioEngine(this, cacheMb))
    }
}
