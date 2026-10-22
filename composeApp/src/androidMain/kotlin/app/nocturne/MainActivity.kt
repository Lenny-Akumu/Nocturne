package app.nocturne

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.nocturne.model.MediaEntry
import app.nocturne.player.PlaybackService
import app.nocturne.ui.App
import app.nocturne.ui.Screen
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var controller: ListenableFuture<MediaController>? = null
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    private var audioCallback: ((Boolean) -> Unit)? = null
    private val audioPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        audioCallback?.invoke(ok)
        audioCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        app.nocturne.source.LocalAudioPermission.launcher = { perm, cb -> audioCallback = cb; audioPermission.launch(perm) }
        setContent { App() }
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        AppGraph.instance.sync.foreground.value = true
        controller = MediaController.Builder(this, SessionToken(this, ComponentName(this, PlaybackService::class.java))).buildAsync()
    }

    override fun onStop() {
        AppGraph.instance.sync.foreground.value = false
        controller?.let { MediaController.releaseFuture(it) }
        controller = null
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.data?.scheme == "nocturne" && intent.data?.host in setOf("join", "pair")) {
            intent.dataString?.let { app.nocturne.party.PartyLinks.handle(it) }
            return
        }
        if (intent?.data?.scheme == "nocturne") {
            intent.dataString?.let { url ->
                // No waiting sign-in (process was killed while in the browser): finish it here.
                if (AuthRedirect.pending?.complete(url) != true && intent.data?.host == "spotify-callback") {
                    val graph = AppGraph.instance
                    graph.scope.launch { runCatching { graph.spotify.completeSignIn(url) }.onFailure { e -> e.message?.let(graph::toast) } }
                }
            }
            return
        }
        val text = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            else -> null
        } ?: return
        val url = Regex("https?://\\S+").find(text)?.value ?: return
        if (app.nocturne.party.PartyLinks.handle(url)) return
        val graph = AppGraph.instance
        graph.scope.launch {
            when (val entry = runCatching { graph.openUrl(url) }.getOrNull()) {
                is MediaEntry.Song -> graph.player.playTrack(entry.track)
                is MediaEntry.Collection -> graph.navigator.push(Screen.Collection(entry.ref))
                null -> Unit
            }
        }
    }
}
