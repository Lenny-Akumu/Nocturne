package app.nocturne.source

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

fun JsonElement?.path(vararg keys: Any): JsonElement? {
    var cur: JsonElement? = this
    for (k in keys) {
        cur = when (k) {
            is String -> (cur as? JsonObject)?.get(k)
            is Int -> (cur as? JsonArray)?.getOrNull(k)
            else -> null
        } ?: return null
    }
    return cur
}

val JsonElement?.str: String? get() = (this as? JsonPrimitive)?.takeIf { it.isString }?.content
val JsonElement?.num: Long? get() = (this as? JsonPrimitive)?.content?.toLongOrNull()
val JsonElement?.bool: Boolean? get() = (this as? JsonPrimitive)?.content?.toBooleanStrictOrNull()
val JsonElement?.arr: JsonArray? get() = this as? JsonArray
val JsonElement?.obj: JsonObject? get() = this as? JsonObject

fun JsonElement?.runsText(): String? =
    path("runs").arr?.joinToString("") { it.path("text").str.orEmpty() }?.takeIf { it.isNotBlank() }
        ?: path("simpleText").str

fun JsonElement.findAll(key: String, out: MutableList<JsonElement> = mutableListOf()): List<JsonElement> {
    when (this) {
        is JsonObject -> for ((k, v) in this) {
            if (k == key) out += v else v.findAll(key, out)
        }
        is JsonArray -> for (v in this) v.findAll(key, out)
        else -> Unit
    }
    return out
}

fun JsonElement?.bestThumbnail(): String? {
    val list = this.arr ?: return null
    val url = list.lastOrNull().path("url").str ?: return null
    return upscaleArtwork(url)
}

fun upscaleArtwork(url: String): String = when {
    url.contains("googleusercontent.com") || url.contains("ggpht.com") ->
        url.replace(Regex("=w\\d+-h\\d+.*$"), "=w544-h544-l90-rj")
    url.contains("sndcdn.com") -> url.replace(Regex("-(large|t\\d+x\\d+|small|badge|tiny|mini)\\."), "-t500x500.")
    else -> url
}

fun parseClock(text: String?): Long {
    if (text.isNullOrBlank()) return 0
    val parts = text.trim().split(":").mapNotNull { it.toLongOrNull() }
    if (parts.isEmpty()) return 0
    return parts.fold(0L) { acc, v -> acc * 60 + v } * 1000
}
