package app.nocturne.data

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import app.nocturne.AndroidContext

actual fun unmeteredNetwork(): Boolean {
    val cm = AndroidContext.app.getSystemService(ConnectivityManager::class.java) ?: return false
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
}
