package app.nocturne.data

import app.nocturne.BuildInfo
import app.nocturne.PlatformKind
import app.nocturne.platformKind
import app.nocturne.source.path
import app.nocturne.source.str
import app.nocturne.source.num
import app.nocturne.source.arr
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

data class UpdateInfo(val version: String, val notes: String, val url: String, val fileName: String, val size: Long)

sealed interface UpdateState {
    data object Idle : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class Failed(val info: UpdateInfo, val message: String) : UpdateState
}

expect suspend fun downloadAndInstall(url: String, fileName: String, onProgress: (Float) -> Unit)

class Updater(private val http: HttpClient, private val settings: SettingsStore) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    suspend fun check(manual: Boolean): UpdateInfo? {
        val response = http.get("https://api.github.com/repos/${BuildInfo.REPO}/releases/latest") {
            header("Accept", "application/vnd.github+json")
        }
        if (!response.status.isSuccess()) {
            if (manual) error("GitHub: ${response.status}")
            return null
        }
        val release = json.parseToJsonElement(response.bodyAsText())
        val version = release.path("tag_name").str?.removePrefix("v") ?: return null
        if (compare(version, BuildInfo.VERSION) <= 0) return null
        if (!manual && settings.value.skippedVersion == version) return null
        val asset = release.path("assets").arr.orEmpty().firstOrNull { a ->
            val name = a.path("name").str.orEmpty()
            when (platformKind) {
                PlatformKind.ANDROID -> name.endsWith(".apk")
                PlatformKind.DESKTOP -> name.contains("Setup", ignoreCase = true) && name.endsWith(".exe")
            }
        } ?: return null
        val info = UpdateInfo(
            version = version,
            notes = release.path("body").str.orEmpty(),
            url = asset.path("browser_download_url").str ?: return null,
            fileName = asset.path("name").str ?: "Nocturne-update",
            size = asset.path("size").num ?: 0,
        )
        _state.value = UpdateState.Available(info)
        return info
    }

    suspend fun install(info: UpdateInfo) {
        _state.value = UpdateState.Downloading(info, 0f)
        runCatching {
            downloadAndInstall(info.url, info.fileName) { p -> _state.value = UpdateState.Downloading(info, p) }
        }.onSuccess {
            _state.value = UpdateState.Idle
        }.onFailure {
            _state.value = UpdateState.Failed(info, it.message ?: "Download failed")
        }
    }

    fun later() {
        _state.value = UpdateState.Idle
    }

    fun skip(info: UpdateInfo) {
        settings.update { it.copy(skippedVersion = info.version) }
        _state.value = UpdateState.Idle
    }

    companion object {
        fun compare(a: String, b: String): Int {
            val pa = a.split('.', '-').map { it.toIntOrNull() ?: 0 }
            val pb = b.split('.', '-').map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(pa.size, pb.size)) {
                val d = (pa.getOrElse(i) { 0 }).compareTo(pb.getOrElse(i) { 0 })
                if (d != 0) return d
            }
            return 0
        }
    }
}
