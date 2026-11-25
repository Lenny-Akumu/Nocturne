package app.nocturne.data

import app.nocturne.AppDirs
import app.nocturne.model.Source
import app.nocturne.model.Track
import kotlin.test.Test
import kotlin.test.assertEquals

class LikedSyncTest {
    private fun yt(id: String) = Track(id, Source.YOUTUBE_MUSIC, id)
    private fun sp(id: String) = Track(id, Source.SPOTIFY, id)

    private fun libraryWith(liked: List<Track>): Library {
        AppDirs.root = java.io.File(System.getProperty("java.io.tmpdir"), "nocturne-likes-" + System.nanoTime()).apply { mkdirs() }
        return Library(SettingsStore()).also { lib -> lib.applySync { it.copy(liked = liked) } }
    }

    @Test
    fun recentLikesStuckAtTheBottomMoveBackUp() {
        // what older versions left: a fresh local like on top, the first synced batch, a Spotify like,
        // then the YouTube likes found later appended at the very end (the gap)
        val lib = libraryWith(listOf(sp("fresh"), yt("r4"), yt("r5"), sp("s1"), yt("r6"), yt("r1"), yt("r2"), yt("r3")))
        lib.syncLiked(listOf(yt("r1"), yt("r2"), yt("r3"), yt("r4"), yt("r5"), yt("r6"), yt("r0")))
        assertEquals(listOf("fresh", "r1", "r2", "r3", "r4", "r5", "s1", "r6", "r0"), lib.state.value.liked.map { it.id })
    }

    @Test
    fun nothingChangesWhenAlreadyInOrder() {
        val lib = libraryWith(listOf(yt("r1"), sp("s1"), yt("r2")))
        lib.syncLiked(listOf(yt("r1"), yt("r2")))
        assertEquals(listOf("r1", "s1", "r2"), lib.state.value.liked.map { it.id })
    }
}
