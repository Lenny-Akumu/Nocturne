package app.nocturne.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import app.nocturne.AppGraph
import app.nocturne.MainActivity

class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val engine = AppGraph.instance.engine as AndroidAudioEngine
        val intent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, engine.sessionPlayer)
            .setSessionActivity(intent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val graph = AppGraph.instance
        if (graph.settings.value.stopOnTaskRemoved || !graph.player.state.value.isPlaying) {
            graph.player.pause()
            stopSelf()
        }
    }

    override fun onDestroy() {
        session?.run {
            release()
            session = null
        }
        super.onDestroy()
    }
}
