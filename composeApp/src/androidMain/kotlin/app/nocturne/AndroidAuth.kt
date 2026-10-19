package app.nocturne

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
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
    AndroidContext.app.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    return try {
        withTimeout(5 * 60_000L) { deferred.await() }
    } finally {
        AuthRedirect.pending = null
    }
}
