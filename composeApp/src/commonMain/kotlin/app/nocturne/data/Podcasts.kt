package app.nocturne.data

import kotlinx.serialization.Serializable

@Serializable
data class PodcastSub(val feedUrl: String, val title: String, val author: String = "", val artworkUrl: String? = null)
