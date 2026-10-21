package app.nocturne

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.nocturne.model.Source
import app.nocturne.model.Track

object DiscordPresence {
    var status by mutableStateOf("")

    fun fill(template: String, track: Track): String = template
        .replace("{title}", track.title)
        .replace("{artist}", track.artistNames)
        .replace("{album}", track.album.orEmpty())
        .replace("{source}", track.source.label)
        .replace("{app}", "Nocturne")
        .replace(Regex("\\s+"), " ")
        .trim()

    fun sourceIcon(source: Source): String {
        val name = when (source) {
            Source.YOUTUBE_MUSIC -> "youtubemusic"
            Source.SPOTIFY -> "spotify"
            Source.SOUNDCLOUD -> "soundcloud"
            Source.LOCAL -> "youtubemusic"
        }
        return "https://raw.githubusercontent.com/lenny-akumu/nocturne/main/docs/rpc/$name.png"
    }
}
