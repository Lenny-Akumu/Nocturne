package app.nocturne

import app.nocturne.model.ArtistRef
import app.nocturne.model.CollectionRef
import app.nocturne.model.CollectionType
import app.nocturne.model.MediaEntry
import app.nocturne.model.SearchFilter
import app.nocturne.model.Source
import app.nocturne.model.Track
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test

// Real network: runs only with LIVE=1, prints song facts and artist play counts.
class DetailsLiveTest {
    @Test
    fun live() = runBlocking {
        if (System.getenv("LIVE") == null) return@runBlocking
        AppDirs.root = java.io.File(System.getProperty("java.io.tmpdir"), "nocturne-live").apply { mkdirs() }
        val g = AppGraph.init(app.nocturne.player.DemoAudioEngine())
        suspend fun <T> probe(name: String, block: suspend () -> T) =
            println("LIVE $name -> " + runCatching { withTimeout(45_000) { block() } }.fold({ "$it" }, { "ERR ${it::class.simpleName}: ${it.message}" }))
        val artist = runCatching { g.youtube.collection(CollectionRef("UC0aXrjVxG5pZr99v77wZdPQ", Source.YOUTUBE_MUSIC, CollectionType.ARTIST, "Imagine Dragons")) }
        println("LIVE artist page: " + artist.fold({ p -> "${p.tracks.size} tracks, plays=${p.plays.size}" }, { "ERR $it" }))
        artist.getOrNull()?.tracks?.take(2)?.forEach { song -> probe("ytm song ${song.title} ${song.id} album=${song.albumId}") { g.youtube.details(song) } }
        probe("ytm video") { g.youtube.details(Track("7wtfhZwyrcc", Source.YOUTUBE_MUSIC, "Believer")) }
        val url = "https://soundcloud.com/forss/flickermood"
        probe("sc") { g.soundcloud.details(Track(id = url, source = Source.SOUNDCLOUD, title = "Flickermood", artists = listOf(ArtistRef("Forss")), url = url)) }
        probe("ytm artist") {
            val p = g.youtube.collection(CollectionRef("UC0aXrjVxG5pZr99v77wZdPQ", Source.YOUTUBE_MUSIC, CollectionType.ARTIST, "Imagine Dragons"))
            p.tracks.take(7).map { it.title + " = " + p.plays[it.key] }
        }
        probe("sc artist") {
            val p = g.soundcloud.collection(CollectionRef("https://soundcloud.com/forss", Source.SOUNDCLOUD, CollectionType.ARTIST, "Forss"))
            p.tracks.take(5).map { it.title + " = " + p.plays[it.key] }
        }
    }
}
