package app.nocturne.data

import app.nocturne.model.ArtistRef
import app.nocturne.model.Source
import app.nocturne.model.Track
import app.nocturne.source.YouTubeMusicProvider.Companion.matchScore
import app.nocturne.source.YouTubeMusicProvider.Companion.sure
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test
import kotlin.test.assertEquals

class MatchTest {
    private fun yt(title: String, artist: String, sec: Int, album: String? = null) =
        Track(title, Source.YOUTUBE_MUSIC, title, listOf(ArtistRef(artist)), album = album, durationMs = sec * 1000L)

    private fun pick(title: String, artists: List<String>, sec: Int, vararg c: Track) =
        c.maxBy { matchScore(title, artists, sec * 1000L, it) }.title

    @Test
    fun theVersionInBracketsDecides() {
        val plain = yt("never sorry", "bullish", 150)
        val hard = yt("never sorry (hardtekk)", "bullish", 136)
        assertEquals("never sorry (hardtekk)", pick("never sorry (hardtekk)", listOf("bullish"), 136, plain, hard))
        assertEquals("never sorry", pick("never sorry", listOf("bullish"), 150, hard, plain))
    }

    @Test
    fun originalOverSpedUpAndLive() {
        val orig = yt("Believer", "Imagine Dragons", 204)
        val sped = yt("Believer (Sped Up)", "Imagine Dragons", 170)
        val live = yt("Believer - Live", "Imagine Dragons", 210)
        assertEquals("Believer", pick("Believer", listOf("Imagine Dragons"), 204, sped, live, orig))
        assertEquals("Believer (Sped Up)", pick("Believer - Sped Up", listOf("Imagine Dragons"), 170, orig, sped))
    }

    @Test
    fun rightArtistAndLength() {
        val cover = yt("Creep", "Postmodern Jukebox", 290)
        val real = yt("Creep", "Radiohead", 238)
        assertEquals("Creep", pick("Creep", listOf("Radiohead"), 238, cover, real))
        assertEquals(real, listOf(cover, real).maxBy { matchScore("Creep", listOf("Radiohead"), 238_000, it) })
    }

    @Test
    fun aDifferentSongIsNeverGoodEnough() {
        assertFalse(sure("Обійми", listOf("Океан Ельзи"), 254_000, yt("Не П'яна - Закохана", "Alena Omargalieva", 228)))
        assertFalse(sure("Creep", listOf("Radiohead"), 238_000, yt("Creep", "Postmodern Jukebox", 238)))
        assertFalse(sure("Believer", listOf("Imagine Dragons"), 204_000, yt("Believer", "Imagine Dragons", 300)))
        assertTrue(sure("Обійми", listOf("Океан Ельзи"), 254_000, yt("Обійми", "Океан Ельзи", 253)))
        assertTrue(sure("Bohemian Rhapsody - Remastered 2011", listOf("Queen"), 354_000, yt("Bohemian Rhapsody", "Queen", 355)))
    }
}
