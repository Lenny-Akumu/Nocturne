package app.nocturne.sync

import app.nocturne.AppDirs
import app.nocturne.data.Library
import app.nocturne.data.SettingsStore
import app.nocturne.model.Source
import app.nocturne.model.Track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PulledLikesSyncTest {
    private fun sp(id: String) = Track(id, Source.SPOTIFY, id)

    private class Device(platformLikes: Boolean) {
        init { AppDirs.root = java.io.File(System.getProperty("java.io.tmpdir"), "nocturne-dev-" + System.nanoTime()).apply { mkdirs() } }
        val settings = SettingsStore().also { s -> s.update { it.copy(platformLikes = platformLikes) } }
        val library = Library(settings)
        val sync = LibrarySync(library, settings)
    }

    @Test
    fun nocturneOnlyDeviceTakesHeartsButNotServiceLikes() {
        val pc = Device(platformLikes = true)
        pc.library.syncLiked(listOf(sp("a"), sp("b")))
        pc.library.toggleLike(sp("mine"))
        val phone = Device(platformLikes = false)
        phone.sync.merge(pc.sync.snapshot())
        assertEquals(listOf("mine"), phone.library.state.value.liked.map { it.id })

        // a synced device takes everything and remembers which came from the service
        val tablet = Device(platformLikes = true)
        tablet.sync.merge(pc.sync.snapshot())
        assertEquals(setOf("mine", "a", "b"), tablet.library.state.value.liked.map { it.id }.toSet())
        assertEquals(setOf("SPOTIFY:a", "SPOTIFY:b"), tablet.library.state.value.pulledLikes)
    }

    @Test
    fun goingNocturneOnlyDoesNotDeleteOnOtherDevices() {
        val pc = Device(platformLikes = true)
        pc.library.syncLiked(listOf(sp("a"), sp("b")))
        pc.library.toggleLike(sp("mine"))
        pc.sync.takeDelta()
        pc.library.dropPulledLikes()
        val delta = pc.sync.takeDelta()
        assertTrue(delta == null || delta.stamps.none { it.value < 0 }, "no removals sent: $delta")
        assertEquals(listOf("mine"), pc.library.state.value.liked.map { it.id })
    }
}
