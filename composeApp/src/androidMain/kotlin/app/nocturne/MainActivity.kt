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

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent { App() }
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        controller = MediaController.Builder(this, SessionToken(this, ComponentName(this, PlaybackService::class.java))).buildAsync()
    }

    override fun onStop() {
        controller?.let { MediaController.releaseFuture(it) }
        controller = null
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val text = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            else -> null
        } ?: return
        val url = Regex("https?://\\S+").find(text)?.value ?: return
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
