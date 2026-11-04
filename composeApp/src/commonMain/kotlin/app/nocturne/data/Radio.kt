package app.nocturne.data

import app.nocturne.AppGraph
import app.nocturne.BuildInfo
import app.nocturne.model.ArtistRef
import app.nocturne.model.Source
import app.nocturne.model.Track
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class RadioStation(
    val id: String,
    val name: String,
    val url: String,
    val favicon: String? = null,
    val tags: String = "",
    val country: String = "",
    val codec: String = "",
    val bitrate: Int = 0,
)

data class RadioCountry(val code: String, val name: String, val stations: Int)

/** Live stations play as plain stream URLs through the local source; albumId marks them. */
val Track.isRadio: Boolean get() = source == Source.LOCAL && albumId?.startsWith("radio:") == true

/** Internet radio from the open Radio Browser directory (radio-browser.info). */
object Radio {
    private val json = Json { ignoreUnknownKeys = true }
    private val servers = listOf("de1", "de2", "fi1", "at1", "nl1").map { "https://$it.api.radio-browser.info" }
    private var countries: List<RadioCountry>? = null
    private val byCountry = mutableMapOf<String, List<RadioStation>>()

    private suspend fun get(path: String, params: Map<String, Any> = emptyMap()): JsonArray? {
        for (base in servers.shuffled()) {
            val arr = runCatching {
                val r = AppGraph.instance.http.get(base + path) {
                    header("User-Agent", "Nocturne/${BuildInfo.VERSION}")
                    params.forEach { (k, v) -> parameter(k, v) }
                }
                if (r.status.isSuccess()) json.parseToJsonElement(r.bodyAsText()) as? JsonArray else null
            }.getOrNull()
            if (arr != null) return arr
        }
        return null
    }

    suspend fun countries(): List<RadioCountry> {
        countries?.let { return it }
        val list = get("/json/countries", mapOf("hidebroken" to "true")).orEmpty().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val code = o["iso_3166_1"]?.jsonPrimitive?.contentOrNull?.uppercase()?.takeIf { it.length == 2 } ?: return@mapNotNull null
            val count = o["stationcount"]?.jsonPrimitive?.intOrNull ?: 0
            if (count == 0) return@mapNotNull null
            RadioCountry(code, o["name"]?.jsonPrimitive?.contentOrNull ?: code, count)
        }.groupBy { it.code }.map { (code, l) -> RadioCountry(code, l.maxBy { it.stations }.name, l.sumOf { it.stations }) }
            .sortedBy { it.name.lowercase() }
        if (list.isNotEmpty()) countries = list
        return list
    }

    /** Most popular working stations in [code] first. */
    suspend fun stations(code: String, refresh: Boolean = false): List<RadioStation> {
        if (!refresh) byCountry[code]?.let { return it }
        val list = parse(get("/json/stations/bycountrycodeexact/$code", mapOf("hidebroken" to "true", "order" to "clickcount", "reverse" to "true", "limit" to 300)))
        if (list.isNotEmpty()) byCountry[code] = list
        return list
    }

    suspend fun search(name: String, code: String? = null): List<RadioStation> {
        val params = buildMap<String, Any> {
            put("name", name); put("hidebroken", "true"); put("order", "clickcount"); put("reverse", "true"); put("limit", 100)
            if (!code.isNullOrBlank()) put("countrycode", code)
        }
        return parse(get("/json/stations/search", params))
    }

    private fun parse(arr: JsonArray?): List<RadioStation> = arr.orEmpty().mapNotNull { e ->
        val o = e as? JsonObject ?: return@mapNotNull null
        fun s(k: String) = o[k]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        val url = s("url_resolved") ?: s("url") ?: return@mapNotNull null
        RadioStation(
            id = s("stationuuid") ?: url,
            name = s("name") ?: return@mapNotNull null,
            url = url,
            favicon = s("favicon")?.takeIf { it.startsWith("http") },
            tags = s("tags").orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.take(3).joinToString(" · "),
            country = s("countrycode").orEmpty().uppercase(),
            codec = s("codec").orEmpty(),
            bitrate = o["bitrate"]?.jsonPrimitive?.intOrNull ?: 0,
        )
    }.distinctBy { it.url }

    fun toTrack(st: RadioStation) = Track(
        id = st.url, source = Source.LOCAL, title = st.name,
        artists = listOf(ArtistRef(st.tags.ifBlank { app.nocturne.i18n.t("Radio") })),
        album = app.nocturne.i18n.t("Radio"), albumId = "radio:${st.id}", artworkUrl = st.favicon,
    )

    /** Plays [list] as a queue so next/previous flip between stations. */
    fun play(list: List<RadioStation>, index: Int) {
        val graph = AppGraph.instance
        graph.player.play(list.map(::toTrack), index, app.nocturne.i18n.t("Radio"))
        graph.settings.update { s -> s.copy(radioRecent = (listOf(list[index]) + s.radioRecent.filterNot { it.id == list[index].id }).take(20)) }
        // the directory's popularity counter
        val id = list[index].id
        graph.scope.launch { runCatching { get("/json/url/$id") } }
    }

    fun toggleFavorite(st: RadioStation) = AppGraph.instance.settings.update { s ->
        s.copy(radioFavorites = if (s.radioFavorites.any { it.id == st.id }) s.radioFavorites.filterNot { it.id == st.id } else s.radioFavorites + st)
    }
}

/** Regional-indicator flag emoji for a country code. */
fun flagOf(code: String): String =
    if (code.length != 2 || !code.all { it.isLetter() }) "" else code.uppercase().map { c ->
        val cp = 0x1F1E6 + (c - 'A') - 0x10000
        charArrayOf((0xD800 + (cp shr 10)).toChar(), (0xDC00 + (cp and 0x3FF)).toChar()).concatToString()
    }.joinToString("")
