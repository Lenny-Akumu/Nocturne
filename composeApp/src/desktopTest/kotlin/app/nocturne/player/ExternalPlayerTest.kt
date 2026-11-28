package app.nocturne.player

import app.nocturne.AppDirs
import app.nocturne.AppGraph
import app.nocturne.model.Source
import app.nocturne.model.Track
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExternalPlayerTest {
    private class FakeSpotify : ExternalPlayer {
        override val name = "Spotify"
        val calls = java.util.Collections.synchronizedList(mutableListOf<String>())
        @Volatile var now: ExternalState? = null
        override fun handles(track: Track) = track.source == Source.SPOTIFY
        override suspend fun start(tracks: List<Track>, ms: Long, play: Boolean) {
            calls += "start:" + tracks.joinToString(",") { it.id } + ":$play"
            now = ExternalState(tracks[0].id, ms, 200_000, play)
        }
        override suspend fun command(op: String, ms: Long, vol: Float) {
            calls += op
            if (op == "pause") now = now?.copy(playing = false)
        }
        override suspend fun poll() = now
    }

    private suspend fun until(what: String, ok: () -> Boolean) = withTimeout(8_000) { while (!ok()) delay(50) }.also { println("ok: $what") }

    @Test
    fun spotifySongsPlayInSpotifyAndTheQueueFollows() = runBlocking {
        AppDirs.root = java.io.File(System.getProperty("java.io.tmpdir"), "nocturne-ext-test").apply { deleteRecursively(); mkdirs() }
        val g = AppGraph.init(DemoAudioEngine())
        val fake = FakeSpotify()
        val p = g.player
        p.external = fake
        val s1 = Track("s1", Source.SPOTIFY, "One", durationMs = 200_000)
        val s2 = Track("s2", Source.SPOTIFY, "Two", durationMs = 200_000)
        val y1 = Track("y1", Source.YOUTUBE_MUSIC, "Three", durationMs = 200_000)
        p.play(listOf(s1, s2, y1), 0)

        until("started") { fake.calls.isNotEmpty() }
        assertEquals("start:s1,s2:true", fake.calls.first())
        until("shown as Spotify") { p.state.value.externalName == "Spotify" }

        // Spotify moved on to the next song of the run by itself
        fake.now = ExternalState("s2", 1_000, 200_000, true)
        until("queue follows") { p.state.value.current?.id == "s2" }
        assertEquals(1, p.state.value.index)

        p.pause()
        until("pause sent") { "pause" in fake.calls }
        assertFalse(p.state.value.isPlaying)

        // a YouTube Music song plays here again and Spotify gets paused
        val before = fake.calls.count { it == "pause" }
        p.next()
        until("left Spotify") { p.state.value.externalName == null && fake.calls.count { it == "pause" } > before }
        assertEquals("y1", p.state.value.current?.id)
        assertTrue(fake.calls.none { it.startsWith("start:y1") })
        assertNull(p.state.value.remoteName)
    }
}
