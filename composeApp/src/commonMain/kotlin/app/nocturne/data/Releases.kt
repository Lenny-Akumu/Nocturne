package app.nocturne.data

import app.nocturne.AppGraph
import app.nocturne.FileStore
import app.nocturne.model.CollectionType
import app.nocturne.model.MediaEntry
import app.nocturne.model.Source
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

expect fun notifyRelease(title: String, text: String)

class ReleaseWatcher(private val graph: AppGraph) {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(String.serializer(), String.serializer())

    suspend fun check() {
        val seen = FileStore.read(FILE)?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty().toMutableMap()
        val artists = graph.library.state.value.saved
            .filter { it.type == CollectionType.ARTIST && it.source == Source.YOUTUBE_MUSIC }
            .take(40)
        val fresh = mutableListOf<Pair<String, String>>()
        for (artist in artists) {
            val page = runCatching { graph.provider(Source.YOUTUBE_MUSIC).collection(artist) }.getOrNull() ?: continue
            val newest = page.sections
                .filter { s -> listOf("album", "single", "release", "альбом", "сингл").any { it in s.title.lowercase() } }
                .flatMap { it.items }
                .filterIsInstance<MediaEntry.Collection>()
                .firstOrNull()?.ref ?: continue
            val previous = seen[artist.id]
            if (previous != null && previous != newest.id) fresh += page.ref.title to newest.title
            seen[artist.id] = newest.id
        }
        FileStore.write(FILE, json.encodeToString(serializer, seen))
        when {
            fresh.size == 1 -> notifyRelease("New from ${fresh[0].first}", fresh[0].second)
            fresh.size > 1 -> notifyRelease("${fresh.size} new releases", fresh.joinToString(", ") { "${it.first} — ${it.second}" })
        }
    }

    companion object {
        private const val FILE = "releases.json"
    }
}
