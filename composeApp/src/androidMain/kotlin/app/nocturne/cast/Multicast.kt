package app.nocturne.cast

import android.content.Context
import android.net.wifi.WifiManager
import app.nocturne.AndroidContext

actual fun acquireMulticastLock(): () -> Unit {
    val wifi = AndroidContext.app.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return {}
    val lock = runCatching { wifi.createMulticastLock("nocturne-cast").apply { setReferenceCounted(false); acquire() } }.getOrNull()
    return { runCatching { lock?.release() } }
}
