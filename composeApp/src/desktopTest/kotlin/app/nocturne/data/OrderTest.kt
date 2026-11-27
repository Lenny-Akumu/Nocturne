package app.nocturne.data

import app.nocturne.model.Source
import app.nocturne.ui.screens.LibraryTab
import app.nocturne.ui.screens.libraryTabs
import kotlin.test.Test
import kotlin.test.assertEquals

class OrderTest {
    @Test
    fun sourcesFollowTheUsersOrder() {
        assertEquals(listOf(Source.YOUTUBE_MUSIC, Source.SPOTIFY, Source.SOUNDCLOUD), orderedSources(emptyList()))
        assertEquals(listOf(Source.SPOTIFY, Source.YOUTUBE_MUSIC, Source.SOUNDCLOUD), orderedSources(listOf(Source.SPOTIFY)))
        assertEquals(listOf(Source.SOUNDCLOUD, Source.SPOTIFY, Source.YOUTUBE_MUSIC), orderedSources(listOf(Source.SOUNDCLOUD, Source.LOCAL, Source.SPOTIFY, Source.YOUTUBE_MUSIC)))
    }

    @Test
    fun libraryTabsKeepUnknownAndNewOnes() {
        val tabs = libraryTabs(listOf("SMART", "gone", "SONGS"))
        assertEquals(LibraryTab.SMART, tabs.first())
        assertEquals(LibraryTab.SONGS, tabs[1])
        assertEquals(LibraryTab.entries.size, tabs.size)
    }
}
