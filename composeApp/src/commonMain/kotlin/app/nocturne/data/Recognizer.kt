package app.nocturne.data

import app.nocturne.AppGraph
import app.nocturne.model.MediaEntry
import app.nocturne.model.SearchFilter
import app.nocturne.model.Track
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Records [seconds] of microphone audio as a 16-bit mono WAV; null without permission or a mic. */
expect suspend fun recordMicWav(seconds: Int): ByteArray?

expect fun hasMicAccess(): Boolean

expect fun requestMicAccess(onResult: (Boolean) -> Unit)

class Recognized(val title: String, val artist: String, val album: String?, val match: Track?)

object Recognizer {
    private val json = Json { ignoreUnknownKeys = true }

    sealed interface Outcome {
        data class Found(val song: Recognized) : Outcome
        data object NotFound : Outcome
        data class Failed(val message: String) : Outcome
    }

    suspend fun identify(): Outcome {
        val wav = recordMicWav(8) ?: return Outcome.Failed("Couldn't use the microphone")
        val token = AppGraph.instance.settings.value.auddToken
        val body = runCatching {
            AppGraph.instance.http.submitFormWithBinaryData("https://api.audd.io/", formData {
                if (token.isNotBlank()) append("api_token", token)
                append("file", wav, Headers.build {
                    append(HttpHeaders.ContentType, "audio/wav")
                    append(HttpHeaders.ContentDisposition, "filename=\"clip.wav\"")
                })
            }).bodyAsText()
        }.getOrElse { return Outcome.Failed("No connection") }
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return Outcome.Failed("Recognition service didn't answer")
        if (root["status"]?.jsonPrimitive?.contentOrNull != "success") {
            val msg = (root["error"] as? JsonObject)?.get("error_message")?.jsonPrimitive?.contentOrNull
            return Outcome.Failed(msg ?: "Recognition failed")
        }
        val r = root["result"] as? JsonObject ?: return Outcome.NotFound
        val title = r["title"]?.jsonPrimitive?.contentOrNull ?: return Outcome.NotFound
        val artist = r["artist"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val album = r["album"]?.jsonPrimitive?.contentOrNull
        return Outcome.Found(Recognized(title, artist, album, find(title, artist)))
    }

    private suspend fun find(title: String, artist: String): Track? {
        for (p in AppGraph.instance.enabledProviders()) {
            val hit = runCatching { p.search("$artist $title", SearchFilter.SONGS) }.getOrNull()
                ?.filterIsInstance<MediaEntry.Song>()?.firstOrNull()?.track
            if (hit != null) return hit
        }
        return null
    }
}
