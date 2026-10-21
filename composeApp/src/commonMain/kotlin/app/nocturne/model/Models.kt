package app.nocturne.model

import kotlinx.serialization.Serializable

@Serializable
enum class Source(val label: String) {
    YOUTUBE_MUSIC("YouTube Music"),
    SPOTIFY("Spotify"),
    SOUNDCLOUD("SoundCloud"),
    LOCAL("On device"),
}

val Source.isOnline: Boolean get() = this != Source.LOCAL

@Serializable
data class ArtistRef(val name: String, val id: String? = null)

@Serializable
data class Track(
    val id: String,
    val source: Source,
    val title: String,
    val artists: List<ArtistRef> = emptyList(),
    val album: String? = null,
    val albumId: String? = null,
    val durationMs: Long = 0,
    val artworkUrl: String? = null,
    val explicit: Boolean = false,
    val url: String? = null,
) {
    val key: String get() = "${source.name}:$id"
    val artistNames: String get() = artists.joinToString(", ") { it.name }
}

@Serializable
enum class CollectionType { ALBUM, PLAYLIST, ARTIST, LOCAL }

@Serializable
data class CollectionRef(
    val id: String,
    val source: Source,
    val type: CollectionType,
    val title: String,
    val subtitle: String? = null,
    val artworkUrl: String? = null,
) {
    val key: String get() = "${source.name}:${type.name}:$id"
}

sealed interface MediaEntry {
    data class Song(val track: Track) : MediaEntry
    data class Collection(val ref: CollectionRef) : MediaEntry
}

enum class SectionLayout { CAROUSEL, QUICK_PICKS }

data class Section(
    val title: String,
    val items: List<MediaEntry>,
    val layout: SectionLayout = SectionLayout.CAROUSEL,
    val source: Source? = null,
)

data class CollectionPage(
    val ref: CollectionRef,
    val tracks: List<Track>,
    val description: String? = null,
    val sections: List<Section> = emptyList(),
)

enum class SearchFilter(val label: String) {
    SONGS("Songs"),
    ALBUMS("Albums"),
    PLAYLISTS("Playlists"),
    ARTISTS("Artists"),
}

@Serializable
enum class AudioQuality(val label: String) { LOW("Low"), NORMAL("Normal"), HIGH("High") }

data class ResolvedStream(
    val url: String,
    val mimeType: String? = null,
    val isHls: Boolean = false,
    val bitrate: Int = 0,
    val codec: String? = null,
)

fun Long.formatDuration(): String {
    if (this <= 0) return "--:--"
    val total = this / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "$h:${m.pad()}:${s.pad()}" else "$m:${s.pad()}"
}

private fun Long.pad() = toString().padStart(2, '0')

data class AccountLibrary(
    val source: Source,
    val liked: CollectionRef? = null,
    val playlists: List<CollectionRef> = emptyList(),
    val albums: List<CollectionRef> = emptyList(),
    val artists: List<CollectionRef> = emptyList(),
)
