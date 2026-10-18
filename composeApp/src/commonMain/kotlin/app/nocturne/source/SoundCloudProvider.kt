package app.nocturne.source

import app.nocturne.model.AudioQuality
import app.nocturne.model.CollectionPage
import app.nocturne.model.CollectionRef
import app.nocturne.model.CollectionType
import app.nocturne.model.MediaEntry
import app.nocturne.model.ResolvedStream
import app.nocturne.model.SearchFilter
import app.nocturne.model.Section
import app.nocturne.model.SectionLayout
import app.nocturne.model.Source
import app.nocturne.model.Track

class SoundCloudProvider(private val newPipe: NewPipeBackend) : MusicProvider {
    override val source = Source.SOUNDCLOUD

    override suspend fun search(query: String, filter: SearchFilter) = newPipe.search(source, query, filter)

    override suspend fun home(): List<Section> {
        val charts = runCatching { newPipe.charts(source) }.getOrDefault(emptyList())
        if (charts.isEmpty()) return emptyList()
        return listOf(Section("SoundCloud charts", charts.map { MediaEntry.Song(it) }, SectionLayout.QUICK_PICKS, source))
    }

    override suspend fun collection(ref: CollectionRef): CollectionPage = when (ref.type) {
        CollectionType.ARTIST -> newPipe.channel(source, ref.id, ref)
        else -> newPipe.playlist(source, ref.id, ref)
    }

    override suspend fun stream(track: Track, quality: AudioQuality): ResolvedStream =
        newPipe.stream(source, track.url ?: track.id, quality)

    override suspend fun related(track: Track): List<Track> =
        runCatching { newPipe.related(source, track.url ?: track.id) }.getOrDefault(emptyList())

    override fun canOpen(url: String) = url.contains("soundcloud.com")

    override suspend fun open(url: String): MediaEntry? {
        val clean = url.substringBefore('?').trimEnd('/')
        val path = clean.substringAfter("soundcloud.com/", "")
        val segments = path.split('/').filter { it.isNotEmpty() }
        return when {
            segments.size >= 3 && segments[1] == "sets" -> MediaEntry.Collection(newPipe.playlist(source, clean, null).ref)
            segments.size == 1 -> MediaEntry.Collection(newPipe.channel(source, clean, null).ref)
            segments.size >= 2 -> MediaEntry.Song(newPipe.track(source, clean))
            else -> null
        }
    }
}
