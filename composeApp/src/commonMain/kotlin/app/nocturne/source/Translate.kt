package app.nocturne.source

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray

/** Line-by-line lyric translation through Google's public web endpoint. */
class Translator(private val http: HttpClient) {
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = mutableMapOf<String, List<String>?>()

    /** null when the text is already in [target] or the service failed. */
    suspend fun lines(lines: List<String>, target: String): List<String>? {
        val key = target + "\u0000" + lines.joinToString("\n")
        if (key in cache) return cache[key]
        val out = ArrayList<String>(lines.size)
        var foreign = false
        // keep each request short enough for a GET
        for (chunk in chunks(lines)) {
            val (text, lang) = request(chunk.joinToString("\n"), target) ?: return null
            if (lang != null && lang != target) foreign = true
            val got = text.split('\n')
            chunk.forEachIndexed { i, src -> out += if (src.isBlank()) "" else got.getOrNull(i)?.trim().orEmpty() }
        }
        val result = out.takeIf { foreign }
        cache[key] = result
        return result
    }

    private fun chunks(lines: List<String>): List<List<String>> {
        val res = mutableListOf<MutableList<String>>(mutableListOf())
        var len = 0
        for (l in lines) {
            if (len + l.length > 1500 && res.last().isNotEmpty()) { res += mutableListOf<String>(); len = 0 }
            res.last() += l
            len += l.length + 1
        }
        return res
    }

    private suspend fun request(text: String, target: String): Pair<String, String?>? = runCatching {
        val r = http.get("https://translate.googleapis.com/translate_a/single") {
            parameter("client", "gtx")
            parameter("sl", "auto")
            parameter("tl", target)
            parameter("dt", "t")
            parameter("q", text)
        }
        if (!r.status.isSuccess()) return null
        val root = json.parseToJsonElement(r.bodyAsText()).jsonArray
        val parts = (root[0] as? JsonArray).orEmpty().mapNotNull { ((it as? JsonArray)?.getOrNull(0) as? JsonPrimitive)?.contentOrNull }
        parts.joinToString("") to (root.getOrNull(2) as? JsonPrimitive)?.contentOrNull
    }.getOrNull()
}
