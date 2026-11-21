package app.nocturne

import app.nocturne.model.ArtistRef
import app.nocturne.model.Source
import app.nocturne.model.Track
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test

// Real network: runs only with LIVE=1, prints how each comments source behaves.
class CommentsLiveTest {
    @Test
    fun live() = runBlocking {
        if (System.getenv("LIVE") == null) return@runBlocking
        AppDirs.root = java.io.File(System.getProperty("java.io.tmpdir"), "nocturne-live").apply { mkdirs() }
        val g = AppGraph.init(app.nocturne.player.DemoAudioEngine())
        suspend fun probe(name: String, block: suspend () -> app.nocturne.source.CommentsPage) {
            val start = System.currentTimeMillis()
            val r = runCatching { withTimeout(45_000) { block() } }
            val took = System.currentTimeMillis() - start
            println("LIVE $name ${took}ms -> " + r.fold({ "ok ${it.comments.size} next=${it.next != null}" }, { "ERR ${it::class.simpleName}: ${it.message}" }))
        }
        probe("yt rick") { g.comments("dQw4w9WgXcQ", null, "Rick Astley Never Gonna Give You Up") }
        probe("yt believer") { g.comments("7wtfhZwyrcc", null, "Imagine Dragons Believer") }
        val url = "https://soundcloud.com/forss/flickermood"
        val sc = Track(id = url, source = Source.SOUNDCLOUD, title = "Flickermood", artists = listOf(ArtistRef("Forss")), url = url)
        probe("sc") { g.soundcloud.comments(sc) }
    }
}
