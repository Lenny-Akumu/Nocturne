package app.nocturne

import app.nocturne.model.MediaEntry
import app.nocturne.model.SearchFilter
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test

// Real network: LIVE=1 prints what each song search returns and which upload we'd pick.
class MatchLiveTest {
    @Test
    fun live() = runBlocking {
        if (System.getenv("LIVE") == null) return@runBlocking
        AppDirs.root = java.io.File(System.getProperty("java.io.tmpdir"), "nocturne-live").apply { mkdirs() }
        val g = AppGraph.init(app.nocturne.player.DemoAudioEngine())
        val songs = listOf(
            Triple("Blinding Lights", listOf("The Weeknd"), 200_040L),
            Triple("Believer", listOf("Imagine Dragons"), 204_346L),
            Triple("Bohemian Rhapsody - Remastered 2011", listOf("Queen"), 354_320L),
            Triple("Daylight", listOf("David Kushner"), 212_953L),
            Triple("Обійми", listOf("Океан Ельзи"), 254_000L),
        )
        for ((t, a, d) in songs) {
            val raw = runCatching { withTimeout(30_000) { g.youtube.search("$t ${a.joinToString(" ")}", SearchFilter.SONGS) } }
            println("LIVE q=$t raw=" + raw.fold({ l -> l.filterIsInstance<MediaEntry.Song>().take(5).map { "${it.track.title} | ${it.track.artistNames} | ${it.track.durationMs} | ${it.track.album}" } }, { "ERR $it" }))
            println("LIVE pick=" + runCatching { withTimeout(60_000) { g.youtube.bestMatch(t, a.first(), d, a)?.let { "${it.title} | ${it.artistNames} | ${it.durationMs} | ${it.id}" } } }.fold({ "$it" }, { "ERR $it" }))
        }
    }
}
