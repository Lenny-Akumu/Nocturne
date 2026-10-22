package app.nocturne

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout

@SuppressLint("StaticFieldLeak")
object AndroidContext {
    lateinit var app: Context
}

object AuthRedirect {
    var pending: CompletableDeferred<String>? = null
}

actual val spotifyRedirectUri: String = "nocturne://spotify-callback"

actual suspend fun authorizeInBrowser(url: String): String {
    val deferred = CompletableDeferred<String>()
    AuthRedirect.pending = deferred
    val view = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    // Ask for a Custom Tab (in-app browser tab) without the androidx.browser dependency.
    val tab = Intent(view).putExtras(Bundle().apply { putBinder("android.support.customtabs.extra.SESSION", null) })
    return try {
        try {
            AndroidContext.app.startActivity(tab)
        } catch (e: ActivityNotFoundException) {
            AndroidContext.app.startActivity(view)
        }
        withTimeout(5 * 60_000L) { deferred.await() }
    } finally {
        AuthRedirect.pending = null
    }
}
